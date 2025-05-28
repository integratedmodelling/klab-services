package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.FileUtils;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.resources.ProjectRequest;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

@RestController
@Secured(Role.ADMINISTRATOR)
@Tag(name = "Resources service administration API")
public class ResourceAdminController {

  @Autowired private ResourcesServer resourcesServer;

  @Autowired private ServiceAuthorizationManager authenticationManager;

  @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_WORKSPACE)
  public @ResponseBody boolean createNewProject(
      @RequestBody Metadata metadata,
      @PathVariable("workspaceName") String workspaceName,
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

  @GetMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_PROJECT)
  public @ResponseBody ResourceSet createNewProject(
      @PathVariable("workspaceName") String workspaceName,
      @PathVariable("projectName") String projectName,
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

  @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_PROJECT)
  public @ResponseBody ResourceSet updateExistingProject(
      @PathVariable("projectName") String projectName,
      @RequestBody Project.Manifest manifest,
      @RequestBody Metadata metadata,
      Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      return admin.updateProject(projectName, manifest, metadata, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_DOCUMENT)
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

  //    @PostMapping(ServicesAPI.RESOURCES.ADMIN.IMPORT_RESOURCE)
  //    public @ResponseBody ResourceSet createResource(@RequestBody Resource resource, Principal
  // principal) {
  //        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
  //            var urn = admin.createResource(resource,
  //                    principal instanceof EngineAuthorization authorization ?
  //                    authorization.getScope(UserScope.class) : null);
  //            return null; // TODO create ResourceSet
  //        }
  //        throw new KlabInternalErrorException("Resources service is incapable of admin
  // operation");
  //    }
  //
  //    @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPLOAD_RESOURCE)
  //    public @ResponseBody ResourceSet createResourceFromPath(@RequestParam("file") MultipartFile
  // file,
  //                                                            Principal principal) {
  //
  //        ResourceSet ret = null;
  //
  //        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal
  // instanceof EngineAuthorization auth) {
  //            var scope = auth.getScope(UserScope.class);
  //            try {
  //                File tempDir = Files.createTempDirectory("klab").toFile();
  //                File resourcePath = new File(tempDir + File.separator +
  // file.getOriginalFilename());
  //                FileUtils.copyInputStreamToFile(file.getInputStream(), resourcePath);
  //                ret = admin.createResource(resourcePath, scope);
  //                tempDir.deleteOnExit();
  //            } catch (IOException e) {
  //                scope.error(e);
  //            }
  //
  //            return ret;
  //        }
  //        throw new KlabInternalErrorException("Resources service is incapable of admin
  // operation");
  //    }

  //    @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_RESOURCE)
  //    public Resource createResourceForProject(@RequestParam("projectName") String projectName,
  //                                             @RequestParam("urnId") String urnId,
  //                                             @RequestParam("adapter") String adapter,
  //                                             @RequestBody Parameters<String> resourceData,
  //                                             Principal principal) {
  //        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
  //            return admin.createResource(projectName, urnId, adapter, resourceData,
  //                    principal instanceof EngineAuthorization authorization ?
  //                    authorization.getScope(UserScope.class) : null);
  //        }
  //        throw new KlabInternalErrorException("Resources service is incapable of admin
  // operation");
  //    }

  @PostMapping(ServicesAPI.RESOURCES.ADMIN.REMOVE_PROJECT)
  public List<ResourceSet> removeProject(
      @RequestParam("projectName") String projectName, Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      admin.deleteProject(projectName, auth.getScope(UserScope.class));
    }
    throw new KlabInternalErrorException("Resources service is incapable of admin operation");
  }

  @PostMapping(ServicesAPI.RESOURCES.ADMIN.REMOVE_WORKSPACE)
  public List<ResourceSet> removeWorkspace(
      @RequestParam("workspaceName") String workspaceName, Principal principal) {
    if (resourcesServer.klabService() instanceof ResourcesService.Admin admin
        && principal instanceof EngineAuthorization auth) {
      admin.deleteWorkspace(workspaceName, auth.getScope(UserScope.class));
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
