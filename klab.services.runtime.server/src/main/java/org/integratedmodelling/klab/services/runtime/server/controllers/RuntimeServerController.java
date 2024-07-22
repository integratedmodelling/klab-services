package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextRequest;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
@Secured(Role.USER)
public class RuntimeServerController {

    @Autowired
    private RuntimeServer runtimeService;

    @GetMapping(ServicesAPI.RUNTIME.CREATE_SESSION)
    public String createSession(@PathVariable(name = "name") String name, Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {

            var userScope = authorization.getScope(UserScope.class);
            if (userScope != null) {
                var ret = userScope.runSession(name);
                return runtimeService.klabService().registerSession(ret);
            }
        }
        return null;
    }

    /**
     * Create a server-side context scope with an empty digital twin and the authorized services for the
     * requesting user.
     * <p>
     * TODO this must contain the URLs of the resolver and resource services, and ensure they can be used
     *  with this runtime, creating the clients within the context scope. Any local service URL passed to
     *  a remote runtime should cause an error.
     *
     * @param request
     * @param principal
     * @param sessionHeader
     * @return the ID of the new context scope
     */
    @PostMapping(ServicesAPI.RUNTIME.CREATE_CONTEXT)
    public String createContext(@RequestBody ContextRequest request, Principal principal,
                                @RequestHeader(ServicesAPI.SCOPE_HEADER) String sessionHeader) {

        if (principal instanceof EngineAuthorization authorization) {

            var sessionScope = authorization.getScope(SessionScope.class);

            if (sessionScope != null) {

                var identity = sessionScope.getIdentity();
                var ret = sessionScope.createContext(request.getName());

                if (ret instanceof ServiceContextScope serviceContextScope) {

                    List<Reasoner> reasoners =
                            new ArrayList<>(request.getReasonerServices().stream().map(url -> new ReasonerClient(url,
                                    identity)).toList());
                    List<RuntimeService> runtimes =
                            new ArrayList<>(List.of(runtimeService.klabService()));
                    List<ResourcesService> resources =
                            new ArrayList<>(request.getResourceServices().stream().map(url -> new ResourcesClient(url,
                                    identity)).toList());
                    List<Resolver> resolvers =
                            new ArrayList<>(request.getResolverServices().stream().map(url -> new ResolverClient(url,
                                    identity)).toList());

                    if (request.getReasonerServices().isEmpty()) {
                        reasoners.addAll(runtimeService.klabService().serviceScope().getServices(Reasoner.class));
                    }

                    // TODO check presence and availability of all services and fail if no response

                    var id = runtimeService.klabService().registerContext(ret);
                    serviceContextScope.setServices(resources, resolvers, reasoners, runtimes);

                    return id;
                }
            }
        }
        return null;
    }
}
