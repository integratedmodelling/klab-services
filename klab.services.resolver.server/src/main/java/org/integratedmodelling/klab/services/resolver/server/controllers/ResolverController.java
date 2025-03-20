package org.integratedmodelling.klab.services.resolver.server.controllers;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.services.resolver.server.ResolverServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@RestController
public class ResolverController {

  @Autowired private ResolverServer resolverServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  @Async
  @PostMapping(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION)
  public @ResponseBody Future<Dataflow> resolveObservation(
      @RequestBody ResolutionRequest resolutionRequest, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope =
          authorization
              .getScope(ContextScope.class)
              .withResolutionConstraints(
                  resolutionRequest
                      .getResolutionConstraints()
                      .toArray(new ResolutionConstraint[0]));
      return resolverServer.klabService().resolve(resolutionRequest.getObservation(), contextScope);
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }
}
