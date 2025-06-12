package org.integratedmodelling.klab.services.resolver.server.controllers;

import java.security.Principal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.resolver.server.ResolverServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Resolver API", description = "API for resolving observations and contextual requests")
public class ResolverController {

  @Autowired private ResolverServer resolverServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  /**
   * Resolve an observation request
   */
  @Operation(summary = "Resolve observation", 
            description = "Resolves an observation based on the provided resolution request")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Resolution job submitted successfully"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PostMapping(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION)
  public @ResponseBody long resolveObservation(
      @Parameter(description = "Resolution request parameters") @RequestBody ResolutionRequest resolutionRequest, 
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
}
