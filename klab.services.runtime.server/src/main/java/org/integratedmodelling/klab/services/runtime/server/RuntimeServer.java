package org.integratedmodelling.klab.services.runtime.server;


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
                               "org.integratedmodelling.runtime.server.controllers"})
public class RuntimeServer extends ServiceNetworkedInstance<RuntimeService> {

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return List.of(KlabService.Type.RESOLVER, KlabService.Type.REASONER, KlabService.Type.RESOURCES);
    }

    @Override
    protected RuntimeService createPrimaryService(ServiceScope serviceScope,
                                                   ServiceStartupOptions options) {
        return new RuntimeService(serviceScope, options);
    }

    public static void main(String[] args) {
        ServiceNetworkedInstance.start(RuntimeServer.class,
                ServiceStartupOptions.create(KlabService.Type.RUNTIME, args));
    }
}
