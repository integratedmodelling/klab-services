package org.integratedmodelling.klab.api.authentication.scope;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KServiceAccessException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;

public abstract interface Scope extends Channel {

	enum Status {
		WAITING, STARTED, CHANGED, FINISHED, ABORTED, /* this only sent by UIs for now */ INTERRUPTED, EMPTY
	}

	enum Type {
		SERVICE, // service-level scope
		USER, // root-level scope
		SCRIPT, // session-level scope
		API, // session for the REST API through a client
		APPLICATION, // session for an application, including the Explorer
		SESSION, // raw session for direct use within Java code
		CONTEXT // context, on which observe() can be called
	}

	/**
	 * Each scope can carry arbitrary data linked to it.
	 * 
	 * @return
	 */
	Parameters<String> getData();

	/**
	 * If this scope is owned by an agent, return the agent handle for
	 * communication.
	 * 
	 * @return the agent or null.
	 */
	Ref getAgent();

	/**
	 * Retrieve the service corresponding to the passed class. A
	 * {@link KServiceAccessException} should be the response when services are
	 * unavailable.
	 * 
	 * @param <T>
	 * @param serviceClass
	 * @return
	 * @throws KServiceAccessException if the requested service is not available
	 */
	<T extends KlabService> T getService(Class<T> serviceClass);
}
