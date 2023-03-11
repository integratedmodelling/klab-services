package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;

public interface Engine extends KlabService {

    default String getServiceName() {
        return "klab.engine.service";
    }
   
    /**
     * Login through an authenticated user identity and return the root scope for that user. The
     * scope for the user should be stored: if the user was logged in previously, the previously
     * logged in scope should be returned..
     * 
     * @param user
     * @return
     */
    UserScope login(UserIdentity user);
}
