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
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.security.Principal;
import java.util.Collection;

@RestController
@Secured(Role.ADMINISTRATOR)
public class ResourceAdminController {

    @Autowired
    private ResourcesServer resourcesServer;

    @Autowired
    private ServiceAuthorizationManager authenticationManager;

    @PostMapping(ServicesAPI.RESOURCES.IMPORT_PROJECT)
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

    @PostMapping(ServicesAPI.RESOURCES.CREATE_PROJECT)
    public Project createProject(@RequestParam("workspaceName") String workspaceName, @RequestParam("projectName") String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createProject(workspaceName, projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_PROJECT)
    public Project updateProject(@RequestParam("projectName") String projectName, @RequestBody Project.Manifest manifest,
                                 @RequestBody Metadata metadata) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.updateProject(projectName, manifest, metadata);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_NAMESPACE)
    public KimNamespace createNamespace(@RequestParam("projectName") String projectName,
                                        @RequestBody String namespaceContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createNamespace(projectName, namespaceContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_NAMESPACE)
    public void updateNamespace(@RequestParam("projectName") String projectName, @RequestBody String namespaceContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateNamespace(projectName, namespaceContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_BEHAVIOR)
    public KActorsBehavior createBehavior(@RequestParam("projectName") String projectName,
                                          @RequestBody String behaviorContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createBehavior(projectName, behaviorContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_BEHAVIOR)
    public void updateBehavior(@RequestParam("projectName") String projectName, @RequestBody String behaviorContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateBehavior(projectName, behaviorContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_ONTOLOGY)
    public KimOntology createOntology(@RequestParam("projectName") String projectName, @RequestBody String ontologyContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createOntology(projectName, ontologyContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_ONTOLOGY)
    public void updateOntology(@RequestParam("projectName") String projectName, @RequestBody String ontologyContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateOntology(projectName, ontologyContent);
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
    public String createResource(@RequestBody Resource resource) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createResource(resource);

        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_RESOURCE_FROM_PATH)
    public String createResourceFromPath(@RequestBody File resourcePath) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createResource(resourcePath);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_RESOURCE_FOR_PROJECT)
    public Resource createResourceForProject(@RequestParam("projectName") String projectName, @RequestParam("urnId") String urnId,
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

    @PostMapping(ServicesAPI.RESOURCES.REMOVE_PROJECT)
    public void removeProject(@RequestParam("projectName") String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.removeProject(projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.REMOVE_WORKSPACE)
    public void removeWorkspace(@RequestParam("workspaceName") String workspaceName) {
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


}
