package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.services.KlabService;

/**
 * A service scope is obtained upon authentication of a service. It is a top-level scope akin to a UserScope
 * for the partner identity running a service. All operations in a service should have access to the scope.
 * <p>
 * TODO check if the maintenance and available flags are best put in the service scope than in the
 *  services themselves.
 *
 * @author ferd
 */
public interface ServiceScope extends Scope {

    /**
     * Local means there is no URL or no external connections are allowed, and the consequences of
     * administering the service do not affect anything but the local enviroment.
     *
     * @return
     */
    boolean isLocal();

    /**
     * Return the service running in this scope. The service should not be part of the set of services
     * returned by the superclass methods.
     *
     * @return
     */
    KlabService getService();

    /**
     * Exclusive means that this is not a federated service or it is operating in non-federated mode, only
     * accessing content that is available locally. Normally this state results from forking a public service
     * before making destructive changes to the knowledge it handles.
     *
     * @return
     */
    boolean isExclusive();

    boolean isDedicated();

}
