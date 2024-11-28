package org.integratedmodelling.klab.api.scope;

import java.util.Collection;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * The scope is a communication channel that holds all information about the knowledge environment in the
 * service. That includes other services, which must be set into the scope in either an embedded or client
 * implementation and are made available through {@link #getService(Class)}. A scope of the appropriate class
 * are a required argument to most calls in the k.LAB API.
 * <p>
 * There are three major classes of scope: authentication produces a {@link UserScope}, which can spawn
 * sessions and applications as "child" {@link SessionScope}s. Within these, {@link ContextScope}s are used to
 * create and maintain the environments where observations are made, serving as a handle to a "digital twin"
 * that manages a dynamic, semantic knowledge graph (the "context"). In addition, a {@link ServiceScope} is
 * used by each service to access logging, the owning identity, and other services.
 * <p>
 * The scope can be seen as an underlying software agent, incarnating the identity that owns the scope in a
 * reactive environment. According to implementation, a reactive agent may or may not be physically
 * implemented (in the reference implementation, agents are created on demand and they run k.Actors
 * behaviors).
 * <p>
 * In a service context, the authentication mechanism is responsible for maintaining a valid hierarchy of
 * scopes based on the authorization token, which must generate a valid {@link UserScope}. Scopes are not
 * serialized for communication between services, but are replicated explicitly by the originating service
 * into any other service that must be aware of them, using the
 * {@link org.integratedmodelling.klab.api.ServicesAPI#CREATE_SESSION} and
 * {@link org.integratedmodelling.klab.api.ServicesAPI#CREATE_CONTEXT} calls, after which they are referred to
 * through the {@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} in REST calls that require
 * scopes below the user level. Each service maintains the state it needs to operate. The originator of
 * {@link SessionScope}s and {@link ContextScope} is always the
 * {@link org.integratedmodelling.klab.api.services.RuntimeService}.
 * <p>
 * A "remote" scope implements {@link #send(Object...)} and the logging methods inherited from {@link Channel}
 * so that they can communicate across services to their peer scopes using a suitable RPC mechanism (the
 * reference implementation uses AMPQ messaging and separate queues for different event streams). The RPC
 * mechanism is crucial to connect observation scopes into distributed digital twins.
 * <p>
 * Scopes expire according to their expiration type returned by {@link #getExpiration()}. Services are
 * configured to define the details and may refuse scope creation requests that require unsupported expiration
 * types.
 *
 * @author Ferd
 */
public interface Scope extends Channel {

    enum Expiration {
        IDLE_TIMEOUT,
        SERVICE_SHUTDOWN,
        EXPLICIT_ACTION
    }

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
        CONTEXT; // context, on which observe() can be called

        public Class<? extends Scope> classify() {

            return switch (this) {
                case SERVICE -> ServiceScope.class;
                case USER -> UserScope.class;
                case SESSION, APPLICATION, API, SCRIPT -> SessionScope.class;
                case CONTEXT -> ContextScope.class;
            };
        }
    }

    /**
     * The expiration type for the scope. The details (e.g. the idle time) depend on service configuration.
     *
     * @return
     */
    Expiration getExpiration();

    /**
     * All scope except a {@link UserScope} have a non-null parent scope. A {@link ContextScope} is the only
     * one that can have another scope of its same class as parent.
     *
     * @return the parent scope, null for {@link UserScope}s.
     */
    Scope getParentScope();

    /**
     * Each scope can carry arbitrary data linked to it.
     *
     * @return
     */
    Parameters<String> getData();


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
     * The service ID serves as a unique key into a specific service instance (or its clients). This is the
     * equivalent of {@link #getService(String, Class)} when a serviceId is communicated and a specific
     * service is wanted. Because this is a precise request, it is expected that the service exists; if not,
     * this method should throw a
     * {@link org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException} exception.
     *
     * @param serviceId
     * @param serviceClass
     * @param <T>
     * @return the service of the passed class and ID.
     * @throws org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException if the service isn't
     *                                                                                 among those listed by
     *                                                                                 {@link
     *                                                                                 #getServices(Class)}
     *                                                                                 for serviceClass.
     */
    <T extends KlabService> T getService(String serviceId, Class<T> serviceClass);

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
     * The scope type is the only safe way to discriminate a scope type from another.
     *
     * @return
     */
    Type getType();

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
