package org.integratedmodelling.klab.api.scope;

/**
 * A service-side scope is a regular scope but carries a string ID. We need the interface to make sure that
 * clients used within services can distinguish them and pass the ID when the requested scopes are mirroring a
 * master one.
 */
public interface ServiceSideScope extends Scope {

    String getId();
}
