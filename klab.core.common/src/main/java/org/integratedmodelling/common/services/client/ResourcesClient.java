package org.integratedmodelling.common.services.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

import org.apache.http.HttpHeaders;
import org.integratedmodelling.common.data.BaseDataImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.services.client.resources.ProjectRequest;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.common.data.DataRequest;
import org.integratedmodelling.klab.common.data.ResourceContextualizationRequest;

public class ResourcesClient extends BaseServiceClient
    implements ResourcesService, ResourcesService.Admin {

  private Capabilities capabilities;
  boolean useCaches = false;

  /** Caches for concepts and observables. */
  private LoadingCache<String, KimConcept> concepts =
      CacheBuilder.newBuilder()
          .maximumSize(500)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<String, KimConcept>() {
                public KimConcept load(String key) {
                  return resolveConceptInternal(key);
                }
              });

  /** Caches for concepts and observables. */
  private LoadingCache<String, KimObservable> observables =
      CacheBuilder.newBuilder()
          .maximumSize(500)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<>() {
                public KimObservable load(String key) {
                  return resolveObservableInternal(key);
                }
              });

  ResourcesClient(
      ServiceClientCatalog.ClientMonitor monitor,
      Scope userScope,
      Settings settings,
      BiConsumer<ServiceStatus, Boolean>... statusListeners) {
    super(monitor, userScope, settings, statusListeners);
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    return capabilities == null
        ? getCapabilities(scope, ResourcesCapabilitiesImpl.class)
        : capabilities;
  }

  @Override
  public List<ResourceSet> resolveProjects(Collection<String> projects, Scope scope) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ResourceSet resolveModel(String modelName, Scope scope) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public KimNamespace retrieveNamespace(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RETRIEVE_NAMESPACE, KimNamespace.class, "urn", urn);
  }

  @Override
  public KimOntology retrieveOntology(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RETRIEVE_ONTOLOGY, KimOntology.class, "urn", urn);
  }

  @Override
  public List<ResourceInfo> queryResources(
      String queryString, Scope scope, KnowledgeClass... resourceTypes) {
    return client
        .withScope(scope)
        .getCollection(
            ServicesAPI.RESOURCES.QUERY_RESOURCES,
            ResourceInfo.class,
            "query",
            queryString,
            "resourceTypes",
            Utils.Strings.join(Arrays.asList(resourceTypes), ","));
  }

  @Override
  public KimObservationStrategyDocument retrieveObservationStrategyDocument(
      String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.RETRIEVE_OBSERVATION_STRATEGY_DOCUMENT,
            KimObservationStrategyDocument.class,
            "urn",
            urn);
  }

  @Override
  public Collection<Workspace> listWorkspaces() {
    return client.getCollection(ServicesAPI.RESOURCES.LIST_WORKSPACES, Workspace.class);
  }

  @Override
  public KActorsBehavior retrieveBehavior(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RETRIEVE_BEHAVIOR, KActorsBehavior.class, "urn", urn);
  }

  @Override
  public Resource retrieveResource(List<String> urns, Scope scope) {
    return client
        .withScope(scope)
        .post(ServicesAPI.RESOURCES.RETRIEVE_RESOURCE, urns, Resource.class);
  }

  @Override
  public Workspace retrieveWorkspace(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RETRIEVE_WORKSPACE, Workspace.class, "urn", urn);
  }

  @Override
  public ResourceSet resolveResourceAdapter(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RESOLVE_ADAPTER, ResourceSet.class, "urn", urn);
  }

  @Override
  public ResourceSet resolveServiceCall(String name, Version version, Scope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.RESOLVE_SERVICE_CALL,
            ResourceSet.class,
            "name",
            name,
            "version",
            (version == null ? null : version.toString()));
  }

  @Override
  public ResourceSet resolveImportSchema(String mediaType, Geometry geometry, Scope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.RESOLVE_IMPORT_SCHEMA,
            ResourceSet.class,
            "mediaType",
            mediaType,
            "geometry",
            (geometry == null ? null : geometry.encode()));
  }

  @Override
  public ResourceSet resolveExportSchema(String mediaType, Geometry geometry, Scope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.RESOLVE_EXPORT_SCHEMA,
            ResourceSet.class,
            "mediaType",
            mediaType,
            "geometry",
            (geometry == null ? null : geometry.encode()));
  }

  @Override
  public ResourceSet resolveResource(List<String> urns, Scope scope) {
    return client
        .withScope(scope)
        .post(ServicesAPI.RESOURCES.RESOLVE_RESOURCE, urns, ResourceSet.class);
  }

  @Override
  public Resource contextualizeResource(Resource resource, Geometry geometry, Scope scope) {

    var request = new ResourceContextualizationRequest();

    request.setUrn(resource.getUrn());
    request.setGeometry(geometry.encode());

    return client
        .withScope(scope)
        .post(ServicesAPI.RESOURCES.CONTEXTUALIZE_RESOURCE, resource, ResourceImpl.class);
  }

  @Override
  public KimConcept retrieveConcept(String definition) {
    if (!useCaches) {
      return resolveConceptInternal(removeExcessParentheses(definition));
    }
    try {
      return concepts.get(removeExcessParentheses(definition));
    } catch (ExecutionException e) {
      Logging.INSTANCE.warn("invalid concept definition: " + definition);
    }
    return null;
  }

  @Override
  public KimObservable retrieveObservable(String definition) {
    if (!useCaches) {
      return resolveObservableInternal(removeExcessParentheses(definition));
    }
    try {
      return observables.get(removeExcessParentheses(definition));
    } catch (ExecutionException e) {
      Logging.INSTANCE.warn("invalid observable definition: " + definition);
    }
    return null;
  }

  private String removeExcessParentheses(String definition) {
    definition = definition.trim();
    while (definition.startsWith("(") && definition.endsWith(")")) {
      definition = definition.substring(1, definition.length() - 1);
    }
    return definition;
  }

  // TODO CACHE
  public KimObservable resolveObservableInternal(String definition) {
    return client.get(
        ServicesAPI.RESOURCES.RETRIEVE_OBSERVABLE, KimObservable.class, "definition", definition);
  }

  @Override
  public KimConcept.Descriptor describeConcept(String conceptUrn) {
    return null;
  }

  public KimConcept resolveConceptInternal(String definition) {
    return client.get(
        ServicesAPI.RESOURCES.RETRIEVE_CONCEPT, KimConcept.class, "definition", definition);
  }

  @Override
  public CompletableFuture<Data> contextualize(
      Resource contextualizedResource,
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      @Nullable Data data,
      Scope scope) {

    DataRequest request =
        DataRequest.newBuilder()
            .setInputData(data instanceof BaseDataImpl data1 ? data1.asInstance() : null)
            .setObservable(observation.getObservable().getUrn())
            .setGeometry(geometry.encode())
            .setResourceUrns(List.of(contextualizedResource.getUrn()))
            .setStartTime(event == null ? 0 : event.getTime().getStart().getMilliseconds())
            .setEndTime(event == null ? 0 : event.getTime().getEnd().getMilliseconds())
            .build();

    return client.withScope(scope).postData(request);
  }

  @Override
  public KimObservationStrategyDocument retrieveDataflow(String urn, Scope scope) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Worldview retrieveWorldview() {
    return client.get(ServicesAPI.RESOURCES.RETRIEVE_WORLDVIEW, Worldview.class);
  }

  @Override
  public List<KimNamespace> dependents(String namespaceId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AdapterDescriptor retrieveAdapterInfo(String adapterType, Scope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.RETRIEVE_ADAPTER_INFO,
            AdapterDescriptor.class,
            "urn",
            adapterType);
  }

  @Override
  public List<KimNamespace> precursors(String namespaceId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ResourceSet resolveModels(Observable observable, ContextScope scope) {
    ResolutionRequest request = new ResolutionRequest();
    request.setObservable(observable);
    request.setResolutionConstraints(scope.getResolutionConstraints());
    if (scope.getContextObservation() != null && scope.getContextObservation().getId() < 0) {
      request
          .getResolutionConstraints()
          .add(
              ResolutionConstraint.of(
                  ResolutionConstraint.Type.UnresolvedContextObservation,
                  scope.getContextObservation()));
    }
    return client
        .withScope(scope)
        .post(ServicesAPI.RESOURCES.RESOLVE_MODELS, request, ResourceSet.class);
  }

  @Override
  public Future<ResourceSet> importResource(Resource resource, UserScope scope) {
    return client
        .withScope(scope)
        .postAsync(ServicesAPI.RESOURCES.IMPORT_RESOURCE, resource, ResourceSet.class);
  }

  @Override
  public ResourceInfo resourceInfo(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RESOURCE_INFO, ResourceInfo.class, "urn", urn);
  }

  @Override
  public boolean setResourceInfo(String urn, ResourceInfo info, Scope scope) {
    return client
        .withScope(scope)
        .post(ServicesAPI.RESOURCES.RESOURCE_INFO, info, Boolean.class, "urn", urn);
  }

  @Override
  public Project retrieveProject(String projectName, Scope scope) {
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
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RESOLVE_URN, ResourceSet.class, "urn", urn);
  }

  @Override
  public boolean createWorkspace(String workspace, Metadata metadata, UserScope scope) {
    return client
        .withScope(scope)
        .post(
            ServicesAPI.RESOURCES.ADMIN.CREATE_WORKSPACE,
            metadata,
            Boolean.class,
            "workspaceName",
            workspace);
  }

  @Override
  public ResourceSet createProject(String workspaceName, String projectName, UserScope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.ADMIN.CREATE_PROJECT,
            ResourceSet.class,
            "workspaceName",
            workspaceName,
            "projectName",
            projectName);
  }

  @Override
  public ResourceSet updateProject(
      String projectName, Project.Manifest manifest, Metadata metadata, UserScope scope) {
    return null;
  }

  @Override
  public List<ResourceSet> createDocument(
      String projectName,
      String documentUrn,
      ProjectStorage.ResourceType documentType,
      UserScope scope) {
    return List.of();
  }

  @Override
  public List<ResourceSet> updateDocument(
      String projectName,
      ProjectStorage.ResourceType documentType,
      String content,
      UserScope scope) {
    var ret =
        client.postCollection(
            ServicesAPI.RESOURCES.ADMIN.UPDATE_DOCUMENT,
            content,
            ResourceSet.class,
            "projectName",
            projectName,
            "documentType",
            documentType);
    invalidateCaches();
    return ret;
  }

  @Override
  public List<ResourceSet> manageRepository(
      String projectName, RepositoryState.Operation operation, String... arguments) {
    ProjectRequest request = new ProjectRequest();
    request.setOperation(operation);
    if (arguments != null) {
      for (String argument : arguments) {
        request.getParameters().add(argument);
      }
    }
    var ret =
        client.postCollection(
            ServicesAPI.RESOURCES.ADMIN.MANAGE_PROJECT,
            request,
            ResourceSet.class,
            "urn",
            projectName);

    invalidateCaches();

    return ret;
  }

  @Override
  public ResourceInfo registerResource(
      String urn,
      KnowledgeClass knowledgeClass,
      File file,
      ResourcePrivileges rights,
      Scope submittingScope) {
    throw new KlabIllegalStateException(
        "resources service: registerResource() should not be called by clients");
  }

  @Override
  public List<ResourceSet> deleteDocument(String projectName, String assetUrn, UserScope scope) {
    return null;
  }

  @Override
  public CompletableFuture<Resource> publishObservation(
      Observation observation, ContextScope scope) {
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
  public boolean lockProject(String urn, UserScope scope) {
    return client.get(ServicesAPI.RESOURCES.ADMIN.LOCK_PROJECT, Boolean.class, "urn", urn);
  }

  @Override
  public boolean unlockProject(String urn, UserScope scope) {
    return client.get(ServicesAPI.RESOURCES.ADMIN.UNLOCK_PROJECT, Boolean.class, "urn", urn);
  }

  private void invalidateCaches() {
    concepts.invalidateAll();
    observables.invalidateAll();
  }
}
