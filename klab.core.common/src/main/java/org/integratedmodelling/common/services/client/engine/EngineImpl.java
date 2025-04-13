package org.integratedmodelling.common.services.client.engine;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.distribution.DevelopmentDistributionImpl;
import org.integratedmodelling.common.distribution.DistributionImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.common.services.client.scope.ClientUserScope;
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

/** */
public class EngineImpl implements Engine, PropertyHolder {

  AtomicBoolean online = new AtomicBoolean(false);
  AtomicBoolean available = new AtomicBoolean(false);
  AtomicBoolean booted = new AtomicBoolean(false);
  AtomicBoolean stopped = new AtomicBoolean(false);
  Map<KlabService.Type, KlabService> currentService = new HashMap<>();
  Map<KlabService.Type, List<KlabService>> currentServices =
      Collections.synchronizedMap(new HashMap<>());
  UserScope defaultUser;
  //  List<BiConsumer<Channel, Message>> scopeListeners = new ArrayList<>();
  private Pair<Identity, List<ServiceReference>> authData;
  List<UserScope> users = new ArrayList<>();
  ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  //  private boolean firstCall = true;
  String serviceId = Utils.Names.shortUUID();
  //  private Worldview worldview;
  private AtomicReference<Status> status = new AtomicReference<>(EngineStatusImpl.inop());
  private Parameters<Setting> settings = Parameters.createSynchronized();

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

  //  public void addScopeListener(BiConsumer<Channel, Message> listener) {
  //    this.scopeListeners.add(listener);
  //  }

  @Override
  public boolean shutdown() {

    serviceScope()
        .send(
            Message.MessageClass.EngineLifecycle,
            Message.MessageType.ServiceUnavailable,
            capabilities(serviceScope()));

    /* shutdown all services that were launched in our scope */
    for (KlabService.Type type :
        new KlabService.Type[] {
          KlabService.Type.RUNTIME,
          KlabService.Type.RESOLVER,
          KlabService.Type.REASONER,
          KlabService.Type.RESOURCES
        }) {
      for (var service : currentServices.computeIfAbsent(type, t -> new ArrayList<>())) {
        if (service instanceof ServiceClient client && client.isLocal()) {
          client.shutdown();
        }
      }
    }

    stopped.set(true);
    booted.set(false);

    return true;
  }

  /**
   * FIXME blocks until all services have ended. Should do better and just report better info
   * through the engine status.
   *
   * @return
   */
  @Override
  public int stopLocalServices() {

    AtomicInteger ret = new AtomicInteger(0);
    List<Callable<Boolean>> tasks = new ArrayList<>();
    for (var type : currentServices.keySet()) {
      for (var service : currentServices.get(type)) {
        if (Utils.URLs.isLocalHost(service.getUrl())) {
          tasks.add(
              () -> {
                if (service.shutdown()) {
                  currentServices.get(type).remove(service);
                  ret.incrementAndGet();
                  return true;
                }
                return false;
              });
        }
      }
    }

    if (!tasks.isEmpty()) {
      try (var executor = Executors.newFixedThreadPool(tasks.size())) {
        // FIXME just use submit and add the maintenance logic on future termination. Return the
        //  number of services BEING STOPPED. The status will inform as the shutdown proceeds.
        executor.invokeAll(tasks);
        // TODO redefine the current service
      } catch (InterruptedException e) {
        return -1;
      }
    }

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
            var service =
                createLocalServiceClient(
                    ServiceReference.local(serviceType, defaultUser),
                    serviceType.localServiceUrl(),
                    defaultUser,
                    defaultUser.getIdentity(),
                    settings);
            ret.put(serviceType, service);
            registerService(serviceType, service);
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

    //    if (this.defaultUser instanceof ChannelImpl channel) {
    //      for (var listener : scopeListeners) {
    //        channel.addListener(listener);
    //      }
    //    }
    this.defaultUser.send(
        Message.MessageClass.EngineLifecycle,
        Message.MessageType.ServiceInitializing,
        capabilities(serviceScope()));
    scheduler.scheduleAtFixedRate(this::timedTasks, 0, 15, TimeUnit.SECONDS);
    booted.set(true);

    if (distribution != null) {
      this.defaultUser.send(
          Message.MessageClass.EngineLifecycle,
          Message.MessageType.UsingDistribution,
          distribution);
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
      //      this.scopeListeners.add(
      //          (channel, message) -> {
      //
      //            // basic listener for knowledge management
      //            if (message.is(
      //                Message.MessageClass.KnowledgeLifecycle,
      // Message.MessageType.WorkspaceChanged)) {
      //              var changes = message.getPayload(ResourceSet.class);
      //              var reasoner = defaultUser.getService(Reasoner.class);
      //              if (reasoner.status().isAvailable()
      //                  && reasoner.isExclusive()
      //                  && reasoner instanceof Reasoner.Admin admin) {
      //                var notifications = admin.updateKnowledge(changes, getUser());
      //                // send the notifications around for display
      //                serviceScope()
      //                    .send(
      //                        Message.MessageClass.KnowledgeLifecycle,
      //                        Message.MessageType.LogicalValidation,
      //                        notifications);
      //                if (Utils.Resources.hasErrors(notifications)) {
      //                  defaultUser.warn(
      //                      "Worldview update caused logical" + " errors in the reasoner",
      //                      UI.Interactivity.DISPLAY);
      //                } else {
      //                  defaultUser.info(
      //                      "Worldview was updated in the reasoner", UI.Interactivity.DISPLAY);
      //                }
      //              }
      //            }
      //          });
      this.users.add(this.defaultUser);
    }

    return this.defaultUser;
  }

  private void timedTasks() {

    if ("off".equals(settings.get(Engine.Setting.POLLING, String.class))) {
      return;
    }

    /*
    check all needed services; put self offline if not available or not there, online otherwise; if
    there's a change in online status, report it through the service scope
     */
    var ok = true;
    for (var type :
        List.of(
            KlabService.Type.RESOURCES,
            KlabService.Type.REASONER,
            KlabService.Type.RUNTIME,
            KlabService.Type.RESOLVER)) {

      var services = currentServices.computeIfAbsent(type, t -> new ArrayList<>());
      if (services.isEmpty()) {
        ok = false;
      }
    }

    recomputeEngineStatus();

    available.set(ok);
  }

  /**
   * TODO remove the polling from the clients, put it here explicitly, check status and update
   * capablities if not available previously
   */
  private synchronized void recomputeEngineStatus() {

    // explore state of all services, determine what we
    EngineStatusImpl engineStatus = new EngineStatusImpl();

    var changes = false;
    var noperational = 0;
    for (var scl :
        List.of(Reasoner.class, ResourcesService.class, Resolver.class, RuntimeService.class)) {
      var service = serviceScope().getService(scl);
      var sertype = KlabService.Type.classify(scl);
      if (service != null) {
        var newStatus = service.status();
        if (newStatus != null) {
          if (newStatus.isOperational()) {
            noperational++;
          }
          var oldStatus = status.get().getServicesStatus().get(sertype);
          if (oldStatus == null || newStatus.hasChangedComparedTo(oldStatus)) {
            changes = true;
            engineStatus.getServicesStatus().put(sertype, newStatus);
          } else {
            engineStatus.getServicesStatus().put(sertype, oldStatus);
          }
          if (status.get().getServicesCapabilities().get(sertype) == null) {
            var capabilities = service.capabilities(getUser());
            if (capabilities != null) {
              engineStatus.getServicesCapabilities().put(sertype, capabilities);
            }
          }
        }
      } else if (status.get().getServicesStatus() != null) {
        changes = true;
      }
    }

    if (users.size() != status.get().getConnectedUsernames().size()) {
      engineStatus
          .getConnectedUsernames()
          .addAll(users.stream().map(userScope -> userScope.getUser().getUsername()).toList());
    }

    engineStatus.setOperational(noperational == 4);
    engineStatus.setAvailable(noperational > 0);

    // if state has changed, swap and send message
    if (changes) {
      this.status.set(engineStatus);

      serviceScope()
          .send(
              Message.MessageClass.EngineLifecycle,
              Message.MessageType.EngineStatusChanged,
              engineStatus);
    }
  }

  private void registerService(KlabService.Type serviceType, KlabService service) {
    currentServices.computeIfAbsent(serviceType, type -> new ArrayList<>()).add(service);
    currentService.putIfAbsent(serviceType, service);
    // HERE install the service listener that will send any message with <serviceReference, message>
    // to us. We in turn dispatch it to the
    // service scope.
  }

  //  /**
  //   * Override to define which services must be there for the engine client to report as
  // available.
  //   * TODO currently set up for testing, default should be everything except COMMUNITY
  //   */
  //  protected boolean serviceIsEssential(KlabService.Type type) {
  //    return type == KlabService.Type.REASONER || type == KlabService.Type.RESOURCES;
  //  }

  @SuppressWarnings("unchecked")
  private UserScope createUserScope(Pair<Identity, List<ServiceReference>> availableServices) {

    var ret =
        new ClientUserScope(authData.getFirst(), this /*,
            (serviceScope() instanceof ChannelImpl channel)
                ? channel.listeners().toArray(new BiConsumer[] {})
                : new BiConsumer[] {}*/) {
          @Override
          public <T extends KlabService> T getService(Class<T> serviceClass) {
            return (T) currentService.get(KlabService.Type.classify(serviceClass));
          }

          @Override
          public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
            return (Collection<T>)
                currentServices.computeIfAbsent(
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
      //      List<ServiceReference> services,
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
  public boolean isAvailable() {
    return this.available.get();
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
    var current = currentService.get(service.getType());
    if (current == null
        || current.serviceId() == null
        || !current.serviceId().equals(service.getServiceId())) {
      for (var s : currentServices.computeIfAbsent(service.getType(), t -> new ArrayList<>())) {
        if (s.serviceId() != null && s.serviceId().equals(service.getServiceId())) {
          currentService.put(service.getType(), s);
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
