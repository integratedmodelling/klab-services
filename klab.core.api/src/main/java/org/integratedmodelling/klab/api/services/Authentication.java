package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;

/**
 * Proxy to whatever authentication strategy is used in the system. Responsible for building the
 * user and service scopes that drive the API usage of everything. Expected to receive valid users
 * (or anonymous) authenticated with external logics.
 * 
 * @author Ferd
 *
 */
public interface Authentication {

    /**
     * 
     * @param permissions
     * @param scope
     * @return
     */
    boolean checkPermissions(ResourcePrivileges permissions, Scope scope);

    /**
     * Get an anonymous user scope with no permissions for testing or for unknown users. In local
     * configurations the anonymous scope may access any local resources and the locally available
     * worldview(s), if any exists.
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
    ServiceScope authorizeService(KlabService service);

    /**
     * Authenticate a user of a given service and return the result of calling the user scope
     * generator on the service scope.
     *
     * @param serviceScope
     * @return
     */
    UserScope authorizeUser(UserIdentity user);
}
