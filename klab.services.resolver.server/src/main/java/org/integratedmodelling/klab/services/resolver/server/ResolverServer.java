package org.integratedmodelling.klab.services.resolver.server;

import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.resolver.ResolverService;
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
                               "org.integratedmodelling.klab.services.resolver.server.controllers"})
public class
ResolverServer extends ServiceNetworkedInstance<ResolverService> {

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return List.of(KlabService.Type.REASONER, KlabService.Type.RESOURCES);
    }

    @Override
    protected List<KlabService.Type> getOperationalServices() {
        return List.of();
    }

    @Override
    protected ResolverService createPrimaryService(AbstractServiceDelegatingScope serviceScope,
                                                   ServiceStartupOptions options) {
        return new ResolverService(serviceScope, options);
    }

    public static void main(String[] args) {
        ServiceNetworkedInstance.start(ResolverServer.class,
                ServiceStartupOptions.create(KlabService.Type.RESOLVER, args));
    }
}
