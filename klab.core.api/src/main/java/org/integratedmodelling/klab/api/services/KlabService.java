package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serializable;
import java.net.URI;
import java.net.URL;
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


    /**
     * A set of services is identified in the hub response to certificate authentication, both for engine
     * (user-level) certificates and for service (partner-level) certificates (the latter is an addition
     * w.r.t. k.LAB 0.11). The service descriptor in the hub response should have a serviceType field that is
     * one of these; if the field isn't there, LEGACY_NODE is assumed.
     */
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
        /*
        Discovery service for services. To be implemented.
         */
        DISCOVERY(8096),
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

        boolean isAvailable();

        boolean isBusy();

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
     * {@link UserScope} or a {@link ServiceScope} according to who owns and operates the engine service.
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

}
