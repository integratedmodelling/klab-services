package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
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
            if (userScope != null) {
                return runtimeService.klabService().createSession(userScope, name);
            }
        }
        return null;
    }

    /**
     * Create a server-side context scope with an empty digital twin and the authorized services for the
     * requesting user.
     *
     * TODO this must contain the URLs of the resolver and resource services, and ensure they can be used
     *  with this runtime, creating the clients within the context scope. Any local service URL passed to
     *  a remote runtime should cause an error.
     *
     * @param contextName
     * @param principal
     * @param sessionHeader
     * @return the ID of the new context scope
     */
    @GetMapping(ServicesAPI.RUNTIME.CREATE_CONTEXT)
    public String createContext(@PathVariable(name = "name") String contextName, Principal principal,
                                @Header(name = ServicesAPI.SCOPE_HEADER) String sessionHeader) {

        if (principal instanceof EngineAuthorization authorization) {

            var scopeData = ContextScope.parseScopeId(sessionHeader);

            if (scopeData.type() == Scope.Type.SESSION) {

                var sessionScope = runtimeService.klabService().getScopeManager().getScope(authorization,
                        ServiceSessionScope.class, scopeData.scopeId());

                if (sessionScope != null) {
                    return runtimeService.klabService().createContext(sessionScope, contextName);
                }
            }
        }
        return null;
    }
}
