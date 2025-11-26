package org.integratedmodelling.klab.services.runtime.server.controllers;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.VisualizationRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@Secured(Role.USER)
@Tag(
    name = "Runtime Server API",
    description =
        "API for runtime operations including observation submission, visualization, and knowledge graph queries")
public class RuntimeServerController {

  @Autowired private RuntimeServer runtimeService;

  /**
   * Observations are set into the digital twin by the context after creating them in an unresolved
   * state. The return long ID is the handle to the resolution; according to the messaging protocol,
   * the observation tasks should monitor resolution until completion.
   *
   * @return
   */
  @Operation(
      summary = "Submit observation for resolution",
      description = "Submits an observation request and returns a resolution handle ID")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Observation submitted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping(ServicesAPI.RUNTIME.SUBMIT_OBSERVATION)
  public @ResponseBody long submit(
      @RequestBody ResolutionRequest resolutionRequest, Principal principal)
      throws ExecutionException, InterruptedException {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope =
          authorization
              .getScope(ContextScope.class)
              .withResolutionConstraints(
                  resolutionRequest
                      .getResolutionConstraints()
                      .toArray(new ResolutionConstraint[0]));
      if (contextScope instanceof ServiceContextScope serviceContextScope) {
        var agent =
            serviceContextScope
                .getDigitalTwin()
                .getKnowledgeGraph()
                .requireAgent(resolutionRequest.getAgentName());
        var scope =
            serviceContextScope.withResolutionConstraints(
                ResolutionConstraint.of(ResolutionConstraint.Type.Provenance, agent));
        var ret = runtimeService.klabService().submit(resolutionRequest.getObservation(), scope);
        return serviceContextScope
            .getJobManager()
            .submit(ret, "Resolution of " + resolutionRequest.getObservation());
      }
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @Operation(
      summary = "Get session information",
      description = "Retrieves information about active sessions")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Session information retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @GetMapping(ServicesAPI.RUNTIME.GET_CONTEXT_INFO)
  public @ResponseBody List<ContextInfo> getSessionInfo(Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      return runtimeService.klabService().getContextInfo(authorization.getScope());
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  /**
   * Connect to the DT through the service. This may cause the scope chain to be reconstructed if
   * it's present in the knowledge graph but there is no a live scope.
   *
   * @param principal
   * @return
   */
  @Operation(
      summary = "Connect to digital twin",
      description =
          "Connect to the digital twin through the service. This may cause the digital twin to be reconstructed from the knowledge graph.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Connected successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Digital twin not found")
      })
  @PostMapping(value = ServicesAPI.RUNTIME.CONNECT)
  public @ResponseBody DigitalTwin.Configuration connectToDigitalTwin(
      Principal principal,
      @Parameter(description = "Digital twin configuration") @RequestBody ScopeRequest request,
      @RequestHeader(value = ServicesAPI.MESSAGING_QUEUES_HEADER, required = false)
          Collection<Message.Queue> queuesHeader,
      HttpServletResponse response) {

    if (principal instanceof EngineAuthorization authorization) {

      var userScope = authorization.getScope(UserScope.class);
      var ret = runtimeService.klabService().connectContext(request.getConfiguration(), userScope);

      if (ret == null) {
        return DigitalTwin.Configuration.builder()
            .withNotification(Notification.error("Cannot find a digital twin with requested ID"))
            .build();
      }
      if (ret instanceof ServiceContextScope serviceContextScope) {
        serviceContextScope.setHostServiceId(runtimeService.klabService().serviceId());
        var federation = Klab.INSTANCE.getFederationData(userScope.getUser());
        if (federation != null) {

          if (queuesHeader == null || queuesHeader.isEmpty()) {
            queuesHeader = ret.defaultQueues();
          }

          var implementedQueues =
              serviceContextScope.setupMessaging(
                  federation, request.getConfiguration().getId(), queuesHeader);

          Logging.INSTANCE.info(
              "Queues set up for context "
                  + request.getConfiguration().getId()
                  + ": "
                  + implementedQueues
                  + " on context scope");

          response.setHeader(
              ServicesAPI.MESSAGING_QUEUES_HEADER, Utils.Strings.join(implementedQueues, ", "));
        }
      }

      return ret.getConfiguration();
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @GetMapping(ServicesAPI.RUNTIME.GET_DIGITAL_TWIN_CONFIGURATION)
  public @ResponseBody DigitalTwin.Configuration getDigitalTwinConfiguration(
      @PathVariable(name = "id") String scopeId, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var sessionScope = authorization.getScope(SessionScope.class);
      return runtimeService.klabService().getConfiguration(scopeId, sessionScope);
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @Operation(
      operationId = ServicesAPI.RUNTIME.DIGITAL_TWIN,
      summary =
          ServicesAPI.RUNTIME.DIGITAL_TWIN
              + " - Connect to or create a digital twin and respond with its description for a client or a connected scope",
      description =
          "Retrieves the graph representation of a digital twin. If the user has the rights, the digital twin can also be created if not existent.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Digital twin graph retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Digital twin not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @GetMapping(value = ServicesAPI.RUNTIME.DIGITAL_TWIN, produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody GraphModel.DigitalTwin getDigitalTwin(
      Principal principal,
      @Parameter(description = "Digital twin ID") @PathVariable(name = "id") String id,
      @Parameter(description = "Persistence") @RequestParam(name = "persistence", required = false)
          Persistence persistence,
      @Parameter(description = "Access rights") @RequestParam(name = "rights", required = false)
          String rights,
      @Parameter(description = "URN of focus point in graph")
          @RequestParam(name = "focus", required = false)
          String focus,
      @Parameter(description = "Graph depth") @RequestParam(name = "depth", required = false)
          int depth) {
    if (principal instanceof EngineAuthorization authorization) {
      var scope = authorization.getScope();
      // if the scope is a context or session
      DigitalTwinImpl digitalTwin = null;
      ServiceSessionScope sessionScope;
      if (scope instanceof ServiceContextScope contextScope && id.equals(contextScope.getId())) {
        if (contextScope.getDigitalTwin() instanceof DigitalTwinImpl digitalTwin1) {}
        digitalTwin = digitalTwin;
      } else {
        sessionScope = scope.getParentScope(Scope.Type.SESSION, ServiceSessionScope.class);
      }
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @GetMapping(value = ServicesAPI.RUNTIME.DIGITAL_TWIN, produces = MediaType.TEXT_HTML_VALUE)
  public void getDigitalTwinExplorer(Principal principal, @PathVariable(name = "id") String id) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(UserScope.class);
      // TODO launch a session with the scope's explorer
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @Operation(
      summary = "Visualize asset with default parameters",
      description = "Generates a visualization of an asset using default settings")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Visualization generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid visualization parameters"),
        @ApiResponse(responseCode = "404", description = "Asset not found")
      })
  @GetMapping(value = ServicesAPI.RUNTIME.VISUALIZE_ASSET)
  public void defaultVisualize(
      @Parameter(description = "Visualization method") @PathVariable(name = "method") String method,
      @Parameter(description = "Asset URN") @PathVariable(name = "urn") String urn,
      @Parameter(description = "Content type") @RequestHeader("Content-Type") MediaType contentType,
      HttpServletResponse response,
      Principal principal)
      throws IOException {

    // TODO find the object to visualize
    // TODO locate the adapter and validate the method w.r.t. the contentType

    response.setContentType(contentType.toString());
    try (OutputStream outputStream = response.getOutputStream()) {
      // TODO invoke the visualization method/class with default parameters, passing the stream to
      //  write on
      //      // Write your binary data
      //      byte[] imageData = getImageData(); // Your method to get binary data
      //      outputStream.write(imageData);
      outputStream.flush();
    }
  }

  @Operation(
      summary = "Visualize asset with custom parameters",
      description = "Generates a visualization of an asset using custom parameters")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Visualization generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid visualization parameters"),
        @ApiResponse(responseCode = "404", description = "Asset not found")
      })
  @PostMapping(value = ServicesAPI.RUNTIME.VISUALIZE_ASSET)
  public void visualize(
      @Parameter(description = "Visualization parameters") @RequestBody
          VisualizationRequest visualizationRequest,
      @Parameter(description = "Visualization method") @PathVariable(name = "method") String method,
      @Parameter(description = "Asset URN") @PathVariable(name = "urn") String urn,
      @Parameter(description = "Content type") @RequestHeader("Content-Type") MediaType contentType,
      HttpServletResponse response,
      Principal principal)
      throws IOException {

    // TODO find the object to visualize
    // TODO locate the adapter and validate the method w.r.t. the contentType

    response.setContentType(contentType.toString());
    try (OutputStream outputStream = response.getOutputStream()) {
      // TODO invoke the visualization method/class, passing the stream to write on
      //      // Write your binary data
      //      byte[] imageData = getImageData(); // Your method to get binary data
      //      outputStream.write(imageData);
      outputStream.flush();
    }
  }

  @Operation(
      summary = "Query knowledge graph",
      description = "Executes a query against the knowledge graph")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Query executed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid query"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @PostMapping(ServicesAPI.RUNTIME.QUERY)
  public @ResponseBody List<? extends RuntimeAsset> queryKnowledgeGraph(
      @Parameter(description = "Knowledge graph query") @RequestBody KnowledgeGraphQuery<?> query,
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      // TODO we may want to cache other RuntimeAssets too
      if (query.getId() != Observation.UNASSIGNED_ID
          && contextScope instanceof ServiceContextScope serviceContextScope
          && query.getResultType() == KnowledgeGraphQuery.AssetType.OBSERVATION) {
        var ret = serviceContextScope.getObservation(query.getId());
        return ret == null ? List.of() : List.of(ret);
      }
      return runtimeService.klabService().queryKnowledgeGraph(query, contextScope);
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @GetMapping(ServicesAPI.RUNTIME.RETRIEVE_KNOWLEDGE_GRAPH_ASSET)
  public @ResponseBody RuntimeAsset retrieveAsset(
      @PathVariable(name = "id") long id, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      return contextScope
          .getDigitalTwin()
          .getKnowledgeGraph()
          .getAsset(id, contextScope, RuntimeAsset.class);
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @GetMapping(ServicesAPI.RUNTIME.RETRIEVE_KNOWLEDGE_GRAPH_LINKS)
  public @ResponseBody Collection<KnowledgeGraph.LinkInfo> retrieveLinks(
      @RequestParam(name = "sourceId") long sourceId,
      @RequestParam(name = "direction") GraphModel.Relationship.Direction direction,
      @RequestParam(name = "types", required = false) String relationshipTypes,
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      var sourceAsset =
          contextScope
              .getDigitalTwin()
              .getKnowledgeGraph()
              .getAsset(sourceId, contextScope, RuntimeAsset.class);
      if (sourceAsset == null) {
        return List.of();
      }
      // TODO this should be more general but not in the API
      if (contextScope.getDigitalTwin().getKnowledgeGraph() instanceof KnowledgeGraphNeo4j kg) {
        List<GraphModel.Relationship> types = List.of();
        if (relationshipTypes != null) {
          types = Utils.Data.parseList(relationshipTypes, GraphModel.Relationship.class);
        }
        return kg.getLinkInfo(
            sourceAsset, direction, contextScope, types.toArray(GraphModel.Relationship[]::new));
      }
    }
    throw new KlabInternalErrorException("Unexpected implementation of scope or knowledge graph");
  }

  @Operation(
      summary = "Resolve contextualizers",
      description = "Resolves a list of contextualizable objects")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Contextualizers resolved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid contextualizers"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @PostMapping(ServicesAPI.RUNTIME.RESOLVE_CONTEXTUALIZERS)
  public @ResponseBody ResourceSet resolveContextualizers(
      @Parameter(description = "List of contextualizable objects") @RequestBody
          List<Contextualizable> contextualizables,
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      return runtimeService.klabService().resolveContextualizables(contextualizables, contextScope);
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  /**
   * Create a session with the passed name. If a broker is available, also setup messaging and any
   * messaging queues requested with the call, defaulting as per implementation.
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
              runtimeService
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
        ret.setHostServiceId(runtimeService.klabService().serviceId());
        for (var service : userScope.getServices(KlabService.class)) {
          if (request.getServiceIds().contains(service.serviceId())) {
            ret.addService(service);
          }
        }

        KActorsBehavior behavior = null;
        if (request.getBehaviorUrn() != null) {
          // TODO resolve the behavior with all resources services
        }
        var id = runtimeService.klabService().declareSessionScope(ret, userScope, behavior);

        //        var federation = Klab.INSTANCE.getFederationData(userScope.getUser());
        //        if (federation != null) {
        //          if (queuesHeader == null) {
        //            queuesHeader = ret.defaultQueues();
        //          }
        //
        //          var implementedQueues = ret.setupMessaging(federation, id, queuesHeader);
        //
        //          Logging.INSTANCE.info(
        //              "Queues set up for session " + id + ": " + implementedQueues + " on session
        // scope");
        //
        //          if (!ret.initializeAgents(id)) {
        //            Logging.INSTANCE.warn("agent initialization failed in session creation");
        //          }
        //          response.setHeader(
        //              ServicesAPI.MESSAGING_QUEUES_HEADER, Utils.Strings.join(implementedQueues,
        // ", "));
        //        }
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
      HttpServletResponse response) {

    if (principal instanceof EngineAuthorization authorization) {

      var sessionScope = authorization.getScope(SessionScope.class);

      if (sessionScope != null) {
        // var userScope = authorization.getScope(UserScope.class);
        var userScope =
            runtimeService
                .klabService()
                .getScopeManager()
                .getScope(
                    authorization, UserScope.class, null, runtimeService.klabService().serviceId());
        var identity = (UserIdentity) userScope.getIdentity();
        var federation = Klab.INSTANCE.getFederationData(userScope.getUser());

        if (federation != null
            && !identity.getData().containsKey(UserIdentity.FEDERATION_DATA_PROPERTY)) {
          // TODO see comment in createSession. This shouldn't happen if we've gone through a
          // session, but
          //  a DT could also be created in other ways at production.
          identity.getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, federation);
        }

        if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

          var ret =
              new ServiceContextScope(
                  serviceSessionScope, request.getConfiguration(), userScope.getUser());

          if (!ret.getUser().getUsername().equals(identity.getUsername())) {
            ret = ret.withIdentity(identity);
          }

          for (var service : userScope.getServices(KlabService.class)) {
            if (request.getServiceIds().contains(service.serviceId())) {
              ret.addService(service);
            }
          }
          ret.setHostServiceId(runtimeService.klabService().serviceId());
          if (contextId != null) {
            ret.setId(contextId);
          }
          if (queuesHeader == null || queuesHeader.isEmpty()) {
            queuesHeader = ret.defaultQueues();
          }

          // this creates the DT and registers the scope
          var id = runtimeService.klabService().declareContextScope(ret, sessionScope, userScope);
          if (federation != null) {

            var implementedQueues = ret.setupMessaging(federation, id, queuesHeader);

            Logging.INSTANCE.info(
                "Queues set up for session " + id + ": " + implementedQueues + " on context scope");

            response.setHeader(
                ServicesAPI.MESSAGING_QUEUES_HEADER, Utils.Strings.join(implementedQueues, ", "));
          }

          if (!ret.initializeAgents(id)) {
            Logging.INSTANCE.warn("agent initialization failed in context creation");
          }

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

  @GetMapping(ServicesAPI.RUNTIME.GET_COMMIT_INFO)
  public @ResponseBody KnowledgeGraph.Commit retrieveCommit(
      @RequestParam(name = "id") String id, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      if (contextScope != null && contextScope.getDigitalTwin() instanceof DigitalTwinImpl dt) {
        return dt.getCommit(id);
      }
    }
    throw new KlabInternalErrorException("Request authorization doesn't carry a context scope");
  }
}
