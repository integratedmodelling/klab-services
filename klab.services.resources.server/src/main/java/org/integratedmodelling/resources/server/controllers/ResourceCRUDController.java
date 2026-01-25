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
import org.integratedmodelling.klab.api.scope.UserScope;
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

    if (scope instanceof UserScope userScope) {
      return (T) resourcesServer.klabService().retrieve(urn, assetClass.getAssetClass(), userScope);
    }

    throw new KlabAuthorizationException("No valid scope in resource RETRIEVE request");
  }

  @GetMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> list(
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal principal) {

    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope instanceof UserScope userScope) {
      return (List<T>) resourcesServer.klabService().list(assetClass.getAssetClass(), userScope);
    }

    throw new KlabAuthorizationException("No valid scope in resource LIST request");
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

    if (scope instanceof UserScope userScope) {
      // TODO check authorization
      return resourcesServer.klabService().delete(urn, knowledgeClass, userScope);
    }

    return List.of(
        ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request")));
  }

  @GetMapping(ServicesAPI.RESOURCES.RESOLVE)
  public ResourceSet resolve(
      @PathVariable(name = "urn") String urn,
      @PathVariable(name = "knowledgeClass") KlabAsset.KnowledgeClass assetClass,
      Principal principal) {
    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope instanceof UserScope userScope) {
      // TODO check authorization
      return resourcesServer.klabService().resolve(urn, assetClass, userScope);
    }
    return ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request"));
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

    if (scope instanceof UserScope userScope) {

      switch (knowledgeClass) {
        case RESOURCE -> {
          var resource = Utils.Json.parseObject(contents, ResourceImpl.class);
          return resourcesServer.klabService().submit(resource, submissionMode, userScope);
        }
        case NAMESPACE -> {
          var namespace = Utils.Json.parseObject(contents, KimNamespaceImpl.class);
          return resourcesServer.klabService().submit(namespace, submissionMode, userScope);
        }
        case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION -> {
          var behavior = Utils.Json.parseObject(contents, KActorsBehaviorImpl.class);
          return resourcesServer.klabService().submit(behavior, submissionMode, userScope);
        }
        case ONTOLOGY -> {
          var ontology = Utils.Json.parseObject(contents, KimOntologyImpl.class);
          return resourcesServer.klabService().submit(ontology, submissionMode, userScope);
        }
        case OBSERVATION_STRATEGY_DOCUMENT -> {
          var strategies = Utils.Json.parseObject(contents, KimObservationStrategiesImpl.class);
          return resourcesServer.klabService().submit(strategies, submissionMode, userScope);
        }
        case PROJECT -> {
          var project = Utils.Json.parseObject(contents, ProjectImpl.class);
          return resourcesServer.klabService().submit(project, submissionMode, userScope);
        }
        case WORKSPACE -> {
          var workspace = Utils.Json.parseObject(contents, WorkspaceImpl.class);
          return resourcesServer.klabService().submit(workspace, submissionMode, userScope);
        }
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
