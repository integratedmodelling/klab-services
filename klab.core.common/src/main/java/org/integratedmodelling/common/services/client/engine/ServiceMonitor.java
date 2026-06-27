package org.integratedmodelling.common.services.client.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.configuration.CommonConfiguration;
import org.integratedmodelling.common.distribution.LocalInstanceImpl;
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
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.rest.EngineAuthenticationResponse;
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

  private final Map<BaseServiceClient, KlabService.ServiceStatus> clients =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private final List<BiConsumer<KlabService, KlabService.ServiceStatus>> serviceConsumers =
      Collections.synchronizedList(new ArrayList<>());
  private final List<Consumer<Engine.Status>> engineConsumers =
      Collections.synchronizedList(new ArrayList<>());
  private EngineStatusImpl lastRecordedStatus = EngineStatusImpl.inop();
  private final Map<KlabService.Type, LocalInstance> serviceInstances = new ConcurrentHashMap<>();
  private final Settings settings;
  private boolean handleAMQPService = false;
  private boolean handleLanguageServer = false;
  private volatile boolean stoppingLocalServices = false;
  private volatile Thread languageServerShutdownHook;
  private volatile String localAuthenticationPackage;

  @SuppressWarnings("unchecked")
  public ServiceMonitor(
      Scope user,
      Settings settings,
      boolean useLocalServices,
      List<ServiceReference> services,
      BiConsumer<KlabService, KlabService.ServiceStatus> serviceChangeMonitor,
      Consumer<Engine.Status> engineChangeMonitor) {

    this.settings = settings;
    if (serviceChangeMonitor != null) {
      serviceConsumers.add(serviceChangeMonitor);
    }
    if (engineChangeMonitor != null) {
      engineConsumers.add(engineChangeMonitor);
    }
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

      for (var client : clientSnapshot()) {
        client.addListener((status, message) -> handleStatus(client, status, message));
        refreshClientStatusAsync(client);
      }
    }
  }

  public void setLocalAuthenticationResponse(EngineAuthenticationResponse authenticationResponse) {
    this.localAuthenticationPackage =
        Authentication.INSTANCE.encodeAuthenticationResponse(authenticationResponse);
    if (this.localAuthenticationPackage != null && authenticationResponse.getUserData() != null) {
      var identity = authenticationResponse.getUserData().getIdentity();
      Logging.INSTANCE.info(
          "Prepared local authentication package for service startup"
              + (identity == null ? "" : " as user " + identity.getId()));
    }
  }

  private void handleStatus(
      BaseServiceClient service, KlabService.ServiceStatus status, Boolean statusChanged) {
    if (status == null) {
      return;
    }
    clients.put(service, status);
    for (var serviceListener : serviceConsumers) {
      serviceListener.accept(service, status);
    }
    if (Boolean.TRUE.equals(statusChanged)) {
      recomputeEngineStatus();
    }
  }

  private void refreshClientStatus(BaseServiceClient client) {
    handleStatus(client, client.refreshStatus(), true);
  }

  private void refreshClientStatusAsync(BaseServiceClient client) {
    Thread.ofVirtual()
        .name("klab-service-status-refresh")
        .start(
            () -> {
              try {
                refreshClientStatus(client);
              } catch (Throwable throwable) {
                Logging.INSTANCE.error(throwable);
              }
            });
  }

  public LocalInstance getServiceInstance(KlabService.Type type) {
    var instance = serviceInstances.get(type);
    return instance != null && instance.getStatus() == LocalInstance.Status.RUNNING
        ? instance
        : null;
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
    boolean localPrimaryServiceActive = false;

    for (var service : clientSnapshot()) {
      var remote = service.getUrl() != null && !Utils.URLs.isLocalHost(service.getUrl());
      var sStatus = clients.get(service);
      if (sStatus == null) {
        continue;
      }
      if (sStatus.getServiceType() != null) {
        status.getServicesStatus().put(sStatus.getServiceType(), sStatus);
      }
      if (!remote
          && KlabService.Type.operationCritical().contains(sStatus.getServiceType())
          && (sStatus.isOperational() || sStatus.isAvailable() || sStatus.isConnected())) {
        localPrimaryServiceActive = true;
      }
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
      var localServiceIsStarted =
          isLocalServiceInstanceRunning(type) || isLocalServiceReachable(type);
      if (localServiceIsStarted) {
        localPrimaryServiceActive = true;
        if (!localOperational.containsKey(type) && !localAvailable.containsKey(type)) {
          localAvailable.merge(type, 1, Integer::sum);
        }
      }
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
    var localServicesAreStopping = stoppingLocalServices && localPrimaryServiceActive;
    if (stoppingLocalServices && !localPrimaryServiceActive) {
      stoppingLocalServices = false;
    }

    status.setShutdown(localServicesAreStopping || !shutdown.isEmpty());

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

    if (localServicesAreStopping || localTransitioningCount > 0) {
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
            || (this.lastRecordedStatus.isShutdown() != status.isShutdown())
            || (this.lastRecordedStatus.getCondition() != status.getCondition()));

    if (!ret) {
      ret = !this.lastRecordedStatus.getServicesProvision().equals(status.getServicesProvision());
    }

    if (!ret) {
      ret =
          !this.lastRecordedStatus
              .getActiveAuxiliaryServices()
              .equals(status.getActiveAuxiliaryServices());
    }

    this.lastRecordedStatus = status;

    return ret;
  }

  private EngineStatusImpl copyStatus(EngineStatusImpl source) {
    var ret = new EngineStatusImpl();
    ret.setAvailable(source.isAvailable());
    ret.setOperational(source.isOperational());
    ret.setConnected(source.isConnected());
    ret.setBusy(source.isBusy());
    ret.setShutdown(source.isShutdown());
    ret.setCondition(source.getCondition());
    ret.getServicesProvision().putAll(source.getServicesProvision());
    ret.getServicesStatus().putAll(source.getServicesStatus());
    ret.getConnectedUsernames().addAll(source.getConnectedUsernames());
    ret.getActiveAuxiliaryServices().addAll(source.getActiveAuxiliaryServices());
    return ret;
  }

  private void publishTransitionStatus(boolean shutdown) {
    EngineStatusImpl status;
    boolean changed;
    synchronized (this) {
      status = copyStatus(lastRecordedStatus);
      status.setShutdown(shutdown);
      status.setCondition(Engine.Status.EngineCondition.TRANSITIONING);
      changed = updateEngineStatus(status);
    }
    if (changed) {
      for (var consumer : engineConsumers) {
        consumer.accept(status);
      }
    }
  }

  private List<BaseServiceClient> clientSnapshot() {
    synchronized (clients) {
      return new ArrayList<>(clients.keySet());
    }
  }

  private void refreshLocalClientStatuses() {
    for (var client : clientSnapshot()) {
      if (Utils.URLs.isLocalHost(client.getUrl())) {
        refreshClientStatus(client);
      }
    }
  }

  private void refreshLocalClientStatusesAsync() {
    Thread.ofVirtual()
        .name("klab-local-service-status-refresh")
        .start(
            () -> {
              refreshLocalClientStatuses();
              recomputeEngineStatus();
            });
  }

  private boolean isLocalServiceInstanceRunning(KlabService.Type type) {
    var instance = serviceInstances.get(type);
    return instance != null
        && (instance.getStatus() == LocalInstance.Status.RUNNING
            || instance.getStatus() == LocalInstance.Status.WAITING);
  }

  private boolean isLocalServiceClient(BaseServiceClient client, KlabService.Type type) {
    return Utils.URLs.isLocalHost(client.getUrl()) && type == KlabService.Type.classify(client);
  }

  private boolean isLocalServiceReachable(KlabService.Type type) {
    for (var client : clientSnapshot()) {
      if (isLocalServiceClient(client, type)) {
        var status = clients.get(client);
        if (status != null
            && (status.isOperational() || status.isAvailable() || status.isConnected())) {
          return true;
        }
        if (client.isAlive()) {
          return true;
        }
      }
    }
    return false;
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

    stoppingLocalServices = true;
    publishTransitionStatus(true);

    var services =
        clientSnapshot().stream()
            .filter(service -> Utils.URLs.isLocalHost(service.getUrl()))
            .toList();

    Thread.ofVirtual()
        .name("klab-local-instance-stop")
        .start(
            () -> {
              var shutdownThreads = new ArrayList<Thread>();
              for (var service : services) {
                shutdownThreads.add(
                    Thread.ofVirtual()
                        .name("klab-local-service-shutdown")
                        .start(
                            () -> {
                              try {
                                service.requestShutdown();
                              } catch (Throwable throwable) {
                                Logging.INSTANCE.error(throwable);
                              }
                            }));
              }
              for (var shutdownThread : shutdownThreads) {
                try {
                  shutdownThread.join();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                }
              }

              for (var service : new ArrayList<>(serviceInstances.values())) {

                if (!Distribution.Product.Type.PRIMARY_SERVICES.contains(
                    service.getProduct().getType())) {
                  continue;
                }

                try {
                  service.stop();
                } catch (Throwable throwable) {
                  Logging.INSTANCE.error(throwable);
                }
              }
              refreshLocalClientStatuses();
              recomputeEngineStatus();
            });

    return services.size();
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
    if (softwareStack == null || distributionTag == null) {
      return false;
    }

    var languageServer =
        softwareStack.instance(Distribution.Product.Type.LANGUAGE_SERVER, distributionTag);
    if (languageServer != null) {
      // required even if the server already runs
      if (languageServer.getStatus() == LocalInstance.Status.RUNNING) {
        languageServer.stop();
      }
      this.serviceInstances.put(KlabService.Type.LANGUAGE_SERVER, languageServer);

      var ret =
          languageServer.start(
              LocalInstance.Option.PROVIDE_INPUT_STREAM,
              LocalInstance.Option.PROVIDE_OUTPUT_STREAM);
      if (ret) {
        installLanguageServerShutdownHook();
        recomputeEngineStatus();
      }
      return ret;
    }
    return false;
  }

  public boolean startAuxiliaryService(
      Stack softwareStack, Stack.Tag distributionTag, KlabService.Type serviceType, UserScope user) {
    return startAuxiliaryService(softwareStack, distributionTag, serviceType, user, true);
  }

  public boolean ensureRuntimeAuxiliaryServices(
      Stack softwareStack, Stack.Tag distributionTag, UserScope user) {
    if (!isLocalServiceInstanceRunning(KlabService.Type.RUNTIME)
        && !isLocalServiceReachable(KlabService.Type.RUNTIME)) {
      return false;
    }

    var changed =
        ensureAuxiliaryService(
            softwareStack, distributionTag, KlabService.Type.DATABASE, null);
    if (shouldStartLocalBroker(user)) {
      changed =
          ensureAuxiliaryService(
                  softwareStack, distributionTag, KlabService.Type.AMQP_BROKER, null)
              || changed;
    }
    if (changed) {
      recomputeEngineStatus();
    }
    return true;
  }

  private boolean ensureAuxiliaryService(
      Stack softwareStack, Stack.Tag distributionTag, KlabService.Type serviceType, UserScope user) {
    if (isAuxiliaryServiceStarted(serviceType)) {
      return false;
    }
    return startAuxiliaryService(softwareStack, distributionTag, serviceType, user, false)
        && isAuxiliaryServiceStarted(serviceType);
  }

  private boolean isAuxiliaryServiceStarted(KlabService.Type serviceType) {
    var service = serviceInstances.get(serviceType);
    return service != null
        && (service.getStatus() == LocalInstance.Status.RUNNING
            || service.getStatus() == LocalInstance.Status.WAITING);
  }

  private boolean startAuxiliaryService(
      Stack softwareStack,
      Stack.Tag distributionTag,
      KlabService.Type serviceType,
      UserScope user,
      boolean publishStatus) {
    if (softwareStack == null || distributionTag == null) {
      return false;
    }

    var productType = auxiliaryProductType(serviceType);
    if (productType == null) {
      return false;
    }

    var product = softwareStack.instance(productType, distributionTag);
    if (product == null) {
      return false;
    }

    this.serviceInstances.put(serviceType, product);
    if (product.getStatus() == LocalInstance.Status.RUNNING) {
      if (publishStatus) {
        recomputeEngineStatus();
      }
      return true;
    }

    var ret = product.start();
    if (ret && user != null) {
      user.info("Service " + serviceType + " is starting");
    }
    if (publishStatus) {
      recomputeEngineStatus();
    }
    return ret;
  }

  private Distribution.Product.Type auxiliaryProductType(KlabService.Type serviceType) {
    return switch (serviceType) {
      case DATABASE -> Distribution.Product.Type.DATABASE_SERVER;
      case AMQP_BROKER -> Distribution.Product.Type.AMQP_BROKER;
      default -> null;
    };
  }

  private void installLanguageServerShutdownHook() {
    if (languageServerShutdownHook == null) {
      synchronized (this) {
        if (languageServerShutdownHook == null) {
          languageServerShutdownHook =
              new Thread(this::stopLanguageServer, "klab-language-server-shutdown");
          Runtime.getRuntime().addShutdownHook(languageServerShutdownHook);
        }
      }
    }
  }

  public void stopLanguageServer() {
    var service = serviceInstances.get(KlabService.Type.LANGUAGE_SERVER);
    if (service != null && service.getStatus() != LocalInstance.Status.STOPPED) {
      try {
        service.stop();
      } catch (Throwable throwable) {
        Logging.INSTANCE.error(throwable);
      }
      recomputeEngineStatus();
    }
  }

  public void stopApplicationAuxiliaryServices() {
    stopLanguageServer();
    if (settings.get(Setting.STOP_AUXILIARY_SERVICES, Boolean.class)) {
      stopRuntimeAuxiliaryServices();
    }
  }

  public Map<KlabService.Type, KlabService> startLocalServices(
      Stack softwareStack, Stack.Tag distributionTag, UserScope user) {

    var ret = new HashMap<KlabService.Type, KlabService>();

    if (softwareStack != null && distributionTag != null && softwareStack.verify(distributionTag)) {

      stoppingLocalServices = false;
      publishTransitionStatus(false);
      startAuxiliaryService(
          softwareStack, distributionTag, KlabService.Type.DATABASE, user, false);
      if (shouldStartLocalBroker(user)) {
        startAuxiliaryService(
            softwareStack, distributionTag, KlabService.Type.AMQP_BROKER, user, false);
      }
      for (var serviceType :
          new KlabService.Type[] {
            KlabService.Type.RESOURCES,
            KlabService.Type.REASONER,
            KlabService.Type.RUNTIME,
            KlabService.Type.RESOLVER
          }) {

        var product =
            softwareStack.instance(
                Distribution.Product.Type.forService(serviceType), distributionTag);

        if (product != null) {

          this.serviceInstances.put(serviceType, product);

          if (isLocalServiceReachable(serviceType)) {
            if (localAuthenticationPackage != null) {
              Logging.INSTANCE.info(
                  "Local service "
                      + serviceType
                      + " is already reachable; authentication package was not sent");
            }
            user.info(
                "Service "
                    + serviceType
                    + " is already reachable: will be attempting connection to locally running "
                    + serviceType);
          } else if (product.getStatus() == LocalInstance.Status.STOPPED) {
            prepareLocalAuthenticationHandoff(product, serviceType, user);
            if (product.start()) {
              user.info("Service " + serviceType + " is starting");
            }
          } else {
            if (localAuthenticationPackage != null) {
              Logging.INSTANCE.info(
                  "Local service "
                      + serviceType
                      + " is already running; authentication package cannot be injected");
            }
            user.info(
                "Service "
                    + serviceType
                    + " is already running: will be attempting connection to locally running "
                    + serviceType);
          }
        }
      }
      refreshLocalClientStatusesAsync();
    } else {
      user.error("Software stack " + softwareStack + " is not usable");
    }

    return ret;
  }

  private void prepareLocalAuthenticationHandoff(
      LocalInstance instance, KlabService.Type serviceType, UserScope user) {

    if (localAuthenticationPackage == null || localAuthenticationPackage.isBlank()) {
      if (instance instanceof LocalInstanceImpl localInstance) {
        localInstance.setEnvironmentOverride(
            ServiceStartupOptions.LOCAL_AUTHENTICATION_RESPONSE_ENV, null);
      }
      if (user != null
          && user.getUser() != null
          && user.getUser().isAuthenticated()
          && !user.getUser().isAnonymous()) {
        Logging.INSTANCE.warn(
            "No local authentication package available when starting "
                + serviceType
                + "; service will authenticate independently");
      }
      return;
    }

    if (instance instanceof LocalInstanceImpl localInstance) {
      localInstance.setEnvironmentOverride(
          ServiceStartupOptions.LOCAL_AUTHENTICATION_RESPONSE_ENV, localAuthenticationPackage);
      Logging.INSTANCE.info(
          "Passing local authentication package to " + serviceType + " through process environment");
    } else {
      Logging.INSTANCE.warn(
          "Cannot pass local authentication package to "
              + serviceType
              + ": local instance implementation is "
              + instance.getClass().getName());
    }
  }

  private boolean shouldStartLocalBroker(UserScope user) {
    var federation = user == null ? null : Klab.INSTANCE.getFederationData(user.getUser());
    return settings.get(Setting.USE_LOCAL_MESSAGE_BROKER, Boolean.class)
        && (federation == null || Federation.LOCAL_FEDERATION_ID.equals(federation.getId()));
  }

  public void stopAuxServices() {
    stopApplicationAuxiliaryServices();
  }

  public void stopRuntimeAuxiliaryServices() {
    var stopped = false;
    for (var service : serviceInstances.values()) {
      if (Distribution.Product.Type.PRIMARY_SERVICES.contains(service.getProduct().getType())) {
        continue;
      }
      if (service.getProduct().getType() == Distribution.Product.Type.LANGUAGE_SERVER) {
        continue;
      }
      try {
        service.stop();
        stopped = true;
      } catch (Throwable throwable) {
        Logging.INSTANCE.error(throwable);
      }
    }
    if (stopped) {
      recomputeEngineStatus();
    }
  }
}
