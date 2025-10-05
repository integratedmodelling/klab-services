package org.integratedmodelling.klab.services.application.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.*;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Scope management")
public class KlabScopeController {

  @Autowired ServiceNetworkedInstance<?> instance;

  @PostMapping(ServicesAPI.NOTIFY_USER_SCOPE)
  public boolean notifyUserScope(@RequestBody UserScopeNotification request, Principal principal) {

    if (principal instanceof EngineAuthorization authorization) {

      var userScope = authorization.getScope(ServiceUserScope.class);
      if (userScope == null) {
        return false;
      }
      return setupUserScope(userScope, request, instance.klabService());
    }
    return false;
  }

  /**
   * Ensure we have clients for all services in the request; if so, create personalized clients for
   * the user scope and set the clients in it. If any service has the same ID of the embedding
   * service, use that instead of creating a client. Return true if all clients were set up
   * correctly.
   *
   * @param userScope
   * @param request
   * @return
   */
  public boolean setupUserScope(
      ServiceUserScope userScope, UserScopeNotification request, KlabService ownerService) {
    for (var serviceInfo : request.getServices()) {
      var service =
          ownerService.serviceId().equals(serviceInfo.getId())
              ? ownerService
              : ServiceClientCatalog.INSTANCE.getService(serviceInfo, ownerService, userScope);
      userScope.addService(service);
    }
    return userScope.validateServices();
  }

  /**
   * Create a session with the passed name. If a broker is available, also setup messaging and any
   * messaging queues requested with the call, defaulting as per implementation.
   *
   * <p>If an ID is passed, the scope will mirror a remote one and the return value should be the
   * same ID in case of success.
   *
   * @param request
   * @param principal
   * @param response
   * @param queuesHeader
   * @return
   */
  @PostMapping(ServicesAPI.CREATE_SESSION)
  public String createSession(
      @RequestBody ScopeRequest request,
      Principal principal,
      HttpServletResponse response,
      @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required = false)
          Collection<Message.Queue> queuesHeader) {

    if (principal instanceof EngineAuthorization authorization) {

      var userScope = authorization.getScope(ServiceUserScope.class);
      if (userScope != null) {

        // an existing session can be reused by multiple clients that don't know about its existence
        // shouldn't need to validate id==null && behavior != null as clients should never request
        // that
        if (request.getConfiguration().getId() != null) {
          var existing =
              instance
                  .klabService()
                  .getScopeManager()
                  .getScope(request.getConfiguration().getId(), SessionScope.class);
          if (existing != null) {
            // TODO bookkeeping of users connected if user is different, possibly validate other
            //  parameters
            return existing.getId();
          }
        }

        var ret = new ServiceSessionScope(userScope);
        ret.setId(request.getConfiguration().getId());
        ret.setName(request.getConfiguration().getName());
        for (var service : userScope.getServices(KlabService.class)) {
          if (request.getServiceIds().contains(service.serviceId())) {
            ret.addService(service);
          }
        }

        KActorsBehavior behavior = null;
        if (request.getBehaviorUrn() != null) {
          // TODO resolve the behavior with all resources services
        }
        var federation = Klab.INSTANCE.getFederationData(userScope.getUser());
        var id = instance.klabService().declareSessionScope(ret, userScope, behavior);
        if (federation != null) {
          ServiceSessionScope serviceSessionScope = (ServiceSessionScope) ret;
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
      HttpServletResponse response) {

    if (principal instanceof EngineAuthorization authorization) {

      var sessionScope = authorization.getScope(SessionScope.class);

      if (sessionScope != null) {
        // var userScope = authorization.getScope(UserScope.class);
        var userScope =
            instance.klabService().getScopeManager().getScope(authorization, UserScope.class, null);
        var identity = userScope.getIdentity();
        var federation = Klab.INSTANCE.getFederationData(userScope.getUser());

        if (federation != null
            && !identity.getData().containsKey(UserIdentity.FEDERATION_DATA_PROPERTY)) {
          // TODO see comment in createSession. This shouldn't happen if we've gone through a
          // session, but
          //  a DT could also be created in other ways at production.
          identity.getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, federation);
        }

        if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

          var ret = new ServiceContextScope(serviceSessionScope, request.getConfiguration());
          for (var service : userScope.getServices(KlabService.class)) {
            if (request.getServiceIds().contains(service.serviceId())) {
              ret.addService(service);
            }
          }
          ret.setHostServiceId(serviceIdHeader);
          if (contextId != null) {
            ret.setId(contextId);
          }
          if (queuesHeader == null || queuesHeader.isEmpty()) {
            queuesHeader = ret.defaultQueues();
          }

          // this creates the DT and registers the scope
          var id = instance.klabService().declareContextScope(ret, userScope);
          var queuesAvailable = ret.setupQueues(queuesHeader);
          Logging.INSTANCE.info("Queues set up for digital twin " + id + ": " + queuesAvailable);

          if (!ret.initializeAgents(id)) {
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
