package org.integratedmodelling.engine.client;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.engine.client.scopes.ClientScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.exceptions.KlabConfigurationException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.engine.AbstractAuthenticatedEngine;
import org.integratedmodelling.klab.rest.ServiceReference;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.resolver.ResolverClient;
import org.integratedmodelling.klab.services.resources.ResourcesClient;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The engine client uses client services and can be configured to use a local server for all services
 * (possibly as a downloaded product) and use the local services if so desired or if online services are not
 * available. The distribution up to this point should remain light so that it can be embedded in a IDE or
 * other product.
 */
public class EngineClient extends AbstractAuthenticatedEngine implements PropertyHolder {

    AtomicBoolean online = new AtomicBoolean(false);
    AtomicBoolean available = new AtomicBoolean(false);
    AtomicBoolean booted = new AtomicBoolean(false);

    Map<KlabService.Type, KlabService> currentServices = new HashMap<>();

    Set<Resolver> availableResolvers = new HashSet<>();
    Set<RuntimeService> availableRuntimeServices = new HashSet<>();
    Set<ResourcesService> availableResourcesServices = new HashSet<>();
    Set<Reasoner> availableReasoners = new HashSet<>();

    Map<KlabService.Type, URL> configuredServiceURLs = new HashMap<>();

    Map<KlabService.Type, RunningInstance> launchedServices = new HashMap<>();

    Distribution engineDistribution = null;

    UserScope defaultUser;

    public EngineClient() {
        for (var type : EnumSet.of(KlabService.Type.REASONER, KlabService.Type.RESOURCES,
                KlabService.Type.RUNTIME, KlabService.Type.RESOLVER)) {
            var key = "service.url." + type.name().toLowerCase();
            try {
                configuredServiceURLs.put(type, new URL(getProperties().getProperty(key,
                        ("http://127.0.0.1:" + type.defaultPort))));
            } catch (MalformedURLException e) {
                throw new KlabConfigurationException(e);
            }
        }
    }

    /**
     * The boot process is a separate thread. Monitor {@link #isOnline()} or listen to engine lifecycle events
     * to check for when the engine is available.
     */
    public void boot() {
        if (!booted.get()) {
            Thread.ofVirtual().start(() -> doBoot());
        }
    }

    private void doBoot() {

        this.defaultUser = authenticate();

        // TODO send auth message to user scope

        booted.set(true);

        for (var key : configuredServiceURLs.keySet()) {
            var url = configuredServiceURLs.get(key);
            if (url == null) {
                return;
            }
        }

        /*
        Lookup any missing services we may have in the linked distribution, if any is there. If so, start
        all the
        services we don't have available.
         */
        for (var serviceType : EnumSet.of(KlabService.Type.RESOURCES, KlabService.Type.REASONER,
                KlabService.Type.RUNTIME, KlabService.Type.RESOLVER, KlabService.Type.COMMUNITY)) {
            if (currentServices.get(serviceType) == null) {
                switch (serviceType) {
                    case REASONER -> {
                        var s = new ReasonerClient(serviceType.localServiceUrl());
                        // TODO use another call to check if the URL existed, not necessarily available yet
                        if (s.scope().isAvailable()) {
                            this.availableReasoners.add(s);
                            currentServices.put(KlabService.Type.REASONER, s);
                        }
                    }
                    case RESOURCES -> {
                        var s = new ResourcesClient(serviceType.localServiceUrl());
                        if (s.scope().isAvailable()) {
                            this.availableResourcesServices.add(s);
                            currentServices.put(KlabService.Type.RESOURCES, s);
                        }
                    }
                    case RESOLVER -> {
                        var s = new ResolverClient(serviceType.localServiceUrl());
                        if (s.scope().isAvailable()) {
                            this.availableResolvers.add(s);
                            currentServices.put(KlabService.Type.RESOLVER, s);
                        }
                    }
                    case RUNTIME -> {
                        var s = new RuntimeClient(serviceType.localServiceUrl());
                        if (s.scope().isAvailable()) {
                            this.availableRuntimeServices.add(s);
                            currentServices.put(KlabService.Type.RUNTIME, s);
                        }
                    }
                    default -> throw new KlabInternalErrorException("Unexpected value: " + serviceType);
                }
            }
        }

        /*
         if we have no local clients, we had no running services but we may still have a product that we can launch
         */
        if (engineDistribution != null) {
            for (var serviceType : EnumSet.of(KlabService.Type.RESOURCES, KlabService.Type.REASONER,
                    KlabService.Type.RUNTIME, KlabService.Type.RESOLVER, KlabService.Type.COMMUNITY)) {

                if (currentServices.get(serviceType) == null) {
                    var product = engineDistribution.findProduct(Product.ProductType.forService(serviceType));
                    if (product != null && !product.getReleases().isEmpty()) {
                        launchedServices.put(serviceType, product.getReleases().get(0).launch(this.defaultUser));
                    }
                }
            }
        }

    }

    @Override
    protected UserScope authenticate() {
        var authData = Authentication.INSTANCE.authenticate();
        var ret = createUserScope(authData);
        // TODO send auth messages to scope
        return ret;
    }

    private UserScope createUserScope(Pair<Identity, List<ServiceReference>> authData) {

        /*
        find out which services are available from the network
         */
        for (var service : authData.getSecond()) {
            switch (service.getServiceType()) {
                case REASONER -> {
                    var s = new ReasonerClient(service.getUrls().get(0));
                    this.availableReasoners.add(s);
                    if (service.isPrimary() && currentServices.get(KlabService.Type.REASONER) == null) {
                        currentServices.put(KlabService.Type.REASONER, s);
                    }
                }
                case RESOURCES -> {
                    var s = new ResourcesClient(service.getUrls().get(0));
                    this.availableResourcesServices.add(s);
                    if (service.isPrimary() && currentServices.get(KlabService.Type.RESOURCES) == null) {
                        currentServices.put(KlabService.Type.RESOURCES, s);
                    }
                }
                case RESOLVER -> {
                    var s = new ResolverClient(service.getUrls().get(0));
                    this.availableResolvers.add(s);
                    if (service.isPrimary() && currentServices.get(KlabService.Type.RESOLVER) == null) {
                        currentServices.put(KlabService.Type.RESOLVER, s);
                    }
                }
                case RUNTIME -> {
                    var s = new RuntimeClient(service.getUrls().get(0));
                    this.availableRuntimeServices.add(s);
                    if (service.isPrimary() && currentServices.get(KlabService.Type.RUNTIME) == null) {
                        currentServices.put(KlabService.Type.RUNTIME, s);
                    }
                }
                case COMMUNITY -> {
                    // TODO we need this too, only in current, there should be one
                    //                    var s = new CommunityClient(service.getUrls().get(0));
                    //                    if (service.isPrimary() && currentServices.get(KlabService.Type
                    //                    .COMMUNITY) == null) {
                    //                        currentServices.put(KlabService.Type.COMMUNITY, s);
                    //                    }
                }
                default ->
                        throw new KlabIllegalStateException("Unexpected value: " + service.getServiceType());
            }
        }

        /*
        TODO
        Start thread to check availability of the default services and set the online status when all are
        available. If any service is still missing, status will be permanently offline.
         */

        var ret = new ClientScope(authData.getFirst()) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                return (T) currentServices.get(KlabService.Type.classify(serviceClass));
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                switch (KlabService.Type.classify(serviceClass)) {
                    case REASONER -> {
                        return (Collection<T>) availableReasoners;
                    }
                    case RESOURCES -> {
                        return (Collection<T>) availableResourcesServices;
                    }
                    case RESOLVER -> {
                        return (Collection<T>) availableResolvers;
                    }
                    case RUNTIME -> {
                        return (Collection<T>) availableRuntimeServices;
                    }
                    case COMMUNITY -> {
                        var ret = currentServices.get(KlabService.Type.COMMUNITY);
                        if (ret != null) {
                            return (Collection<T>) List.of(ret);
                        }
                    }
                    case ENGINE -> {
                        return List.of((T) EngineClient.this);
                    }
                }
                return Collections.emptyList();
            }
        };

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

    public static void main(String[] args) {
        var client = new EngineClient();
        client.boot();
    }

}
