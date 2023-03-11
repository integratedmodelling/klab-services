package org.integratedmodelling.klab.api.authentication.scope;

/**
 * A service scope is obtained upon authentication of a service.
 * 
 * @author ferd
 *
 */
public abstract interface ServiceScope extends Scope {

    /**
     * Local means there is no URL or no external connections are allowed, and the consequences of
     * administering the service do not affect anything but the local enviroment.
     * 
     * @return
     */
    boolean isLocal();

    /**
     * Exclusive means that this is not a federated service or it is operating in non-federated
     * mode, only accessing content that is available locally.
     * 
     * @return
     */
    boolean isExclusive();

    boolean isDedicated();

}
