package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Secured(Role.USER)
public class RuntimeServerController {

    @Autowired
    ScopeManager scopeManager;

    @Autowired
    private RuntimeService runtimeService;

    public String createSession(@PathVariable(name = "name") String contextName, Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            UserScope userScope = scopeManager.getOrCreateScope(authorization,  SessionScope.class, null);
        }
        return null;
    }

    @GetMapping(ServicesAPI.RUNTIME.CREATE_CONTEXT)
    public String createContext(@PathVariable(name = "name") String contextName) {
        return null;
    }
}
