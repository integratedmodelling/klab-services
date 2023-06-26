package org.integratedmodelling.klab.api.authentication.scope;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KServiceAccessException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * The scope is a communication channel that holds the truth about knowledge
 * environments in the engine. Scopes are not sent through endpoints so they are
 * not Serializable; rather, scopes for endpoints that serve remote engines
 * should be recreated based on the authentication info sent with the request,
 * and set up to modify the "peer" scope at client side through RPC when setting
 * methods or any of the {@link Channel} methods are called.
 * <p>
 * There are three major classes of scope: authentication produces a
 * {@link UserScope}, which can spawn sessions and applications as "child"
 * scopes. Within these, {@link ContextScope}s are used to make and manage
 * observations.
 * 
 * @author Ferd
 *
 */
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

	/**
	 * Return the status of the scope at the time of the call.
	 * 
	 * @return
	 */
	Status getStatus();

	/*
	 * -----------------------------------------------------------------------------
	 * Modifying methods must be callable from any scope; if remote, the scope is in
	 * charge of communicating to the peer scope at the client side via RPC.
	 * -----------------------------------------------------------------------------
	 */

	/**
	 * Set the status as needed. Setting the status to INTERRUPTED should cause
	 * {@link #isInterrupted()} to return true.
	 * 
	 * @param status
	 */
	void setStatus(Status status);

	/**
	 * Set data held by the scope and returned by {@link #getData()}.
	 * 
	 * @param key
	 * @param value
	 */
	void setData(String key, Object value);

	/**
	 * Stopping the scope leaves it in an unusable state and has different side
	 * effects depending on the specific scope type. User scopes will logout the
	 * user, script scopes will stop any running scripts. All of them should release
	 * any resources and temporary storage as well as stopping all their child
	 * scopes.
	 */
	void stop();
}
