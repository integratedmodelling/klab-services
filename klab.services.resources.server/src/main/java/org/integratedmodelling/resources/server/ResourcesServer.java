package org.integratedmodelling.resources.server;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.services.application.Service;
import org.integratedmodelling.klab.services.application.ServiceApplication;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

public class ResourcesServer extends Service<ResourcesProvider> {

    public ResourcesServer(ResourcesProvider service) {
        super(service);
    }

    public ResourcesServer(ResourcesProvider service, StartupOptions options, KlabCertificate certificate) {
        super(service, options, certificate);
    }

    public static void main(String[] args) {
        ServiceApplication application = new ServiceApplication();
        application.run(new ResourcesProvider(getServiceScope(), getDefaultListeners()), args);
    }

    private static String getDefaultListeners() {
        return null;
    }

    private static ServiceScope getServiceScope() {
        return null;
    }
}
