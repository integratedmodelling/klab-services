package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@Tag(
    name = "Resources CRUD operations",
    description =
        "Endpoints for managing k.LAB resources, bridging to the info, submit, retrieve and delete endpoints of the Resources service")
@Secured(Role.USER)
public class ResourceCRUDController {

  @Autowired private ResourcesServer resourcesServer;

  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE)
  public <T extends KlabAsset> T retrieve(
      String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal scope) {
    return null;
  }

  @GetMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> list(
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass, Principal scope) {
    return List.of();
  }

  @PostMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> query(
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass, Principal scope) {
    return List.of();
  }

  @DeleteMapping(ServicesAPI.RESOURCES.DELETE)
  public List<ResourceSet> delete(
      String urn, KlabAsset.KnowledgeClass knowledgeClass, Principal principal) {

    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope == null) {
      // TODO check authorization
      return List.of(
          ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request")));
    }

    switch (knowledgeClass) {
      case RESOURCE -> {}
      case NAMESPACE -> {}
      case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION -> {}
      case ONTOLOGY -> {}
      case OBSERVATION_STRATEGY_DOCUMENT -> {}
      case COMPONENT -> {}
      case PROJECT -> {}
      case WORKSPACE -> {}
      default -> {}
    }

    return List.of(
        ResourceSet.empty(
            Notification.error("Cannot delete URN " + urn + " of type " + knowledgeClass)));
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOLVE)
  public ResourceSet resolve(
      @PathVariable(name = "urn") String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal principal) {

    return null;
  }

  @PutMapping(ServicesAPI.RESOURCES.SUBMIT)
  public <T> List<ResourceSet> submit(
      @PathVariable(name = "urn") String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass knowledgeClass,
      @PathVariable(name = "submissionMode") ResourcesService.SubmissionMode submissionMode,
      @RequestBody String contents,
      Principal principal) {

    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope == null) {
      // TODO check authorization
      return List.of(
          ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request")));
    }

    switch (knowledgeClass) {
      case RESOURCE -> {}
      case NAMESPACE -> {}
      case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION -> {}
      case ONTOLOGY -> {}
      case OBSERVATION_STRATEGY_DOCUMENT -> {}
      case COMPONENT -> {}
      case PROJECT -> {}
      case WORKSPACE -> {}
      default -> {}
    }

    return List.of(
            ResourceSet.empty(
                    Notification.error("Cannot delete URN " + urn + " of type " + knowledgeClass)));
  }

  @GetMapping(ServicesAPI.RESOURCES.STATUS)
  public Object status(String urn, KlabAsset.KnowledgeClass assetClass, Principal scope) {
    return null;
  }
}
