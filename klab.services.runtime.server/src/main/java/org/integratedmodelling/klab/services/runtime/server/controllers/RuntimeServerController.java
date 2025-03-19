package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.objects.AssetRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.mapdb.elsa.ElsaSerializerBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@Secured(Role.USER)
public class RuntimeServerController {

  @Autowired private RuntimeServer runtimeService;

  /**
   * Observations are set into the digital twin by the context after creating them in an unresolved
   * state. The return long ID is the handle to the resolution; according to the messaging protocol,
   * the observation tasks should monitor resolution until completion.
   *
   * @return
   */
  @Async
  @PostMapping(ServicesAPI.RUNTIME.SUBMIT_OBSERVATION)
  public @ResponseBody Future<Observation> submit(
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
        return runtimeService.klabService().submit(resolutionRequest.getObservation(), scope);
      }
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @GetMapping(ServicesAPI.RUNTIME.GET_SESSION_INFO)
  public @ResponseBody List<SessionInfo> getSessionInfo(Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      return runtimeService.klabService().getSessionInfo(authorization.getScope());
    }
    return List.of();
  }

  @PostMapping(ServicesAPI.RUNTIME.QUERY)
  public @ResponseBody List<? extends RuntimeAsset> queryKnowledgeGraph(
      @RequestBody KnowledgeGraphQuery<?> query, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      // TODO we may want to cache other RuntimeAssets too
      if (query.getId() != Observation.UNASSIGNED_ID
          && contextScope instanceof ServiceContextScope serviceContextScope
          && query.getResultType() == KnowledgeGraphQuery.AssetType.OBSERVATION) {
        var ret = serviceContextScope.getObservation(query.getId());
        if (ret != null) {
          return List.of(ret);
        }
      }
      return runtimeService.klabService().queryKnowledgeGraph(query, contextScope);
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  @Async
  @PostMapping(ServicesAPI.RUNTIME.RESOLVE_CONTEXTUALIZERS)
  public @ResponseBody Future<ResourceSet> resolveContextualizers(
      @RequestBody List<Contextualizable> contextualizables, Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope = authorization.getScope(ContextScope.class);
      return CompletableFuture.supplyAsync(
          () ->
              runtimeService
                  .klabService()
                  .resolveContextualizables(contextualizables, contextScope));
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }
}
