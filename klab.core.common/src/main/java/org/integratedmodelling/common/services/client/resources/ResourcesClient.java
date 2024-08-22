package org.integratedmodelling.common.services.client.resources;

import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

public class ResourcesClient extends ServiceClient implements ResourcesService, ResourcesService.Admin {

    private static final long serialVersionUID = 4305387731730961701L;

    public ResourcesClient() {
        super(Type.RESOURCES);
    }

    public ResourcesClient(URL url, Identity identity) {
        super(Type.RESOURCES, identity, List.of());
    }

    public ResourcesClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.RESOURCES, url, identity, services, listeners);
    }

    public ResourcesClient(URL url) {
        super(url);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, ResourcesCapabilitiesImpl.class);
    }

    @Override
    public List<ResourceSet> projects(Collection<String> projects, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet model(String modelName, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KimNamespace resolveNamespace(String urn, Scope scope) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_NAMESPACE_URN, KimNamespace.class, "urn", urn);
    }

    @Override
    public KimOntology resolveOntology(String urn, Scope scope) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_ONTOLOGY_URN, KimOntology.class, "urn", urn);
    }

    @Override
    public KimObservationStrategyDocument resolveObservationStrategyDocument(String urn, Scope scope) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_OBSERVATION_STRATEGY_DOCUMENT_URN,
                KimObservationStrategyDocument.class, "urn", urn);
    }

    @Override
    public Collection<Workspace> listWorkspaces() {
        return client.getCollection(ServicesAPI.RESOURCES.LIST_WORKSPACES, Workspace.class);
    }

    @Override
    public KActorsBehavior resolveBehavior(String urn, Scope scope) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_BEHAVIOR_URN, KActorsBehavior.class, "urn", urn);
    }

    @Override
    public Resource resolveResource(String urn, Scope scope) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_RESOURCE_URN, Resource.class, "urn", urn);
    }

    @Override
    public Workspace resolveWorkspace(String urn, Scope scope) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_WORKSPACE_URN, Workspace.class, "urn", urn);
    }

    @Override
    public ResourceSet resolveServiceCall(String name, Scope scope) {
        // TODO
        return null;
    }

    @Override
    public KimObservable resolveObservable(String definition) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_OBSERVABLE, KimObservable.class, "definition",
                definition);
    }

    @Override
    public KimConcept.Descriptor describeConcept(String conceptUrn) {
        return null;
    }

    @Override
    public KimConcept resolveConcept(String definition) {
        return client.get(ServicesAPI.RESOURCES.RESOLVE_CONCEPT, KimConcept.class, "definition", definition);
    }

    @Override
    public Resource contextualizeResource(Resource originalResource, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KlabData contextualize(Resource contextualizedResource, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KdlDataflow resolveDataflow(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Worldview getWorldview() {
        return client.get(ServicesAPI.RESOURCES.GET_WORLDVIEW, Worldview.class);
    }

    @Override
    public List<KimNamespace> dependents(String namespaceId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<KimNamespace> precursors(String namespaceId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet queryModels(Observable observable, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> queryResources(String urnPattern, KnowledgeClass... resourceTypes) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceStatus resourceStatus(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Project resolveProject(String projectName, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Coverage modelGeometry(String modelUrn) throws KlabIllegalArgumentException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KActorsBehavior readBehavior(URL url) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet resolve(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ResourceSet> importProject(String workspaceName, String projectUrl,
                                           boolean overwriteIfExisting,
                                           UserScope scope) {
        ProjectRequest request = new ProjectRequest();
        request.setWorkspaceName(workspaceName);
        request.setProjectUrl(projectUrl);
        request.setOverwrite(overwriteIfExisting);
        return client.postCollection(ServicesAPI.RESOURCES.ADMIN.IMPORT_PROJECT, request, ResourceSet.class);
    }

    @Override
    public ResourceSet createProject(String workspaceName, String projectName, UserScope scope) {
        return null;
    }

    @Override
    public ResourceSet updateProject(String projectName, Project.Manifest manifest, Metadata metadata,
                                     UserScope scope) {
        return null;
    }

    @Override
    public List<ResourceSet> createDocument(String projectName, String documentUrn,
                                            ProjectStorage.ResourceType documentType,
                                            UserScope scope) {
        return List.of();
    }

    @Override
    public List<ResourceSet> updateDocument(String projectName, ProjectStorage.ResourceType documentType,
                                            String content, UserScope scope) {
        return client.postCollection(ServicesAPI.RESOURCES.ADMIN.UPDATE_DOCUMENT, content,
                ResourceSet.class, "projectName", projectName, "documentType", documentType);
    }

    @Override
    public List<ResourceSet> manageRepository(String projectName, RepositoryState.Operation operation,
                                              String... arguments) {
        ProjectRequest request = new ProjectRequest();
        request.setOperation(operation);
        if (arguments != null) {
            for (String argument : arguments) {
                request.getParameters().add(argument);
            }
        }
        return client.postCollection(ServicesAPI.RESOURCES.ADMIN.MANAGE_PROJECT, request, ResourceSet.class
                , "urn",
                projectName);
    }

    @Override
    public ResourceSet createResource(Resource resource, UserScope scope) {
        return null;
    }

    @Override
    public ResourceSet createResource(File resourcePath, UserScope scope) {
        return null;
    }

    @Override
    public Resource createResource(String projectName, String urnId, String adapter,
                                   Parameters<String> resourceData, UserScope scope) {
        return null;
    }

    @Override
    public List<ResourceSet> deleteDocument(String projectName, String assetUrn,
                                            UserScope scope) {
        return null;
    }

    @Override
    public List<ResourceSet> deleteProject(String projectName, UserScope scope) {
        return null;
    }

    @Override
    public List<ResourceSet> deleteWorkspace(String workspaceName, UserScope scope) {
        return null;
    }

    @Override
    public Collection<Project> listProjects(Scope scope) {
        return client.getCollection(ServicesAPI.RESOURCES.LIST_PROJECTS, Project.class);
    }

    @Override
    public Collection<String> listResourceUrns(Scope scope) {
        return null;
    }

    @Override
    public URL lockProject(String urn, UserScope scope) {
        return client.get(ServicesAPI.RESOURCES.ADMIN.LOCK_PROJECT, URL.class, "urn", urn);
    }

    @Override
    public boolean unlockProject(String urn, UserScope scope) {
        return client.get(ServicesAPI.RESOURCES.ADMIN.UNLOCK_PROJECT, Boolean.class, "urn", urn);
    }


}
