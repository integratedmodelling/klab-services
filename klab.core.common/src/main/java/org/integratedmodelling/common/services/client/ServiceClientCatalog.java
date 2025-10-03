package org.integratedmodelling.common.services.client;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
  private final ScheduledExecutorService pollingTasks = Executors.newScheduledThreadPool(10);

  /**
   * One of these is kept per service URL; the clients are built from it, adding the identity that
   * signs the service requests. For each of these, a polling task is scheduled to keep the status
   * up to date.
   */
  public class ServiceMonitor {
    private final Utils.Http.Client client;
    private final URL url;
    private String serverId;
    private final String ownerServiceId; // null in clients that are not owned by a service
    private final KlabService.Type type;
    private final AtomicReference<KlabService.ServiceStatus> status;
    private final boolean local;
    private ScheduledFuture<?> schedule;

    private Set<BaseServiceClient> registeredClients = new HashSet<>();

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

    public ServiceMonitor(
        URL url, // never null
        String serverId, // null if unknown
        String
            ownerServiceId, // null in clients that are not owned by a service; validated otherwise
        KlabService.Type type, // if not null, will be validated
        AtomicReference<KlabService.ServiceStatus> status) {
      this.client = Utils.Http.getServiceClient(url);
      this.url = url;
      this.serverId = serverId;
      this.ownerServiceId = ownerServiceId;
      this.type = type;
      this.status = status;
      this.local = Utils.URLs.isLocalHost(url);
      Thread.ofVirtual().start(() -> connect());
    }

    public void registerClient(BaseServiceClient client) {
      registeredClients.add(client);
    }

    public int release(BaseServiceClient client) {
      registeredClients.remove(client);
      if (registeredClients.isEmpty()) {
        close();
      }
      return registeredClients.size();
    }

    void connect() {
      this.schedule =
          pollingTasks.scheduleAtFixedRate(
              this::timedTasks,
              0,
              this.local ? localPollCycleSeconds : onlinePollCycleSeconds,
              TimeUnit.SECONDS);
    }

    void timedTasks() {

      //        if (settings != null && "off".equals(settings.get(Setting.POLLING, String.class))) {
      //            return;
      //        }

      if (!client.isAlive()) {
        this.status.set(KlabService.ServiceStatus.offline(type, serverId));
        return;
      }

      var statusBeforeChecking = status.get();
      try {
        readStatus();
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
            serviceClients.put(serverId, this);
          }

          for (var client : registeredClients) {
            for (var listener : client.statusListeners) {
              listener.accept(status.get(), statusHasChanged);
            }
          }
        }
      }
    }

    void readStatus() {
      var status =
          client.get(ServicesAPI.STATUS, ServiceStatusImpl.class, Notification.Mode.Silent);
      if (status != null) {
        this.status.set(status);
      } else {
        this.status.set(KlabService.ServiceStatus.offline(type, serverId));
      }
    }

    // TODO this should be called internally when the reference count drops to zero. Service
    //  clients should decrement the ref count
    private void close() {
      this.schedule.cancel(true);
      serviceClients.remove(serverId);
    }

    public boolean isLocal() {
      return local;
    }
  }

  private final Map<String, ServiceMonitor> serviceClients = new ConcurrentHashMap<>();

  public BaseServiceClient getService(
      UserScopeNotification.ServiceInfo request, KlabService ownerService, UserScope userScope) {
    var monitor =
        serviceClients.computeIfAbsent(
            request.getId(), id -> createServiceMonitor(request, ownerService));
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
      Scope userScope,
      Class<T> serviceClass,
      BiConsumer<KlabService.ServiceStatus, Boolean>... statusListeners) {
    var request = new UserScopeNotification.ServiceInfo();
    request.setUrl(serviceUrl);
    request.setType(KlabService.Type.classify(serviceClass));
    var monitor = createServiceMonitor(request, null);
    return (T)
        switch (request.getType()) {
          case REASONER -> new ReasonerClient(monitor, userScope, settings, statusListeners);
          case RESOURCES -> new ResourcesClient(monitor, userScope, settings, statusListeners);
          case RESOLVER -> new ResolverClient(monitor, userScope, settings, statusListeners);
          case RUNTIME -> new RuntimeClient(monitor, userScope, settings, statusListeners);
          default ->
              throw new KlabIllegalStateException(
                  "Wrong service type in UserScopeNotification.ServiceInfo request");
        };
  }

  private ServiceMonitor createServiceMonitor(
      UserScopeNotification.ServiceInfo request, KlabService ownerService) {
    return new ServiceMonitor(
        request.getUrl(),
        request.getId(),
        ownerService == null ? null : ownerService.serviceId(),
        request.getType(),
        new AtomicReference<>(
            KlabService.ServiceStatus.offline(request.getType(), request.getId())));
  }
}
