package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

/**
 * User scopes restrict a service's permissions to those available to a specific
 * user.
 * 
 * @author ferd
 *
 */
public interface UserScope extends Scope {

	/**
	 * The scope is created for an authenticated user by the engine.
	 * 
	 * @return
	 */
	UserIdentity getUser();

	/**
	 * Start a raw session with a given identifier and return the scope that
	 * controls it. This will locate and connect an available runtime among those
	 * that are visible to the user.
	 * 
	 * @param sessionName
	 * @return
	 */
	SessionScope runSession(String sessionName);

	/**
	 * Run an individual application, test case or script and return the scope that
	 * controls it. Different VMs and agent behaviors are used according to the
	 * type, which can only be one of the independently runnable behaviors: APP,
	 * SCRIPT or TESTCASE.
	 * 
	 * @param behavior
	 * @return
	 */
	SessionScope run(String behaviorName, KActorsBehavior.Type behaviorType);

}
