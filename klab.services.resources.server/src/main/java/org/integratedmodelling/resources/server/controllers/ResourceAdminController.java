package org.integratedmodelling.resources.server.controllers;

import org.integratedmodelling.common.services.client.resources.ProjectRequest;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.KlabService;
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

import java.io.File;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

@RestController
@Secured(Role.ADMINISTRATOR)
public class ResourceAdminController {

    @Autowired
    private ResourcesServer resourcesServer;

    @Autowired
    private ServiceAuthorizationManager authenticationManager;

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.IMPORT_PROJECT)
    public ResourceSet importProject(@RequestBody ProjectRequest request, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            if (admin.importProject(request.getWorkspaceName(), request.getProjectUrl(),
                    request.isOverwrite())) {
                return resourcesServer.klabService().project(request.getProjectName(), /* TODO use scope in
                 principal */ resourcesServer.klabService().serviceScope());
            }
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_PROJECT)
    public Project createProject(@RequestParam("workspaceName") String workspaceName, @RequestParam(
            "projectName") String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createProject(workspaceName, projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_PROJECT)
    public Project updateProject(@RequestParam("projectName") String projectName,
                                 @RequestBody Project.Manifest manifest,
                                 @RequestBody Metadata metadata, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.updateProject(projectName, manifest, metadata, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_NAMESPACE)
    public ResourceSet createNamespace(@RequestParam("projectName") String projectName,
                                       @RequestBody String namespaceContent, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.createNamespace(projectName, namespaceContent, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_NAMESPACE)
    public List<ResourceSet> updateNamespace(@RequestParam("projectName") String projectName,
                                             @RequestBody String namespaceContent, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.updateNamespace(projectName, namespaceContent, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_BEHAVIOR)
    public ResourceSet createBehavior(@RequestParam("projectName") String projectName,
                                      @RequestBody String behaviorContent, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.createBehavior(projectName, behaviorContent, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_BEHAVIOR)
    public List<ResourceSet> updateBehavior(@RequestParam("projectName") String projectName,
                                            @RequestBody String behaviorContent, Principal principal) {
        // TODO check if user has locked the project
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.updateBehavior(projectName, behaviorContent, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_ONTOLOGY)
    public ResourceSet createOntology(@RequestParam("projectName") String projectName,
                                      @RequestBody String ontologyContent, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.createOntology(projectName, ontologyContent, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_ONTOLOGY)
    public List<ResourceSet> updateOntology(@RequestParam("projectName") String projectName,
                                            @RequestBody String ontologyContent, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.updateOntology(projectName, ontologyContent, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.UPDATE_STRATEGIES)
    public List<ResourceSet> updateStrategies(@RequestParam("projectName") String projectName,
                                              @RequestBody String strategiesContent, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.updateObservationStrategies(projectName, strategiesContent, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.PUBLISH_PROJECT)
    public boolean publishProject(@RequestParam("projectUrl") String projectUrl,
                                  @RequestBody ResourcePrivileges permissions) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.publishProject(projectUrl, permissions);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UNPUBLISH_PROJECT)
    public boolean unpublishProject(@RequestParam("projectUrl") String projectUrl) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.unpublishProject(projectUrl);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_RESOURCE)
    public @ResponseBody ResourceSet createResource(@RequestBody Resource resource) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            var urn = admin.createResource(resource);
            return null; // TODO create ResourceSet
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_RESOURCE_FROM_PATH)
    public @ResponseBody ResourceSet createResourceFromPath(@RequestBody File resourcePath) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            var urn = admin.createResource(resourcePath);
            return null; // TODO create ResourceSet
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.CREATE_RESOURCE_FOR_PROJECT)
    public Resource createResourceForProject(@RequestParam("projectName") String projectName,
                                             @RequestParam("urnId") String urnId,
                                             @RequestParam("adapter") String adapter,
                                             @RequestBody Parameters<String> resourceData) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createResource(projectName, urnId, adapter, resourceData);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.PUBLISH_RESOURCE)
    public boolean publishResource(@RequestParam("resourceUrn") String resourceUrn,
                                   @RequestBody ResourcePrivileges permissions) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.publishResource(resourceUrn, permissions);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UNPUBLISH_RESOURCE)
    public boolean unpublishResource(@RequestParam("resourceUrn") String resourceUrn) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.unpublishResource(resourceUrn);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.REMOVE_PROJECT)
    public List<ResourceSet> removeProject(@RequestParam("projectName") String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.removeProject(projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.ADMIN.REMOVE_WORKSPACE)
    public List<ResourceSet> removeWorkspace(@RequestParam("workspaceName") String workspaceName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.removeWorkspace(workspaceName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping(value = ServicesAPI.RESOURCES.LIST_PROJECTS, produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody Collection<Project> listProjects() {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.listProjects();
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping(value = ServicesAPI.RESOURCES.LIST_RESOURCE_URNS, produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody Collection<String> listResourceUrns() {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.listResourceUrns();
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping(value = ServicesAPI.RESOURCES.ADMIN.LOCK_PROJECT)
    public URL lockProject(@PathVariable("urn") String urn, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.lockProject(urn, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping(value = ServicesAPI.RESOURCES.ADMIN.UNLOCK_PROJECT)
    public boolean unlockProject(@PathVariable("urn") String urn, Principal principal) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin && principal instanceof EngineAuthorization auth) {
            return admin.unlockProject(urn, auth.getToken());
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }


}
