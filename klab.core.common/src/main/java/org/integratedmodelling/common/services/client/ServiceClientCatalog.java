package org.integratedmodelling.common.services.client;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
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
    private final String serverId;
    private final String ownerServiceId; // null in clients that are not owned by a service
    private final KlabService.Type type;
    private final AtomicReference<KlabService.ServiceStatus> status;
    private final boolean local;
    private ScheduledFuture<?> schedule;
    private AtomicInteger refCount = new AtomicInteger(0);

    public Utils.Http.Client getClient() {
      return client;
    }

    public URL getUrl() {
      return url;
    }

    public String getServerId() {
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
        Utils.Http.Client client,
        URL url,
        String serverId,
        String ownerServiceId, // null in clients that are not owned by a service
        KlabService.Type type,
        AtomicReference<KlabService.ServiceStatus> status) {
      this.client = client;
      this.url = url;
      this.serverId = serverId;
      this.ownerServiceId = ownerServiceId;
      this.type = type;
      this.status = status;
      this.local = Utils.URLs.isLocalHost(url);
      Thread.ofVirtual().start(() -> connect());
    }

    public void registerClient() {
      refCount.incrementAndGet();
    }

    public int release() {
      var ret = refCount.decrementAndGet();
      if (ret == 0) {
        close();
      }
      return 0;
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
      //
      //        //    if (this.shutdown.get()) {
      //        //      //      scope.send(
      //        //      //          Message.MessageClass.ServiceLifecycle,
      //        //      //          Message.MessageType.ServiceStatus,
      //        //      //          ServiceStatus.offline(serviceType, this.serviceId()));
      //        //      return;
      //        //    }
      //
      //        try {
      //
      //            //      var connectedBeforeChecking = connected.get();
      //            var statusBeforeChecking = status.get();
      //
      //      /*
      //      TODO check for changes of status and send messages over
      //       */
      //            try {
      //                var currentServiceStatus = readServiceStatus(this.url, scope);
      //                if (currentServiceStatus == null) {
      //                    connected.set(false);
      //                    shutdown.set(false);
      //                    status.set(
      //                            KlabService.ServiceStatus.offline(
      //                                    serviceType,
      //                                    this.capabilities == null ? null :
      // this.capabilities.getServiceId()));
      //                } else {
      //                    status.set(currentServiceStatus);
      //                    connected.set(true);
      //          /*
      //          System.out.println("Service " + currentServiceStatus.getServiceType()
      //                  + " with id "+currentServiceStatus.getServiceId() + " is "
      //                  + ((currentServiceStatus.isAvailable()) ? "online" : "offline"));
      //
      //           */
      //                    if (this.capabilities == null) {
      //                        this.capabilities = capabilities(scope);
      //                        if (this.capabilities != null) {
      //                            this.serviceId = capabilities.getServiceId();
      //                        }
      //                    }
      //                }
      //
      //                ((ServiceStatusImpl) status.get()).setShutdown(this.shutdown.get());
      //
      //            } finally {
      //
      //                boolean statusHasChanged =
      //                        (statusBeforeChecking == null && status.get() != null)
      //                                || (statusBeforeChecking != null && status.get() == null)
      //                                || (status.get() != null
      //                                && statusBeforeChecking != null
      //                                && status.get().hasChangedComparedTo(statusBeforeChecking));
      //
      //                if (connected.get()) {
      //
      //                    // see if we have a local service and change the token
      //                    if ((token == null || token.isEmpty()) &&
      // Utils.URLs.isLocalHost(getUrl())) {
      //                        // may have gotten lost if the service was starting when we booted
      //                        var secret = Configuration.INSTANCE.getServiceSecret(serviceType);
      //                        if (secret != null) {
      //                            token = secret;
      //                            client.setAuthorization(token);
      //                            local = true;
      //                        }
      //                    }
      //                }
      //
      //                if (statusHasChanged) {
      //                    this.capabilities = capabilities(scope);
      //                }
      //
      //                for (var listener : statusListeners) {
      //                    listener.accept(status.get(), statusHasChanged);
      //                }
      //            }
      //
      //        } catch (Throwable t) {
      //            scope.error(t);
      //        }
      //
      //        this.connectionAttempted.set(true);
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

  public ServiceClient getService(
      UserScopeNotification.ServiceInfo request, KlabService ownerService, UserScope userScope) {
    var monitor =
        serviceClients.computeIfAbsent(
            request.getId(), id -> createServiceMonitor(request, ownerService));
    return switch (request.getType()) {
      case REASONER -> null;
      case RESOURCES -> null;
      case RESOLVER -> null;
      case RUNTIME -> null;
      default ->
          throw new KlabIllegalStateException(
              "Wrong service type in UserScopeNotification.ServiceInfo request");
    };
  }

  private ServiceMonitor createServiceMonitor(
      UserScopeNotification.ServiceInfo request, KlabService ownerService) {
    var client = Utils.Http.getServiceClient(request.getUrl(), request.getId());
    return new ServiceMonitor(
        client,
        request.getUrl(),
        request.getId(),
        ownerService.serviceId(),
        request.getType(),
        new AtomicReference<>(
            KlabService.ServiceStatus.offline(request.getType(), request.getId())));
  }
}
