package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;

public interface Authentication extends KlabService {

    default String getServiceName() {
        return "klab.engine.authentication";
    }

    interface Capabilities extends ServiceCapabilities {
    }

    /**
     * 
     * @return
     */
    @Override
    Capabilities getCapabilities();

    boolean checkPermissions(ResourcePrivileges permissions, Scope scope);

    /**
     * Get an anonymous user scope with no permissions for testing or for unknown users. In local
     * configurations the anonymous scope may access any local resources.
     * 
     * @return
     */
    UserScope getAnonymousScope();

    /**
     * Authenticate the passed service and return a scope. If the service cannot or isn't meant to
     * be authenticated, return the local scope.
     * 
     * @param service
     * @return
     */
    ServiceScope authenticateService(KlabService service);
}
