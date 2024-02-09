package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.KlabService;

/**
 * User scopes restrict a service's permissions to those available to a specific user.
 *
 * @author ferd
 */
public interface UserScope extends Scope {

    /**
     * The scope is created for an authenticated user by the engine.
     *
     * @return
     */
    UserIdentity getUser();

    /**
     * Start a raw session with a given identifier and return the scope that controls it. This will locate and
     * connect an available runtime among those that are visible to the user.
     *
     * @param sessionName
     * @return
     */
    SessionScope runSession(String sessionName);

    /**
     * Run an individual application, test case or script and return the scope that controls it. Different VMs
     * and agent behaviors are used according to the type, which can only be one of the independently runnable
     * behaviors: APP, SCRIPT or TESTCASE.
     *
     * @param behavior
     * @return
     */
    SessionScope run(String behaviorName, KActorsBehavior.Type behaviorType);

    /**
     * Switch the "current" service for the service class to the passed one, adding it to the available
     * services in the scope if it's not there already. After this call, the user scope's
     * {@link #getService(Class)} will return the passed service for that class.
     */
    void switchService(KlabService service);

}
