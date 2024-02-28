package org.integratedmodelling.klab.services;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.resolver.ResolverClient;
import org.integratedmodelling.klab.services.resources.ResourcesClient;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * This class is a wrapper for a {@link KlabService} that provides it with a ServiceScope and creates it for
 * embedded use. The default ServiceScope enables the service for local mode only, becoming available only if
 * all of the other needed services, if any, are also available locally. If embedded, non-REST versions of
 * these services are desired, they can be created or provided from a custom scope by overriding
 * {@link #createDefaultService(KlabService.Type, long)}, which in its default implementation will create
 * clients for either configured or embedded services whose URLs can be discovered.
 * <p>
 * Once a {@link ServiceInstance} has been booted, the {@link KlabService} can be used through its API. The
 * {@link ServiceInstance} does not provide network controllers, which can be provided by wrapping the Service
 * within a properly configured ServiceApplication.
 *
 * @author ferdinando.villa
 */
public abstract class ServiceInstance<T extends BaseService> {

    private StartupOptions startupOptions;
    private ConfigurableApplicationContext context;
    private T service;

    /**
     * Holders of "other" services for the ServiceScope
     */
    Map<KlabService.Type, KlabService> currentServices = new HashMap<>();

    Set<Resolver> availableResolvers = new HashSet<>();
    Set<RuntimeService> availableRuntimeServices = new HashSet<>();
    Set<ResourcesService> availableResourcesServices = new HashSet<>();
    Set<Reasoner> availableReasoners = new HashSet<>();

    private long bootTime;

    public ServiceInstance(T service) {
        this.service = service;
    }

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
    protected abstract T createPrimaryService(ServiceScope serviceScope);

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
        URL localServiceUrl = serviceType.localServiceUrl();
        if (Utils.Network.isAlive(localServiceUrl.toString())) {
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

    public ServiceInstance(T service, StartupOptions options) {
        this(service);
        this.startupOptions = options;
    }

    public void run(String[] args) {
        ServiceStartupOptions options = new ServiceStartupOptions();
        options.initialize(args);
    }


    public void stop() {
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

        context.close();
    }

    public T klabService() {
        return service;
    }

    public long getBootTime() {
        return bootTime;
    }


}
