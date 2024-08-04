package org.integratedmodelling.klab.services.runtime.server.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
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
import java.util.Collection;
import java.util.List;

@RestController
@Secured(Role.USER)
public class RuntimeServerController {

    @Autowired
    private RuntimeServer runtimeService;

    /**
     * Create a session with the passed name. If a broker is available, also setup messaging and any messaging
     * queues requested with the call, defaulting as per implementation.
     *
     * @param name
     * @param principal
     * @param response
     * @param queuesHeader
     * @return
     */
    @GetMapping(ServicesAPI.RUNTIME.CREATE_SESSION)
    public String createSession(@PathVariable(name = "name") String name, Principal principal,
                                HttpServletResponse response,
                                @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required =
                                        false) Collection<Message.Queue> queuesHeader) {
        if (principal instanceof EngineAuthorization authorization) {

            var userScope = authorization.getScope(UserScope.class);
            if (userScope != null) {
                var ret = userScope.runSession(name);
                var brokerUrl = runtimeService.klabService().capabilities(userScope).getBrokerURI();
                var id = runtimeService.klabService().registerSession(ret);
                if (brokerUrl != null) {
                    response.setHeader(ServicesAPI.MESSAGING_URN_HEADER, brokerUrl.toString());
                }
                if (brokerUrl != null && ret instanceof ServiceSessionScope serviceSessionScope) {

                    if (queuesHeader == null) {
                        queuesHeader = serviceSessionScope.defaultQueues();
                    }

                    var implementedQueues = serviceSessionScope.setupMessaging(brokerUrl.toString(),
                            queuesHeader);
                    response.setHeader(ServicesAPI.MESSAGING_QUEUES_HEADER,
                            Utils.Strings.join(implementedQueues, ", "));
                }
                return id;
            }
        }
        return null;
    }

    /**
     * Create a server-side context scope with an empty digital twin and the authorized services for the
     * requesting user. Also setup any messaging queues requested with the call, defaulting as per
     * implementation.
     * <p>
     * The call contains the URLs of the resolver and resource services, and must ensure they can be used with
     * this runtime, creating the clients within the context scope. Any local service URL passed to a remote
     * runtime should cause an error.
     *
     * @param request
     * @param principal
     * @param sessionHeader
     * @return the ID of the new context scope
     */
    @PostMapping(ServicesAPI.RUNTIME.CREATE_CONTEXT)
    public String createContext(@RequestBody ContextRequest request, Principal principal,
                                @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required =
                                        false) Collection<Message.Queue> queuesHeader,
                                HttpServletResponse response) {

        if (principal instanceof EngineAuthorization authorization) {

            var sessionScope = authorization.getScope(SessionScope.class);

            if (sessionScope != null) {

                var identity = sessionScope.getIdentity();
                var ret = sessionScope.createContext(request.getName());

                if (ret instanceof ServiceContextScope serviceContextScope) {

                    if (queuesHeader == null || queuesHeader.isEmpty()) {
                        queuesHeader = serviceContextScope.defaultQueues();
                    }

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

                    var queuesAvailable = serviceContextScope.setupMessagingQueues(queuesHeader);
                    response.setHeader(ServicesAPI.MESSAGING_QUEUES_HEADER,
                            Utils.Strings.join(queuesAvailable, ", "));

                    return id;
                }
            }
        }
        return null;
    }
}
