package org.integratedmodelling.resources.server;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@EnableAutoConfiguration
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.application.security",
                               "org.integratedmodelling.klab.services.application.controllers",
                                "org.integratedmodelling.resources.server.controllers"})
public class ResourcesServer extends ServiceNetworkedInstance<ResourcesProvider> {

    public ResourcesServer(ServiceStartupOptions startupOptions) {
        super(startupOptions);
    }

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return Collections.emptyList();
    }

    @Override
    protected ResourcesProvider createPrimaryService(ServiceScope serviceScope, ServiceStartupOptions options) {
        return new ResourcesProvider(serviceScope, options);
    }

    //    public static void main(String[] args) {
    //        ServiceApplication application = new ServiceApplication();
    //        var server = new ResourcesServer(new ResourcesProvider(getServiceScope(), getDefaultListeners
    //        ()), St)
    //        application.run(, args);
    //    }
}
