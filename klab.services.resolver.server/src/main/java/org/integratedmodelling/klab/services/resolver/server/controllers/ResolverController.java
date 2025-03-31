package org.integratedmodelling.klab.services.resolver.server.controllers;

import java.security.Principal;
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
public class ResolverController {

  @Autowired private ResolverServer resolverServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  @PostMapping(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION)
  public @ResponseBody long resolveObservation(
      @RequestBody ResolutionRequest resolutionRequest, Principal principal) {
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
