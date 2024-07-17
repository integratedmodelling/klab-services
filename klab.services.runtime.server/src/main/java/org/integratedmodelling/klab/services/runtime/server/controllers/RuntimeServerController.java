package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Secured(Role.USER)
public class RuntimeServerController {

    @Autowired
    private RuntimeServer runtimeService;

    @GetMapping(ServicesAPI.RUNTIME.CREATE_SESSION)
    public String createSession(@PathVariable(name = "name") String name, Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {

            ServiceUserScope userScope = runtimeService.klabService().getScopeManager().getOrCreateUserScope(authorization);
            var session = userScope.runSession(name);
            if (session instanceof ServiceSessionScope sessionScope) {
                return sessionScope.getId();
            }
        }
        return null;
    }

    @GetMapping(ServicesAPI.RUNTIME.CREATE_CONTEXT)
    public String createContext(@PathVariable(name = "name") String contextName, Principal principal,
                                @Header(name = ServicesAPI.SCOPE_HEADER) String sessionHeader) {

        if (principal instanceof EngineAuthorization authorization) {

            var sessionScope = runtimeService.klabService().getScopeManager().getScope(authorization,
                    ServiceSessionScope.class, sessionHeader);

            if (sessionScope != null) {
                var contextScope = sessionScope.createContext(contextName);
                if (contextScope instanceof ServiceContextScope serviceContextScope) {
                    return serviceContextScope.getId();
                }
            }
        }
        return null;
    }
}
