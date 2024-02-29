package org.integratedmodelling.resources.server;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.util.Collections;
import java.util.List;

public class ResourcesServer extends ServiceNetworkedInstance<ResourcesProvider> {

    public ResourcesServer(ServiceStartupOptions startupOptions) {
        super(startupOptions);
    }

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return Collections.emptyList();
    }

    @Override
    protected ResourcesProvider createPrimaryService(ServiceScope serviceScope) {
        return new ResourcesProvider(serviceScope);
    }

    //    public static void main(String[] args) {
    //        ServiceApplication application = new ServiceApplication();
    //        var server = new ResourcesServer(new ResourcesProvider(getServiceScope(), getDefaultListeners
    //        ()), St)
    //        application.run(, args);
    //    }

    private static String getDefaultListeners() {
        return null;
    }

    private static ServiceScope getServiceScope() {
        return null;
    }
}
