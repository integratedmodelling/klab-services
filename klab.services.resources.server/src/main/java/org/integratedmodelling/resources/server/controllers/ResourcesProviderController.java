package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

@RestController
@Secured(Role.USER)
@Tag(name = "Resources service core API")
public class ResourcesProviderController {

    @Autowired
    private ResourcesServer resourcesServer;

    @Autowired
    private ServiceAuthorizationManager authenticationManager;

    /**
     * Retrieve all the knowledge included in one or more projects. The return set contains all needed
     * documnents with their versions, in order of dependency.
     *
     * @param projects
     * @param principal
     * @return the resources to load to ingest the knowledge included in the requested projects
     */
    @GetMapping(ServicesAPI.RESOURCES.PROJECTS)
    public @ResponseBody List<ResourceSet> getProjects(@RequestParam Collection<String> projects,
                                                       Principal principal) {
        return resourcesServer.klabService().projects(projects,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.PROJECT)
    public @ResponseBody Project getProject(@PathVariable("projectName") String projectName,
                                            Principal principal) {
        return resourcesServer.klabService().resolveProject(projectName,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.MODEL)
    public @ResponseBody ResourceSet getModel(@PathVariable("modelName") String modelName,
                                              Principal principal) {
        return resourcesServer.klabService().model(modelName,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_URN)
    public @ResponseBody ResourceSet resolve(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolve(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_NAMESPACE_URN)
    public @ResponseBody KimNamespace resolveNamespace(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveNamespace(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_ONTOLOGY_URN)
    public @ResponseBody KimOntology resolveOntology(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveOntology(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_OBSERVATION_STRATEGY_DOCUMENT_URN)
    public @ResponseBody KimObservationStrategyDocument resolveObservationStrategyDocument(@PathVariable(
            "urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveObservationStrategyDocument(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.LIST_WORKSPACES)
    public @ResponseBody Collection<Workspace> listWorkspaces() {
        return resourcesServer.klabService().listWorkspaces();
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_BEHAVIOR_URN)
    public @ResponseBody KActorsBehavior resolveBehavior(@PathVariable("urn") String urn,
                                                         Principal principal) {
        return resourcesServer.klabService().resolveBehavior(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }


    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_RESOURCE_URN)
    public @ResponseBody Resource resolveResource(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveResource(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_WORKSPACE_URN)
    public @ResponseBody Workspace resolveWorkspace(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveWorkspace(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_SERVICE_CALL)
    public @ResponseBody ResourceSet resolveServiceCall(@PathVariable("name") String name,
                                                        Principal principal) {
        return resourcesServer.klabService().resolveServiceCall(name,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOURCE_STATUS)
    public @ResponseBody ResourceStatus resourceStatus(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resourceStatus(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_OBSERVABLE)
    public @ResponseBody KimObservable resolveObservable(@RequestParam("definition") String definition) {
        return resourcesServer.klabService().resolveObservable(definition);
    }

    @GetMapping(ServicesAPI.RESOURCES.DESCRIBE_CONCEPT)
    public @ResponseBody KimConcept.Descriptor describeConcept(@PathVariable("conceptUrn") String conceptUrn) {
        return resourcesServer.klabService().describeConcept(conceptUrn);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_CONCEPT)
    public @ResponseBody KimConcept resolveConcept(@PathVariable("definition") String definition) {
        return resourcesServer.klabService().resolveConcept(definition);
    }

    @PostMapping(ServicesAPI.RESOURCES.CONTEXTUALIZE_RESOURCE)
    public @ResponseBody Resource contextualizeResource(@RequestBody Resource originalResource,
                                                        Principal principal) {
        return resourcesServer.klabService().contextualizeResource(originalResource,
                principal instanceof EngineAuthorization authorization ?
                authorization.getScope(ContextScope.class) : null);
    }

    @PostMapping(ServicesAPI.RESOURCES.CONTEXTUALIZE)
    public @ResponseBody KlabData contextualize(@RequestBody Resource contextualizedResource,
                                                Principal principal) {
        return resourcesServer.klabService().contextualize(contextualizedResource,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOLVE_DATAFLOW_URN)
    public @ResponseBody KdlDataflow resolveDataflow(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().resolveDataflow(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.GET_WORLDVIEW)
    public @ResponseBody Worldview getWorldview() {
        return resourcesServer.klabService().getWorldview();
    }

    @GetMapping(ServicesAPI.RESOURCES.DEPENDENTS)
    public @ResponseBody List<KimNamespace> dependents(@PathVariable("namespaceId") String namespaceId) {
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
    public @ResponseBody Project resolveProject(@PathVariable("projectName") String projectName,
                                                Principal principal) {
        return resourcesServer.klabService().resolveProject(projectName,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    // FIXME use POST and a ResolutionRequest
    @PostMapping(ServicesAPI.RESOURCES.QUERY_MODELS)
    public @ResponseBody ResourceSet queryModels(@RequestBody ResolutionRequest request,
                                                 Principal principal) {
        return resourcesServer.klabService().queryModels(request.getObservable(),
                principal instanceof EngineAuthorization authorization ?
                authorization.getScope(ContextScope.class)
                             .withResolutionConstraints(request.getResolutionConstraints().toArray(new ResolutionConstraint[0])) : null);
    }

    @GetMapping(ServicesAPI.RESOURCES.MODEL_GEOMETRY)
    public @ResponseBody Coverage modelGeometry(@PathVariable("modelUrn") String modelUrn) {
        return resourcesServer.klabService().modelGeometry(modelUrn);
    }

    @GetMapping(ServicesAPI.RESOURCES.READ_BEHAVIOR)
    public @ResponseBody KActorsBehavior readBehavior(@RequestParam("url") URL url) {
        return resourcesServer.klabService().readBehavior(url);
    }

    @GetMapping(ServicesAPI.RESOURCES.RESOURCE_RIGHTS)
    public ResourcePrivileges getResourceRights(@PathVariable("urn") String urn, Principal principal) {
        return resourcesServer.klabService().getRights(urn,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

    @PutMapping(ServicesAPI.RESOURCES.RESOURCE_RIGHTS)
    public boolean setResourceRights(@PathVariable("urn") String urn,
                                     @RequestBody ResourcePrivileges resourcePrivileges,
                                     Principal principal) {
        return resourcesServer.klabService().setRights(urn, resourcePrivileges,
                principal instanceof EngineAuthorization authorization ? authorization.getScope() : null);
    }

}