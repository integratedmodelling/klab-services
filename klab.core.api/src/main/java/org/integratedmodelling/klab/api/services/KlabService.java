package org.integratedmodelling.klab.api.services;

import java.io.Serializable;

/**
 * Services may be locally implemented or clients to remote services: each service implementation
 * should provide both forms. The latter ones must publish a URL. In all cases they are added in
 * serialized form to ResourceSet and other responses, so they should abide to Java bean conventions
 * and only use set/get methods to expose fields that are themselves serializable. All service
 * methods should NOT use getXxx/setXxx syntax.
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

    String getServiceName();

}
