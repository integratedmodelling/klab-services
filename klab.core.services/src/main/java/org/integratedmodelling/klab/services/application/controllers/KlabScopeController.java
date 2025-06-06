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
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
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

  @Autowired ServiceNetworkedInstance<?> instance;

  /**
   * Create a session with the passed name. If a broker is available, also setup messaging and any
   * messaging queues requested with the call, defaulting as per implementation.
   *
   * <p>If an ID is passed, the scope will mirror a remote one and the return value should be the
   * same ID in case of success.
   *
   * @param request
   * @param sessionId
   * @param principal
   * @param response
   * @param queuesHeader
   * @return
   */
  @PostMapping(ServicesAPI.CREATE_SESSION)
  public String createSession(
      @RequestBody ScopeRequest request,
      @RequestParam(name = "id", required = false) String sessionId,
      Principal principal,
      HttpServletResponse response,
      @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required = false)
          Collection<Message.Queue> queuesHeader,
      @RequestHeader(value = ServicesAPI.MESSAGING_URL_HEADER, required = false) String brokerUrl,
      @RequestHeader(value = ServicesAPI.FEDERATION_ID_HEADER, required = false)
          String federationId) {

    if (principal instanceof EngineAuthorization authorization) {

      var userScope = authorization.getScope(UserScope.class);
      if (userScope != null) {

        Federation federation = null;
        if (federationId != null) {
          federation = new Federation(federationId, brokerUrl);
        }

        var ret = userScope.getUserSession(userScope.getService(RuntimeService.class));
        var identity = userScope.getIdentity();

        if (federation != null
            && !identity.getData().containsKey(UserIdentity.FEDERATION_DATA_PROPERTY)) {
          // this way the federation goes into the service clients. TODO the logic here is really
          // stateful and not clean as things can be invalidated simply by improper client behavior.
          // It should be revised by moving the federation info at first authentication
          // with the hub, which would  require it to be in the JWT, or a specific hub call.
          identity.getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, federation);
        }

        List<Reasoner> reasoners =
            instance.klabService() instanceof Reasoner r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getReasonerServices().stream()
                        .map(
                            url ->
                                new ReasonerClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());
        List<RuntimeService> runtimes =
            instance.klabService() instanceof RuntimeService r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getRuntimeServices().stream()
                        .map(
                            url ->
                                new RuntimeClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());
        List<ResourcesService> resources =
            instance.klabService() instanceof ResourcesService r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getResourceServices().stream()
                        .map(
                            url ->
                                new ResourcesClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());
        List<Resolver> resolvers =
            instance.klabService() instanceof Resolver r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getResolverServices().stream()
                        .map(
                            url ->
                                new ResolverClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());

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

        var id = instance.klabService().registerSession(ret, federation);
        if (brokerUrl != null && ret instanceof ServiceSessionScope serviceSessionScope) {

          if (queuesHeader == null) {
            queuesHeader = serviceSessionScope.defaultQueues();
          }

          var implementedQueues = serviceSessionScope.setupMessaging(federation, id, queuesHeader);

          Logging.INSTANCE.info(
              "Queues set up for session " + id + ": " + implementedQueues + " on session scope");

          if (!serviceSessionScope.initializeAgents(id)) {
            Logging.INSTANCE.warn("agent initialization failed in session creation");
          }
          response.setHeader(
              ServicesAPI.MESSAGING_QUEUES_HEADER, Utils.Strings.join(implementedQueues, ", "));
        }
        return id;
      } else {
        Logging.INSTANCE.error("Session instantiation failed: no valid user scope for request");
      }
    }
    return null;
  }

  /**
   * Create a server-side context scope with an empty digital twin and the authorized services for
   * the requesting user. Also setup any messaging queues requested with the call, defaulting as per
   * implementation.
   *
   * <p>The call contains the URLs of the resolver and resource services, and must ensure they can
   * be used with this runtime, creating the clients within the context scope. Any local service URL
   * passed to a remote runtime should cause an error.
   *
   * <p>If an ID is passed, the scope will mirror a remote one and the return value should be the
   * same ID in case of success.
   *
   * @param request
   * @param contextId if passed, the context mirrors an existing one in the calling service
   * @param principal
   * @return the ID of the new context scope
   */
  @PostMapping(ServicesAPI.CREATE_CONTEXT)
  public String createContext(
      @RequestBody ScopeRequest request,
      @RequestParam(name = "id", required = false) String contextId,
      Principal principal,
      @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required = false)
          Collection<Message.Queue> queuesHeader,
      @RequestHeader(value = ServicesAPI.SERVICE_ID_HEADER, required = false)
          String serviceIdHeader,
      @RequestHeader(value = ServicesAPI.FEDERATION_ID_HEADER, required = false)
          String federationId,
      @RequestHeader(value = ServicesAPI.MESSAGING_URL_HEADER, required = false) String brokerUrl,
      HttpServletResponse response) {

    if (principal instanceof EngineAuthorization authorization) {

      var sessionScope = authorization.getScope(SessionScope.class);

      if (sessionScope != null) {

        var identity = sessionScope.getIdentity();
        Federation federation = null;
        if (federationId != null) {
          federation = new Federation(federationId, brokerUrl);
        }

        if (federation != null
            && !identity.getData().containsKey(UserIdentity.FEDERATION_DATA_PROPERTY)) {
          // TODO see comment in createSession. This shouldn't happen if we've gone through a
          // session, but
          //  a DT could also be created in other ways at production.
          identity.getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, federation);
        }
        List<Reasoner> reasoners =
            instance.klabService() instanceof Reasoner r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getReasonerServices().stream()
                        .map(
                            url ->
                                new ReasonerClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());
        List<RuntimeService> runtimes =
            instance.klabService() instanceof RuntimeService r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getRuntimeServices().stream()
                        .map(
                            url ->
                                new RuntimeClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());
        List<ResourcesService> resources =
            instance.klabService() instanceof ResourcesService r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getResourceServices().stream()
                        .map(
                            url ->
                                new ResourcesClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());
        List<Resolver> resolvers =
            instance.klabService() instanceof Resolver r
                ? new ArrayList<>(List.of(r))
                : new ArrayList<>(
                    request.getResolverServices().stream()
                        .map(
                            url ->
                                new ResolverClient(
                                    url, identity, instance.klabService(), instance.settings()))
                        .toList());

        if (request.getReasonerServices().isEmpty()) {
          reasoners.addAll(instance.klabService().serviceScope().getServices(Reasoner.class));
        }

        var ret = sessionScope.createContext(request.getName());

        if (ret instanceof ServiceContextScope serviceContextScope) {

          serviceContextScope.setHostServiceId(serviceIdHeader);
          serviceContextScope.setServices(resources, resolvers, reasoners, runtimes);
          if (contextId != null) {
            // slave mode: session ID is provided by a calling service. The service's
            // registerSession should check that.
            serviceContextScope.setId(contextId);
          }

          // TODO check presence and availability of all services and fail if no response

          if (queuesHeader == null || queuesHeader.isEmpty()) {
            queuesHeader = serviceContextScope.defaultQueues();
          }

          var id = instance.klabService().registerContext(ret, federation);

          var queuesAvailable = serviceContextScope.setupQueues(queuesHeader);

          Logging.INSTANCE.info(
              "Queues set up for session " + id + ": " + queuesAvailable + " on context scope");

          if (!serviceContextScope.initializeAgents(id)) {
            Logging.INSTANCE.warn("agent initialization failed in context creation");
          }

          response.setHeader(
              ServicesAPI.MESSAGING_QUEUES_HEADER, Utils.Strings.join(queuesAvailable, ", "));

          return id;
        }
      } else {
        Logging.INSTANCE.error("Context instantiation failed: no valid session scope for request");
      }
    }
    return null;
  }

  @GetMapping(ServicesAPI.RELEASE_SESSION)
  public boolean closeSession(Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {
      var sessionScope = authorization.getScope(SessionScope.class);
      if (sessionScope != null) {
        sessionScope.close();
        return true;
      }
    }
    return false;
  }

  @GetMapping(ServicesAPI.RELEASE_CONTEXT)
  public boolean closeContext(Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      if (contextScope != null) {
        contextScope.close();
        return true;
      }
    }
    return false;
  }
}
