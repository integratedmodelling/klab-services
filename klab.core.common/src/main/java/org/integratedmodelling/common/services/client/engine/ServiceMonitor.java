package org.integratedmodelling.common.services.client.engine;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
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

      for (var service : services) {
        if (accepted.contains(service.getIdentityType())) {
          var client =
              switch (service.getIdentityType()) {
                case REASONER ->
                    ReasonerClient.createOffline(service.getUrls().getFirst(), identity, settings);
                case RESOURCES ->
                    ResourcesClient.createOffline(service.getUrls().getFirst(), identity, settings);
                case RESOLVER ->
                    ResolverClient.createOffline(service.getUrls().getFirst(), identity, settings);
                case RUNTIME ->
                    RuntimeClient.createOffline(service.getUrls().getFirst(), identity, settings);
                default -> throw new KlabIllegalStateException("Can't happen");
              };
          clients.put(client, client.status());
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
  public <T extends KlabService> T getService(Class<T> serviceClass) {
    return (T)
        clients.keySet().stream()
            .filter(
                s -> serviceClass.isAssignableFrom(s.getClass()) && clients.get(s).isOperational())
            .findFirst()
            .orElse(null);
  }

  @SuppressWarnings("unchecked")
  public <T extends KlabService> List<T> getServices(Class<T> serviceClass) {
    return (List<T>)
        clients.keySet().stream()
            .filter(
                s -> serviceClass.isAssignableFrom(s.getClass()) && clients.get(s).isOperational())
            .toList();
  }

  private void recomputeEngineStatus() {

    EngineStatusImpl status = new EngineStatusImpl();

    Set<KlabService.Type> online = EnumSet.noneOf(KlabService.Type.class);
    Set<KlabService.Type> active = EnumSet.noneOf(KlabService.Type.class);
    Set<KlabService.Type> shutdown = EnumSet.noneOf(KlabService.Type.class);

    for (var service : clients.keySet()) {
      var sStatus = clients.get(service);
      if (sStatus.isOperational()) {
        online.add(sStatus.getServiceType());
      }
      if (sStatus.isAvailable()) {
        active.add(sStatus.getServiceType());
      }
      if (sStatus.isShutdown()) {
        shutdown.add(sStatus.getServiceType());
      }
    }

    status.setAvailable(active.size() > 3);
    status.setOperational(online.size() > 3);
    status.setShutdown(!shutdown.isEmpty());

    // TODO the rest

    if (updateEngineStatus(status)) {
      for (var consumer : engineConsumers) {
        consumer.accept(status);
      }
    }
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
