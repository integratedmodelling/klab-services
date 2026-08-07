package org.integratedmodelling.common.services.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
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
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
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
import org.integratedmodelling.klab.rest.ResourceContextualizationRequest;

public class ResourcesClient extends BaseServiceClient implements ResourcesService {

  private Capabilities capabilities;
  boolean useCaches = false;

  /** Caches for concepts and observables. */
  private final LoadingCache<String, KimConcept> concepts =
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
  private final LoadingCache<String, KimObservable> observables =
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
  public List<ResourceSet> delete(String urn, KnowledgeClass knowledgeClass, UserScope scope) {
    // TODO RESOURCES-CRUD Utils.Http.Client currently discards DELETE response bodies. Preserve the
    // server-side changesets when typed DELETE support is added to the HTTP client.
    client
        .withScope(scope)
        .delete(ServicesAPI.RESOURCES.DELETE, "urn", urn, "knowledgeClass", knowledgeClass);
    return List.of();
  }

  @Override
  public ResourceSet resolve(String urn, KnowledgeClass assetClass, UserScope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.RESOLVE,
            ResourceSet.class,
            "urn",
            urn,
            "knowledgeClass",
            assetClass);
  }

  @Override
  public <T extends KlabAsset> List<ResourceSet> submit(
      T asset, SubmissionMode submissionMode, UserScope scope) {
    return client
        .withScope(scope)
        .putCollection(
            ServicesAPI.RESOURCES.SUBMIT,
            asset instanceof KlabDocument<?> document
                ? document.getSourceCode()
                : Utils.Json.asString(asset),
            ResourceSet.class,
            "knowledgeClass",
            KlabAsset.classify(asset),
            "submissionMode",
            submissionMode,
            "urn",
            asset.getUrn());
  }

  @Override
  public <T extends KlabAsset> T retrieve(String urn, Class<T> assetClass, UserScope scope) {

    // ensure caches are used
    if (KimConcept.class.isAssignableFrom(assetClass)) {
      return (T) resolveConceptInternal(urn);
    } else if (KimObservable.class.isAssignableFrom(assetClass)) {
      return (T) resolveObservableInternal(urn);
    }

    return client
        .withScope(scope)
        .get(
            ServicesAPI.RESOURCES.RETRIEVE,
            assetClass,
            "urn",
            urn,
            "knowledgeClass",
            KnowledgeClass.classify(assetClass));
  }

  @Override
  public <T extends KlabAsset> List<T> list(Class<T> assetClass, UserScope scope) {
    return client
        .withScope(scope)
        .getCollection(
            ServicesAPI.RESOURCES.LIST,
            assetClass,
            "knowledgeClass",
            KnowledgeClass.classify(assetClass));
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
  public KimConcept declareConcept(String definition) {
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
  public KimObservable declareObservable(String definition) {
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
  public Future<ResourceSet> importResource(Resource resource, UserScope scope) {
    return client
        .withScope(scope)
        .postAsync(ServicesAPI.RESOURCES.IMPORT_RESOURCE, resource, ResourceSet.class);
  }

  @Override
  public <T extends KlabAsset> T parseAsset(
      URL url, Class<T> assetClass, UserScope scope) {
    var content = Utils.URLs.readUrlContents(url);
    return client
        .withScope(scope)
        .post(
            ServicesAPI.RESOURCES.PARSE_ASSET,
            content,
            assetClass,
            "assetClass",
            assetClass.getCanonicalName());
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
            ServicesAPI.RESOURCES.MANAGE_PROJECT, request, ResourceSet.class, "urn", projectName);

    invalidateCaches();

    return ret;
  }

  @Override
  public CompletableFuture<Resource> publishObservation(
      Observation observation, ContextScope scope) {
    return null;
  }

  @Override
  public boolean lockProject(String urn, UserScope scope) {
    return client.get(ServicesAPI.RESOURCES.LOCK_PROJECT, Boolean.class, "urn", urn);
  }

  @Override
  public boolean unlockProject(String urn, UserScope scope) {
    return client.get(ServicesAPI.RESOURCES.UNLOCK_PROJECT, Boolean.class, "urn", urn);
  }

  private void invalidateCaches() {
    concepts.invalidateAll();
    observables.invalidateAll();
  }
}
