package org.integratedmodelling.klab.services.reasoner.embedded;

import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

import java.util.List;

/**
 * Service instance to be used in embedded mode. Just create one and call start(), then if needed
 * {@link #waitOnline(int)}.
 */
public class ReasonerInstance extends ServiceInstance<ReasonerService> {

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
}
