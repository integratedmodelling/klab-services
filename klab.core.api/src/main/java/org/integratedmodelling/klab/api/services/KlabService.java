package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serializable;
import java.util.List;

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

    enum Type {
        REASONER(8091),
        RESOURCES(8092),
        RESOLVER(8093),
        RUNTIME(8094),
        COMMUNITY(8095),
        /**
         * Engine is not a k.LAB Service but has its own public observation API
         */
        ENGINE(8283),

        LEGACY_NODE(8287);

        public int defaultPort;

        private Type(int defaultPort) {
            this.defaultPort = defaultPort;
        }

        public Type classify(KlabService service) {
            return switch (service) {
                case ResourcesService s -> RESOLVER;
                case RuntimeService s -> RUNTIME;
                case Reasoner s -> REASONER;
                case Community s -> COMMUNITY;
                case Resolver s -> RESOLVER;
                default -> null;
            };
        }
    }

    /**
     * Service status should be cheap to obtain and may be polled by monitoring clients to visualize service
     * status at regular intervals. Only the known fields may be reported, with negative values representing
     * unknown values.
     */
    interface ServiceStatus extends Serializable {

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
    }

    /**
     * At the very minimum, each service advertises its type and local name.
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
    }

    /**
     * Each service publishes capabilities, overridden to the specific capability class for each service.
     *
     * @return
     */
    ServiceCapabilities capabilities();

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
    String getUrl();

    /**
     * Local name should be unique among services even of the same type. It should reflect the local node and
     * always appear in REST calls as the requesting entity, so that federated calls coming from the same
     * service in a ping-pong chain of calls can be filtered out and avoid infinite call chains.
     *
     * @return
     */
    String getLocalName();

    /**
     * A local service runs on the local machine. This is checked by controlling that the hardware signature
     * of the machine running the service matches the one of the machine making the request. In clients, the
     * machine ID must be added as a header if this is ever to return true.
     *
     * @return
     */
    boolean isLocal();

    /**
     * Each service operates under a root scope that is used to report issues, talk to clients and derive
     * child scopes for users and when appropriate, sessions and contexts.
     *
     * @return
     */
    ServiceScope scope();

    /**
     * All services provide a shutdown call to clean up things upon normal termination. The service should not
     * be expected to exist after this is called.
     *
     * @return
     */
    boolean shutdown();

}
