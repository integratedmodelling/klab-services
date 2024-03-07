package org.integratedmodelling.resources.server.controllers;

import org.integratedmodelling.common.services.client.resources.ProjectRequest;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

@RestController
@Secured(Role.USER)
public class ResourcesProviderController {

    @Autowired
    private ResourcesServer resourcesServer;

    @Autowired
    private ServiceAuthorizationManager authenticationManager;

    @GetMapping(ServicesAPI.RESOURCES.PROJECTS)
    public ResourceSet getProjects(@RequestParam Collection<String> projects, Principal principal) {
        return resourcesServer.klabService().projects(projects,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.PROJECT)
    public ResourceSet getProject(@PathVariable String projectName, Principal principal) {
        return resourcesServer.klabService().project(projectName,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.MODEL)
    public ResourceSet getModel(@PathVariable String modelName, Principal principal) {
        return resourcesServer.klabService().model(modelName, authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_URN)
    public ResourceSet resolve(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolve(urn, authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_NAMESPACE_URN)
    public KimNamespace resolveNamespace(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveNamespace(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_ONTOLOGY_URN)
    public KimOntology resolveOntology(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveOntology(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_OBSERVATION_STRATEGY_DOCUMENT_URN)
    public KimObservationStrategyDocument resolveObservationStrategyDocument(@PathVariable String urn,
                                                                             Principal principal) {
        return resourcesServer.klabService().resolveObservationStrategyDocument(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.LIST_WORKSPACES)
    public Collection<Workspace> listWorkspaces() {
        return resourcesServer.klabService().listWorkspaces();
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_BEHAVIOR_URN)
    public KActorsBehavior resolveBehavior(@PathVariable String urn,
                                           Principal principal) {
        return resourcesServer.klabService().resolveBehavior(urn,
                authenticationManager.resolveScope(principal));
    }


    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_RESOURCE_URN)
    public Resource resolveResource(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveResource(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_WORKSPACE_URN)
    public Workspace resolveWorkspace(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveWorkspace(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_SERVICE_CALL)
    public ResourceSet resolveServiceCall(@PathVariable String name,
                                          Principal principal) {
        return resourcesServer.klabService().resolveServiceCall(name,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOURCE_STATUS)
    public ResourceStatus resourceStatus(@PathVariable String urn,
                                         Principal principal) {
        return resourcesServer.klabService().resourceStatus(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_OBSERVABLE)
    public KimObservable resolveObservable(@RequestParam String definition) {
        return resourcesServer.klabService().resolveObservable(definition);
    }

    @GetMapping(ServicesAPI.RESOURCES.DESCRIBE_CONCEPT)
    public KimConcept.Descriptor describeConcept(@PathVariable String conceptUrn) {
        return resourcesServer.klabService().describeConcept(conceptUrn);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_CONCEPT)
    public KimConcept resolveConcept(@PathVariable String definition) {
        return resourcesServer.klabService().resolveConcept(definition);
    }

    @PostMapping(ServicesAPI.RESOURCES.CONTEXTUALIZE_RESOURCE)
    public Resource contextualizeResource(@RequestBody Resource originalResource, Principal principal) {
        return resourcesServer.klabService().contextualizeResource(originalResource,
                authenticationManager.resolveScope(principal, ContextScope.class));
    }

    @PostMapping(ServicesAPI.RESOURCES.CONTEXTUALIZE)
    public KlabData contextualize(@RequestBody Resource contextualizedResource, Principal principal) {
        return resourcesServer.klabService().contextualize(contextualizedResource,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_DATAFLOW_URN)
    public KdlDataflow resolveDataflow(@PathVariable String urn,
                                       Principal principal) {
        return resourcesServer.klabService().resolveDataflow(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.GET_WORLDVIEW)
    public Worldview getWorldview() {
        return resourcesServer.klabService().getWorldview();
    }

    @GetMapping(ServicesAPI.RESOURCES.DEPENDENTS)
    public List<KimNamespace> dependents(@PathVariable String namespaceId) {
        return resourcesServer.klabService().dependents(namespaceId);
    }

    @GetMapping(ServicesAPI.RESOURCES.PRECURSORS)
    public List<KimNamespace> precursors(@PathVariable String namespaceId) {
        return resourcesServer.klabService().precursors(namespaceId);
    }

    @GetMapping(ServicesAPI.RESOURCES.QUERY_RESOURCES)
    public List<String> queryResources(@RequestParam String urnPattern,
                                       @RequestParam KlabAsset.KnowledgeClass... resourceTypes) {
        return resourcesServer.klabService().queryResources(urnPattern, resourceTypes);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_PROJECT)
    public Project resolveProject(@PathVariable String projectName, Principal principal) {
        return resourcesServer.klabService().resolveProject(projectName,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.QUERY_MODELS)
    public ResourceSet queryModels(@RequestParam Observable observable, Principal principal) {
        return resourcesServer.klabService().queryModels(observable,
                authenticationManager.resolveScope(principal, ContextScope.class));
    }

    @GetMapping(ServicesAPI.RESOURCES.MODEL_GEOMETRY)
    public Coverage modelGeometry(@PathVariable String modelUrn) {
        return resourcesServer.klabService().modelGeometry(modelUrn);
    }

    @GetMapping(ServicesAPI.RESOURCES.READ_BEHAVIOR)
    public KActorsBehavior readBehavior(@RequestParam URL url) {
        return resourcesServer.klabService().readBehavior(url);
    }

    @PostMapping(ServicesAPI.RESOURCES.IMPORT_PROJECT)
    public ResourceSet importProject(@RequestBody ProjectRequest request, Principal principal) {
        checkAdminPrivileges(principal);
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            if (admin.importProject(request.getWorkspaceName(), request.getProjectUrl(),
                    request.isOverwrite())) {
                return resourcesServer.klabService().project(request.getProjectName(), /* TODO use scope in
                 principal */ resourcesServer.klabService().scope());
            }
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    private void checkAdminPrivileges(Principal principal) {
        // TODO if principal isn't authorized, throw unauthorized exception
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