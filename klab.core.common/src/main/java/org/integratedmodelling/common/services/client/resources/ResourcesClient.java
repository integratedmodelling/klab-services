package org.integratedmodelling.common.services.client.resources;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.data.DataImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.common.data.DataRequest;
import org.integratedmodelling.klab.rest.ServiceReference;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

public class ResourcesClient extends ServiceClient
    implements ResourcesService, ResourcesService.Admin {

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
              new CacheLoader<String, KimObservable>() {
                public KimObservable load(String key) {
                  return resolveObservableInternal(key);
                }
              });

  public ResourcesClient(
      URL url, Identity identity, KlabService owner, Parameters<Engine.Setting> settings) {
    super(Type.RESOURCES, url, identity, List.of(), settings, owner);
  }

  public ResourcesClient(
      URL url,
      Identity identity,
      List<ServiceReference> services,
      Parameters<Engine.Setting> settings,
      BiConsumer<Channel, Message>... listeners) {
    super(Type.RESOURCES, url, identity, settings, services, listeners);
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    return client.get(ServicesAPI.CAPABILITIES, ResourcesCapabilitiesImpl.class);
  }

  /**
   * When called as a slave from a service, add the sessionId parameter to build a peer scope at the
   * remote service side.
   *
   * @param scope a client scope that should record the ID for future communication. If the ID is
   *     null, the call has failed.
   * @return
   */
  @Override
  public String registerSession(SessionScope scope) {
    ScopeRequest request = new ScopeRequest();
    request.setName(scope.getName());

    var hasMessaging =
        scope.getParentScope() instanceof MessagingChannel messagingChannel
            && messagingChannel.hasMessaging();

    for (var service : scope.getServices(ResourcesService.class)) {
      if (service instanceof ServiceClient serviceClient) {
        // we only send a local URL if we're local ourselves
        if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
          request.getResourceServices().add(serviceClient.getUrl());
        }
      }
    }

    for (var service : scope.getServices(Resolver.class)) {
      if (service instanceof ServiceClient serviceClient) {
        // we only send a local URL if we're local ourselves
        if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
          request.getResolverServices().add(serviceClient.getUrl());
        }
      }
    }

    if (getOwnerService() != null) {
      switch (getOwnerService()) {
        case Resolver resolver -> request.getResolverServices().add(resolver.getUrl());
        case RuntimeService runtimeService ->
            request.getRuntimeServices().add(runtimeService.getUrl());
        case ResourcesService resourcesService ->
            request.getResourceServices().add(resourcesService.getUrl());
        case Reasoner reasoner -> request.getReasonerServices().add(reasoner.getUrl());
        default -> {}
      }
    }

    if (isLocal()
        && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient
        && reasonerClient.isLocal()) {
      request.getReasonerServices().add(reasonerClient.getUrl());
    }

    if (hasMessaging) {
      // TODO setup desired request. This will send no header and use the defaults.
      // Resolver should probably only catch events and errors.
    }

    var ret =
        client
            .withScope(scope.getParentScope())
            .post(
                ServicesAPI.CREATE_SESSION,
                request,
                String.class,
                "id",
                scope instanceof ServiceSideScope serviceSideScope
                    ? serviceSideScope.getId()
                    : null);

    var brokerURI = client.getResponseHeader(ServicesAPI.MESSAGING_URN_HEADER);
    if (brokerURI != null && scope instanceof MessagingChannelImpl messagingChannel) {
      var queues =
          getQueuesFromHeader(scope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
      messagingChannel.setupMessaging(brokerURI, ret, queues);
    }

    return ret;
  }

  /**
   * When called as a slave from a service, add the sessionId parameter to build a peer scope at the
   * remote service side.
   *
   * @param scope a client scope that should record the ID for future communication. If the ID is
   *     null, the call has failed.
   * @return
   */
  @Override
  public String registerContext(ContextScope scope) {

    ScopeRequest request = new ScopeRequest();
    request.setName(scope.getName());

    var runtime = scope.getService(RuntimeService.class);
    var hasMessaging =
        scope.getParentScope() instanceof MessagingChannel messagingChannel
            && messagingChannel.hasMessaging();

    // The runtime needs to use our resolver(s) and resource service(s), as long as they're
    // accessible.
    // The reasoner can be the runtime's own unless we have locked worldview projects.
    for (var service : scope.getServices(ResourcesService.class)) {
      if (service instanceof ServiceClient serviceClient) {
        // we only send a local URL if we're local ourselves
        if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
          request.getResourceServices().add(serviceClient.getUrl());
        }
      }
    }

    for (var service : scope.getServices(Resolver.class)) {
      if (service instanceof ServiceClient serviceClient) {
        // we only send a local URL if we're local ourselves
        if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
          request.getResolverServices().add(serviceClient.getUrl());
        }
      }
    }

    if (isLocal()
        && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient
        && reasonerClient.isLocal()) {
      request.getReasonerServices().add(reasonerClient.getUrl());
    }

    if (getOwnerService() != null) {
      switch (getOwnerService()) {
        case Resolver resolver -> request.getResolverServices().add(resolver.getUrl());
        case RuntimeService runtimeService ->
            request.getRuntimeServices().add(runtimeService.getUrl());
        case ResourcesService resourcesService ->
            request.getResourceServices().add(resourcesService.getUrl());
        case Reasoner reasoner -> request.getReasonerServices().add(reasoner.getUrl());
        default -> {}
      }
    }

    if (hasMessaging) {
      // TODO setup desired request. This will send no header and use the defaults.
      // Resolver should probably only catch events and errors.
    }

    var ret =
        client
            .withScope(scope.getParentScope())
            .post(
                ServicesAPI.CREATE_CONTEXT,
                request,
                String.class,
                "id",
                scope instanceof ServiceSideScope serviceSideScope
                    ? serviceSideScope.getId()
                    : null);

    if (hasMessaging) {
      var queues =
          getQueuesFromHeader(scope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
      if (scope instanceof MessagingChannelImpl messagingChannel) {
        messagingChannel.setupMessagingQueues(ret, queues);
      }
    }

    return ret;
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
    return client.withScope(scope).post(ServicesAPI.RESOURCES.RETRIEVE_RESOURCE, urns, Resource.class);
  }

  @Override
  public Workspace retrieveWorkspace(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RESOURCES.RETRIEVE_WORKSPACE, Workspace.class, "urn", urn);
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
  public ResourceSet resolveResource(List<String> urns, Scope scope) {
    return client
            .withScope(scope)
            .post(
                    ServicesAPI.RESOURCES.RESOLVE_RESOURCE,
                    urns,
                    ResourceSet.class);
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
  public Data contextualize(
      Resource contextualizedResource, Geometry geometry, @Nullable Data data, Scope scope) {

    DataRequest request =
        DataRequest.newBuilder()
            .setInputData(data instanceof DataImpl data1 ? data1.asInstance() : null)
            .setGeometry(geometry.encode())
            .setResourceUrns(List.of(contextualizedResource.getUrn()))
            .build();

    return client.postData(request);
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
  public List<KimNamespace> precursors(String namespaceId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ResourceSet resolveModels(Observable observable, ContextScope scope) {
    ResolutionRequest request = new ResolutionRequest();
    request.setObservable(observable);
    request.setResolutionConstraints(scope.getResolutionConstraints());
    return client
        .withScope(scope)
        .post(ServicesAPI.RESOURCES.RESOLVE_MODELS, request, ResourceSet.class);
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
  public ResourceSet createProject(String workspaceName, String projectName, UserScope scope) {
    return null;
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
  public ResourceStatus registerResource(
      String urn, KnowledgeClass knowledgeClass, File file, Scope submittingScope) {
    throw new KlabIllegalStateException(
        "resources service: registerResource() should not be called by clients");
  }

  @Override
  public List<ResourceSet> deleteDocument(String projectName, String assetUrn, UserScope scope) {
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

  private void invalidateCaches() {
    concepts.invalidateAll();
    observables.invalidateAll();
  }
}
