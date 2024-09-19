package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * Services may be locally implemented or clients to remote services: each service implementation should
 * provide both forms. The latter ones must publish a URL. In all cases they are added in serialized form to
 * ResourceSet and other responses, so they should abide to Java bean conventions and only use set/get methods
 * to expose fields that are themselves serializable. All service methods should NOT use getXxx/setXxx
 * syntax.
 * <p>
 * The API of a service is designed in a way that the serialized version of a full service can deserialize
 * directly to a service client that communicates with it.
 *
 * @author Ferd
 */
public interface KlabService extends Service {


    /**
     * A set of services is identified in the hub response to certificate authentication, both for engine
     * (user-level) certificates and for service (partner-level) certificates (the latter is an addition
     * w.r.t. k.LAB 0.11). The service descriptor in the hub response should have a serviceType field that is
     * one of these; if the field isn't there, LEGACY_NODE is assumed.
     */
    enum Type {

        /**
         *
         */
        REASONER(8091),

        /**
         *
         */
        RESOURCES(8092),

        /**
         *
         */
        RESOLVER(8093),

        /**
         *
         */
        RUNTIME(8094),

        /**
         *
         */
        COMMUNITY(8095),

        /**
         * The engine is an orchestrator of other k.LAB Services and a provider of scopes at user level and
         * below. It serves the public observation API.
         */
        ENGINE(8283),

        /**
         * Discovery service for other services. To be implemented. When a certificate provides a discovery
         * service, it should interact with the community service and used to supply the other services,
         * including filtering by worldview and allowing global refactoring.
         */
        DISCOVERY(8096),

        /**
         * These are the pre-1.0 nodes and may or may not be used at some point in the transition to 1.0.
         */
        LEGACY_NODE(8287);

        public int defaultPort;
        public String defaultServicePath;

        private Type(int defaultPort) {
            this.defaultServicePath = this.name().toLowerCase();
            this.defaultPort = defaultPort;
        }

        public URL localServiceUrl() {
            try {
                return new URI("http://127.0.0.1:" + defaultPort + "/" + defaultServicePath).toURL();
            } catch (Exception e) {
                // naah
                throw new RuntimeException(e);
            }
        }

        public Class<? extends KlabService> classify() {
            return switch (this) {
                case REASONER -> Reasoner.class;
                case RESOURCES -> ResourcesService.class;
                case RESOLVER -> Resolver.class;
                case RUNTIME -> RuntimeService.class;
                case COMMUNITY -> Community.class;
                case ENGINE -> Engine.class;
                case DISCOVERY -> null;
                case LEGACY_NODE -> null;
            };
        }

        public static Type classify(KlabService service) {
            return switch (service) {
                case ResourcesService s -> RESOURCES;
                case RuntimeService s -> RUNTIME;
                case Reasoner s -> REASONER;
                case Community s -> COMMUNITY;
                case Resolver s -> RESOLVER;
                default -> null;
            };
        }


        public static <T extends KlabService> Type classify(Class<T> serviceClass) {
            if (Reasoner.class.isAssignableFrom(serviceClass)) {
                return Type.REASONER;
            } else if (Community.class.isAssignableFrom(serviceClass)) {
                return Type.COMMUNITY;
            } else if (ResourcesService.class.isAssignableFrom(serviceClass)) {
                return Type.RESOURCES;
            } else if (Resolver.class.isAssignableFrom(serviceClass)) {
                return Type.RESOLVER;
            } else if (RuntimeService.class.isAssignableFrom(serviceClass)) {
                return Type.RUNTIME;
            }
            throw new KlabIllegalArgumentException("Unexpected service class " + serviceClass.getCanonicalName());
        }


    }

    /**
     * Service status should be cheap to obtain and may be polled by monitoring clients to visualize service
     * status at regular intervals. Only the known fields may be reported, with negative values representing
     * unknown values.
     */
    interface ServiceStatus extends Serializable {

        KlabService.Type getServiceType();

        String getServiceId();

        /**
         * Available means that the service has been initialized and is accepting connections. It does not
         * mean that the API is fully functional: it may not have gathered enough information from other
         * services, or it may be configured in ways that make it inoperative. Use {@link #isOperational()} to
         * check for that, and/or use the capabilities to check what functions are supported.
         *
         * @return
         */
        boolean isAvailable();

        /**
         * Operational means that the entire API of the service is available to support the functions declared
         * as supported in the capabilities.
         *
         * @return
         */
        boolean isOperational();

        boolean isBusy();

        /**
         * This may be false when the service is both available and not busy. Refers to the internal
         * consistency of the data space; e.g. in the reasoner it may mean that there are errors in the
         * worldview, or the resources server may have internal consistency issues.
         *
         * @return
         */
        boolean isConsistent();

        ServiceScope.Locality getLocality();

        int getHealthPercentage();

        int getLoadPercentage();

        long getMemoryAvailableBytes();

        long getMemoryUsedBytes();

        int getConnectedSessionCount();

        int getKnownSessionCount();

        long getUptimeMs();

        long getBootTimeMs();

        List<Notification> getAdvisories();

        Metadata getMetadata();

        public static ServiceStatus offline() {
            return new ServiceStatusImpl();
        }

        default boolean hasChangedComparedTo(ServiceStatus statusBeforeChecking) {
            return this.isAvailable() != statusBeforeChecking.isAvailable() ||
                    this.isBusy() != statusBeforeChecking.isBusy() ||
                    this.isOperational() != statusBeforeChecking.isOperational() ||
                    !this.getAdvisories().equals(statusBeforeChecking.getAdvisories());
        }
    }

    /**
     * At the very minimum, each service advertises its type, an instance ID and a local name. There is also a
     * secret key that's not advertised but can be read by clients on the same machine.
     *
     * @author Ferd
     */
    interface ServiceCapabilities extends Serializable {

        Type getType();

        String getLocalName();

        String getServiceName();

        /**
         * The service ID should be unique and generated at the first service boot, then persisted in
         * configuration so it never changes for as long as the service exists.
         *
         * @return
         */
        String getServiceId();

        /**
         * A unique server ID implemented as a hash based on the hardware and persisted in configuration so it
         * never changes.
         *
         * @return
         */
        String getServerId();

        /**
         * The URL may be null, which means that the service isn't accessible through a client. Any service
         * that is accessible by clients should send the URL at least with the capabilities sent through the
         * {@link org.integratedmodelling.klab.api.services.runtime.Message.MessageType#ServiceAvailable}
         * message. The URL may be filled in by the server itself or, if empty, by the service orchestrator.
         *
         * @return
         */
        URL getUrl();

        /**
         * Messaging queues listed here are available for user scopes only if the capabilities are retrieved
         * by a privileged user (normally a local, exclusive user or an authorized administrator). These are
         * used in all send() methods invoked on an authorized user scope. This may return an empty set
         * without jeopardizing the ability to instrument session and context scopes with messaging, as long
         * as {@link #getBrokerURI()} returns a valid URI.
         *
         * @return the set of messaging queues published by this service for the requesting user, in the form
         * <code>username.queuetype</code>.
         */
        Set<Message.Queue> getAvailableMessagingQueues();

        /**
         * URI for the message broker. If null, the service doesn't have messaging capabilities and will not
         * enable distributed digital twin functionalities. If this isn't null, messaging can be added to
         * scopes.
         *
         * @return the broker URL or null
         */
        URI getBrokerURI();
    }

    /**
     * Each service publishes capabilities, overridden to the specific capability class for each service.
     *
     * @param scope the scope under which capabilities are computed. The scope can be null or generic, which
     *              will return general capabilities without scope-specific resources or services.
     * @return
     */
    ServiceCapabilities capabilities(Scope scope);

    /**
     * The service status should be cheap to obtain and small enough to enable multiple and frequent polling.
     * Contents should be prioritized in favor of efficiency.
     *
     * @return
     */
    ServiceStatus status();

    /**
     * Get the URL to this service. If this is null, the service cannot be used except through direct
     * injection. If it's a local URL, it can only be used locally. All these properties will be reflected in
     * the service scope. Otherwise, this could be a full-fledged service or a client for one, and will speak
     * the service API.
     *
     * @return
     */
    URL getUrl();

    /**
     * Local name should be unique among services even of the same type. It should reflect the local node and
     * always appear in REST calls as the requesting entity, so that federated calls coming from the same
     * service in a ping-pong chain of calls can be filtered out and avoid infinite call chains.
     *
     * @return
     */
    String getLocalName();

    /**
     * The service ID is an ugly, unique string that uniquely identifies a server instance. Any clients that
     * use a service must report the same ID as the remote service. The service ID is also available in the
     * {@link ServiceCapabilities#serviceId()} but it must be public and available for quick access so it's
     * also exposed in the core service API.
     *
     * @return the service ID. Never null.
     */
    String serviceId();

    /**
     * Each service operates under a root scope that is used to report issues, talk to clients and derive
     * child scopes for users and when appropriate, sessions and contexts. The service scope may be a
     * {@link ServiceScope} except in the {@link Engine} service, which may run under a {@link UserScope} or a
     * according to who owns and operates the engine.
     *
     * @return
     */
    Scope serviceScope();

    /**
     * All services provide a shutdown call to clean up things upon normal termination. The service should not
     * be expected to exist after this is called.
     *
     * @return
     */
    boolean shutdown();

    /**
     * Register a session created by the scope manager after receiving a CREATE_SESSION request. Return a
     * unique session ID that may be requested with the session or generated within the service.
     *
     * @param sessionScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return the ID of the new session created at server side, or null in case of failure.
     */
    String registerSession(SessionScope sessionScope);

    /**
     * Register a context scope created by the scope manager. Return a unique session ID that may be requested
     * with the session or generated within the service. Context starts empty with the default observer for
     * the worldview, using the services available to the user and passed as parameters. The same runtime that
     * hosts the context must become the one and only runtime accessible to the resulting scope. If the
     * service is not a runtime, the request must come from another service and the scope should be
     * instrumented as necessary for its purposes.
     *
     * @param contextScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return the ID of the new context scope created at server side, or null in case of failure.
     */
    String registerContext(ContextScope contextScope);


    /**
     * Exclusive status means that the service is either an application started by the requesting JVM or a
     * client to a server exclusively dedicate to the running application. If a service is exclusive, we can
     * be sure that all endpoints are available, including the administrative ones, and we should prepare to
     * take care of all needed initialization and maintenance if we change content that are relevant to this
     * service.
     * <p>
     * Exclusive status can be checked in clients when the access token used is the service secret only
     * available on the same filesystem as the service. For services exposed to the network, it implies that
     * some kind of locking has been done by an authorized client, or that the configuration guarantees
     * exclusive use (including refusing connections from non-exclusive clients).
     *
     * @return true if exclusive
     */
    boolean isExclusive();

    /**
     * If true, the user/session/context scopes managed by the service should return a valid
     * {@link org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref} to run behaviors and
     * applications. At the moment only the runtime has this requirement to support agentive observations.
     * Returning true will initialize an actor system on boot and provide an agent to each new scope.
     *
     * @return
     * @deprecated this should move to BaseService as it's only relevant there.
     */
    boolean scopesAreReactive();

    /**
     * Get the access rights for the passed resource. If the resource does not exist or is inaccessible to the
     * scope, return empty rights.
     *
     * @param resourceUrn
     * @param scope
     * @return
     */
    ResourcePrivileges getRights(String resourceUrn, Scope scope);

    /**
     * Set the access rights for the named resource.
     *
     * @param resourceUrn
     * @param resourcePrivileges
     * @param scope
     * @return true if the resource was accessible and the rights were set.
     */
    boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope);

    /**
     * Retrieve all stored credential information for the passed scope.
     *
     * @param scope
     * @return
     */
    List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope);

    /**
     * Add the passed credentials to the service's credential store. Scope determines what the credentials can
     * apply to.
     *
     * @param host        stripped-down hostname (possibly with port and path), e.g. github.com/user
     * @param credentials
     * @param scope
     * @return true if successful
     */
    ExternalAuthenticationCredentials.CredentialInfo addCredentials(String host,
                                                                    ExternalAuthenticationCredentials credentials, Scope scope);
}
