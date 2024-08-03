package org.integratedmodelling.klab.services.community;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Community;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.utilities.Utils;

import java.net.URL;

public class CommunityService extends BaseService implements Community {

    protected CommunityService(ServiceScope scope, Type serviceType, ServiceStartupOptions options) {
        super(scope, serviceType, options);
    }

    @Override
    public void initializeService() {

    }

    @Override
    public ServiceCapabilities capabilities(Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServiceStatus status() {
        return null;
    }

    @Override
    public String serviceId() {
        // TODO
        return null;
    }

    @Override
    public String getServiceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean shutdown() {
        // TODO Auto-generated method stub
        return super.shutdown();
    }


}
