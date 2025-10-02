package org.integratedmodelling.common.services.client;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
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
  class ServiceMonitor {
    private final Utils.Http.Client client;
    private final URL url;
    private final String serverId;
    private final String ownerServiceId; // null in clients that are not owned by a service
    private final KlabService.Type type;
    private final AtomicReference<KlabService.ServiceStatus> status;
    private final boolean local;

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

    void connect() {
      //      var secret = Configuration.INSTANCE.getServiceSecret(type);
      //      if (secret != null) {
      //        local = Utils.URLs.isLocalHost(getUrl());
      //        if (local) {
      //          client.setHeader(ServicesAPI.SERVER_KEY_HEADER, secret);
      //          ret = secret;
      //        }
      //      }
      //
      //      if (statusListeners != null) {
      //        this.statusListeners.addAll(Arrays.asList(statusListeners));
      //      }

      //      this.scope =
      //          new AbstractServiceDelegatingScope(channel) {
      //
      //            @Override
      //            public UserScope createUser(String username, String password) {
      //              return null;
      //            }
      //
      //            @Override
      //            public <T extends KlabService> T getService(
      //                Class<T> serviceClass, Predicate<T>... selectors) {
      //              return KlabService.Type.classify(serviceClass) == serviceType
      //                  ? (T) ServiceClient.this
      //                  : null;
      //            }
      //
      //            @Override
      //            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass)
      // {
      //              return KlabService.Type.classify(serviceClass) == serviceType
      //                  ? (Collection<T>) List.of(ServiceClient.this)
      //                  : Collections.emptyList();
      //            }
      //          };

      pollingTasks.scheduleAtFixedRate(
          this::timedTasks,
          0,
          this.local ? localPollCycleSeconds : onlinePollCycleSeconds,
          TimeUnit.SECONDS);
    }

    void timedTasks() {}

    void close() {}
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
