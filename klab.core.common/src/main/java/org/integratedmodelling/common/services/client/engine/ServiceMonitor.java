package org.integratedmodelling.common.services.client.engine;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;
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

  Map<ServiceClient, KlabService.ServiceStatus> clients =
      Collections.synchronizedMap(new LinkedHashMap<>());
  List<BiConsumer<KlabService, KlabService.ServiceStatus>> serviceConsumers = new ArrayList<>();
  List<Consumer<Engine.Status>> engineConsumers = new ArrayList<>();
  EngineStatusImpl lastRecordedStatus = EngineStatusImpl.inop();

  @SuppressWarnings("unchecked")
  public ServiceMonitor(
      Identity identity,
      Parameters<Engine.Setting> settings,
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
              case REASONER -> ReasonerClient.createLocalOffline(identity, settings);
              case RESOURCES -> ResourcesClient.createLocalOffline(identity, settings);
              case RESOLVER -> ResolverClient.createLocalOffline(identity, settings);
              case RUNTIME -> RuntimeClient.createLocalOffline(identity, settings);
              default -> throw new KlabIllegalStateException("Can't happen");
            };
        clients.put(service, service.status());
      }

      var localOnly = settings.get(Engine.Setting.LOCAL_ONLY, Boolean.FALSE);

      if (!localOnly) {
        for (var service : services) {
          if (accepted.contains(service.getIdentityType())) {
            var client =
                switch (service.getIdentityType()) {
                  case REASONER ->
                      ReasonerClient.createOffline(
                          service.getUrls().getFirst(), identity, settings);
                  case RESOURCES ->
                      ResourcesClient.createOffline(
                          service.getUrls().getFirst(), identity, settings);
                  case RESOLVER ->
                      ResolverClient.createOffline(
                          service.getUrls().getFirst(), identity, settings);
                  case RUNTIME ->
                      RuntimeClient.createOffline(service.getUrls().getFirst(), identity, settings);
                  default -> throw new KlabIllegalStateException("Can't happen");
                };
            clients.put(client, client.status());
          }
        }
      }

      for (var client : clients.keySet()) {
        client.connect((status, message) -> handleStatus(client, status, message));
      }
    }
  }

  private void handleStatus(
      ServiceClient service, KlabService.ServiceStatus status, Boolean statusChanged) {
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
      } else if (sStatus.isAvailable() || sStatus.isShutdown()) {
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

    if (localTransitioningCount > 0 ) {
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

  private EngineStatusImpl.ServiceProvision operationalStatus(
      KlabService.Type type,
      Map<KlabService.Type, Integer> localOperational,
      Map<KlabService.Type, Integer> localAvailable,
      Map<KlabService.Type, Integer> remoteOperational) {
    if (remoteOperational.containsKey(type)) {
      if (localOperational.containsKey(type)) {
        return remoteOperational.containsKey(type)
            ? (remoteOperational.get(type) > 1
                ? EngineStatusImpl.ServiceProvision.LOCAL_REMOTE_MULTI
                : EngineStatusImpl.ServiceProvision.LOCAL_REMOTE_SINGLE)
            : EngineStatusImpl.ServiceProvision.LOCAL_INOP_SINGLE;
      } else if (localAvailable.containsKey(type)) {
        return remoteOperational.containsKey(type)
            ? (remoteOperational.get(type) > 1
                ? EngineStatusImpl.ServiceProvision.LOCAL_INOP_REMOTE_MULTI
                : EngineStatusImpl.ServiceProvision.LOCAL_INOP_REMOTE_SINGLE)
            : EngineStatusImpl.ServiceProvision.LOCAL_INOP_SINGLE;
      } else
        return remoteOperational.get(type) > 1
            ? EngineStatusImpl.ServiceProvision.REMOTE_MULTI
            : EngineStatusImpl.ServiceProvision.REMOTE_SINGLE;
    }
    return EngineStatusImpl.ServiceProvision.INOP;
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
}
