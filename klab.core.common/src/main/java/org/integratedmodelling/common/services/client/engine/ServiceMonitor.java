package org.integratedmodelling.common.services.client.engine;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.integratedmodelling.common.configuration.CommonConfiguration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.services.runtime.Notification;
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

  private Map<BaseServiceClient, KlabService.ServiceStatus> clients =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private List<BiConsumer<KlabService, KlabService.ServiceStatus>> serviceConsumers =
      new ArrayList<>();
  private List<Consumer<Engine.Status>> engineConsumers = new ArrayList<>();
  private EngineStatusImpl lastRecordedStatus = EngineStatusImpl.inop();
  private Map<KlabService.Type, LocalInstance> serviceInstances = new HashMap<>();
  private Settings settings;
  private boolean handleAMQPService = false;
  private boolean handleLanguageServer = false;

  @SuppressWarnings("unchecked")
  public ServiceMonitor(
      Scope user,
      Settings settings,
      boolean useLocalServices,
      List<ServiceReference> services,
      BiConsumer<KlabService, KlabService.ServiceStatus> serviceChangeMonitor,
      Consumer<Engine.Status> engineChangeMonitor) {

    this.settings = settings;
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

  public LocalInstance getServiceInstance(KlabService.Type type) {
    return serviceInstances.get(type);
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
    var localTransitioningCount =
        status.getServicesProvision().values().stream()
            .filter(p -> !p.isOperational() && p.isLocal())
            .count();

    status.setAvailable(active.size() > 3);
    status.setOperational(online.size() > 3 && localTransitioningCount == 0);
    status.setShutdown(!shutdown.isEmpty());

    var database = serviceInstances.get(KlabService.Type.DATABASE);
    var langServ = serviceInstances.get(KlabService.Type.LANGUAGE_SERVER);
    var msBroker = serviceInstances.get(KlabService.Type.AMQP_BROKER);

    if (database != null && database.getStatus() == LocalInstance.Status.RUNNING) {
      status.getActiveAuxiliaryServices().add(Distribution.Product.Type.DATABASE_SERVER);
    }
    if (langServ != null && langServ.getStatus() == LocalInstance.Status.RUNNING) {
      status.getActiveAuxiliaryServices().add(Distribution.Product.Type.LANGUAGE_SERVER);
    }
    if (msBroker != null && msBroker.getStatus() == LocalInstance.Status.RUNNING) {
      status.getActiveAuxiliaryServices().add(Distribution.Product.Type.AMQP_BROKER);
    }

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

  public static void main(String[] dio) {

    Klab.INSTANCE.setConfiguration(new CommonConfiguration());

    AtomicReference<Engine.Status> engineMonitor = new AtomicReference<>(EngineStatusImpl.inop());
    //    var distribution = new Distribution();
    var user = Utils.Authentication.login();
    var settings = SettingsImpl.forEngine();
    var monitor =
        new ServiceMonitor(user, settings, true, new ArrayList<>(), null, engineMonitor::set);
    var softwareStack = Stack.of("klab", settings);

    Utils.CLI
        .create()
        .with(
            "sync",
            cmds -> {
              //              distribution.sync();
            })
        .with(
            "start",
            cmds -> {
              monitor.startLocalServices(softwareStack, Stack.Tag.LATEST_DEVELOP, user);
            })
        .with(
            "stop",
            cmds -> {
              monitor.stopLocalServices();
            })
        .with(
            "lsp",
            cmds -> {
              monitor.startLSPServer(softwareStack, Stack.Tag.LATEST_DEVELOP, user);
            })
        .with(
            "?",
            cmds -> {
              System.out.println(engineMonitor.get());
              for (var service : monitor.serviceInstances.keySet()) {
                System.out.println(service + ": " + monitor.serviceInstances.get(service));
              }
            })
        .run();
  }

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

    for (var service : serviceInstances.values()) {

      if (!Distribution.Product.Type.PRIMARY_SERVICES.contains(service.getProduct().getType())
          && !settings.get(Setting.STOP_AUXILIARY_SERVICES, Boolean.class)) {
        continue;
      }

      service.stop();
    }

    return tasks.size();
  }

  /**
   * Start the LSP server. This uses I/O from the standard streams so it must be stopped and
   * restarted if running, to capture them.
   *
   * @param softwareStack
   * @param distributionTag
   * @param user
   * @return
   */
  public boolean startLSPServer(Stack softwareStack, Stack.Tag distributionTag, UserScope user) {

    var languageServer =
        softwareStack.instance(Distribution.Product.Type.LANGUAGE_SERVER, distributionTag);
    if (languageServer != null) {
      // required even if the server already runs
      if (languageServer.getStatus() == LocalInstance.Status.RUNNING) {
        languageServer.stop();
      }
      this.serviceInstances.put(KlabService.Type.LANGUAGE_SERVER, languageServer);

      return languageServer.start(
          LocalInstance.Option.PROVIDE_INPUT_STREAM, LocalInstance.Option.PROVIDE_OUTPUT_STREAM);
    }
    return false;
  }

  public Map<KlabService.Type, KlabService> startLocalServices(
      Stack softwareStack, Stack.Tag distributionTag, UserScope user) {

    var ret = new HashMap<KlabService.Type, KlabService>();

    if (softwareStack.verify(distributionTag)) {

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
            KlabService.Type.RESOLVER,
            KlabService.Type.DATABASE
            // TODO add database and, when needed, language server and AMQP server
          }) {

        var product =
            softwareStack.instance(
                Distribution.Product.Type.forService(serviceType), distributionTag);

        if (product != null) {

          this.serviceInstances.put(serviceType, product);

          if (product.getStatus() == LocalInstance.Status.STOPPED) {
            if (product.start()) {
              user.info("Service " + serviceType + " is starting");
            }
          } else {
            user.info(
                "Service "
                    + serviceType
                    + " is already running: will be attempting connection to locally running "
                    + serviceType);
          }
        }
      }
    } else {
      user.error("Software stack " + softwareStack + " is not usable");
    }

    return ret;
  }
}
