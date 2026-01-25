package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
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
  public <T extends KlabAsset> @ResponseBody T retrieve(
      String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal principal) {
    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope == null) {
      throw new KlabAuthorizationException("No valid scope in resource RETRIEVE request");
    }

    return (T) resourcesServer.klabService().retrieve(urn, assetClass.getAssetClass(), scope);
  }

  @GetMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> list(
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal principal) {

    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    return (List<T>) resourcesServer.klabService().list(assetClass.getAssetClass(), scope);
  }

  @PostMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> query(
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass, Principal scope) {
    return List.of(); // resourcesServer.klabService().q(assetClass, scope);
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

    return resourcesServer.klabService().delete(urn, knowledgeClass, scope);
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOLVE)
  public ResourceSet resolve(
      @PathVariable(name = "urn") String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal principal) {
    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope == null) {
      // TODO check authorization
      return ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request"));
    }

    return resourcesServer.klabService().resolve(urn, assetClass, scope);
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
      // TODO check authorization too
      return List.of(
          ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request")));
    }

    switch (knowledgeClass) {
      case RESOURCE -> {
        var resource = Utils.Json.parseObject(contents, ResourceImpl.class);
        return resourcesServer.klabService().submit(resource, submissionMode, scope);
      }
      case NAMESPACE -> {
        var namespace = Utils.Json.parseObject(contents, KimNamespaceImpl.class);
        return resourcesServer.klabService().submit(namespace, submissionMode, scope);
      }
      case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION -> {
        var behavior = Utils.Json.parseObject(contents, KActorsBehaviorImpl.class);
        return resourcesServer.klabService().submit(behavior, submissionMode, scope);
      }
      case ONTOLOGY -> {
        var ontology = Utils.Json.parseObject(contents, KimOntologyImpl.class);
        return resourcesServer.klabService().submit(ontology, submissionMode, scope);
      }
      case OBSERVATION_STRATEGY_DOCUMENT -> {
        var strategies = Utils.Json.parseObject(contents, KimObservationStrategiesImpl.class);
        return resourcesServer.klabService().submit(strategies, submissionMode, scope);
      }
      case PROJECT -> {
        var project = Utils.Json.parseObject(contents, ProjectImpl.class);
        return resourcesServer.klabService().submit(project, submissionMode, scope);
      }
      case WORKSPACE -> {
        var workspace = Utils.Json.parseObject(contents, WorkspaceImpl.class);
        return resourcesServer.klabService().submit(workspace, submissionMode, scope);
      }
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
