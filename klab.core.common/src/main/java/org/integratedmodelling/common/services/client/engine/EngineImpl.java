package org.integratedmodelling.common.services.client.engine;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.distribution.DevelopmentDistributionImpl;
import org.integratedmodelling.common.distribution.DistributionImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.common.services.client.scope.ClientUserScope;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.impl.AbstractDistributionImpl;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import static org.integratedmodelling.klab.api.identities.EngineIdentity.type;

/** */
public class EngineImpl implements Engine, PropertyHolder {

  private AtomicBoolean online = new AtomicBoolean(false);
  private AtomicBoolean booted = new AtomicBoolean(false);
  private AtomicBoolean stopped = new AtomicBoolean(false);
  private Map<KlabService.Type, KlabService> currentServices =
      Collections.synchronizedMap(new HashMap<>());
  private Map<KlabService.Type, List<KlabService>> availableServices =
      Collections.synchronizedMap(new HashMap<>());
  private UserScope defaultUser;
  private Pair<Identity, List<ServiceReference>> authData;
  private List<UserScope> users = new ArrayList<>();
  private String serviceId = Utils.Names.shortUUID();
  private AtomicReference<Status> status = new AtomicReference<>(EngineStatusImpl.inop());
  private Parameters<Setting> settings = Parameters.createSynchronized();
  private LocalServiceMonitor localServiceMonitor;
  private DistributionImpl distribution;
  private DistributionImpl developmentDistribution;
  private DistributionImpl downloadedDistribution;
  private Distribution.Status distributionStatus;

  public EngineImpl() {
    settings.put(Setting.POLLING, "on");
    settings.put(Setting.POLLING_INTERVAL, 5);
    settings.put(Setting.LOG_EVENTS, false);
    settings.put(Setting.LAUNCH_PRODUCT, true);

    if (DistributionImpl.isDevelopmentDistributionAvailable()) {
      this.developmentDistribution = new DevelopmentDistributionImpl();
    }

    this.downloadedDistribution = new DistributionImpl();
    this.distribution =
        this.developmentDistribution == null
            ? this.downloadedDistribution
            : this.developmentDistribution;

    var status = new AbstractDistributionImpl.StatusImpl();
    status.setAvailableDevelopmentVersion(
        // TODO should use the Git status
        this.developmentDistribution == null ? Version.EMPTY_VERSION : Version.CURRENT_VERSION);
    status.setDevelopmentStatus(
        this.developmentDistribution == null
            ? Product.Status.UNAVAILABLE
            : Product.Status.UP_TO_DATE);

    // TODO -- no handling for now; the downloaded distro should carry the latest version available

    this.distributionStatus = status;
  }

  @Override
  public Distribution.Status getDistributionStatus() {
    return this.distributionStatus;
  }

  public UserScope getUser() {
    return !this.users.isEmpty() ? users.getFirst() : null;
  }

  @Override
  public List<UserScope> getUsers() {
    return users;
  }

  @Override
  public ServiceCapabilities capabilities(Scope scope) {
    return null;
  }

  @Override
  public ServiceStatus status() {
    return null;
  }

  @Override
  public URL getUrl() {
    return null;
  }

  @Override
  public String getLocalName() {
    return null;
  }

  /**
   * The client engine works under a user scope.
   *
   * @return
   */
  @Override
  public UserScope serviceScope() {
    return defaultUser;
  }

  @Override
  public boolean shutdown() {

    // TODO see what to do with the local services - should depend on options

    serviceScope()
        .send(
            Message.MessageClass.EngineLifecycle,
            Message.MessageType.ServiceUnavailable,
            capabilities(serviceScope()));

    stopped.set(true);
    booted.set(false);

    return true;
  }

  @Override
  public int stopLocalServices() {

    AtomicInteger ret = new AtomicInteger(0);
    List<Supplier<KlabService>> tasks = new ArrayList<>();
    for (var type : availableServices.keySet()) {
      for (var service : availableServices.get(type)) {
        if (Utils.URLs.isLocalHost(service.getUrl())) {
          tasks.add(
              () -> {
                service.shutdown();
                return service;
              });
        }
      }
    }

    if (!tasks.isEmpty()) {
      try (var executor = Executors.newFixedThreadPool(tasks.size())) {
        for (var task : tasks) {
          CompletableFuture.supplyAsync(task)
              .thenApply(
                  service -> {
                    var type = KlabService.Type.classify(service);
                    availableServices.get(type).remove(service);

                    this.defaultUser.send(
                        Message.MessageClass.ServiceLifecycle,
                        Message.MessageType.ServiceUnavailable,
                        currentServices.get(type).capabilities(this.defaultUser));

                    currentServices.remove(type);

                    if (currentServices.get(type) != null
                        && currentServices.get(type).serviceId().equals(service.serviceId())) {
                      if (!availableServices.get(type).isEmpty()) {
                        currentServices.put(type, availableServices.get(type).getFirst());
                        this.defaultUser.send(
                            Message.MessageClass.ServiceLifecycle,
                            Message.MessageType.ServiceSwitched,
                            currentServices.get(type).capabilities(this.defaultUser));
                      }
                    }
                    ret.incrementAndGet();
                    return service;
                  });
        }
      } catch (Exception e) {
        return -1;
      }
    }

    this.defaultUser.send(
        Message.MessageClass.EngineLifecycle,
        Message.MessageType.EngineStatusChanged,
        computeEngineStatus(currentServices));

    return ret.get();
  }

  public Distribution getDistribution() {
    return distribution;
  }

  @Override
  public Map<KlabService.Type, KlabService> startLocalServices() {

    var ret = new HashMap<KlabService.Type, KlabService>();

    if (distribution != null && distribution.isAvailable()) {

      for (var serviceType :
          new KlabService.Type[] {Type.RESOURCES, Type.REASONER, Type.RUNTIME, Type.RESOLVER}) {
        var product = distribution.findProduct(Product.ProductType.forService(serviceType));
        if (product != null) {
          var instance = product.getInstance(defaultUser);
          if (instance.start()) {
            serviceScope()
                .info(
                    "Service is starting: will be attempting connection to locally running "
                        + serviceType);
          }
        }
      }
    }

    return ret;
  }

  @Override
  public String registerSession(SessionScope sessionScope) {

    var sessionId = getUser().getService(RuntimeService.class).registerSession(sessionScope);
    if (sessionId != null) {
      // TODO advertise the session to all other services that will use it. Keep only the
      //  services that accepted it.
    }
    return sessionId;
  }

  @Override
  public String registerContext(ContextScope contextScope) {

    var contextId = getUser().getService(RuntimeService.class).registerContext(contextScope);
    if (contextId != null) {
      // TODO advertise the context to all other services that will use it. Keep only the
      // services that accept it.

    }
    return contextId;
  }

  @Override
  public boolean isExclusive() {
    // the engine is just an orchestrator so we can assume every client is local.
    return true;
  }

  @Override
  public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
    return null;
  }

  @Override
  public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
    return false;
  }

  @Override
  public void boot() {

    if (this.defaultUser == null) {
      this.defaultUser = authenticate();
    }

    this.defaultUser.send(
        Message.MessageClass.EngineLifecycle,
        Message.MessageType.ServiceInitializing,
        capabilities(serviceScope()));
    //    scheduler.scheduleAtFixedRate(this::timedTasks, 0, 2, TimeUnit.SECONDS);
    booted.set(true);

    if (distribution != null) {
      this.defaultUser.send(
          Message.MessageClass.EngineLifecycle,
          Message.MessageType.UsingDistribution,
          distribution);
    }
  }

  private void notifyLocalEngine(Map<Type, KlabService> serviceMap, Boolean online) {
    var status = computeEngineStatus(serviceMap);
    this.defaultUser.send(
        Message.MessageClass.EngineLifecycle, Message.MessageType.EngineStatusChanged, status);
  }

  private void notifyLocalService(KlabService klabService, ServiceStatus serviceStatus) {
    boolean local = Utils.URLs.isLocalHost(klabService.getUrl());
    if (serviceStatus.getServiceId() == null) {
      return;
    }
    var exist =
        availableServices
            .computeIfAbsent(serviceStatus.getServiceType(), s -> new ArrayList<>())
            .stream()
            .anyMatch(s -> serviceStatus.getServiceId().equals(s.serviceId()));
    if (!exist) {
      availableServices.get(serviceStatus.getServiceType()).add(klabService);
    }
    if (local) {
      if (currentServices.get(serviceStatus.getServiceType()) == null
          || !currentServices
              .get(serviceStatus.getServiceType())
              .serviceId()
              .equals(klabService.serviceId())) {
        currentServices.put(serviceStatus.getServiceType(), klabService);
        this.defaultUser.send(
            Message.MessageClass.ServiceLifecycle,
            Message.MessageType.ServiceSwitched,
            klabService.capabilities(this.defaultUser));
      }
    }
  }

  @Override
  public UserScope authenticate() {

    if (this.defaultUser == null) {

      this.authData = Authentication.INSTANCE.authenticate(settings);
      this.defaultUser = createUserScope(authData);
      this.defaultUser.send(
          Message.MessageClass.Authorization,
          Message.MessageType.UserAuthorized,
          authData.getFirst());
      if (distribution != null) {
        this.localServiceMonitor =
            new LocalServiceMonitor(
                defaultUser.getIdentity(),
                settings,
                this::notifyLocalService,
                this::notifyLocalEngine);
      }

      this.users.add(this.defaultUser);
    }

    return this.defaultUser;
  }

  /**
   * TODO remove the polling from the clients, put it here explicitly, check status and update
   * capablities if not available previously
   */
  private EngineStatusImpl computeEngineStatus(Map<KlabService.Type, KlabService> map) {

    EngineStatusImpl engineStatus = new EngineStatusImpl();

    currentServices
        .values()
        .forEach(
            s -> engineStatus.getServicesStatus().put(KlabService.Type.classify(s), s.status()));

    var nOperational =
        engineStatus.getServicesStatus().values().stream()
            .filter(ServiceStatus::isOperational)
            .count();
    var nAvailable =
        engineStatus.getServicesStatus().values().stream()
            .filter(ServiceStatus::isAvailable)
            .count();

    if (users.size() != status.get().getConnectedUsernames().size()) {
      engineStatus
          .getConnectedUsernames()
          .addAll(users.stream().map(userScope -> userScope.getUser().getUsername()).toList());
    }

    engineStatus.setOperational(nOperational == 4);
    engineStatus.setAvailable(nAvailable > 0);
    this.online.set(nOperational == 4);

    return engineStatus;
  }

  private void registerService(KlabService.Type serviceType, KlabService service) {
    availableServices.computeIfAbsent(serviceType, type -> new ArrayList<>()).add(service);
    currentServices.putIfAbsent(serviceType, service);
  }

  @SuppressWarnings("unchecked")
  private UserScope createUserScope(Pair<Identity, List<ServiceReference>> availableServices) {

    var ret =
        new ClientUserScope(authData.getFirst(), this) {
          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            return (T) currentServices.get(KlabService.Type.classify(serviceClass));
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            return (Collection<T>)
                EngineImpl.this.availableServices.computeIfAbsent(
                    KlabService.Type.classify(serviceClass), s -> new ArrayList<>());
          }
        };

    for (var service : availableServices.getSecond()) {
      for (var url : service.getUrls()) {
        if (ServiceClient.readServiceStatus(url, ret) != null) {
          ret.info(
              "Using authenticated "
                  + service.getIdentityType()
                  + " service from "
                  + service.getPartner().getId());
          var client =
              createLocalServiceClient(service, url, ret, availableServices.getFirst(), settings);
          registerService(service.getIdentityType(), client);
        }
      }
    }

    return ret;
  }

  @SuppressWarnings("unchecked")
  public final <T extends KlabService> T createLocalServiceClient(
      ServiceReference serviceReference,
      URL url,
      Scope scope,
      Identity identity,
      Parameters<Engine.Setting> settings) {
    T ret =
        switch (serviceReference.getIdentityType()) {
          case REASONER -> (T) new ReasonerClient(url, identity, settings);
          case RESOURCES -> (T) new ResourcesClient(url, identity, settings);
          case RESOLVER -> (T) new ResolverClient(url, identity, settings);
          case RUNTIME -> (T) new RuntimeClient(url, identity, settings);
          default ->
              throw new IllegalStateException(
                  "Unexpected value: " + serviceReference.getIdentityType());
        };

    ((ServiceClient) ret).setProperties(serviceReference);

    scope.send(
        Message.MessageClass.ServiceLifecycle,
        Message.MessageType.ServiceInitializing,
        serviceReference.getIdentityType()
            + " service at "
            + serviceReference.getIdentityType().localServiceUrl());

    return ret;
  }

  @Override
  public boolean isOnline() {
    return this.online.get();
  }

  @Override
  public String configurationPath() {
    return "engine/client";
  }

  private boolean isStopped() {
    return this.stopped.get();
  }

  @Override
  public String getServiceName() {
    return null;
  }

  public String serviceId() {
    return serviceId;
  }

  public void setDefaultService(ServiceCapabilities service) {

    boolean found = false;
    var current = currentServices.get(service.getType());
    if (current == null
        || current.serviceId() == null
        || !current.serviceId().equals(service.getServiceId())) {
      for (var s : availableServices.computeIfAbsent(service.getType(), t -> new ArrayList<>())) {
        if (s.serviceId() != null && s.serviceId().equals(service.getServiceId())) {
          currentServices.put(service.getType(), s);
          found = true;
          break;
        }
      }
    } else {
      // no change needed, things are already as requested
      found = true;
    }

    if (!found) {
      serviceScope()
          .error(
              "EngineImpl: cannot set unknown "
                  + service.getType()
                  + " service with "
                  + "ID "
                  + service.getServiceId()
                  + " as default: service is not available to the engine");
    }
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
    return Authentication.INSTANCE.getCredentialInfo(scope);
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    return Authentication.INSTANCE.addExternalCredentials(host, credentials, scope);
  }

  @Override
  public Map<Setting, Object> getSettings() {
    return settings;
  }

  @Override
  public InputStream exportAsset(
      String urn, ResourceTransport.Schema exportSchema, String mediaType, Scope scope) {
    // TODO establish which service we're targeting and route the request to it
    return null;
  }

  @Override
  public String importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope) {
    // TODO establish which service we're targeting and route the request to it
    if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES) {

    } else if (schema.getType() == ResourceTransport.Schema.Type.STREAM) {

    }
    return null;
  }
}
