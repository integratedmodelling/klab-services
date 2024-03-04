package org.integratedmodelling.klab.services.resources.embedded;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.util.Collections;
import java.util.List;

public class ResourcesServiceInstance extends ServiceInstance<ResourcesProvider> {

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return Collections.emptyList();
    }

    @Override
    protected ResourcesProvider createPrimaryService(ServiceScope serviceScope, ServiceStartupOptions options) {
        return new ResourcesProvider(serviceScope, options);
    }
}
