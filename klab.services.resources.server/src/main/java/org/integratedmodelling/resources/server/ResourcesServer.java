package org.integratedmodelling.resources.server;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.Service;
import org.integratedmodelling.klab.services.application.ServiceApplication;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ResourcesServer extends Service<ResourcesProvider> {

    public ResourcesServer(ResourcesProvider service) {
        super(service);
    }

    @Override
    protected List<KlabService.Type> getEssentialServices() {
        return Collections.emptyList();
    }

    public ResourcesServer(ResourcesProvider service, StartupOptions options, KlabCertificate certificate) {
        super(service, options, certificate);
    }

//    public static void main(String[] args) {
//        ServiceApplication application = new ServiceApplication();
//        var server = new ResourcesServer(new ResourcesProvider(getServiceScope(), getDefaultListeners()), St)
//        application.run(, args);
//    }

    private static String getDefaultListeners() {
        return null;
    }

    private static ServiceScope getServiceScope() {
        return null;
    }
}
