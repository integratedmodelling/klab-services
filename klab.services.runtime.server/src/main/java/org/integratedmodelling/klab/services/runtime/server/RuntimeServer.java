package org.integratedmodelling.klab.services.runtime.server;


import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
// TODO remove the argument when all gson dependencies are the same (never)
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.application.security",
                               "org.integratedmodelling.klab.services.messaging",
                               "org.integratedmodelling.klab.services.application.controllers",
                               "org.integratedmodelling.klab.services.runtime.server.controllers"})
public class RuntimeServer extends ServiceNetworkedInstance<RuntimeService> {

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        /**
         * This runtime gets resolvers and resource services from the observation requests, so does not need
         * its own services besides reasoning.
         *
         * TODO the context request contains the service URLs actually. So all these should be optional only
         *  for the runtime to work standalone (but there are issues if these aren't there).
         */
        return List.of(KlabService.Type.RESOURCES, KlabService.Type.REASONER, KlabService.Type.RESOLVER);
    }

    @Override
    protected RuntimeService createPrimaryService(AbstractServiceDelegatingScope serviceScope,
                                                  ServiceStartupOptions options) {
        return new RuntimeService(serviceScope, options);
    }

    public static void main(String[] args) {
        ServiceNetworkedInstance.start(RuntimeServer.class,
                ServiceStartupOptions.create(KlabService.Type.RUNTIME, args));
    }

}
