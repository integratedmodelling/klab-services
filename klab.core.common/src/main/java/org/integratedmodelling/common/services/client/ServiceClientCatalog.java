package org.integratedmodelling.common.services.client;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;

/**
 * Singleton that collects the "abstract" service client information, still uncommitted to an
 * identity. When a scope needs a service, it can retrieve a personalized client by asking the
 * catalog for a client given URL, service ID and scope identity. The singleton creates the service
 * transparently managing the lifecycle of the underlying monitors.
 */
public enum ServiceClientCatalog {
  INSTANCE;

  private final long localPollCycleSeconds = (Integer) Setting.POLLING_INTERVAL_LOCAL.defaultValue;
  private final long onlinePollCycleSeconds =
      (Integer) Setting.POLLING_INTERVAL_REMOTE.defaultValue;
  private static final int REMOTE_FAILURES_BEFORE_OFFLINE = 3;
  private final ScheduledThreadPoolExecutor pollingTasks = createPollingExecutor();

  private static ScheduledThreadPoolExecutor createPollingExecutor() {
    var executor = new ScheduledThreadPoolExecutor(10,
        Thread.ofPlatform().daemon(true).name("klab-service-monitor-", 0).factory());
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  /**
   * One of these is kept per service URL; the clients are built from it, adding the identity that
   * signs the service requests. For each of these, a polling task is scheduled to keep the status
   * up to date.
   */
  public class ClientMonitor {
    private final Utils.Http.Client client;
    private final URL url;
    private String serverId;
    private final String ownerServiceId; // null in clients that are not owned by a service
    private final KlabService.Type type;
    private final AtomicReference<KlabService.ServiceStatus> status;
    private final boolean local;
    private int consecutiveFailedPolls = 0;
    private ScheduledFuture<?> schedule;
    private final boolean pollingEnabled;
    private volatile boolean closed;

    // Scopes own clients. Monitoring must not keep replaced clients (and their scopes) alive.
    private final Set<BaseServiceClient> registeredClients =
        Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public Utils.Http.Client getClient() {
      return client;
    }

    public URL getUrl() {
      return url;
    }

    public String getServiceId() {
      return serverId;
    }

    public String getOwnerServiceId() {
      return ownerServiceId;
    }

    public KlabService.Type getType() {
      return type;
    }

    public AtomicReference<KlabService.ServiceStatus> getStatus() {
      return status;
    }

    public void updateFromAdvertisement(UserScopeNotification.ServiceInfo request) {
      if (serverId == null && request.getId() != null) {
        serverId = request.getId();
      }
      if (request.getStatus() != null
          && shouldAcceptAdvertisedStatus(request.getStatus(), status.get())) {
        status.set(request.getStatus());
      }
    }

    private boolean shouldAcceptAdvertisedStatus(
        KlabService.ServiceStatus advertised, KlabService.ServiceStatus current) {
      if (current == null) {
        return true;
      }
      return advertised.isOperational() && !current.isOperational()
          || advertised.isAvailable() && !current.isAvailable()
          || advertised.isConnected() && !current.isConnected();
    }

    public ClientMonitor(
        URL url, // never null
        String serverId, // null if unknown
        String
            ownerServiceId, // null in clients that are not owned by a service; validated otherwise
        KlabService.Type type, // if not null, will be validated
        AtomicReference<KlabService.ServiceStatus> status) {
      this(url, serverId, ownerServiceId, type, status, true);
    }

    ClientMonitor(
        URL url,
        String serverId,
        String ownerServiceId,
        KlabService.Type type,
        AtomicReference<KlabService.ServiceStatus> status,
        boolean startPolling) {
      this.client = Utils.Http.getServiceClient(url);
      this.url = url;
      this.serverId = serverId;
      this.ownerServiceId = ownerServiceId;
      this.type = type;
      this.status = status;
      this.local = Utils.URLs.isLocalHost(url);
      this.pollingEnabled = startPolling;
    }

    public void registerClient(BaseServiceClient client) {
      synchronized (ServiceClientCatalog.this) {
        if (closed) throw new KlabIllegalStateException("Service monitor is closed");
        registeredClients.add(client);
        if (pollingEnabled && schedule == null) connect();
      }
    }

    public int release(BaseServiceClient client) {
      synchronized (ServiceClientCatalog.this) {
        registeredClients.remove(client);
        if (registeredClients.isEmpty()) close();
        return registeredClients.size();
      }
    }

    void connect() {
      this.schedule =
          pollingTasks.scheduleWithFixedDelay(
              this::timedTasks,
              0,
              this.local ? localPollCycleSeconds : onlinePollCycleSeconds,
              TimeUnit.SECONDS);
    }

    void timedTasks() {
      synchronized (ServiceClientCatalog.this) {
        // Accessing the weak set expunges collected clients. The scheduled task itself otherwise
        // keeps an unused monitor, its HTTP client and its catalog entry alive indefinitely.
        if (closed || registeredClients.isEmpty()) {
          close();
          return;
        }
      }
      try {
        refreshStatus(true);
      } catch (Exception e) {
        // An unchecked HTTP/listener failure must not suppress every subsequent scheduled poll.
        org.integratedmodelling.common.logging.Logging.INSTANCE.warn("Service status poll failed: " + e);
      }
    }

    public KlabService.ServiceStatus refreshStatus() {
      return refreshStatus(false);
    }

    synchronized KlabService.ServiceStatus refreshStatus(boolean notifyListeners) {

      var statusBeforeChecking = status.get();
      try {
        var refreshed = readStatus();
        if (refreshed) {
          consecutiveFailedPolls = 0;
        } else if (local || ++consecutiveFailedPolls >= REMOTE_FAILURES_BEFORE_OFFLINE) {
          this.status.set(KlabService.ServiceStatus.offline(type, serverId));
        }
      } finally {

        boolean statusHasChanged =
            (statusBeforeChecking == null && status.get() != null)
                || (statusBeforeChecking != null && status.get() == null)
                || (status.get() != null
                    && statusBeforeChecking != null
                    && status.get().hasChangedComparedTo(statusBeforeChecking));

        if (statusHasChanged) {
          if (serverId == null && status.get().getServiceId() != null) {
            serverId = status.get().getServiceId();
            synchronized (ServiceClientCatalog.this) {
              if (!closed) serviceClients.putIfAbsent(serverId, this);
            }
          }

          if (notifyListeners) {
            List<BaseServiceClient> clients;
            synchronized (registeredClients) {
              clients = new ArrayList<>(registeredClients);
            }
            for (var client : clients) {
              for (var listener : client.statusListeners) {
                try {
                  listener.accept(status.get(), statusHasChanged);
                } catch (Exception e) {
                  org.integratedmodelling.common.logging.Logging.INSTANCE.warn("Service status listener failed: " + e);
                }
              }
            }
          }
        }
      }

      return status.get();
    }

    boolean readStatus() {
      var status =
          client.get(ServicesAPI.STATUS, ServiceStatusImpl.class, Notification.Mode.Silent);
      if (status != null) {
        this.status.set(status);
        return true;
      } else {
        return false;
      }
    }

    private void close() {
      closed = true;
      if (this.schedule != null) {
        this.schedule.cancel(false);
      }
      if (serverId != null) serviceClients.remove(serverId, this);
    }

    public boolean isLocal() {
      return local;
    }
  }

  private final Map<String, ClientMonitor> serviceClients = new ConcurrentHashMap<>();

  public synchronized BaseServiceClient getService(
      UserScopeNotification.ServiceInfo request, KlabService ownerService, UserScope userScope) {
    if (request.getId() == null || request.getUrl() == null || request.getType() == null) {
      throw new KlabIllegalStateException("Incomplete service advertisement");
    }
    var monitor =
        serviceClients.compute(
            request.getId(),
            (id, existing) -> {
              if (existing == null) {
                return createServiceMonitor(request, ownerService);
              }
              existing.updateFromAdvertisement(request);
              return existing;
            });
    return switch (request.getType()) {
      case REASONER -> new ReasonerClient(monitor, userScope, null);
      case RESOURCES -> new ResourcesClient(monitor, userScope, null);
      case RESOLVER -> new ResolverClient(monitor, userScope, null);
      case RUNTIME -> new RuntimeClient(monitor, userScope, null);
      default ->
          throw new KlabIllegalStateException(
              "Wrong service type in UserScopeNotification.ServiceInfo request");
    };
  }

  public <T extends KlabService> T getService(
      URL serviceUrl,
      Settings settings,
      Scope scope,
      Class<T> serviceClass,
      BiConsumer<KlabService.ServiceStatus, Boolean>... statusListeners) {
    var request = new UserScopeNotification.ServiceInfo();
    request.setUrl(serviceUrl);
    request.setType(KlabService.Type.classify(serviceClass));
    var monitor = createServiceMonitor(request, null);
    return serviceClass.cast(
        switch (request.getType()) {
          case REASONER -> new ReasonerClient(monitor, scope, settings, statusListeners);
          case RESOURCES -> new ResourcesClient(monitor, scope, settings, statusListeners);
          case RESOLVER -> new ResolverClient(monitor, scope, settings, statusListeners);
          case RUNTIME -> new RuntimeClient(monitor, scope, settings, statusListeners);
          default ->
              throw new KlabIllegalStateException(
                  "Wrong service type in UserScopeNotification.ServiceInfo request");
        });
  }

  private ClientMonitor createServiceMonitor(
      UserScopeNotification.ServiceInfo request, KlabService ownerService) {
    return new ClientMonitor(
        request.getUrl(),
        request.getId(),
        ownerService == null ? null : ownerService.serviceId(),
        request.getType(),
        new AtomicReference<>(
            request.getStatus() == null
                ? KlabService.ServiceStatus.offline(request.getType(), request.getId())
                : request.getStatus()));
  }
}
