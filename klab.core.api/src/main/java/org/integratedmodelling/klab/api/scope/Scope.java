package org.integratedmodelling.klab.api.scope;

import java.util.Collection;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * The scope is a communication channel that holds all information about the knowledge environment in the
 * service. That includes other services, which must be set into the scope in either an embedded or client
 * implementation and are made available through {@link #getService(Class)}. Scopes are passed to most
 * functions in k.LAB.
 * <p>
 * There are three major classes of scope: authentication produces a {@link UserScope}, which can spawn
 * sessions and applications as "child" scopes. Within these, {@link ContextScope}s are used to make and
 * manage observations. In addition, a {@link ServiceScope} is used by each service to access logging, the
 * owning identity, and other services.
 * <p>
 * The scope API exposes an agent handle through {@link #getAgent()} which is used to communicate with an
 * underlying software agent, incarnating the identity that owns the scope in a reactive environment.
 * According to implementation, the agent may or may not be present.
 * <p>
 * In a server context, the authentication mechanism is responsible for maintaing a valid hierarchy of scopes
 * based on the authorization token. Scopes should never be transferred through REST calls (they do not
 * implement Serializable on purpose) unless embedded in authorization tokens, from which they should be
 * extracted and sent to calls that require scopes. A "remote" scope MUST implement {@link #send(Object...)}
 * and the logging methods (with the possible exception of {@link #debug(Object...)}) so that they communicate
 * their arguments to the client-side scope using a suitable RPC mechanism.
 * <p>
 * At the moment, there is no requirement for the remote scope to be able to communicate with a remote agent
 * in case {@link #getAgent()} isn't null at the client side. This may become a requirement in the future.
 *
 * @author Ferd
 */
public abstract interface Scope extends Channel {

    enum Status {
        WAITING, STARTED, CHANGED, FINISHED, ABORTED, INTERRUPTED, EMPTY
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
     * If this scope is owned by an agent, return the agent handle for communication.
     *
     * @return the agent or null.
     */
    Ref getAgent();

    /**
     * Retrieve the service corresponding to the passed class. A {@link KlabServiceAccessException} should be
     * the response when services are unavailable. If there are multiple services available for the class, a
     * default one should be chosen based on load factor, vicinity or any other sensible logic.
     *
     * @param <T>
     * @param serviceClass
     * @return
     * @throws KlabServiceAccessException if the requested service is not available
     */
    <T extends KlabService> T getService(Class<T> serviceClass);

    /**
     * Retrieve all the currently available services corresponding to the passed class, including the default
     * one returned by {@link #getService(Class)} if applicable.
     *
     * @param <T>
     * @param serviceClass
     * @return
     */
    <T extends KlabService> Collection<T> getServices(Class<T> serviceClass);

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
     * Set the status as needed. Setting the status to INTERRUPTED should cause {@link #isInterrupted()} to
     * return true.
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

}
