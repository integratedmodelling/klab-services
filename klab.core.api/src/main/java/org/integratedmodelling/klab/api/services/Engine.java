package org.integratedmodelling.klab.api.services;

import java.io.Serializable;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.scope.KScope;

public interface Engine extends KlabService {

    /**
     * All services publish capabilities and have a call to obtain them. Capabilities may depend on
     * authentication but the endpoint should be publicly available as well.
     * 
     * @author Ferd
     *
     */
    interface Capabilities extends Serializable {

        /**
         * If true, the service is local or dedicated to the service that uses it.
         * 
         * @return
         */
        boolean isExclusive();

        /**
         * If true, the user asking for capabilities can use the admin functions. If isExclusive()
         * returns false, using admin functions can be dangerous.
         * 
         * @return
         */
        boolean canWrite();

    }

    /**
     * 
     * @return
     */
    Capabilities capabilities();

    /**
     * Login through an authenticated user identity and return the root scope for that user. The
     * scope for the user should be stored: if the user was logged in previously, the previously
     * logged in scope should be returned..
     * 
     * @param user
     * @return
     */
    KScope login(UserIdentity user);
}
