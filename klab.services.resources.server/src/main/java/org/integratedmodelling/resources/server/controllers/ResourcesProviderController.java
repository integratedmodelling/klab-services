package org.integratedmodelling.resources.server.controllers;

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
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/resources")
public class ResourcesProviderController {

    @Autowired
    private ResourcesServer resourcesServer;

    @Autowired
    private ServiceAuthorizationManager authenticationManager;

    @GetMapping("/projects")
    public ResourceSet getProjects(@RequestParam Collection<String> projects, Principal principal) {
        return resourcesServer.klabService().projects(projects, authenticationManager.resolveScope(principal));
    }

    @GetMapping("/project/{projectName}")
    public ResourceSet getProject(@PathVariable String projectName, Principal principal) {
        return resourcesServer.klabService().project(projectName,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/model/{modelName}")
    public ResourceSet getModel(@PathVariable String modelName, Principal principal) {
        return resourcesServer.klabService().model(modelName, authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resolve/{urn}")
    public ResourceSet resolve(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolve(urn, authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resolveNamespace/{urn}")
    public KimNamespace resolveNamespace(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveNamespace(urn,
                authenticationManager.resolveScope(principal));
    }


    @GetMapping("/resolveOntology/{urn}")
    public KimOntology resolveOntology(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveOntology(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resolveObservationStrategyDocument/{urn}")
    public KimObservationStrategyDocument resolveObservationStrategyDocument(@PathVariable String urn,
                                                                             Principal principal) {
        return resourcesServer.klabService().resolveObservationStrategyDocument(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/listWorkspaces")
    public Collection<Workspace> listWorkspaces() {
        return resourcesServer.klabService().listWorkspaces();
    }

    @GetMapping("/resolveBehavior/{urn}")
    public KActorsBehavior resolveBehavior(@PathVariable String urn,
                                           Principal principal) {
        return resourcesServer.klabService().resolveBehavior(urn,
                authenticationManager.resolveScope(principal));
    }


    @GetMapping("/resolveResource/{urn}")
    public Resource resolveResource(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveResource(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resolveWorkspace/{urn}")
    public Workspace resolveWorkspace(@PathVariable String urn, Principal principal) {
        return resourcesServer.klabService().resolveWorkspace(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resolveServiceCall/{name}")
    public ResourceSet resolveServiceCall(@PathVariable String name,
                                          Principal principal) {
        return resourcesServer.klabService().resolveServiceCall(name,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resourceStatus/{urn}")
    public ResourceStatus resourceStatus(@PathVariable String urn,
                                         Principal principal) {
        return resourcesServer.klabService().resourceStatus(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resolveObservable")
    public KimObservable resolveObservable(@RequestParam String definition) {
        return resourcesServer.klabService().resolveObservable(definition);
    }

    @GetMapping("/describeConcept/{conceptUrn}")
    public KimConcept.Descriptor describeConcept(@PathVariable String conceptUrn) {
        return resourcesServer.klabService().describeConcept(conceptUrn);
    }

    @GetMapping("/resolveConcept/{definition}")
    public KimConcept resolveConcept(@PathVariable String definition) {
        return resourcesServer.klabService().resolveConcept(definition);
    }

    @PostMapping("/contextualizeResource")
    public Resource contextualizeResource(@RequestBody Resource originalResource, Principal principal) {
        return resourcesServer.klabService().contextualizeResource(originalResource,
                authenticationManager.resolveScope(principal, ContextScope.class));
    }

    @PostMapping("/contextualize")
    public KlabData contextualize(@RequestBody Resource contextualizedResource, Principal principal) {
        return resourcesServer.klabService().contextualize(contextualizedResource,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/resolveDataflow/{urn}")
    public KdlDataflow resolveDataflow(@PathVariable String urn,
                                       Principal principal) {
        return resourcesServer.klabService().resolveDataflow(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/getWorldview")
    public Worldview getWorldview() {
        return resourcesServer.klabService().getWorldview();
    }

    @GetMapping("/dependents/{namespaceId}")
    public List<KimNamespace> dependents(@PathVariable String namespaceId) {
        return resourcesServer.klabService().dependents(namespaceId);
    }

    @GetMapping("/precursors/{namespaceId}")
    public List<KimNamespace> precursors(@PathVariable String namespaceId) {
        return resourcesServer.klabService().precursors(namespaceId);
    }

    @GetMapping("/queryResources")
    public List<String> queryResources(@RequestParam String urnPattern,
                                       @RequestParam KlabAsset.KnowledgeClass... resourceTypes) {
        return resourcesServer.klabService().queryResources(urnPattern, resourceTypes);
    }

    @GetMapping("/resolveProject/{projectName}")
    public Project resolveProject(@PathVariable String projectName, Principal principal) {
        return resourcesServer.klabService().resolveProject(projectName,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping("/queryModels")
    public ResourceSet queryModels(@RequestParam Observable observable, Principal principal) {
        return resourcesServer.klabService().queryModels(observable,
                authenticationManager.resolveScope(principal, ContextScope.class));
    }

    @GetMapping("/modelGeometry/{modelUrn}")
    public Coverage modelGeometry(@PathVariable String modelUrn) {
        return resourcesServer.klabService().modelGeometry(modelUrn);
    }

    @GetMapping("/readBehavior")
    public KActorsBehavior readBehavior(@RequestParam URL url) {
        return resourcesServer.klabService().readBehavior(url);
    }

    @PostMapping("/importProject")
    public boolean importProject(@RequestParam String workspaceName, @RequestParam String projectUrl,
                                 @RequestParam boolean overwriteIfExisting) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.importProject(workspaceName, projectUrl, overwriteIfExisting);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/createProject")
    public Project createProject(@RequestParam String workspaceName, @RequestParam String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createProject(workspaceName, projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/updateProject")
    public Project updateProject(@RequestParam String projectName, @RequestBody Project.Manifest manifest,
                                 @RequestBody Metadata metadata) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.updateProject(projectName, manifest, metadata);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/createNamespace")
    public KimNamespace createNamespace(@RequestParam String projectName,
                                        @RequestBody String namespaceContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createNamespace(projectName, namespaceContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/updateNamespace")
    public void updateNamespace(@RequestParam String projectName, @RequestBody String namespaceContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateNamespace(projectName, namespaceContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/createBehavior")
    public KActorsBehavior createBehavior(@RequestParam String projectName,
                                          @RequestBody String behaviorContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createBehavior(projectName, behaviorContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/updateBehavior")
    public void updateBehavior(@RequestParam String projectName, @RequestBody String behaviorContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateBehavior(projectName, behaviorContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/createOntology")
    public KimOntology createOntology(@RequestParam String projectName, @RequestBody String ontologyContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createOntology(projectName, ontologyContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/updateOntology")
    public void updateOntology(@RequestParam String projectName, @RequestBody String ontologyContent) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.updateOntology(projectName, ontologyContent);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/publishProject")
    public boolean publishProject(@RequestParam String projectUrl,
                                  @RequestBody ResourcePrivileges permissions) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.publishProject(projectUrl, permissions);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/unpublishProject")
    public boolean unpublishProject(@RequestParam String projectUrl) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.unpublishProject(projectUrl);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/createResource")
    public String createResource(@RequestBody Resource resource) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createResource(resource);

        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/createResourceFromPath")
    public String createResourceFromPath(@RequestBody File resourcePath) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createResource(resourcePath);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/createResourceForProject")
    public Resource createResourceForProject(@RequestParam String projectName, @RequestParam String urnId,
                                             @RequestParam String adapter,
                                             @RequestParam Parameters<String> resourceData) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.createResource(projectName, urnId, adapter, resourceData);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/publishResource")
    public boolean publishResource(@RequestParam String resourceUrn,
                                   @RequestBody ResourcePrivileges permissions) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.publishResource(resourceUrn, permissions);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/unpublishResource")
    public boolean unpublishResource(@RequestParam String resourceUrn) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.unpublishResource(resourceUrn);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/removeProject")
    public void removeProject(@RequestParam String projectName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.removeProject(projectName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @PostMapping("/removeWorkspace")
    public void removeWorkspace(@RequestParam String workspaceName) {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            admin.removeWorkspace(workspaceName);
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping("/listProjects")
    public Collection<Project> listProjects() {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.listProjects();
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }

    @GetMapping("/listResourceUrns")
    public Collection<String> listResourceUrns() {
        if (resourcesServer.klabService() instanceof ResourcesService.Admin admin) {
            return admin.listResourceUrns();
        }
        throw new KlabInternalErrorException("Resources service is incapable of admin operation");
    }
}