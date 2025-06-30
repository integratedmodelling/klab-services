package org.integratedmodelling.common.services.client.engine;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.distribution.DevelopmentDistributionImpl;
import org.integratedmodelling.common.distribution.DistributionImpl;
import org.integratedmodelling.common.services.ServiceStartupOptions;
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
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Channel;
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
  private Federation federationData;
  private Consumer<Status> engineStatusMonitor;
  private BiConsumer<KlabService, KlabService.ServiceStatus> serviceStatusMonitor;

  public EngineImpl(
      Consumer<Status> engineStatusMonitor,
      BiConsumer<KlabService, KlabService.ServiceStatus> serviceStatusMonitor) {

    settings.put(Setting.POLLING, "on");
    settings.put(Setting.POLLING_INTERVAL, 5);
    settings.put(Setting.LOG_EVENTS, false);
    settings.put(Setting.LAUNCH_PRODUCT, true);
    settings.put(Setting.LOCAL_ONLY, false);

    if (DistributionImpl.isDevelopmentDistributionAvailable()) {
      this.developmentDistribution = new DevelopmentDistributionImpl();
    }

    this.serviceStatusMonitor = serviceStatusMonitor;
    this.engineStatusMonitor = engineStatusMonitor;
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
  public UserScope getOwner() {
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

      for (var serviceType :
          new KlabService.Type[] {
            KlabService.Type.RESOURCES,
            KlabService.Type.REASONER,
            KlabService.Type.RUNTIME,
            KlabService.Type.RESOLVER
          }) {
        var product = distribution.findProduct(Product.ProductType.forService(serviceType));
        if (product != null) {
          var instance = product.getInstance(defaultUser);
          if (serviceType == KlabService.Type.RUNTIME
              && instance.getSettings() instanceof ServiceStartupOptions serviceStartupOptions) {
            serviceStartupOptions.setStartLocalBroker(true);
          }

          if (instance.start()) {
            this.defaultUser.info(
                "Service is starting: will be attempting connection to locally running "
                    + serviceType);
          }
        }
      }
    }

    return ret;
  }

  @Override
  public void boot() {

    if (this.defaultUser == null) {
      this.defaultUser = authenticate();
    }

    var federation =
        this.defaultUser
            .getIdentity()
            .getData()
            .get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);
    /* No federation even with local services, which will message to downstream scopes */
    if (federation != null
        && !Federation.LOCAL_FEDERATION_ID.equals(federation.getId())
        && this.defaultUser instanceof MessagingChannelImpl messagingChannel) {
      messagingChannel.setupMessaging(
          federation, federation.getId(), messagingChannel.defaultQueues());
    }
  }

  private void notifyLocalEngine(Engine.Status status) {
    if (engineStatusMonitor != null) {
      engineStatusMonitor.accept(status);
    }
    //    this.defaultUser.send(
    //        Message.MessageClass.EngineLifecycle, Message.MessageType.EngineStatusChanged,
    // status);
  }

  private void notifyLocalService(
      KlabService klabService, KlabService.ServiceStatus serviceStatus) {
    if (serviceStatusMonitor != null) {
      serviceStatusMonitor.accept(klabService, serviceStatus);
    }
    //    this.defaultUser.send(
    //        Message.MessageClass.EngineLifecycle, Message.MessageType.ServiceStatus,
    // serviceStatus);
    //    Logging.INSTANCE.info("GOT SERVICE STATUS " + serviceStatus);
  }

  @Override
  public UserScope authenticate() {

    if (this.defaultUser == null) {

      this.authData = Authentication.INSTANCE.authenticate(settings);

      /*
       * If user is federated, we don't start the local broker. Otherwise, we set up a local
       * federated identity and tell the runtime service to create an embedded broker on the default
       * URL and port.
       *
       * FIXME check if this still applies (federation is a group)
       */
      this.federationData =
          authData
              .getFirst()
              .getData()
              .get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);

      if (federationData == null || federationData.getBroker() == null) {
        var id = federationData == null ? null : federationData.getId();
        if (id == null) {
          id = Federation.LOCAL_FEDERATION_ID;
        }
        federationData = new Federation(id, Channel.LOCAL_BROKER_URL + Channel.LOCAL_BROKER_PORT);
      }

      /* federation must be already established at this point */
      this.defaultUser =
          new ClientUserScope((UserIdentity) authData.getFirst(), this) {
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

      this.serviceMonitor =
          new ServiceMonitor(
              authData.getFirst(),
              settings,
              true,
              authData.getSecond(),
              this::notifyLocalService,
              this::notifyLocalEngine);
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

  public String serviceId() {
    return serviceId;
  }

  @Override
  public Map<Setting, Object> getSettings() {
    return settings;
  }
}
