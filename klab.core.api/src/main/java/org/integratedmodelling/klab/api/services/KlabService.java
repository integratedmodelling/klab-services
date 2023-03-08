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

    String getUrl();

}
