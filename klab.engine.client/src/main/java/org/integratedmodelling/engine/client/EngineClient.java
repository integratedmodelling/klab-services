package org.integratedmodelling.engine.client;

import org.integratedmodelling.engine.client.distribution.Distribution;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.exceptions.KlabConfigurationException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.engine.AbstractAuthenticatedEngine;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class EngineClient extends AbstractAuthenticatedEngine implements PropertyHolder {

    boolean booted = false;
    boolean available = false;
    boolean online = false;

    Reasoner defaultReasoner;
    Resolver defaultResolver;
    ResourcesService defaultResourcesService;
    RuntimeService defaultRuntime;

    Set<Resolver> availableResolvers = new HashSet<>();
    Set<RuntimeService> availableRuntimeServices = new HashSet<>();
    Set<ResourcesService> availableResourcesServices = new HashSet<>();
    Set<Reasoner> availableReasoners = new HashSet<>();

    Map<KlabService.Type, URL> configuredServiceURLs = new HashMap<>();

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

    public void boot() {

        if (!booted) {

            // TODO use a delegate with forwarding of sent messages through the listeners
            this.defaultUser = authenticate();

            // TODO send auth message to user scope

            booted = true;

            for (var key : configuredServiceURLs.keySet()) {
                var url = configuredServiceURLs.get(key);
                if (url == null) {
                    return;
                }

            }


            if (defaultReasoner == null || defaultResourcesService == null || defaultResolver == null || defaultRuntime == null) {
                throw new KlabIllegalStateException("one or more services are not available: cannot boot " +
                        "the " +
                        "engine");
            }

            /*
             * Create the service scope for all embedded services. The order of initialization is
             * resources, reasoner, resolver and runtime. The community service should always be
             * remote except in test situations. The worldview must be loaded in the reasoner before
             * the resource workspaces are read.
             *
             * Order matters and logic is intricate, careful when making changes.
             */
            //            Worldview worldview = null;
            //            for (var service : new KlabService[]{defaultResourcesService, defaultReasoner,
            //            defaultResolver,
            //                                                 defaultRuntime}) {
            //                if (service instanceof BaseService baseService) {
            //                    baseService.initializeService();
            //                    if (service instanceof ResourcesService admin) {
            //                        worldview = admin.getWorldview();
            //                    } else if (service instanceof Reasoner.Admin admin && !worldview.isEmpty
            //                    ()) {
            //                        admin.loadKnowledge(worldview, baseService.scope());
            //                    }
            //                }
            //            }

            //            if (defaultResourcesService instanceof BaseService && defaultResourcesService
            //            instanceof ResourcesService.Admin) {
            //                ((ResourcesService.Admin) defaultResourcesService).loadWorkspaces();
            //            }
        }
    }

    @Override
    protected UserScope authenticate() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return this.available;
    }

    @Override
    public boolean isOnline() {
        return this.online;
    }

    @Override
    public String configurationPath() {
        return "engine/client";
    }
}
