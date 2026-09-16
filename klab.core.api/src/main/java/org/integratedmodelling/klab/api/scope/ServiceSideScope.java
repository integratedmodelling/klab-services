package org.integratedmodelling.klab.api.scope;

/**
 * A service-side scope is a regular scope but carries a string ID. We need the interface to make sure that
 * clients used within services can distinguish them and pass the ID when the requested scopes are mirroring a
 * master one.
 */
public interface ServiceSideScope extends Scope {

    String getId();

    /**
     * Permission granted by the hosting service, independently of identity-provider groups.
     * Implementations without a service permission policy grant nothing by default.
     */
    default boolean isAuthorized(org.integratedmodelling.klab.api.authentication.CRUDOperation operation) {
        return false;
    }
}
