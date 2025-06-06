package org.integratedmodelling.klab.services;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.ServiceIdentityImpl;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.base.BaseService;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is a wrapper for a {@link KlabService} whose main purpose is to provide it with a
 * {@link ServiceScope} to run under. The default service scope is produced using a k.LAB user
 * certificate, so it's a promoted user scope that can only run a local service (along with other
 * services that may come from the network). If the user certificate isn't available, the service
 * will operate in anonymous mode and only clients for local services can fulfill its service
 * dependencies.
 *
 * <p>Service initialization only happens after all needed services are available. The instance
 * automatically waits for them to come online if they're configured in any way. Implementations may
 * call {@link #waitOnline(int)} to block sensibly until the service is fully initialized and ready
 * to use.
 *
 * <p>If embedded, non-REST versions of the services are desired, they can be created or provided
 * from a custom scope; in its default implementation will create clients for either configured or
 * embedded services whose URLs can be discovered. If services are missing, the wrapped service will
 * not be available. The lookup of a service distribution to start a needed service is turned off in
 * service instances, as that should be only done by clients in a local configuration.
 *
 * <p>Once a {@link ServiceInstance} has successfully booted, the wrapped {@link KlabService} can be
 * used through its API and is available through {@link #klabService()}. The {@link ServiceInstance}
 * does not provide network controllers, which can be provided through the outer wrapper {@link
 * ServiceNetworkedInstance} after defining the controllers using Spring.
 *
 * <p>TODO move all startup/shutdown notifications to the wrapper
 *
 * @author ferdinando.villa
 */
public abstract class ServiceInstance<T extends BaseService> {

  AtomicBoolean initialized = new AtomicBoolean(false);
  AtomicBoolean operationalized = new AtomicBoolean(false);

  private ServiceStartupOptions startupOptions;
  private T service;
  private AbstractServiceDelegatingScope serviceScope;

  /** Holders of "other" services for the ServiceScope */
  Map<KlabService.Type, KlabService> currentServices = new HashMap<>();

  ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  Set<Resolver> availableResolvers = new HashSet<>();
  Set<RuntimeService> availableRuntimeServices = new HashSet<>();
  Set<ResourcesService> availableResourcesServices = new HashSet<>();
  Set<Reasoner> availableReasoners = new HashSet<>();

  private long bootTime;
  private Pair<Identity, List<ServiceReference>> identity;
  //    private boolean firstCall = true;
  private Parameters<Engine.Setting> settings = Parameters.createSynchronized();

  protected ServiceInstance() {
    settings.put(Engine.Setting.POLLING, "on");
    settings.put(Engine.Setting.POLLING_INTERVAL, 15);
    settings.put(Engine.Setting.LOG_EVENTS, true);
    settings.put(Engine.Setting.LAUNCH_PRODUCT, false);
  }

  /**
   * Return the type of any <em>other</em> services required for this service to become online. The
   * service chain must not have circular dependencies in these requirements. When at least one of
   * each required service is available, the {@link BaseService#initializeService()} function will
   * be called on the service.
   *
   * @return
   */
  protected abstract List<KlabService.Type> getEssentialServices();

  /**
   * The services returned here, which must not overlap those returned by {@link
   * #getEssentialServices()}, are needed for full operation but do not prevent initialization. When
   * all the remaining services listed here are available,
   *
   * @return
   */
  protected abstract List<KlabService.Type> getOperationalServices();

  public Identity getServiceOwner() {
    return identity == null ? null : identity.getFirst();
  }

  /**
   * This method must create the primary service, using the passed ServiceScope.
   *
   * @return
   */
  protected abstract T createPrimaryService(
      AbstractServiceDelegatingScope serviceScope, ServiceStartupOptions options);

  /**
   * Called only if the service(s) specified in the certificate are unavailable or missing. This
   * will be called for all service types as long as the service is not available, with a
   * configurable interval. The default implementation launches a thread waiting for a service to
   * become available locally and keeps track of the online status of the overall service resulting
   * from the availability.
   *
   * <p>For essential services, this will be called every X minutes for as long as at least one
   * instance of the service is missing. Non-essential services will only get one call with
   * timeUnavailable == 0.
   *
   * @param serviceType
   * @param timeUnavailable time since noticing the unavailability for the first time, in seconds.
   *     The first call will always get 0 here.
   * @return
   */
  protected KlabService createDefaultService(
      KlabService.Type serviceType, Scope scope, long timeUnavailable) {
    return createLocalServiceClient(
        serviceType, serviceType.localServiceUrl(), scope, serviceScope.getIdentity(), settings);
  }

  private <T extends KlabService> T createLocalServiceClient(
      KlabService.Type serviceType,
      URL url,
      Scope scope,
      Identity identity,
      Parameters<Engine.Setting> settings) {
    return switch (serviceType) {
      case REASONER -> (T) new ReasonerClient(url, identity, settings);
      case RESOURCES -> (T) new ResourcesClient(url, identity, settings);
      case RESOLVER -> (T) new ResolverClient(url, identity, settings);
      case RUNTIME -> (T) new RuntimeClient(url, identity, settings);
      default -> throw new IllegalStateException("Unexpected value: " + serviceType);
    };
  }

  /**
   * Wait for available (online) status until the passed timeout. If the service hasn't been
   * started, this will time out without effect.
   *
   * <p>Ensure that atomic operations set the available flag in the scope, then wrap any service
   * call that depends on the internal environment within a <code>if (waitOnline(x) { ... }</code>
   * block to ensure proper handling of atomic operations. Send a redirect to the "temporarily
   * unavailable" response outside the block to catch the timeout.
   *
   * @param timeoutSeconds
   * @return
   */
  public boolean waitOnline(int timeoutSeconds) {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + (timeoutSeconds * 1000)) {
      if (serviceScope.isAvailable()) {
        return true;
      }
      try {
        Thread.sleep(150);
      } catch (InterruptedException e) {
        break;
      }
    }
    return false;
  }

  protected ServiceStartupOptions getStartupOptions() {
    return startupOptions;
  }

  /**
   * Authentication for a simple ServiceInstance is through user/engine certificate, with the option
   * of anonymous.
   *
   * @return
   */
  protected Pair<Identity, List<ServiceReference>> authenticateService() {
    return Authentication.INSTANCE.authenticate(settings);
  }

  /**
   * Create the service scope that implements the authentication, messaging and service access
   * strategy.
   *
   * @return
   */
  protected AbstractServiceDelegatingScope createServiceScope() {

    this.identity = authenticateService();

    for (ServiceReference s : this.identity.getSecond()) {
      switch (s.getIdentityType()) {
        case KlabService.Type.REASONER -> {
          ReasonerClient reasoner = new ReasonerClient(s.getUrls().getFirst(), new ServiceIdentityImpl(s.getId(), s.get, null, s.getUrls()), null);
          availableReasoners.add(reasoner);
        }
        case KlabService.Type.RUNTIME  -> {
          RuntimeClient runtime = new RuntimeClient(s.getUrls().getFirst(), new ServiceIdentityImpl(s.getId(), s.getId(), null, s.getUrls()), null);
          availableRuntimeServices.add(runtime);
        }
        case KlabService.Type.RESOURCES -> {
          ResourcesClient resources = new ResourcesClient(s.getUrls().getFirst(), new ServiceIdentityImpl(s.getId(), s.getId(), null, s.getUrls()), null);
          availableResourcesServices.add(resources);
        }
        case KlabService.Type.RESOLVER -> {
          ResolverClient resolver = new ResolverClient(s.getUrls().getFirst(), new ServiceIdentityImpl(s.getId(), s.getId(), null, s.getUrls()), null);
          availableResolvers.add(resolver);
        }
        default -> {
        }
      }
    }

    return new AbstractServiceDelegatingScope(
        new ChannelImpl(identity.getFirst()) {
          @Override
          public String getDispatchId() {
            return service.serviceId();
          }
        }) {

      @Override
      public UserScope createUser(String username, String password) {
        throw new KlabIllegalStateException("Service scope does not support user creation");
      }

      @Override
      public Locality getLocality() {
        return Locality.EMBEDDED;
      }

      @Override
      public <T extends KlabService> T getService(Class<T> serviceClass) {
        return (T) currentServices.get(KlabService.Type.classify(serviceClass));
      }

      @Override
      public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
        return switch (KlabService.Type.classify(serviceClass)) {
          case REASONER -> (Collection<T>) availableReasoners;
          case RESOURCES -> (Collection<T>) availableResourcesServices;
          case RESOLVER -> (Collection<T>) availableResolvers;
          case RUNTIME -> (Collection<T>) availableRuntimeServices;
          case COMMUNITY -> {
            var cs = getService(serviceClass);
            yield cs == null ? Collections.emptyList() : (Collection<T>) List.of(cs);
          }
          case ENGINE -> Collections.emptyList();
          case LEGACY_NODE, NODE, DISCOVERY ->
              throw new KlabIllegalArgumentException(
                  "Cannot ask a scope for a legacy " + "service" + " ");
        };
      }
    };
  }

  public boolean start(ServiceStartupOptions options) {

    setEnvironment(options);
    this.service = createPrimaryService(this.serviceScope = createServiceScope(), options);

    /** Must do this now */
    switch (this.service) {
      case Reasoner reasoner -> {
        currentServices.put(KlabService.Type.REASONER, reasoner);
        availableReasoners.add(reasoner);
      }
      case RuntimeService runtime -> {
        currentServices.put(KlabService.Type.RUNTIME, runtime);
        availableRuntimeServices.add(runtime);
      }
      case ResourcesService resources -> {
        currentServices.put(KlabService.Type.RESOURCES, resources);
        availableResourcesServices.add(resources);
      }
      case Resolver resolver -> {
        currentServices.put(KlabService.Type.RESOLVER, resolver);
        availableResolvers.add(resolver);
      }
      case Community community -> {
        currentServices.put(KlabService.Type.COMMUNITY, community);
      }
      default -> {}
    }

    bootTime = System.currentTimeMillis();
    serviceScope.setStatus(Scope.Status.STARTED);
    serviceScope.setMaintenanceMode(true);
    scheduler.scheduleAtFixedRate(() -> timedTasks(), 0, 5, TimeUnit.SECONDS);
    return true;
  }

  private void setEnvironment(ServiceStartupOptions options) {
    this.startupOptions = options;
    // TODO sync the config environment with the options
    if (options.getDataDir() != null) {
      //            Configuration.INSTANCE.setDataPath();
    }
  }

  private void registerService(KlabService service, boolean isDefault) {

    if (isDefault) {
      this.currentServices.put(KlabService.Type.classify(service), service);
    }

    switch (service) {
      case Reasoner reasoner -> {
        availableReasoners.add(reasoner);
      }
      case ResourcesService resources -> {
        availableResourcesServices.add(resources);
      }
      case Resolver resolver -> {
        availableResolvers.add(resolver);
      }
      case RuntimeService runtime -> {
        availableRuntimeServices.add(runtime);
      }
      default -> {}
    }
  }

  private void timedTasks() {

    try {

      /*
      check all needed services; put self offline if not available or not there, online otherwise; if
      there's a change in online status, report it through the service scope
       */

      var essentials = getEssentialServices();
      var operational = getOperationalServices();
      var allservices = EnumSet.noneOf(KlabService.Type.class);
      allservices.addAll(essentials);
      allservices.addAll(operational);

      boolean wasAvailable = serviceScope.isAvailable();

      /**
       * TODO if we start with remote services and we are local (starting with a user certificate), we MUST
       *  switch the default service to a local service as soon as one comes online. So here we must detect
       *  local services for all the needed ones and keep checking their status. Keep a map of local clients
       *  and fill it if we are local.
       */

      // create all clients that we may need and know how to create
      for (var serviceType : allservices) {
        var service = currentServices.get(serviceType);
        if (service == null) {
          service =
              this.createDefaultService(
                  serviceType, serviceScope, (System.currentTimeMillis() - bootTime) / 1000);
          if (service != null) {
            registerService(service, true);
          }
        }
      }

      // now check if they're OK
      boolean okEssentials = true;
      boolean okOperationals = true;

      for (var serviceType : allservices) {
        var service = currentServices.get(serviceType);
        if (essentials.contains(serviceType)) {
          if (service == null || !service.status().isAvailable()) {
            okEssentials = false;
          }
        }
        if (operational.contains(serviceType)) {
          if (service == null || !service.status().isAvailable()) {
            okOperationals = false;
          }
        }
      }

      if (okEssentials) {
        setAvailable(true);
        serviceScope.setStatus(Scope.Status.STARTED);
      } else {
        setAvailable(false);
        serviceScope.setStatus(Scope.Status.WAITING);
      }

      //            firstCall = false;

//      if (wasAvailable != okEssentials) {
//        if (okEssentials) {
//          if (initialized.get()) {
//            serviceScope.send(
//                Message.MessageClass.ServiceLifecycle,
//                Message.MessageType.ServiceAvailable,
//                klabService().capabilities(serviceScope));
//          } else {
//            serviceScope.send(
//                Message.MessageClass.ServiceLifecycle,
//                Message.MessageType.ServiceInitializing,
//                klabService().capabilities(serviceScope));
//          }
//        } else {
//          serviceScope.send(
//              Message.MessageClass.ServiceLifecycle,
//              Message.MessageType.ServiceUnavailable,
//              klabService().capabilities(serviceScope));
//        }
//      }

      /*
      if status is OK and the service hasn't been initialized, set maintenance mode and call
      initializeService().
       */
      if (okEssentials && !initialized.get()) {
        setBusy(true);
        klabService().initializeService();
        klabService().setInitialized(true);
        initialized.set(true);
//        serviceScope.send(
//            Message.MessageClass.ServiceLifecycle,
//            Message.MessageType.ServiceAvailable,
//            klabService().capabilities(serviceScope));
        setBusy(false);
      }

      if (okEssentials && okOperationals && !operationalized.get()) {

        setBusy(true);
        operationalized.set(true);
        klabService().setOperational(klabService().operationalizeService());

        // register remote components and adapters with our component registry
        for (var service : klabService().serviceScope().getServices(ResourcesService.class)) {
          klabService().getComponentRegistry().registerService(service.capabilities(serviceScope));
        }
        for (var service : klabService().serviceScope().getServices(Reasoner.class)) {
          klabService().getComponentRegistry().registerService(service.capabilities(serviceScope));
        }
        for (var service : klabService().serviceScope().getServices(Resolver.class)) {
          klabService().getComponentRegistry().registerService(service.capabilities(serviceScope));
        }
        for (var service : klabService().serviceScope().getServices(RuntimeService.class)) {
          klabService().getComponentRegistry().registerService(service.capabilities(serviceScope));
        }
        setBusy(false);
      }
    } catch (Throwable t) {
      if (this.klabService().status().getServiceType() == KlabService.Type.RESOURCES) {
        Logging.INSTANCE.error(
            "Exception during scheduled tasks: " + Utils.Exceptions.stackTrace(t));
      }
    }
  }

  public void stop() {
    klabService().shutdown();
  }

  public T klabService() {
    return service;
  }

  public long getBootTime() {
    return bootTime;
  }

  protected void setAvailable(boolean b) {
    serviceScope.setMaintenanceMode(!b);
  }

  public Parameters<Engine.Setting> settings() {
    return settings;
  }

  protected void setBusy(boolean b) {
    serviceScope.setAtomicOperationMode(b);
  }
}
