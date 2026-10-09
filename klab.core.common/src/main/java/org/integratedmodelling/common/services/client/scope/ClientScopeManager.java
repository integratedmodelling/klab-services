package org.integratedmodelling.common.services.client.scope;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;

/**
 * A singleton used to keep track of scopes that were created within an instance. Differently from
 * the scope manager at service side, this only manages Session and Context scopes, from different
 * runtimes. User scopes are always obtained by authentication on the client side.
 *
 * <p>Wire IDs remain runtime-owned. Local keys include the hosting runtime so identical user
 * session IDs on different runtimes cannot alias. Creation and reconnection publish initialized
 * peers through {@link #register(ClientSessionScope)}; failed and derived scopes are never published.
 */
public enum ClientScopeManager {
  INSTANCE;

  private record ScopeKey(String runtimeId, String scopeId) {}
  private final Map<ScopeKey, ClientSessionScope> scopes = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  /**
   * Get an existing scope, interrogating the runtime if we don't have it cached.
   *
   * @param scopeId
   * @param scopeClass
   * @return
   * @param <T>
   */
  public <T extends SessionScope> T getScope(String scopeId, Class<T> scopeClass) {
    var matches = scopes.values().stream()
        .filter(s -> Objects.equals(scopeId, s.getId()) && scopeClass.isInstance(s)).toList();
    if (matches.size() > 1) {
      throw new KlabIllegalStateException("Scope ID is ambiguous across runtimes: " + scopeId);
    }
    return matches.isEmpty() ? null : scopeClass.cast(matches.getFirst());
  }

  public <T extends SessionScope> T getScope(String runtimeId, String scopeId, Class<T> scopeClass) {
    var scope = scopes.get(new ScopeKey(runtimeId, scopeId));
    return scopeClass.isInstance(scope) ? scopeClass.cast(scope) : null;
  }

  /**
   * Retrieves a context scope based on the provided runtime and configuration. Optionally, a new
   * scope may be created if it does not already exist. The requested configuration must exist on
   * the service and a connect call will be made to request connection.
   *
   * @param configuration the {@link DigitalTwin.Configuration} containing the configuration details
   *     for this scope. Persistence and other creation metadata are only relevant for scopes that
   *     are created by the call.
   * @param createIfMissing a boolean indicating whether to create a new scope if it does not
   *     already exist; set to true to create a new scope, false otherwise
   * @param requestingScope the requesting user scope
   * @return the {@link ContextScope} instance that matches the specified parameters, or null if not
   *     found and creation is not allowed
   */
  public ContextScope getContextScope(
      DigitalTwin.Configuration configuration, boolean createIfMissing, UserScope requestingScope) {

    if (configuration.getId() == null) {
      throw new KlabIllegalStateException("Cannot connect to remote scope: missing scope ID");
    }

    var service = findService(configuration, requestingScope);
    var existing = getScope(service.serviceId(), configuration.getId(), ClientContextScope.class);
    if (existing != null) return existing;
    if (createIfMissing) {

      /* issue a CONNECT call to the service to ensure we have rights and the scope exists. */
      if (!service.status().isOperational()) {
        requestingScope.error(
            "Cannot connect to remote scope for digital twin "
                + configuration.getName()
                + ": service is not operational");
        return null;
      }

      return service.connectContext(configuration, requestingScope);
    }

    return null;
  }

  private RuntimeService findService(
      DigitalTwin.Configuration configuration, UserScope requestingScope) {
    for (var runtime : requestingScope.getServices(RuntimeService.class)) {
      if ((configuration.getServiceId() != null
          && Objects.equals(runtime.serviceId(), configuration.getServiceId()))
          || (configuration.getServiceId() == null && configuration.getServiceUrl() != null
          && runtime.getUrl().toString().equals(configuration.getServiceUrl().toString()))) {
        return runtime;
      }
    }

    throw new KlabIllegalStateException("No available runtime matches digital twin " + configuration.getId());
    //    var newRuntime =
    //        ServiceClientCatalog.INSTANCE.getService(
    //            configuration.getUrl(), requestingScope.getIdentity(), SettingsImpl.forEngine());
    //
    //    // FIXME this will cause an exception as the client list is read-only. The client-side
    // services
    //    //  are ultimately stored in the engine - must deal with that.
    //    requestingScope.getServices(RuntimeService.class).add(newRuntime);
    //
    //    return newRuntime;
  }

  public synchronized void register(ClientSessionScope ret) {
    if (ret.isEmpty() || ret.getId() == null || ret.getId().isBlank()
        || ret.getHostServiceId() == null) {
      throw new KlabIllegalStateException("Cannot register an uninitialized client scope");
    }
    var key = new ScopeKey(ret.getHostServiceId(), ret.getId());
    var existing = scopes.get(key);
    if (existing != null && existing != ret) {
      throw new KlabIllegalStateException("A client peer is already registered for " + ret.getId());
    }
    if (existing == ret) return;
    if (ret instanceof ClientContextScope contextScope) {
      contextScope.createDigitalTwin(ret.getId());
      var engine = contextScope.getEngine();
      if (!engine.getSettings().get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class)) {
        /*
         * Create the default observer and schedule its resolution for a bit later, so that we ensure that
         * it is received after the client has registered the scope.
         */
        scheduler.schedule(contextScope::resolveDefaultObserver, 1, TimeUnit.SECONDS);
      }
    }
    scopes.put(key, ret);
  }

  public void unregister(ClientSessionScope clientSessionScope) {
    scopes.remove(new ScopeKey(clientSessionScope.getHostServiceId(), clientSessionScope.getId()), clientSessionScope);
  }

  /** Disconnect this client; remote twins are governed by the runtime's persistence policy. */
  public void close() {
    closePeers();
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  void closePeers() {
    // Closing a peer unregisters it. Snapshot the collection before doing local cleanup.
    for (var scope : List.copyOf(scopes.values())) {
      try {
        scope.closePeer();
      } catch (Exception e) {
        org.integratedmodelling.common.logging.Logging.INSTANCE.warn(
            "Cannot disconnect client scope " + scope.getId(), e);
      }
    }
    scopes.clear();
  }

}
