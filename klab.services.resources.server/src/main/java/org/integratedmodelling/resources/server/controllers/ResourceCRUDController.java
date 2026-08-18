package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
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
import java.util.Map;

@RestController
@Tag(
    name = "Resources CRUD operations",
    description =
        "Endpoints for managing k.LAB resources, bridging to the info, submit, retrieve and delete endpoints of the Resources service")
@Secured(Role.USER)
public class ResourceCRUDController {

  @Autowired private ResourcesServer resourcesServer;

  @Operation(
      summary = "Retrieve asset",
      description = "Retrieve a k.LAB asset by its knowledge class and URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Asset retrieved successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "404", description = "Asset not found")
  })
  @GetMapping(ServicesAPI.RESOURCES.RETRIEVE)
  public <T extends KlabAsset> @ResponseBody T retrieve(
      @Parameter(description = "URN of the asset to retrieve") @PathVariable(name = "urn")
          String urn,
      @Parameter(description = "Knowledge class of the asset")
          @PathVariable(name = "knowledgeClass")
          KlabAsset.KnowledgeClass assetClass,
      Principal principal) {
    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope instanceof UserScope userScope) {
      return (T) resourcesServer.klabService().retrieve(urn, assetClass.getAssetClass(), userScope);
    }

    throw new KlabAuthorizationException("No valid scope in resource RETRIEVE request");
  }

  @Operation(
      summary = "List assets",
      description = "List all assets for the specified knowledge class")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Assets listed successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping(ServicesAPI.RESOURCES.LIST)
  public <T extends KlabAsset> List<T> list(
      @Parameter(description = "Knowledge class to list") @PathVariable(name = "knowledgeClass")
          KlabAsset.KnowledgeClass assetClass,
      Principal principal) {

    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope instanceof UserScope userScope) {
      return (List<T>) resourcesServer.klabService().list(assetClass.getAssetClass(), userScope);
    }

    throw new KlabAuthorizationException("No valid scope in resource LIST request");
  }

  @Operation(summary = "Delete asset", description = "Delete a k.LAB asset by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Asset deleted successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "404", description = "Asset not found")
  })
  @DeleteMapping(ServicesAPI.RESOURCES.DELETE)
  public List<ResourceSet> delete(
      @Parameter(description = "URN of the asset to delete") @PathVariable(name = "urn") String urn,
      @Parameter(description = "Knowledge class of the asset")
          @PathVariable(name = "knowledgeClass")
          KlabAsset.KnowledgeClass knowledgeClass,
      Principal principal) {

    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope instanceof UserScope userScope) {
      // TODO check authorization
      return resourcesServer.klabService().delete(urn, knowledgeClass, userScope);
    }

    return List.of(
        ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request")));
  }

  @Operation(summary = "Resolve asset", description = "Resolve a k.LAB asset by its URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Asset resolved successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "404", description = "Asset not found")
  })
  @GetMapping(ServicesAPI.RESOURCES.RESOLVE)
  public ResourceSet resolve(
      @Parameter(description = "URN of the asset to resolve") @PathVariable(name = "urn")
          String urn,
      @Parameter(description = "Knowledge class of the asset")
          @PathVariable(name = "knowledgeClass")
          KlabAsset.KnowledgeClass assetClass,
      Principal principal) {
    var scope =
        principal instanceof EngineAuthorization authorization ? authorization.getScope() : null;

    if (scope instanceof UserScope userScope) {
      // TODO check authorization
      return resourcesServer.klabService().resolve(urn, assetClass, userScope);
    }
    return ResourceSet.empty(Notification.error("No valid scope in resource SUBMIT request"));
  }

  @Operation(
      summary = "Submit asset",
      description =
          "Submit or update an asset definition for the specified knowledge class and submission mode")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Asset submitted successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid asset payload"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @PutMapping(ServicesAPI.RESOURCES.SUBMIT)
  public <T> List<ResourceSet> submit(
      @Parameter(description = "URN of the asset to submit") @PathVariable(name = "urn") String urn,
      @Parameter(description = "Knowledge class of the asset")
          @PathVariable(name = "knowledgeClass")
          KlabAsset.KnowledgeClass knowledgeClass,
      @Parameter(description = "Submission mode (add, update, replace)")
          @PathVariable(name = "submissionMode")
          ResourcesService.SubmissionMode submissionMode,
      @Parameter(description = "Serialized asset definition") @RequestBody String contents,
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
        case BEHAVIOR, COMPONENT, SCRIPT, TESTCASE, APPLICATION -> {
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

  @Operation(
      summary = "Get asset status",
      description = "Retrieve status information for a k.LAB asset by URN")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Asset status retrieved successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "404", description = "Asset not found")
  })
  @GetMapping(ServicesAPI.RESOURCES.STATUS)
  public Object status(
      @Parameter(description = "URN of the asset") String urn,
      @Parameter(description = "Knowledge class of the asset") KlabAsset.KnowledgeClass assetClass,
      Principal scope) {
    return null;
  }
}
