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
    public ResourceSet getProject(@PathVariable("projectName") String projectName, Principal principal) {
        return resourcesServer.klabService().project(projectName,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.MODEL)
    public ResourceSet getModel(@PathVariable("modelName") String modelName, Principal principal) {
        return resourcesServer.klabService().model(modelName, authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_URN)
    public ResourceSet resolve(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolve(urn, authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_NAMESPACE_URN)
    public KimNamespace resolveNamespace(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveNamespace(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_ONTOLOGY_URN)
    public KimOntology resolveOntology(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveOntology(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_OBSERVATION_STRATEGY_DOCUMENT_URN)
    public KimObservationStrategyDocument resolveObservationStrategyDocument(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveObservationStrategyDocument(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.LIST_WORKSPACES)
    public Collection<Workspace> listWorkspaces() {
        return resourcesServer.klabService().listWorkspaces();
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_BEHAVIOR_URN)
    public KActorsBehavior resolveBehavior(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveBehavior(urn,
                authenticationManager.resolveScope(principal));
    }


    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_RESOURCE_URN)
    public Resource resolveResource(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveResource(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_WORKSPACE_URN)
    public Workspace resolveWorkspace(@PathVariable("urn") String urn, Principal principal) {
        var ret = resourcesServer.klabService().resolveWorkspace(urn,
                authenticationManager.resolveScope(principal));
        // TODO only leave a file:// URL in project metadata if the principal is a local user and an
        //  administrator
        return ret;
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_SERVICE_CALL)
    public ResourceSet resolveServiceCall(@PathVariable("name") String name, Principal principal) {
        return resourcesServer.klabService().resolveServiceCall(name,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOURCE_STATUS)
    public ResourceStatus resourceStatus(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resourceStatus(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_OBSERVABLE)
    public KimObservable resolveObservable(@RequestParam("definition") String definition) {
        return resourcesServer.klabService().resolveObservable(definition);
    }

    @GetMapping(ServicesAPI.RESOURCES.DESCRIBE_CONCEPT)
    public KimConcept.Descriptor describeConcept(@PathVariable("conceptUrn") String conceptUrn) {
        return resourcesServer.klabService().describeConcept(conceptUrn);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_CONCEPT)
    public KimConcept resolveConcept(@PathVariable("definition") String definition) {
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
    public KdlDataflow resolveDataflow(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveDataflow(urn,
                authenticationManager.resolveScope(principal));
    }

    @GetMapping(ServicesAPI.RESOURCES.GET_WORLDVIEW)
    public Worldview getWorldview() {
        return resourcesServer.klabService().getWorldview();
    }

    @GetMapping(ServicesAPI.RESOURCES.DEPENDENTS)
    public List<KimNamespace> dependents(@PathVariable("namespaceId") String namespaceId) {
        return resourcesServer.klabService().dependents(namespaceId);
    }

    @GetMapping(ServicesAPI.RESOURCES.PRECURSORS)
    public List<KimNamespace> precursors(@PathVariable("namespaceId") String namespaceId) {
        return resourcesServer.klabService().precursors(namespaceId);
    }

    @GetMapping(ServicesAPI.RESOURCES.QUERY_RESOURCES)
    public List<String> queryResources(@RequestParam("urnPattern") String urnPattern,
                                       @RequestParam("resourceTypes") KlabAsset.KnowledgeClass... resourceTypes) {
        return resourcesServer.klabService().queryResources(urnPattern, resourceTypes);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_PROJECT)
    public Project resolveProject(@PathVariable("projectName") String projectName, Principal principal) {
        return resourcesServer.klabService().resolveProject(projectName,
                authenticationManager.resolveScope(principal));
        // TODO only leave a file:// URL in project metadata if the principal is a local user and an
        //  administrator

    }

    @GetMapping(ServicesAPI.RESOURCES.QUERY_MODELS)
    public ResourceSet queryModels(@RequestParam("observable") Observable observable, Principal principal) {
        return resourcesServer.klabService().queryModels(observable,
                authenticationManager.resolveScope(principal, ContextScope.class));
    }

    @GetMapping(ServicesAPI.RESOURCES.MODEL_GEOMETRY)
    public Coverage modelGeometry(@PathVariable("modelUrn") String modelUrn) {
        return resourcesServer.klabService().modelGeometry(modelUrn);
    }

    @GetMapping(ServicesAPI.RESOURCES.READ_BEHAVIOR)
    public KActorsBehavior readBehavior(@RequestParam("url") URL url) {
        return resourcesServer.klabService().readBehavior(url);
    }

}