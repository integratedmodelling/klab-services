package org.integratedmodelling.klab.services.application.controllers;


import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Handshaking after authentication, will notify a scope from the remote side and provide it with
 * a peer at the service side. If the scope is using the secret or the principal is an administrator
 * and requests it, the scopes will be linked using websockets so that send() at one side will trigger
 * send() with the same parameters at the other.
 */
@RestController
@Secured(Role.USER)
public class KlabScopeController {

    @Autowired
    ServiceNetworkedInstance<?> service;

    @Autowired
    ServiceAuthorizationManager scopeManager;

    /**
     * return the websockets URL to use for communication with this scope, or an empty string if the feature
     * is unavailable.
     *
     * @param scopeType
     * @param scopeId
     * @param principal
     * @return a websockets URL
     */
    @GetMapping(ServicesAPI.SCOPE.REGISTER)
    public String registerScope(@PathVariable Scope.Type scopeType, @PathVariable String scopeId, Principal principal) {
        return "ws://suca";
    }

    @GetMapping(ServicesAPI.SCOPE.DISPOSE)
    public boolean disposeScope(@PathVariable String scopeId, Principal principal) {
        return true;
    }

}
