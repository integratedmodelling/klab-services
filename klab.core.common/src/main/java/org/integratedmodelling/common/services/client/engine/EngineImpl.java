package org.integratedmodelling.common.services.client.engine;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.common.distribution.DevelopmentDistributionImpl;
import org.integratedmodelling.common.distribution.DistributionImpl;
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
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

/** */
public class EngineImpl implements Engine, PropertyHolder {

  private final AtomicBoolean online = new AtomicBoolean(false);
  private final AtomicBoolean booted = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private UserScope defaultUser;
  private Pair<Identity, List<ServiceReference>> authData;
  private final List<UserScope> users = new ArrayList<>();
  private final String serviceId = Utils.Names.shortUUID();
  private final Parameters<Setting> settings = Parameters.createSynchronized();
  private ServiceMonitor serviceMonitor;
  private final DistributionImpl distribution;
  private DistributionImpl developmentDistribution;
  private DistributionImpl downloadedDistribution;
  private final Distribution.Status distributionStatus;

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

    stopped.set(true);
    booted.set(false);

    return true;
  }

  @Override
  public int stopLocalServices() {
    return serviceMonitor.stopLocalServices();
  }

  public Distribution getDistribution() {
    return distribution;
  }

  @Override
  public Map<KlabService.Type, KlabService> startLocalServices() {

    var ret = new HashMap<KlabService.Type, KlabService>();

    if (distribution != null && distribution.isAvailable()) {

      /*
       * If user is federated, we don't start the local broker. Otherwise, we set up a local
       * federated identity and tell the runtime service to create an embedded broker on the default
       * URL and port.
       */
      var federationData =
          getUser()
              .getUser()
              .getData()
              .get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);

      if (federationData == null || federationData.getBroker() == null) {
        var id = federationData == null ? null : federationData.getId();
        if (id == null) {
          id = "local.federation";
        }
        federationData = new Federation(id, Channel.LOCAL_BROKER_URL + Channel.LOCAL_BROKER_PORT);
        getUser().getUser().getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, federationData);
      }

      for (var serviceType :
          new KlabService.Type[] {Type.RESOURCES, Type.REASONER, Type.RUNTIME, Type.RESOLVER}) {
        var product = distribution.findProduct(Product.ProductType.forService(serviceType));
        if (product != null) {
          var instance = product.getInstance(defaultUser);
          if (serviceType == Type.RUNTIME
              && instance.getSettings() instanceof ServiceStartupOptions serviceStartupOptions) {
            serviceStartupOptions.setStartLocalBroker(true);
          }

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
  public String registerSession(SessionScope sessionScope, Federation federation) {

    var sessionId =
        getUser().getService(RuntimeService.class).registerSession(sessionScope, federation);
    if (sessionId != null) {
      // TODO advertise the session to all other services that will use it. Keep only the
      //  services that accepted it.
    }
    return sessionId;
  }

  @Override
  public String registerContext(ContextScope contextScope, Federation federation) {

    var contextId =
        getUser().getService(RuntimeService.class).registerContext(contextScope, federation);
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

  private void notifyLocalEngine(Engine.Status status) {
    this.defaultUser.send(
        Message.MessageClass.EngineLifecycle, Message.MessageType.EngineStatusChanged, status);
  }

  private void notifyLocalService(KlabService klabService, ServiceStatus serviceStatus) {
    this.defaultUser.send(
        Message.MessageClass.EngineLifecycle, Message.MessageType.ServiceStatus, serviceStatus);
    //    Logging.INSTANCE.info("GOT SERVICE STATUS " + serviceStatus);
  }

  @Override
  public UserScope authenticate() {

    if (this.defaultUser == null) {

      this.authData = Authentication.INSTANCE.authenticate(settings);
      this.serviceMonitor =
          new ServiceMonitor(
              authData.getFirst(),
              settings,
              true,
              authData.getSecond(),
              this::notifyLocalService,
              this::notifyLocalEngine);
      this.defaultUser =
          new ClientUserScope(authData.getFirst(), this) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
              return (T) serviceMonitor.getService(serviceClass);
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
              return serviceMonitor.getServices(serviceClass);
            }
          };
      this.users.add(this.defaultUser);
      this.defaultUser.send(
          Message.MessageClass.Authorization,
          Message.MessageType.UserAuthorized,
          authData.getFirst());
    }

    return this.defaultUser;
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
