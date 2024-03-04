package org.integratedmodelling.klab.services;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.resolver.ResolverClient;
import org.integratedmodelling.klab.services.resources.ResourcesClient;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;

/**
 * This class is a wrapper for a {@link KlabService} whose main purpose is to provide it with a
 * {@link ServiceScope} to run under. The default service scope is produced using a k.LAB user certificate, so
 * it's a promoted user scope that can only run a local service (along with other services that may come from
 * the network). If the user certificate isn't available, the service will operate in anonymous mode and only
 * clients for local services can fulfill its service dependencies.
 * <p>
 * Service initialization only happens after all needed services are available. The instance automatically
 * waits for them to come online if they're configured in any way. Implementations may call
 * {@link #waitOnline(int)} to block sensibly until the service is fully initialized and ready to use.
 * <p>
 * If embedded, non-REST versions of the services are desired, they can be created or provided from a custom
 * scope by overriding {@link #createDefaultService(KlabService.Type, long)}, which in its default
 * implementation will create clients for either configured or embedded services whose URLs can be discovered.
 * If services are missing, the wrapped service will not be available.
 * <p>
 * Once a {@link ServiceInstance} has successfully booted, the wrapped {@link KlabService} can be used through
 * its API and is available through {@link #klabService()}. The {@link ServiceInstance} does not provide
 * network controllers, which can be provided through the outer wrapper
 * {@link org.integratedmodelling.klab.services.application.ServiceNetworkedInstance} after defining the
 * controllers using Spring.
 * <p>
 *  TODO move all startup/shutdown notifications to the wrapper
 *
 * @author ferdinando.villa
 */
public abstract class ServiceInstance<T extends BaseService> {

    AtomicBoolean initialized = new AtomicBoolean(false);

    protected ServiceStartupOptions startupOptions;
    private T service;

    private AbstractServiceDelegatingScope serviceScope;
    /**
     * Holders of "other" services for the ServiceScope
     */
    Map<KlabService.Type, KlabService> currentServices = new HashMap<>();

    ExecutorService servicesThreadPool = Executors.newFixedThreadPool(4);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    Set<Resolver> availableResolvers = new HashSet<>();
    Set<RuntimeService> availableRuntimeServices = new HashSet<>();
    Set<ResourcesService> availableResourcesServices = new HashSet<>();
    Set<Reasoner> availableReasoners = new HashSet<>();

    private long bootTime;

    /**
     * Return the type of any <em>other</em> services required for this service to be online. For each of
     * these the {@link #createDefaultService(KlabService.Type, long)} function will be called and online
     * status won't be set until all of these are available.
     *
     * @return
     */
    protected abstract List<KlabService.Type> getEssentialServices();

    /**
     * This method must create the primary service, using the passed ServiceScope.
     *
     * @return
     */
    protected abstract T createPrimaryService(ServiceScope serviceScope, ServiceStartupOptions options);

    /**
     * Called only if the service(s) specified in the certificate are unavailable or missing. This will be
     * called for all service types as long as the service is not available, with a configurable interval. The
     * default implementation launches a thread waiting for a service to become available locally and keeps
     * track of the online status of the overall service resulting from the availability.
     * <p>
     * For essential services, this will be called every X minutes for as long as at least one instance of the
     * service is missing. Non-essential services will only get one call with timeUnavailable == 0.
     *
     * @param serviceType
     * @param timeUnavailable time since noticing the unavailability for the first time, in seconds. The first
     *                        call will always get 0 here.
     * @return
     */
    protected KlabService createDefaultService(KlabService.Type serviceType, long timeUnavailable) {
        var localServiceUrl = serviceType.localServiceUrl();
        if (Utils.Network.isAlive(localServiceUrl)) {
            return switch (serviceType) {
                case REASONER -> new ReasonerClient(localServiceUrl);
                case RESOURCES -> new ResourcesClient(localServiceUrl);
                case RESOLVER -> new ResolverClient(localServiceUrl);
                case RUNTIME -> new RuntimeClient(localServiceUrl);
                //                    case COMMUNITY -> new CommunityClient(localServiceUrl);
                default -> throw new KlabInternalErrorException("Unexpected request from " +
                        "ServiceInstance::createDefaultService: " + serviceType);
            };
        }
        return null;
    }

    /**
     * Wait for available (online) status until the passed timeout. If {@link #start()} hasn't been called,
     * this will time out without effect.
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

    /**
     * Create the service scope that implements the authentication, messaging and service access strategy.
     *
     * @return
     */
    protected AbstractServiceDelegatingScope createServiceScope() {
        var identity = Authentication.INSTANCE.authenticate();
        return new AbstractServiceDelegatingScope(new ChannelImpl(identity.getFirst())) {
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
                    case LEGACY_NODE ->
                            throw new KlabIllegalArgumentException("Cannot ask a scope for a legacy service" +
                                    " ");
                };
            }

        };
    }

    public boolean start(ServiceStartupOptions options) {

        this.startupOptions = options;
        this.service = createPrimaryService(this.serviceScope = createServiceScope(), options);

        bootTime = System.currentTimeMillis();
        serviceScope.setStatus(Scope.Status.STARTED);
        serviceScope.setMaintenanceMode(true);

        var essentialServices = getEssentialServices();
        if (essentialServices.size() > 0) {
            for (var serviceType : getEssentialServices()) {
                var service = this.createDefaultService(serviceType, 0);
                if (service != null) {
                    registerService(service, true);
                }
            }
        }
        scheduler.scheduleAtFixedRate(() -> timedTasks(), 0, 15, TimeUnit.SECONDS);

        return true;
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
            case Community community -> {
            }
            default -> {
            }
        }
    }

    private void timedTasks() {

        /*
        check all needed services; put self offline if not available or not there, online otherwise; if
        there's a change in online status, report it through the service scope
         */
        boolean ok = true;

        for (var serviceType : getEssentialServices()) {
            var service = currentServices.get(serviceType);
            if (service == null) {
                service = this.createDefaultService(serviceType,
                        (System.currentTimeMillis() - bootTime) / 1000);
                ok = service != null && service.isOnline();
                if (service != null) {
                    registerService(service, true);
                }
            } else if (!service.isOnline()) {
                ok = false;
            }
        }

        /*
        check and reassign server status; if any changes, report status
         */
        if (serviceScope.isAvailable() && !ok) {
            serviceScope.setMaintenanceMode(true);
        } else if (!serviceScope.isAvailable() && ok) {
            serviceScope.setMaintenanceMode(false);
        }


        /*
        if status is OK and the service hasn't been initialized, set maintenance mode and call
        initializeService().
         */
        if (ok && !initialized.get()) {
            klabService().initializeService();
            initialized.set(true);
        }

        /*
        if subscribed and configured interval has passed, send service health status through the scope
         */
    }


    public void stop() {

        /*
        if WE have started the embedded other services, stop them, otherwise let them run. We should
        log out if the service is a client.
         */

        /*
        call shutdown() on the wrapped service
         */
        klabService().shutdown();

        /*
        send notifications
         */


        // // shutdown all components
        // if (this.sessionClosingTask != null) {
        // this.sessionClosingTask.cancel(true);
        // }
        //
        // // shutdown the task executor
        // if (taskExecutor != null) {
        // taskExecutor.shutdown();
        // try {
        // if (!taskExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        // taskExecutor.shutdownNow();
        // }
        // } catch (InterruptedException e) {
        // taskExecutor.shutdownNow();
        // }
        // }
        //
        // // shutdown the script executor
        // if (scriptExecutor != null) {
        // scriptExecutor.shutdown();
        // try {
        // if (!scriptExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        // scriptExecutor.shutdownNow();
        // }
        // } catch (InterruptedException e) {
        // scriptExecutor.shutdownNow();
        // }
        // }
        //
        // // and the session scheduler
        // if (scheduler != null) {
        // scheduler.shutdown();
        // try {
        // if (!scheduler.awaitTermination(800, TimeUnit.MILLISECONDS)) {
        // scheduler.shutdownNow();
        // }
        // } catch (InterruptedException e) {
        // scheduler.shutdownNow();
        // }
        // }
        //
        // // shutdown the runtime
        // Klab.INSTANCE.getRuntimeProvider().shutdown();

        //        context.close();
    }

    public T klabService() {
        return service;
    }

    public long getBootTime() {
        return bootTime;
    }

    //    public static void run(String[] args) {
    //        ServiceStartupOptions options = new ServiceStartupOptions();
    //        options.initialize(args);
    //        start(options);
    //    }

}
