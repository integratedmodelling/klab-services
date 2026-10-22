package org.integratedmodelling.klab.services.application.ui;

import static org.springframework.http.HttpStatus.*;

import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.view.application.*;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.view.ApplicationUiModel;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** User-isolated running instances, bounded attachment tickets and independent renderer leases. */
@Component
public final class ApplicationUiManager implements AutoCloseable {
  public record Request(String urn, String locale, String idempotencyKey, String resume) {}

  public record InstanceState(
      String id,
      String urn,
      org.integratedmodelling.klab.runtime.kactors.view.ApplicationUiSession.ResumeState state,
      Map<String, String> stylesheets) {}

  public record Ticket(String ticket, long expiresAt) {}

  private record Grant(
      String id, String origin, EngineAuthorization user, String credential, Instant expires) {}

  private static final class Instance {
    final String id, owner;
    final UiApplicationLauncher.Published published;
    final ApplicationBase agent;
    Instant detached;
    Connection connection;

    Instance(
        String id, String owner, UiApplicationLauncher.Published published, ApplicationBase agent) {
      this.id = id;
      this.owner = owner;
      this.published = published;
      this.agent = agent;
      detached = Instant.now();
    }
  }

  private final UiApplicationLauncher launcher;
  private final ServiceAuthorizationManager authentication;
  private final Duration retention;
  private final Map<String, Instance> instances = new HashMap<>();
  private final Map<String, String> creations = new HashMap<>();
  private final Map<String, Grant> tickets = new HashMap<>();
  private final ScheduledExecutorService maintenance =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofVirtual().name("kactors-ui-retention").factory());

  public ApplicationUiManager(
      UiApplicationLauncher launcher,
      ServiceAuthorizationManager authentication,
      @Value("${klab.ui.retention-seconds:1800}") long retentionSeconds) {
    this.launcher = launcher;
    this.authentication = authentication;
    if (retentionSeconds < 1) throw new IllegalArgumentException("UI retention must be positive");
    retention = Duration.ofSeconds(retentionSeconds);
    maintenance.scheduleWithFixedDelay(this::maintain, 10, 10, TimeUnit.SECONDS);
  }

  public synchronized InstanceState launch(Request request, EngineAuthorization user) {
    validate(user);
    if (request.urn() == null
        || request.urn().isBlank()
        || request.urn().length() > 1024
        || request.idempotencyKey() == null
        || !request.idempotencyKey().matches("[a-zA-Z0-9-]{1,100}"))
      throw new ResponseStatusException(BAD_REQUEST);
    if (request.resume() != null && !request.resume().isBlank()) {
      var existing = owned(request.resume(), user);
      if (!existing.published.urn().equals(request.urn()))
        throw new ResponseStatusException(CONFLICT);
      return state(existing);
    }
    String key = user.getFullyQualifiedUsername() + "/" + request.idempotencyKey();
    String previous = creations.get(key);
    if (previous != null) {
      var existing = owned(previous, user);
      if (!existing.published.urn().equals(request.urn()))
        throw new ResponseStatusException(CONFLICT);
      return state(existing);
    }
    if (instances.size() >= 128
        || instances.values().stream()
                .filter(i -> i.owner.equals(user.getFullyQualifiedUsername()))
                .count()
            >= 8)
      throw new ResponseStatusException(TOO_MANY_REQUESTS, "Application instance limit reached");
    var published = launcher.resolve(request.urn(), request.locale(), user);
    launcher.authorize(published, user);
    var agent = launcher.start(published, user);
    var instance =
        new Instance(
            UUID.randomUUID().toString(), user.getFullyQualifiedUsername(), published, agent);
    instances.put(instance.id, instance);
    creations.put(key, instance.id);
    return state(instance);
  }

  public synchronized InstanceState state(String id, EngineAuthorization user) {
    return state(owned(id, user));
  }

  private InstanceState state(Instance instance) {
    awaitInputs(instance);
    return new InstanceState(
        instance.id,
        instance.published.urn(),
        instance.agent.ui().session().resumeState(),
        instance.published.stylesheets());
  }

  public synchronized Ticket ticket(
      String id, String origin, EngineAuthorization user, String credential) {
    owned(id, user);
    if (credential == null || credential.isBlank()) throw new ResponseStatusException(UNAUTHORIZED);
    tickets.values().removeIf(grant -> !grant.expires().isAfter(Instant.now()));
    if (tickets.size() >= 1024) throw new ResponseStatusException(TOO_MANY_REQUESTS);
    String token = UUID.randomUUID() + "-" + UUID.randomUUID();
    Instant expiry = Instant.now().plusSeconds(30);
    tickets.put(token, new Grant(id, origin, user, credential, expiry));
    return new Ticket(token, expiry.toEpochMilli());
  }

  public synchronized Connection attach(
      String ticket, String origin, Consumer<Map<String, Object>> output, Runnable disconnect) {
    var grant = tickets.remove(ticket);
    if (grant == null
        || !grant.expires().isAfter(Instant.now())
        || !Objects.equals(origin, grant.origin())) throw new ResponseStatusException(UNAUTHORIZED);
    var user = refresh(grant.credential(), grant.user());
    var instance = owned(grant.id(), user);
    if (instance.connection != null) instance.connection.close();
    var connection = new Connection(instance, user, grant.credential(), output, disconnect);
    instance.connection = connection;
    instance.detached = null;
    try {
      awaitInputs(instance);
      instance.agent.ui().session().attach(connection.toolkit());
      connection.send(
          "ready",
          Map.of("lastAcceptedSequence", instance.agent.ui().session().lastAcceptedSequence()));
    } catch (RuntimeException error) {
      connection.close();
      throw error;
    }
    return connection;
  }

  public synchronized void stop(String id, EngineAuthorization user) {
    terminate(owned(id, user));
  }

  private void terminate(Instance instance) {
    if (instance.connection != null) instance.connection.close();
    var managed =
        org.integratedmodelling.klab.runtime.kactors.compiler.runtime.AgentRegistry.INSTANCE
            .getAgent(instance.agent.getUrn());
    if (managed != null) managed.stop();
    else instance.agent.stop();
    instances.remove(instance.id);
    creations.values().removeIf(instance.id::equals);
    tickets.values().removeIf(grant -> grant.id().equals(instance.id));
  }

  private Instance owned(String id, EngineAuthorization user) {
    validate(user);
    var instance = instances.get(id);
    if (instance == null || !instance.owner.equals(user.getFullyQualifiedUsername()))
      throw new ResponseStatusException(NOT_FOUND);
    launcher.authorize(instance.published, user);
    if (instance.agent.rootScope().isDone()) {
      terminate(instance);
      throw new ResponseStatusException(GONE);
    }
    return instance;
  }

  private void validate(EngineAuthorization user) {
    if (user == null
        || !user.isAuthenticated()
        || user.isAnonymous()
        || !(user.getScope() instanceof UserScope)
        || user.getExpiration() == null
        || !user.getExpiration().isAfter(Instant.now()))
      throw new ResponseStatusException(UNAUTHORIZED);
  }

  private EngineAuthorization refresh(String credential, EngineAuthorization user) {
    // Browser principals carry the upstream hub JWT; only the presented service credential
    // preserves the opaque browser session's expiration, revocation and isolation semantics.
    var refreshed = authentication.validateToken(credential, Map.of());
    validate(refreshed);
    if (!user.getFullyQualifiedUsername().equals(refreshed.getFullyQualifiedUsername()))
      throw new ResponseStatusException(UNAUTHORIZED);
    return refreshed;
  }

  private void awaitInputs(Instance instance) {
    try {
      instance.agent.ui().afterInput().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(SERVICE_UNAVAILABLE);
    } catch (ExecutionException | TimeoutException | RejectedExecutionException error) {
      throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Application input queue unavailable");
    }
  }

  private synchronized void maintain() {
    for (var instance : List.copyOf(instances.values())) {
      try {
        if (instance.agent.rootScope().isDone()
            || instance.detached != null
                && instance.detached.plus(retention).isBefore(Instant.now())) terminate(instance);
        else if (instance.connection != null) instance.connection.check();
      } catch (RuntimeException denied) {
        if (denied instanceof ResponseStatusException response
            && response.getStatusCode().value() == 401) {
          if (instance.connection != null) instance.connection.close();
        } else terminate(instance);
      }
    }
    tickets.values().removeIf(grant -> !grant.expires().isAfter(Instant.now()));
  }

  public final class Connection implements AutoCloseable {
    private final Instance instance;
    private EngineAuthorization user;
    private final String credential;
    private final Consumer<Map<String, Object>> output;
    private final Runnable disconnect;
    private Consumer<UiEvent> input;
    private boolean closed;
    private long rateWindow = System.nanoTime();
    private int messages;

    private Connection(
        Instance instance,
        EngineAuthorization user,
        String credential,
        Consumer<Map<String, Object>> output,
        Runnable disconnect) {
      this.instance = instance;
      this.user = user;
      this.credential = credential;
      this.output = output;
      this.disconnect = disconnect;
    }

    private UiToolkit toolkit() {
      return new UiToolkit() {
        public String id() {
          return "quasar";
        }

        public Set<String> supportedTypes() {
          return ApplicationUiModel.TYPES;
        }

        public View open(Consumer<UiEvent> receiver) {
          input = receiver;
          return new View() {
            public void render(UiSnapshot snapshot) {
              send("snapshot", snapshot);
            }

            public void close() {}
          };
        }
      };
    }

    private void send(String type, Object value) {
      output.accept(Map.of("type", type, "value", value));
    }

    public void event(UiEvent event) {
      synchronized (ApplicationUiManager.this) {
        checkActive();
        if (System.nanoTime() - rateWindow > TimeUnit.SECONDS.toNanos(1)) {
          messages = 0;
          rateWindow = System.nanoTime();
        }
        if (++messages > 60) throw new ResponseStatusException(TOO_MANY_REQUESTS);
        if (!instance.agent.ui().validInput(event)) {
          send("error", "Invalid widget input");
          return;
        }
        var session = instance.agent.ui().session();
        long before = session.lastAcceptedSequence();
        input.accept(event);
        long after = session.lastAcceptedSequence();
        if (after > before)
          instance
              .agent
              .ui()
              .afterInput()
              .whenComplete(
                  (ignored, failure) -> {
                    if (failure == null) send("ack", after);
                    else send("error", "Application input failed");
                  });
        else send("resync", session.resumeState());
      }
    }

    public void ping() {
      synchronized (ApplicationUiManager.this) {
        check();
        send("pong", true);
      }
    }

    private void checkActive() {
      validate(user);
      if (closed || instance.connection != this) throw new ResponseStatusException(GONE);
    }

    private void check() {
      checkActive();
      user = refresh(credential, user);
      launcher.authorize(instance.published, user);
    }

    @Override
    public void close() {
      synchronized (ApplicationUiManager.this) {
        if (closed) return;
        closed = true;
        if (instance.connection == this) {
          instance.connection = null;
          instance.detached = Instant.now();
          instance.agent.ui().session().detach();
        }
        disconnect.run();
      }
    }
  }

  @PreDestroy
  @Override
  public synchronized void close() {
    maintenance.shutdownNow();
    List.copyOf(instances.values()).forEach(this::terminate);
    tickets.clear();
    creations.clear();
  }
}
