package org.integratedmodelling.klab.services.runtime.server.controllers;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.VisualizationRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
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
  @GetMapping(ServicesAPI.RUNTIME.GET_SESSION_INFO)
  public @ResponseBody List<SessionInfo> getSessionInfo(Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      return runtimeService.klabService().getSessionInfo(authorization.getScope());
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
  @GetMapping(
      value = ServicesAPI.RUNTIME.DIGITAL_TWIN,
      produces = MediaType.APPLICATION_JSON_VALUE)
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
        if (contextScope.getDigitalTwin()  instanceof DigitalTwinImpl digitalTwin1) {}
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
      var ret =
          runtimeService.klabService().resolveContextualizables(contextualizables, contextScope);
      return ret;
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }
}
