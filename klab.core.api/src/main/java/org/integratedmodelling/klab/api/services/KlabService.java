package org.integratedmodelling.klab.api.services;

import java.io.Serializable;

import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;

/**
 * Services may be locally implemented or clients to remote services: each service implementation
 * should provide both forms. The latter ones must publish a URL. In all cases they are added in
 * serialized form to ResourceSet and other responses, so they should abide to Java bean conventions
 * and only use set/get methods to expose fields that are themselves serializable. All service
 * methods should NOT use getXxx/setXxx syntax.
 * <p>
 * The API of a service is designed in a way that the serialized version of a full service can
 * deserialize directly to a service client that communicates with it.
 * 
 * @author Ferd
 *
 */
public interface KlabService extends Serializable {

    /**
     * At the very minimum, each service advertises its type and local name.
     * 
     * @author Ferd
     *
     */
    interface ServiceCapabilities extends Serializable {

        String getLocalName();

        String getServiceName();
    }

    ServiceCapabilities getCapabilities();

    /**
     * Get the URL to this service. If this is null, the service cannot be used except through
     * direct injection. If it's a local URL, it can only be used locally. All these properties will
     * be reflected in the service scope. Otherwise, this could be a full-fledged service or a
     * client for one, and will speak the service API.
     * 
     * @return
     */
    String getUrl();

    /**
     * Local name should be unique among services even of the same type. It should reflect the local
     * node and always appear in REST calls as the requesting entity, so that federated calls coming
     * from the same service in a ping-pong chain of calls can be filtered out and avoid infinite
     * call chains.
     * 
     * @return
     */
    String getLocalName();

    /**
     * The identifier of the service type in the k.LAB ecosystem. This is the same for all services
     * of the same type and the derived interfaces provide a default implementation.
     * 
     * @return
     */
    String getServiceName();

    /**
     * Each service operates under a root scope that is used to report issues, talk to clients and
     * derive child scopes for users and when appropriate, sessions and contexts.
     * 
     * @return
     */
    ServiceScope scope();

}
