package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import org.integratedmodelling.common.services.client.resources.ProjectRequest;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
@Secured(Role.ADMINISTRATOR)
@Tag(name = "Resources service administration API")
public class ResourceAdminController {

  @Autowired private ResourcesServer resourcesServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  /** Create a new workspace */
  @Operation(
      summary = "Create a new workspace",
      description = "Creates a new workspace with the specified name and metadata")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Workspace created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid workspace parameters"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires administrator role")
      })
  @PostMapping(ServicesAPI.RESOURCES.CREATE_WORKSPACE)
  public @ResponseBody boolean createNewProject(
      @Parameter(description = "Workspace metadata") @RequestBody Metadata metadata,
      @Parameter(description = "Name of the workspace") @PathVariable("workspaceName")
          String workspaceName,
      Principal principal) {
    return resourcesServer
        .klabService()
        .createWorkspace(
            workspaceName,
            metadata,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope(UserScope.class)
                : null);
  }

  /** Create a new project in a workspace */
  @Operation(
      summary = "Create a new project",
      description = "Creates a new project in the specified workspace")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Project created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid project parameters"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires administrator role"),
        @ApiResponse(responseCode = "404", description = "Workspace not found")
      })
  @GetMapping(ServicesAPI.RESOURCES.CREATE_PROJECT)
  public @ResponseBody ResourceSet createNewProject(
      @Parameter(description = "Name of the workspace") @PathVariable("workspaceName")
          String workspaceName,
      @Parameter(description = "Name of the project") @PathVariable("projectName")
          String projectName,
      Principal principal) {
    return resourcesServer
        .klabService()
        .createProject(
            workspaceName,
            projectName,
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope(UserScope.class)
                : null);
  }

  /** Update an existing project */
  @Operation(
      summary = "Update an existing project",
      description = "Updates the manifest and metadata of an existing project")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Project updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid project parameters"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires administrator role"),
        @ApiResponse(responseCode = "404", description = "Project not found")
      })
  @PostMapping(ServicesAPI.RESOURCES.UPDATE_PROJECT)
  public @ResponseBody ResourceSet updateExistingProject(
      @Parameter(description = "Name of the project") @PathVariable("projectName")
          String projectName,
      @Parameter(description = "Project manifest") @RequestBody Project.Manifest manifest,
      @Parameter(description = "Project metadata") @RequestBody Metadata metadata,
      Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer
          .klabService()
          .updateProject(projectName, manifest, metadata, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.CREATE_DOCUMENT)
  public List<ResourceSet> createDocument(
      @PathVariable("projectName") String projectName,
      @PathVariable("documentType") ProjectStorage.ResourceType documentType,
      @PathVariable("urn") String urn,
      Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer
          .klabService()
          .createDocument(projectName, urn, documentType, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.REMOVE_DOCUMENT)
  public List<ResourceSet> createDocument(
      @PathVariable("projectName") String projectName,
      @PathVariable("urn") String urn,
      @PathVariable("documentType") ProjectStorage.ResourceType documentType,
      Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer
          .klabService()
          .deleteDocument(projectName, urn, documentType, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @PostMapping(ServicesAPI.RESOURCES.UPDATE_DOCUMENT)
  public List<ResourceSet> updateOntology(
      @PathVariable("projectName") String projectName,
      @PathVariable("documentType") ProjectStorage.ResourceType documentType,
      @RequestBody String content,
      Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer
          .klabService()
          .updateDocument(projectName, documentType, content, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.REMOVE_PROJECT)
  public List<ResourceSet> removeProject(
      @PathVariable("urn") String projectName, Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer
          .klabService()
          .deleteProject(projectName, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.REMOVE_WORKSPACE)
  public List<ResourceSet> removeWorkspace(
      @PathVariable("urn") String workspaceName, Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer
          .klabService()
          .deleteWorkspace(workspaceName, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(
      value = ServicesAPI.RESOURCES.LIST_PROJECTS,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody Collection<Project> listProjects(Principal principal) {
    return resourcesServer
        .klabService()
        .listProjects(
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(
      value = ServicesAPI.RESOURCES.LIST_RESOURCE_URNS,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody Collection<String> listResourceUrns(Principal principal) {
    return resourcesServer
        .klabService()
        .listResourceUrns(
            principal instanceof EngineAuthorization authorization
                ? authorization.getScope()
                : null);
  }

  @GetMapping(value = ServicesAPI.RESOURCES.LOCK_PROJECT)
  public boolean lockProject(@PathVariable("urn") String urn, Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer.klabService().lockProject(urn, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(value = ServicesAPI.RESOURCES.UNLOCK_PROJECT)
  public boolean unlockProject(@PathVariable("urn") String urn, Principal principal) {
    if (principal instanceof EngineAuthorization auth) {
      return resourcesServer.klabService().unlockProject(urn, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @PostMapping(value = ServicesAPI.RESOURCES.MANAGE_PROJECT)
  public List<ResourceSet> manageProject(
      @PathVariable("urn") String urn, @RequestBody ProjectRequest request, Principal principal) {
    return resourcesServer
        .klabService()
        .manageRepository(
            urn, request.getOperation(), request.getParameters().toArray(new String[] {}));
  }
}
