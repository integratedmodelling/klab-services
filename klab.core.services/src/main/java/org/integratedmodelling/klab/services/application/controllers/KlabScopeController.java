package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
@Tag(name = "Scope management")
public class KlabScopeController {

    @Autowired
    ServiceNetworkedInstance<?> instance;

    /**
     * Create a session with the passed name. If a broker is available, also setup messaging and any messaging
     * queues requested with the call, defaulting as per implementation.
     * <p>
     * If an ID is passed, the scope will mirror a remote one and the return value should be the same ID in
     * case of success.
     *
     * @param request
     * @param sessionId
     * @param principal
     * @param response
     * @param queuesHeader
     * @return
     */
    @PostMapping(ServicesAPI.CREATE_SESSION)
    public String createSession(@RequestBody ScopeRequest request,
                                @RequestParam(name = "id", required = false) String sessionId,
                                Principal principal,
                                HttpServletResponse response,
                                @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required =
                                        false) Collection<Message.Queue> queuesHeader) {

        if (principal instanceof EngineAuthorization authorization) {

            var userScope = authorization.getScope(UserScope.class);
            if (userScope != null) {

                var ret = userScope.runSession(request.getName());
                var identity = userScope.getIdentity();

                List<Reasoner> reasoners =
                        instance.klabService() instanceof Reasoner r
                        ? new ArrayList<>(List.of(r))
                        :
                        new ArrayList<>(request.getReasonerServices().stream().map(url -> new ReasonerClient(url,
                                identity, instance.klabService())).toList());
                List<RuntimeService> runtimes =
                        instance.klabService() instanceof RuntimeService r
                        ? new ArrayList<>(List.of(r))
                        :
                        new ArrayList<>(request.getRuntimeServices().stream().map(url -> new RuntimeClient(url,
                                identity, instance.klabService())).toList());
                List<ResourcesService> resources =
                        instance.klabService() instanceof ResourcesService r
                        ? new ArrayList<>(List.of(r))
                        :
                        new ArrayList<>(request.getResourceServices().stream().map(url -> new ResourcesClient(url,
                                identity, instance.klabService())).toList());
                List<Resolver> resolvers =
                        instance.klabService() instanceof Resolver r
                        ? new ArrayList<>(List.of(r))
                        :
                        new ArrayList<>(request.getResolverServices().stream().map(url -> new ResolverClient(url,
                                identity, instance.klabService())).toList());

                if (request.getReasonerServices().isEmpty()) {
                    reasoners.addAll(instance.klabService().serviceScope().getServices(Reasoner.class));
                }

                // TODO check presence and availability of all services and fail if no response

                if (ret instanceof ServiceSessionScope serviceSessionScope) {

                    serviceSessionScope.setServices(resources, resolvers, reasoners, runtimes);
                    if (sessionId != null) {
                        // slave mode: session ID is provided by a calling service. The service's
                        // registerSession should check that.
                        serviceSessionScope.setId(sessionId);
                    }
                }

                var brokerUrl = instance.klabService().capabilities(userScope).getBrokerURI();
                var id = instance.klabService().registerSession(ret);
                if (brokerUrl != null) {
                    response.setHeader(ServicesAPI.MESSAGING_URN_HEADER, brokerUrl.toString());
                }
                if (brokerUrl != null && ret instanceof ServiceSessionScope serviceSessionScope) {

                    if (queuesHeader == null) {
                        queuesHeader = serviceSessionScope.defaultQueues();
                    }

                    var implementedQueues = serviceSessionScope.setupMessaging(brokerUrl.toString(), id,
                            queuesHeader);

                    if (instance.klabService().scopesAreReactive() && !serviceSessionScope.initializeAgents(id)) {
                        Logging.INSTANCE.warn("agent initialization failed in session creation");
                    }
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
     * <p>
     * If an ID is passed, the scope will mirror a remote one and the return value should be the same ID in
     * case of success.
     *
     * @param request
     * @param contextId if passed, the context mirrors an existing one in the calling service
     * @param principal
     * @return the ID of the new context scope
     */
    @PostMapping(ServicesAPI.CREATE_CONTEXT)
    public String createContext(@RequestBody ScopeRequest request,
                                @RequestParam(name = "ids", required = false) String contextId,
                                Principal principal,
                                @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required =
                                        false) Collection<Message.Queue> queuesHeader,
                                HttpServletResponse response) {

        if (principal instanceof EngineAuthorization authorization) {

            var sessionScope = authorization.getScope(SessionScope.class);

            if (sessionScope != null) {

                var identity = sessionScope.getIdentity();
                var ret = sessionScope.createContext(request.getName());

                if (ret instanceof ServiceContextScope serviceContextScope) {

                    if (contextId != null) {
                        // slave mode: session ID is provided by a calling service. The service's
                        // registerSession should check that.
                        serviceContextScope.setId(contextId);
                    }


                    if (queuesHeader == null || queuesHeader.isEmpty()) {
                        queuesHeader = serviceContextScope.defaultQueues();
                    }
                    var id = instance.klabService().registerContext(ret);

                    var queuesAvailable = serviceContextScope.setupMessagingQueues(id, queuesHeader);

                    if (instance.klabService().scopesAreReactive() && !serviceContextScope.initializeAgents(id)) {
                        Logging.INSTANCE.warn("agent initialization failed in context creation");
                    }
                    response.setHeader(ServicesAPI.MESSAGING_QUEUES_HEADER,
                            Utils.Strings.join(queuesAvailable, ", "));

                    return id;
                }
            }
        }
        return null;
    }
}
