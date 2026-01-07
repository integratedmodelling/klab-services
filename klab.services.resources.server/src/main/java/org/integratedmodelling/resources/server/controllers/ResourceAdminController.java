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
  @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_WORKSPACE)
  public @ResponseBody boolean createNewProject(
      @Parameter(description = "Workspace metadata") @RequestBody Metadata metadata,
      @Parameter(description = "Name of the workspace") @PathVariable("workspaceName")
          String workspaceName,
      Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
      return admin.createWorkspace(
          workspaceName,
          metadata,
          principal instanceof EngineAuthorization authorization
              ? authorization.getScope(UserScope.class)
              : null);
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
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
  @GetMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_PROJECT)
  public @ResponseBody ResourceSet createNewProject(
      @Parameter(description = "Name of the workspace") @PathVariable("workspaceName")
          String workspaceName,
      @Parameter(description = "Name of the project") @PathVariable("projectName")
          String projectName,
      Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
      return admin.createProject(
          workspaceName,
          projectName,
          principal instanceof EngineAuthorization authorization
              ? authorization.getScope(UserScope.class)
              : null);
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
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
  @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_PROJECT)
  public @ResponseBody ResourceSet updateExistingProject(
      @Parameter(description = "Name of the project") @PathVariable("projectName")
          String projectName,
      @Parameter(description = "Project manifest") @RequestBody Project.Manifest manifest,
      @Parameter(description = "Project metadata") @RequestBody Metadata metadata,
      Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.updateProject(projectName, manifest, metadata, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_DOCUMENT)
  public List<ResourceSet> createDocument(
      @PathVariable("projectName") String projectName,
      @PathVariable("documentType") ProjectStorage.ResourceType documentType,
      @PathVariable("urn") String urn,
      Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.createDocument(projectName, urn, documentType, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.ADMIN.REMOVE_DOCUMENT)
  public List<ResourceSet> createDocument(
      @PathVariable("projectName") String projectName,
      @PathVariable("urn") String urn,
      @PathVariable("documentType") ProjectStorage.ResourceType documentType,
      Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.deleteDocument(projectName, urn, documentType, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_DOCUMENT)
  public List<ResourceSet> updateOntology(
      @PathVariable("projectName") String projectName,
      @PathVariable("documentType") ProjectStorage.ResourceType documentType,
      @RequestBody String content,
      Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.updateDocument(
          projectName, documentType, content, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.ADMIN.REMOVE_PROJECT)
  public List<ResourceSet> removeProject(
      @PathVariable("urn") String projectName, Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.deleteProject(projectName, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(ServicesAPI.RESOURCES.ADMIN.REMOVE_WORKSPACE)
  public List<ResourceSet> removeWorkspace(
      @PathVariable("urn") String workspaceName, Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.deleteWorkspace(workspaceName, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(
      value = ServicesAPI.RESOURCES.LIST_PROJECTS,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody Collection<Project> listProjects(Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
      return admin.listProjects(
          principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(
      value = ServicesAPI.RESOURCES.LIST_RESOURCE_URNS,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody Collection<String> listResourceUrns(Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
      return admin.listResourceUrns(
          principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(value = ServicesAPI.RESOURCES.ADMIN.LOCK_PROJECT)
  public boolean lockProject(@PathVariable("urn") String urn, Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.lockProject(urn, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @GetMapping(value = ServicesAPI.RESOURCES.ADMIN.UNLOCK_PROJECT)
  public boolean unlockProject(@PathVariable("urn") String urn, Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.unlockProject(urn, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @PostMapping(value = ServicesAPI.RESOURCES.ADMIN.MANAGE_PROJECT)
  public List<ResourceSet> manageProject(
      @PathVariable("urn") String urn, @RequestBody ProjectRequest request, Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
      return admin.manageRepository(
          urn, request.getOperation(), request.getParameters().toArray(new String[] {}));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }
}
