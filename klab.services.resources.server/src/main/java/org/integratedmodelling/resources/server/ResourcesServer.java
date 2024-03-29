package org.integratedmodelling.resources.server;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.Collections;
import java.util.List;

@Component
// TODO remove the argument when all gson dependencies are the same (never)
@EnableAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration.class})
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.application.security",
                               "org.integratedmodelling.klab.services.messaging",
                               "org.integratedmodelling.klab.services.application.controllers",
                               "org.integratedmodelling.resources.server.controllers"})
public class ResourcesServer extends ServiceNetworkedInstance<ResourcesProvider> {

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return Collections.emptyList();
    }

    @Override
    protected ResourcesProvider createPrimaryService(ServiceScope serviceScope,
                                                     ServiceStartupOptions options) {
        return new ResourcesProvider(serviceScope, options);
    }

    public static void main(String[] args) {
        ServiceNetworkedInstance.start(ResourcesServer.class,
                ServiceStartupOptions.create(KlabService.Type.RESOURCES, args));
    }
}
