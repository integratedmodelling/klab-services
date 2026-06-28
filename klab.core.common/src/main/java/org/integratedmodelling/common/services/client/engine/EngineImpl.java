package org.integratedmodelling.common.services.client.engine;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.scope.ClientScopeManager;
import org.integratedmodelling.common.services.client.scope.ClientUserScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;
import org.integratedmodelling.klab.rest.EngineAuthenticationResponse;
import org.integratedmodelling.klab.rest.ServiceReference;

/** */
public class EngineImpl implements Engine, PropertyHolder {

  private static final long WORLDVIEW_UPDATE_INTERVAL_MINUTES = 5;
  private final AtomicBoolean online = new AtomicBoolean(false);
  private final AtomicBoolean booted = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private UserScope defaultUser;
  private Pair<Identity, List<ServiceReference>> authData;
  private EngineAuthenticationResponse authenticationResponse;
  private final List<UserScope> users = new ArrayList<>();
  private final String serviceId = Utils.Names.shortUUID();
  private final Settings settings = SettingsImpl.forEngine();
  private ServiceMonitor serviceMonitor;
  private Federation federationData;
  private Consumer<Status> engineStatusMonitor;
  private BiConsumer<KlabService, KlabService.ServiceStatus> serviceStatusMonitor;
  private boolean onlineStatusNotified = false;
  private final AtomicBoolean runtimeAuxiliaryCheckRunning = new AtomicBoolean(false);
  private Stack softwareStack;
  private Stack.Tag distributionTag = Stack.Tag.LATEST_STABLE;
  private Worldview worldview;
  private long lastWorldviewUpdate;
  private final Set<String> advertisedScopeTargets = ConcurrentHashMap.newKeySet();

  public EngineImpl(
      Consumer<Status> engineStatusMonitor,
      BiConsumer<KlabService, KlabService.ServiceStatus> serviceStatusMonitor) {
    Configuration.INSTANCE.setDefaults(settings);
    this.serviceStatusMonitor = serviceStatusMonitor;
    this.engineStatusMonitor = engineStatusMonitor;
  }

  public ServiceMonitor getServiceMonitor() {
    return serviceMonitor;
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

  public Federation getFederation() {
    return federationData;
  }

  @Override
  public boolean shutdown() {

    stopped.set(true);
    booted.set(false);

    if (serviceMonitor != null) {
      serviceMonitor.stopApplicationAuxiliaryServices();
    }
    ClientScopeManager.INSTANCE.close();

    return true;
  }

  @Override
  public int stopLocalServices() {
    var ret = serviceMonitor.stopLocalServices();
    if (settings.get(Setting.EXIT_WHEN_STOPPING_SERVICES, Boolean.class)) {
      serviceMonitor.stopApplicationAuxiliaryServices();
      Executors.newScheduledThreadPool(1).schedule(() -> System.exit(0), 2, TimeUnit.SECONDS);
    }
    return ret;
  }

  @Override
  public Stack getSoftwareStack() {
    return softwareStack;
  }

  @Override
  public boolean hasValidSoftwareStack() {
    return softwareStack != null
        && distributionTag != null
        && softwareStack.verify(distributionTag);
  }

  public void setDistributionTag(Stack.Tag distributionTag) {
    this.distributionTag = distributionTag;
  }

  @Override
  public boolean startAuxiliaryServices(KlabService.Type... types) {
    if (serviceMonitor == null || softwareStack == null || distributionTag == null) {
      return false;
    }
    if (types != null) {
      for (KlabService.Type type : types) {
        if (type == KlabService.Type.LANGUAGE_SERVER) {
          if (!serviceMonitor.startLSPServer(softwareStack, distributionTag, defaultUser)) {
            return false;
          }
        } else if (type == KlabService.Type.DATABASE || type == KlabService.Type.AMQP_BROKER) {
          if (!serviceMonitor.startAuxiliaryService(
              softwareStack, distributionTag, type, defaultUser)) {
            return false;
          }
        } else {
          throw new UnsupportedOperationException(
              "Auxiliary service type not yet supported: " + type);
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public Stack.Tag getDistributionTag() {
    return distributionTag;
  }

  @Override
  public Map<KlabService.Type, KlabService> startLocalServices() {
    // this will force a re-advertising of services when they all come up, as long as the engine
    // becomes operational again only when the 4 local are available
    onlineStatusNotified = false;
    advertisedScopeTargets.clear();
    return serviceMonitor.startLocalServices(softwareStack, distributionTag, defaultUser);
  }

  @Override
  public LocalInstance getServiceInstance(KlabService.Type type) {
    return serviceMonitor.getServiceInstance(type);
  }

  @Override
  public void boot() {

    Klab.INSTANCE.setExecutionContext(
        new Klab.ExecutionContext(KlabService.Type.ENGINE) {
          @Override
          public String uptime() {
            return Utils.Time.formatDuration(getBootTime(), System.currentTimeMillis());
          }
        });

    if (this.defaultUser == null) {
      this.defaultUser = authenticate();
    }

    // This instruments the user scope with the federation queues. Only meant for federation-wide
    // messaging. TODO check which queues are used and whether they should be persistent or not.
    // TODO probably the federation queue should be independent and temporary. Also check that
    // the session scope do not get instrumented at client side.
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

    this.softwareStack = Stack.of("klab", settings);

    if (settings.get(Setting.USE_DEVELOPMENT_DISTRIBUTION_IF_AVAILABLE, Boolean.class)) {
      softwareStack.tags().stream()
          .filter(tag -> tag.version() == Version.HEAD)
          .findFirst()
          .ifPresent(head -> this.distributionTag = head);
    }
    if (this.distributionTag.version() != Version.HEAD
        && getUser().getUser().getGroups().stream()
            .anyMatch(group -> group.getName().equals("DEVELOPERS"))) {
      this.distributionTag = Stack.Tag.LATEST_DEVELOP;
    }

    ensureRuntimeAuxiliariesForLocalRuntime(null);

    // TODO explore how to best save and restore the chosen tag - we have just established a default

    // TODO now check what is available and default to any admissible defaults if present

    // TODO see if we need the local federation and if so, ensure the local broker is running
  }

  private void notifyLocalEngine(Engine.Status status) {

    ensureRuntimeAuxiliariesForLocalRuntime(status);

    if (engineStatusMonitor != null) {
      engineStatusMonitor.accept(status);
    }
    if (!onlineStatusNotified
        && status.getCondition() != Status.EngineCondition.TRANSITIONING
        && status.isOperational()) {
      // advertise the user scope to all online services
      // TODO strategy is static - if we have services coming in at runtime we will need to notify
      //  them too.
      notifyScopeToServices(defaultUser);
      onlineStatusNotified = true;
    }
  }

  private void ensureRuntimeAuxiliariesForLocalRuntime(Engine.Status status) {
    if (serviceMonitor == null || softwareStack == null || distributionTag == null) {
      return;
    }
    if (status != null) {
      var runtimeProvision = status.getServicesProvision().get(KlabService.Type.RUNTIME);
      if (runtimeProvision == null || !runtimeProvision.isLocal()) {
        return;
      }
    }
    if (runtimeAuxiliaryCheckRunning.compareAndSet(false, true)) {
      Thread.ofVirtual()
          .name("klab-runtime-auxiliary-ensure")
          .start(
              () -> {
                try {
                  serviceMonitor.ensureRuntimeAuxiliaryServices(
                      softwareStack, distributionTag, defaultUser);
                } catch (Throwable throwable) {
                  Logging.INSTANCE.error(throwable);
                } finally {
                  runtimeAuxiliaryCheckRunning.set(false);
                }
              });
    }
  }

  private void notifyScopeToServices(UserScope userScope) {

    var request = createScopeNotification(userScope);

    for (var service : getUser().getServices(KlabService.class)) {
      notifyScopeToService(service, request);
    }
  }

  private UserScopeNotification createScopeNotification(UserScope userScope) {
    var request = new UserScopeNotification();

    // TODO mixed aux info
    request.setEmailAddress(userScope.getUser().getEmailAddress());

    var services =
        serviceMonitor == null
            ? userScope.getServices(KlabService.class)
            : serviceMonitor.getAllServices(KlabService.class);

    for (var service : services) {
      var serviceInfo = new UserScopeNotification.ServiceInfo();
      var status = service.status();
      serviceInfo.setId(
          status == null || status.getServiceId() == null
              ? service.serviceId()
              : status.getServiceId());
      serviceInfo.setUrl(service.getUrl());
      serviceInfo.setType(
          status == null || status.getServiceType() == null
              ? KlabService.Type.classify(service)
              : status.getServiceType());
      if (status instanceof ServiceStatusImpl serviceStatus) {
        serviceInfo.setStatus(serviceStatus);
      }
      if (serviceInfo.getId() == null
          || serviceInfo.getUrl() == null
          || serviceInfo.getType() == null) {
        Logging.INSTANCE.warn(
            "Skipping incomplete service advertisement for "
                + service.getClass().getSimpleName()
                + ": id="
                + serviceInfo.getId()
                + ", url="
                + serviceInfo.getUrl()
                + ", type="
                + serviceInfo.getType());
        continue;
      }
      request.getServices().add(serviceInfo);
    }

    return request;
  }

  private void notifyScopeToService(KlabService service, UserScopeNotification request) {
    if (service instanceof BaseServiceClient serviceClient) {
      Thread.ofVirtual()
          .start(
              () -> {
                if (serviceClient.notifyScope(request)) {
                  var serviceId = serviceClient.serviceId();
                  if (serviceId != null) {
                    advertisedScopeTargets.add(serviceId);
                  }
                }
              });
    }
  }

  private void notifyLocalService(
      KlabService klabService, KlabService.ServiceStatus serviceStatus) {
    if (serviceStatusMonitor != null) {
      serviceStatusMonitor.accept(klabService, serviceStatus);
    }
    if (defaultUser != null
        && onlineStatusNotified
        && serviceStatus != null
        && serviceStatus.isOperational()
        && serviceStatus.getServiceId() != null
        && advertisedScopeTargets.add(serviceStatus.getServiceId())) {
      notifyScopeToService(klabService, createScopeNotification(defaultUser));
    }
  }

  @Override
  public UserScope authenticate() {
    return authenticate(null);
  }

  @Override
  public UserScope authenticate(KlabCertificate certificate) {
    if (this.defaultUser != null) {
      return this.defaultUser;
    }

    this.authData =
        certificate == null
            ? Authentication.INSTANCE.authenticate(settings)
            : Authentication.INSTANCE.authenticate(certificate, settings);
    this.authenticationResponse = Authentication.INSTANCE.getLastEngineAuthenticationResponse();
    if (this.authenticationResponse == null
        && authData.getFirst() instanceof UserIdentity user
        && user.isAuthenticated()
        && !user.isAnonymous()) {
      Logging.INSTANCE.warn(
          "Authenticated engine user "
              + user.getUsername()
              + " has no hub authentication response available for local service startup handoff");
    }

    /*
     * If user is federated, we don't start the local broker. Otherwise, we set up a local
     * federated identity and tell the runtime service to create an embedded broker on the default
     * URL and port.
     *
     * FIXME check if this still applies (federation is a group)
     */
    this.federationData =
        authData.getFirst().getData().get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);

    if (federationData == null || federationData.getBroker() == null) {
      var id = federationData == null ? null : federationData.getId();
      if (id == null) {
        id = Federation.LOCAL_FEDERATION_ID;
      }
      federationData = new Federation(id, Channel.LOCAL_BROKER_URL + Channel.LOCAL_BROKER_PORT);
    }

    /* federation must be already established at this point */
    this.defaultUser = new ClientUserScope((UserIdentity) authData.getFirst(), this);

    this.users.add(this.defaultUser);
    this.serviceMonitor =
        new ServiceMonitor(
            this.defaultUser,
            settings,
            true,
            authData.getSecond(),
            this::notifyLocalService,
            this::notifyLocalEngine);
    this.serviceMonitor.setLocalAuthenticationResponse(this.authenticationResponse);

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
  public Settings getSettings() {
    return settings;
  }

  public boolean resetWorldview() {
    var ret = this.worldview != null;
    this.worldview = null;
    return ret;
  }

  public Worldview getWorldview() {

    if (worldview == null) {

      var resources =
          Utils.Resources.queryResources(
              this.defaultUser,
              ResourcesService.class,
              // FIXME use the worldview ID from the certificate
              service ->
                  service.resolve("imod", KlabAsset.KnowledgeClass.WORLDVIEW, this.defaultUser));

      if (resources.isEmpty() || Utils.Notifications.hasErrors(resources.getNotifications())) {
        return Worldview.empty(resources.getNotifications().toArray(Notification[]::new));
      }

      this.worldview = Utils.Resources.collectWorldview(resources, this.defaultUser);
      this.lastWorldviewUpdate = System.currentTimeMillis();

    } else if (Duration.ofMillis(System.currentTimeMillis() - lastWorldviewUpdate).toMinutes()
        > WORLDVIEW_UPDATE_INTERVAL_MINUTES) {
      this.worldview.update(this.defaultUser);
      this.lastWorldviewUpdate = System.currentTimeMillis();
    }

    return this.worldview;
  }
}
