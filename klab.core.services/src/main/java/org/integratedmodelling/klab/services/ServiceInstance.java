package org.integratedmodelling.klab.services;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.ServiceIdentityImpl;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.PartnerIdentity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.base.BaseService;

/**
 * This class is a wrapper for a {@link KlabService} whose main purpose is to provide it with a
 * {@link ServiceScope} to run under. If the service runs locally under a user scope, the default
 * service scope is produced using a k.LAB user certificate, so it acts like a stripped-down engine
 * and the service scope is a promoted user scope that can only run the service locally (along with
 * other services that may come from the network). If the user certificate isn't available, the
 * service will operate in anonymous mode and only clients for local services can fulfill its
 * service dependencies.
 *
 * <p>Service initialization only happens after all necessary services are available. The instance
 * automatically waits for them to come online if they're configured in any way. Implementations may
 * call {@link #waitOnline(int)} to block sensibly until the service is fully initialized and ready
 * to use.
 *
 * <p>If embedded, non-REST versions of the services are desired, they can be created or provided
 * from a custom scope; in its default implementation will create clients for either configured or
 * embedded services whose URLs can be discovered. If services are missing, the wrapped service will
 * not be available. The lookup of a service distribution to start a needed service is turned off in
 * service instances, as that should be only done by clients in a local configuration. The
 * configuration with embedded services is untested.
 *
 * <p>Once a {@link ServiceInstance} has successfully booted, the wrapped {@link KlabService} can be
 * used through its API and is available through {@link #klabService()}. The {@link ServiceInstance}
 * does not provide REST controllers, which can be provided through the outer Spring wrapper {@link
 * ServiceNetworkedInstance} after defining the controllers implementing the service's {@link
 * org.integratedmodelling.klab.api.ServicesAPI} endpoints.
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
  private ServiceScope serviceScope;
  ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  /* the network-accessible services from the certificate, only used in remote configurations. The local services
  use a ServiceMonitor instance like the one the engine uses. */
  private final Map<KlabService.Type, Set<KlabService>> onlineServices = new HashMap<>();
  private long bootTime;
  private Pair<Identity, List<ServiceReference>> identity;

  protected ServiceInstance() {}

  protected abstract KlabService.Type serviceType();

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
      ServiceScope serviceScope, ServiceStartupOptions options);

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
    if (serviceScope == null) {
      return false;
    }
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

  class InstanceServiceScope extends AbstractServiceDelegatingScope {

    public InstanceServiceScope(Channel messageBus) {
      super(messageBus);
    }

    @Override
    public Locality getLocality() {
      return Locality.EMBEDDED;
    }

    @Override
    public <T extends KlabService> T getService(Class<T> serviceClass) {
      var primaryService = klabService();
      if (primaryService != null && serviceClass.isAssignableFrom(primaryService.getClass())) {
        return serviceClass.cast(primaryService);
      }

      var available =
          onlineServices.getOrDefault(KlabService.Type.classify(serviceClass), Set.of()).stream()
              .filter(s -> s.status().isAvailable())
              .findFirst()
              .orElse(null);

      return serviceClass.cast(available);
    }

    @Override
    public <T extends KlabService> Optional<T> findService(
        Class<T> serviceClass, Predicate<T> selectors) {
      var service = getService(serviceClass);
      return service != null && (selectors == null || selectors.test(service))
          ? Optional.of(service)
          : Optional.empty();
    }

    @Override
    public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
      var service = getService(serviceClass);
      return service == null ? List.of() : List.of(service);
    }
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
    if (startupOptions != null) {
      var authenticationPackage = startupOptions.getAuthenticationPackage();
      if (authenticationPackage != null && !authenticationPackage.isBlank()) {
        Logging.INSTANCE.info(
            "Service "
                + serviceType()
                + " received local authentication package; reconstructing user identity without "
                + "hub authentication");
        var authentication =
            Authentication.INSTANCE.decodeAuthenticationResponse(authenticationPackage);
        if (authentication != null) {
          var ret = Authentication.INSTANCE.authenticate(authentication);
          if (ret.getFirst() instanceof UserIdentity user && !user.isAnonymous()) {
            Logging.INSTANCE.info(
                "Service "
                    + serviceType()
                    + " using local authentication package for user "
                    + user.getUsername()
                    + " with "
                    + ret.getSecond().size()
                    + " advertised services");
            return ret;
          }
          Logging.INSTANCE.warn(
              "Local authentication package for service "
                  + serviceType()
                  + " reconstructed an anonymous identity; falling back to certificate "
                  + "authentication");
        } else {
          Logging.INSTANCE.warn(
              "Local authentication package for service "
                  + serviceType()
                  + " could not be decoded; falling back to certificate authentication");
        }
      } else {
        Logging.INSTANCE.info(
            "Service "
                + serviceType()
                + " has no local authentication package; authenticating through certificate/hub");
      }
    }
    return Authentication.INSTANCE.authenticate(SettingsImpl.forService(serviceType()));
  }

  /**
   * Create the service scope that implements the authentication, messaging and service access
   * strategy.
   *
   * @return
   */
  protected ServiceScope createServiceScope() {

    this.identity = authenticateService();
    PartnerIdentity partnerIdentity = null;
    if (identity.getFirst() instanceof PartnerIdentity) {
      partnerIdentity = (PartnerIdentity) identity.getFirst();
    } else if (identity.getFirst() instanceof UserIdentity user) {
      if (user.isAnonymous()) {
        Logging.INSTANCE.info("Authentication failed: anonymous service instance started");
      }
    }

    // local services (user-level certificate) only see other local services
    boolean iAmLocal =
        !this.identity.getFirst().is(Identity.Type.SERVICE) && partnerIdentity == null;

    if (iAmLocal) {
      return setupLocalServices(
          this.identity.getSecond().stream()
              .filter(sr -> KlabService.Type.operationCritical().contains(sr.getIdentityType()))
              .toList(),
          startupOptions);
    }

    return setupRemoteServices(
        this.identity.getSecond().stream()
            .filter(sr -> KlabService.Type.operationCritical().contains(sr.getIdentityType()))
            .toList(),
        partnerIdentity,
        startupOptions);
  }

  private String serviceDispatchId() {
    return service == null ? serviceType().name() : service.serviceId();
  }

  private AbstractServiceDelegatingScope setupRemoteServices(
      List<ServiceReference> list, PartnerIdentity partnerIdentity, ServiceStartupOptions options) {

    var ret =
        new InstanceServiceScope(
            new ChannelImpl(identity.getFirst()) {
              @Override
              public String getDispatchId() {
                return serviceDispatchId();
              }
            });
    for (var s : list) {

      if (getEssentialServices().contains(s.getIdentityType())
          || getOperationalServices().contains(s.getIdentityType())) {

        var serviceIdentity =
            new ServiceIdentityImpl(
                s.getId(),
                s.getId(),
                new Date(),
                s.getUrls(),
                partnerIdentity.getToken(),
                Utils.URLs.newURL(partnerIdentity.getAuthenticatingHub()));

        var scope =
            new InstanceServiceScope(
                new ChannelImpl(serviceIdentity) {
                  @Override
                  public String getDispatchId() {
                    return serviceDispatchId();
                  }
                });

        var client =
            ServiceClientCatalog.INSTANCE.getService(
                s.getUrls().getFirst(),
                SettingsImpl.forService(s.getIdentityType()),
                scope,
                s.getIdentityType().classify());

        this.onlineServices
            .computeIfAbsent(s.getIdentityType(), k -> new LinkedHashSet<>())
            .add(client);
      }
    }

    this.service = createPrimaryService(ret, options);
    this.service.setIdentity(identity.getFirst());

    return ret;
  }

  private ServiceScope setupLocalServices(
      List<ServiceReference> serviceList, ServiceStartupOptions options) {

    var scope =
        new UserServiceScope((UserIdentity) identity.getFirst(), serviceType(), serviceList);
    service = createPrimaryService(scope, options);
    service.setIdentity(identity.getFirst());
    scope.setService(service);
    return scope;
  }

  public boolean start(ServiceStartupOptions options) {

    setEnvironment(options);
    this.serviceScope = createServiceScope();
    bootTime = System.currentTimeMillis();
    serviceScope.setStatus(Scope.Status.STARTED);
    serviceScope.setMaintenanceMode(true);
    scheduler.scheduleAtFixedRate(this::timedTasks, 0, 5, TimeUnit.SECONDS);
    return true;
  }

  private void setEnvironment(ServiceStartupOptions options) {
    this.startupOptions = options;
    // TODO sync the config environment with the options
  }

  private void timedTasks() {

    try {

      klabService().sampleLoad();
      var iAmLocal = identity instanceof UserIdentity;
      /*
      check all needed services; put self offline if not available or not there, online otherwise; if
      there's a change in online status, report it through the service scope
       */

      // now check if they're OK
      boolean okEssentials = true;
      boolean okOperationals = true;

      if (!getEssentialServices().isEmpty()) {
        for (var serviceType : getEssentialServices()) {
          if (iAmLocal) {
            var anyAvailable =
                klabService().serviceScope().getService(serviceType.classify()) != null;
            if (getEssentialServices().contains(serviceType) && !anyAvailable) {
              okEssentials = false;
            }
          } else {
            var available = onlineServices.computeIfAbsent(serviceType, t -> new LinkedHashSet<>());
            boolean anyAvailable =
                !available.isEmpty() && available.stream().anyMatch(s -> s.status().isAvailable());
            if (getEssentialServices().contains(serviceType) && !anyAvailable) {
              okEssentials = false;
            }
          }
        }
      }
      if (!getOperationalServices().isEmpty()) {
        for (var serviceType : getOperationalServices()) {
          if (iAmLocal) {
            var anyAvailable =
                    klabService().serviceScope().getService(serviceType.classify()) != null;
            if (getOperationalServices().contains(serviceType) && !anyAvailable) {
              okOperationals = false;
            }
          } else {
            var available = onlineServices.computeIfAbsent(serviceType, t -> new LinkedHashSet<>());
            boolean anyAvailable =
                !available.isEmpty() && available.stream().anyMatch(s -> s.status().isAvailable());
            if (getOperationalServices().contains(serviceType) && !anyAvailable) {
              okOperationals = false;
            }
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

      /*
      if status is OK and the service hasn't been initialized, set maintenance mode and call
      initializeService().
       */
      if (okEssentials && !initialized.get()) {
        setBusy(true);
        var success = klabService().initializeService();
        Logging.INSTANCE.info("Service " + serviceType() + " initialized: " + success);
        klabService().setInitialized(success);
        initialized.set(success);
        setBusy(false);
      }

      if (initialized.get() && okEssentials && okOperationals && !operationalized.get()) {
        setBusy(true);
        if (klabService().operationalizeService()) {
          operationalized.set(true);
          klabService().setOperational(true);
        }
        setBusy(false);
      }
    } catch (Throwable t) {
      setBusy(false);
      Logging.INSTANCE.error("Exception during scheduled tasks: " + Utils.Exceptions.stackTrace(t));
    }

    klabService().runAdditionalTimedTasks();
  }

  protected void setOperationalized(boolean operationalized) {
    this.operationalized.set(operationalized);
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

  protected void setBusy(boolean b) {
    serviceScope.setAtomicOperationMode(b);
  }
}
