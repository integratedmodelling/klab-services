package org.integratedmodelling.klab.services.reasoner;


import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
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
                               "org.integratedmodelling.klab.services.reasoner.controllers"})
public class ReasonerServer extends ServiceNetworkedInstance<ReasonerService> {
    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return List.of(KlabService.Type.RESOURCES);
    }

    @Override
    protected List<KlabService.Type> getOperationalServices() {
        return List.of();
    }

    @Override
    protected ReasonerService createPrimaryService(AbstractServiceDelegatingScope serviceScope, ServiceStartupOptions options) {
        return new ReasonerService(serviceScope, options);
    }

    public static void main(String[] args) {
        ServiceNetworkedInstance.start(ReasonerServer.class,
                ServiceStartupOptions.create(KlabService.Type.REASONER, args));
    }
}
