package org.integratedmodelling.common.services.client.engine;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.integratedmodelling.common.distribution.DistributionImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

/**
 * Create one of these when there is a local distribution and subscribe to events. Keeps a client
 * for each service and monitors the appearance of a local service. When the service appears an
 * engine or service can switch to it.
 *
 * <p>TODO also the shutdown should be managed here, and messages should be sent when shutdown is
 * complete.
 */
public class ServiceMonitor {

  Map<BaseServiceClient, KlabService.ServiceStatus> clients =
      Collections.synchronizedMap(new LinkedHashMap<>());
  List<BiConsumer<KlabService, KlabService.ServiceStatus>> serviceConsumers = new ArrayList<>();
  List<Consumer<Engine.Status>> engineConsumers = new ArrayList<>();
  EngineStatusImpl lastRecordedStatus = EngineStatusImpl.inop();
  boolean firstTimeOnline = false;

  @SuppressWarnings("unchecked")
  public ServiceMonitor(
      Scope user,
      Settings settings,
      boolean useLocalServices,
      List<ServiceReference> services,
      BiConsumer<KlabService, KlabService.ServiceStatus> serviceChangeMonitor,
      Consumer<Engine.Status> engineChangeMonitor) {

    serviceConsumers.add(serviceChangeMonitor);
    engineConsumers.add(engineChangeMonitor);
    var accepted =
        EnumSet.of(
            KlabService.Type.RESOURCES,
            KlabService.Type.REASONER,
            KlabService.Type.RESOLVER,
            KlabService.Type.RUNTIME);
    if (useLocalServices) {
      for (var type :
          List.of(
              KlabService.Type.RESOURCES,
              KlabService.Type.REASONER,
              KlabService.Type.RESOLVER,
              KlabService.Type.RUNTIME)) {
        var service =
            switch (type) {
              case REASONER ->
                  ServiceClientCatalog.INSTANCE.getService(
                      type.localServiceUrl(), settings, user, Reasoner.class);
              case RESOURCES ->
                  ServiceClientCatalog.INSTANCE.getService(
                      type.localServiceUrl(), settings, user, ResourcesService.class);
              case RESOLVER ->
                  ServiceClientCatalog.INSTANCE.getService(
                      type.localServiceUrl(), settings, user, Resolver.class);
              case RUNTIME ->
                  ServiceClientCatalog.INSTANCE.getService(
                      type.localServiceUrl(), settings, user, RuntimeService.class);
              default -> throw new KlabIllegalStateException("Can't happen");
            };
        clients.put((BaseServiceClient) service, service.status());
      }

      var localOnly = settings.get(Setting.LOCAL_ONLY, Boolean.class);

      if (!localOnly) {
        for (var service : services) {
          if (accepted.contains(service.getIdentityType())) {
            var client =
                switch (service.getIdentityType()) {
                  case REASONER ->
                      ServiceClientCatalog.INSTANCE.getService(
                          service.getUrls().getFirst(), settings, user, Reasoner.class);
                  case RESOURCES ->
                      ServiceClientCatalog.INSTANCE.getService(
                          service.getUrls().getFirst(), settings, user, ResourcesService.class);
                  case RESOLVER ->
                      ServiceClientCatalog.INSTANCE.getService(
                          service.getUrls().getFirst(), settings, user, Resolver.class);
                  case RUNTIME ->
                      ServiceClientCatalog.INSTANCE.getService(
                          service.getUrls().getFirst(), settings, user, RuntimeService.class);
                  default -> throw new KlabIllegalStateException("Can't happen");
                };
            clients.put((BaseServiceClient) client, client.status());
          }
        }
      }

      for (var client : clients.keySet()) {
        client.addListener((status, message) -> handleStatus(client, status, message));
      }
    }
  }

  private void handleStatus(
      BaseServiceClient service, KlabService.ServiceStatus status, Boolean statusChanged) {
    clients.put(service, status);
    for (var serviceListener : serviceConsumers) {
      serviceListener.accept(service, status);
    }
    if (statusChanged) {
      recomputeEngineStatus();
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends KlabService> T getService(Class<T> serviceClass, Predicate<T>... selectors) {

    var services = getServices(serviceClass);

    if (selectors == null || selectors.length == 0) {
      if (services.isEmpty()) {
        throw new KlabServiceAccessException(
            "No suitable service for request of " + serviceClass.getSimpleName());
      }
      return (T) services.getFirst();
    }

    for (var selector : selectors) {
      var ret =
          services.stream().filter(serviceClient -> selector.test((T) serviceClient)).toList();
      if (!ret.isEmpty()) {
        return (T) ret.getFirst();
      }
    }

    throw new KlabServiceAccessException(
        "No suitable service for request of " + serviceClass.getSimpleName());
  }

  @SuppressWarnings("unchecked")
  public <T extends KlabService> List<T> getServices(Class<T> serviceClass) {
    return (List<T>)
        clients.keySet().stream()
            .filter(
                s -> serviceClass.isAssignableFrom(s.getClass()) && clients.get(s).isOperational())
            .toList();
  }

  private synchronized void recomputeEngineStatus() {

    EngineStatusImpl status = new EngineStatusImpl();

    Set<KlabService.Type> online = EnumSet.noneOf(KlabService.Type.class);
    Set<KlabService.Type> active = EnumSet.noneOf(KlabService.Type.class);
    Set<KlabService.Type> shutdown = EnumSet.noneOf(KlabService.Type.class);

    Map<KlabService.Type, Integer> localOperational = new HashMap<>();
    Map<KlabService.Type, Integer> localAvailable = new HashMap<>();
    Map<KlabService.Type, Integer> remoteOperational = new HashMap<>();

    for (var service : clients.keySet()) {
      var remote = service.getUrl() != null && !Utils.URLs.isLocalHost(service.getUrl());
      var sStatus = clients.get(service);
      if (sStatus.isOperational()) {
        if (remote) {
          remoteOperational.merge(sStatus.getServiceType(), 1, Integer::sum);
        } else {
          localOperational.merge(sStatus.getServiceType(), 1, Integer::sum);
        }
        online.add(sStatus.getServiceType());
      } else if (sStatus.isAvailable() || sStatus.isConnected()) {
        active.add(sStatus.getServiceType());
        if (!remote) {
          localAvailable.merge(sStatus.getServiceType(), 1, Integer::sum);
        }
        if (sStatus.isShutdown()) {
          shutdown.add(sStatus.getServiceType());
        }
      }
    }

    for (var type : KlabService.Type.operationCritical()) {
      status
          .getServicesProvision()
          .put(type, operationalStatus(type, localOperational, localAvailable, remoteOperational));
    }

    status.setAvailable(active.size() > 3);
    status.setOperational(online.size() > 3);
    status.setShutdown(!shutdown.isEmpty());

    var localTransitioningCount =
        status.getServicesProvision().values().stream()
            .filter(p -> !p.isOperational() && p.isLocal())
            .count();

    var localOperationalCount =
        status.getServicesProvision().values().stream()
            .filter(p -> p.isOperational() && p.isLocal())
            .count();

    var remoteOperationalCount =
        status.getServicesProvision().values().stream()
            .filter(p -> p.isOperational() && !p.isLocal())
            .count();

    if (localTransitioningCount > 0) {
      status.setCondition(Engine.Status.EngineCondition.TRANSITIONING);
    } else if (localOperationalCount == 0 && remoteOperationalCount < 4) {
      status.setCondition(Engine.Status.EngineCondition.INOPERATIVE);
    } else if (localOperationalCount == 0) {
      status.setCondition(Engine.Status.EngineCondition.ACTIVE_REMOTE_ONLY);
    } else if (localOperationalCount == 4 && remoteOperationalCount == 0) {
      status.setCondition(Engine.Status.EngineCondition.ACTIVE_LOCAL_ONLY);
    } else {
      status.setCondition(Engine.Status.EngineCondition.ACTIVE_LOCAL_AND_REMOTE);
    }

    if (updateEngineStatus(status)) {
      for (var consumer : engineConsumers) {
        consumer.accept(status);
      }
    }
  }

  private Engine.Status.ServiceProvision operationalStatus(
      KlabService.Type type,
      Map<KlabService.Type, Integer> localOperational,
      Map<KlabService.Type, Integer> localAvailable,
      Map<KlabService.Type, Integer> remoteOperational) {

    if (localOperational.containsKey(type) && remoteOperational.containsKey(type)) {
      return remoteOperational.get(type) > 1
          ? Engine.Status.ServiceProvision.LOCAL_REMOTE_MULTI
          : Engine.Status.ServiceProvision.LOCAL_REMOTE_SINGLE;
    } else if (localOperational.containsKey(type)) {
      return Engine.Status.ServiceProvision.LOCAL_SINGLE;
    } else if (localAvailable.containsKey(type)) {
      return Engine.Status.ServiceProvision.LOCAL_INOP_SINGLE;
    } else if (remoteOperational.containsKey(type)) {
      return remoteOperational.get(type) > 1
          ? Engine.Status.ServiceProvision.REMOTE_MULTI
          : Engine.Status.ServiceProvision.REMOTE_SINGLE;
    }
    return Engine.Status.ServiceProvision.INOPERATIVE;
  }

  private boolean updateEngineStatus(EngineStatusImpl status) {

    if (this.lastRecordedStatus == null) {
      this.lastRecordedStatus = status;
      return true;
    }

    var ret =
        ((this.lastRecordedStatus.isAvailable() != status.isAvailable())
            || (this.lastRecordedStatus.isOperational() != status.isOperational())
            || (this.lastRecordedStatus.isShutdown() != status.isShutdown()));

    if (!ret) {
      ret = !this.lastRecordedStatus.getServicesProvision().equals(status.getServicesProvision());
    }

    this.lastRecordedStatus = status;

    return ret;
  }

  public static void main(String[] dio) {}

  public int stopLocalServices() {

    var status = lastRecordedStatus;
    status.setShutdown(true);
    status.setCondition(Engine.Status.EngineCondition.TRANSITIONING);
    lastRecordedStatus = status;

    for (var consumer : engineConsumers) {
      consumer.accept(status);
    }

    List<Supplier<KlabService>> tasks = new ArrayList<>();
    for (var service : clients.keySet()) {
      if (Utils.URLs.isLocalHost(service.getUrl())) {
        tasks.add(
            () -> {
              service.shutdown();
              return service;
            });
      }
    }

    if (!tasks.isEmpty()) {
      try (var executor = Executors.newFixedThreadPool(tasks.size())) {
        for (var task : tasks) {
          CompletableFuture.supplyAsync(task);
        }
      } catch (Exception e) {
        Logging.INSTANCE.error(e);
        return 0;
      }
    }

    return tasks.size();
  }

  public Map<KlabService.Type, KlabService> startLocalServices(
      DistributionImpl distribution, UserScope user) {

    var ret = new HashMap<KlabService.Type, KlabService>();

    if (distribution != null && distribution.isUsable()) {

      var status = lastRecordedStatus;
      status.setShutdown(false);
      status.setCondition(Engine.Status.EngineCondition.TRANSITIONING);
      lastRecordedStatus = status;

      for (var consumer : engineConsumers) {
        consumer.accept(status);
      }
      for (var serviceType :
          new KlabService.Type[] {
            KlabService.Type.RESOURCES,
            KlabService.Type.REASONER,
            KlabService.Type.RUNTIME,
            KlabService.Type.RESOLVER
          }) {
        var product = distribution.findProduct(Product.ProductType.forService(serviceType));
        if (product != null) {
          var instance = product.getInstance(user);
          if (serviceType == KlabService.Type.RUNTIME
              && instance.getSettings() instanceof ServiceStartupOptions serviceStartupOptions) {
            serviceStartupOptions.setStartLocalBroker(true);
          }

          if (instance.start()) {
            user.info(
                "Service is starting: will be attempting connection to locally running "
                    + serviceType);
          }
        }
      }
    }

    return ret;
  }
}
