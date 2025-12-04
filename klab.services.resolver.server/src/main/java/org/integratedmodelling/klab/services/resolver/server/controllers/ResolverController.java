package org.integratedmodelling.klab.services.resolver.server.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.resolver.server.ResolverServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Resolver API", description = "API for resolving observations and contextual requests")
public class ResolverController {

  @Autowired private ResolverServer resolverServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  /** Resolve an observation request */
  @Operation(
      summary = "Resolve observation",
      description = "Resolves an observation based on the provided resolution request")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Resolution job submitted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION)
  public @ResponseBody long resolveObservation(
      @Parameter(description = "Resolution request parameters") @RequestBody
          ResolutionRequest resolutionRequest,
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope =
          authorization
              .getScope(ContextScope.class)
              .withResolutionConstraints(
                  resolutionRequest
                      .getResolutionConstraints()
                      .toArray(new ResolutionConstraint[0]));
      if (contextScope instanceof ServiceContextScope serviceContextScope) {
        var job =
            resolverServer.klabService().resolve(resolutionRequest.getObservation(), contextScope);
        return serviceContextScope
            .getJobManager()
            .submit(job, "Resolution of " + resolutionRequest.getObservation());
      }
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @PostMapping(ServicesAPI.RESOLVER.SUBMIT_RESOURCE)
  public @ResponseBody Resource submitResource(
      @RequestBody Observation observation, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      if (contextScope instanceof ServiceContextScope serviceContextScope) {
        return resolverServer.klabService().submitResource(observation, contextScope);
      }
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @GetMapping(ServicesAPI.RESOLVER.GET_SUBMITTED_RESOURCES)
  public @ResponseBody List<Resource> submitResource(Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      if (contextScope instanceof ServiceContextScope serviceContextScope) {
        return resolverServer.klabService().getSubmittedResources(contextScope);
      }
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }
}
