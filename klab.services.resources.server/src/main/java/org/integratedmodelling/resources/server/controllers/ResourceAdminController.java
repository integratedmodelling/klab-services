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
                 principal */ resourcesServer.klabService().scope());
            }
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_PROJECT)
    public Project createProject(@RequestParam String workspaceName, @RequestParam String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createProject(workspaceName, projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_PROJECT)
    public Project updateProject(@RequestParam String projectName, @RequestBody Project.Manifest manifest,
                                 @RequestBody Metadata metadata) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.updateProject(projectName, manifest, metadata);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_NAMESPACE)
    public KimNamespace createNamespace(@RequestParam String projectName,
                                        @RequestBody String namespaceContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createNamespace(projectName, namespaceContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_NAMESPACE)
    public void updateNamespace(@RequestParam String projectName, @RequestBody String namespaceContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateNamespace(projectName, namespaceContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_BEHAVIOR)
    public KActorsBehavior createBehavior(@RequestParam String projectName,
                                          @RequestBody String behaviorContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createBehavior(projectName, behaviorContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_BEHAVIOR)
    public void updateBehavior(@RequestParam String projectName, @RequestBody String behaviorContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateBehavior(projectName, behaviorContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.CREATE_ONTOLOGY)
    public KimOntology createOntology(@RequestParam String projectName, @RequestBody String ontologyContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createOntology(projectName, ontologyContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UPDATE_ONTOLOGY)
    public void updateOntology(@RequestParam String projectName, @RequestBody String ontologyContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateOntology(projectName, ontologyContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.PUBLISH_PROJECT)
    public boolean publishProject(@RequestParam String projectUrl,
                                  @RequestBody ResourcePrivileges permissions) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.publishProject(projectUrl, permissions);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UNPUBLISH_PROJECT)
    public boolean unpublishProject(@RequestParam String projectUrl) {
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
    public Resource createResourceForProject(@RequestParam String projectName, @RequestParam String urnId,
                                             @RequestParam String adapter,
                                             @RequestParam Parameters<String> resourceData) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createResource(projectName, urnId, adapter, resourceData);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.PUBLISH_RESOURCE)
    public boolean publishResource(@RequestParam String resourceUrn,
                                   @RequestBody ResourcePrivileges permissions) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.publishResource(resourceUrn, permissions);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.UNPUBLISH_RESOURCE)
    public boolean unpublishResource(@RequestParam String resourceUrn) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.unpublishResource(resourceUrn);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.REMOVE_PROJECT)
    public void removeProject(@RequestParam String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.removeProject(projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping(ServicesAPI.RESOURCES.REMOVE_WORKSPACE)
    public void removeWorkspace(@RequestParam String workspaceName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.removeWorkspace(workspaceName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping(ServicesAPI.RESOURCES.LIST_PROJECTS)
    public Collection<Project> listProjects() {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.listProjects();
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping(ServicesAPI.RESOURCES.LIST_RESOURCE_URNS)
    public Collection<String> listResourceUrns() {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.listResourceUrns();
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }


}
