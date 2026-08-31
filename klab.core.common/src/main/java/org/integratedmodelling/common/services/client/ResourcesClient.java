package org.integratedmodelling.common.services.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.net.URL;
import java.util.Base64;
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
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.common.data.DataRequest;
import org.integratedmodelling.klab.rest.ResourceContextualizationRequest;

public class ResourcesClient extends BaseServiceClient implements ResourcesService {

  private Capabilities capabilities;
  boolean useCaches = false;

  @Override
  public Workflow getWorkflow(String workflowId, UserScope scope) {
    return client.withScope(scope).get(ServicesAPI.RESOURCES.WORKFLOW, Workflow.class, "workflowId", workflowId);
  }

  @Override
  public Flow createFlow(String workflowId, Flow.State initialState, UserScope scope) {
    return client.withScope(scope).post(ServicesAPI.RESOURCES.FLOWS, initialState, Flow.class, "workflowId", workflowId);
  }

  @Override
  public Flow getFlow(String flowId, UserScope scope) {
    return client.withScope(scope).get(ServicesAPI.RESOURCES.FLOW, Flow.class, "flowId", flowId);
  }

  @Override
  public Flow reopenFlow(String flowId, UserScope scope) {
    return client.withScope(scope).post(ServicesAPI.RESOURCES.FLOW_REOPEN, null, Flow.class, "flowId", flowId);
  }

  @Override
  public List<Flow> getFlows(boolean includeClosed, UserScope scope) {
    return client.withScope(scope).getCollection(ServicesAPI.RESOURCES.FLOWS, Flow.class, "includeClosed", includeClosed);
  }

  @Override
  public Flow.State createFlowState(String flowId, Flow.State state, UserScope scope) {
    return client.withScope(scope).post(ServicesAPI.RESOURCES.FLOW_STATES, state, Flow.State.class, "flowId", flowId);
  }

  @Override
  public Flow.State updateFlowState(String flowId, String stateId, Flow.State state, UserScope scope) {
    return client.withScope(scope).post(ServicesAPI.RESOURCES.FLOW_STATE, state, Flow.State.class, "flowId", flowId, "stateId", stateId);
  }

  @Override
  public boolean deleteFlowState(String flowId, String stateId, UserScope scope) {
    client.withScope(scope).delete(ServicesAPI.RESOURCES.FLOW_STATE, "flowId", flowId, "stateId", stateId);
    return true;
  }

  @Override
  public Flow transitionFlow(String flowId, Flow.TransitionRequest request, UserScope scope) {
    return client.withScope(scope).post(ServicesAPI.RESOURCES.FLOW_TRANSITIONS, request, Flow.class, "flowId", flowId);
  }

  @Override
  public Flow.Attachment addFlowAttachment(String flowId, String stateId, Flow.AttachmentUpload upload, UserScope scope) {
    return client.withScope(scope).post(ServicesAPI.RESOURCES.FLOW_ATTACHMENTS, upload, Flow.Attachment.class, "flowId", flowId, "stateId", stateId);
  }

  @Override
  public byte[] getFlowAttachment(String flowId, String attachmentId, UserScope scope) {
    String encoded = client.withScope(scope).get(ServicesAPI.RESOURCES.FLOW_ATTACHMENT, String.class, "flowId", flowId, "attachmentId", attachmentId);
    return Base64.getDecoder().decode(encoded);
  }

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
            Utils.Json.asString(asset),
            ResourceSet.class,
            "knowledgeClass",
            KlabAsset.classify(asset),
            "submissionMode",
            submissionMode,
            "urn",
            asset.getUrn());
  }

  @Override
  public boolean markPublished(
      String resourceUrn,
      String authoritativeServiceId,
      String authoritativeResourceUrn,
      UserScope scope) {
    return Boolean.TRUE.equals(
        client
            .withScope(scope)
            .put(
                ServicesAPI.RESOURCES.MARK_PUBLISHED,
                Map.of("authoritativeResourceUrn", authoritativeResourceUrn),
                Boolean.class,
                "urn",
                resourceUrn,
                "serviceId",
                authoritativeServiceId));
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
    return client.post(
        ServicesAPI.RESOURCES.PARSE_ASSET,
        definition,
        KimObservable.class,
        "assetClass",
        KlabAsset.KnowledgeClass.OBSERVABLE);
  }

  public KimConcept resolveConceptInternal(String definition) {
    return client.post(
        ServicesAPI.RESOURCES.PARSE_ASSET,
        definition,
        KimConcept.class,
        "assetClass",
        KnowledgeClass.CONCEPT);
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
        // FIXME this one is used both in synchronous and asynchronous calls
        .postAsync(
            ServicesAPI.RESOURCES.SUBMIT,
            resource,
            ResourceSet.class,
            "knowledgeClass",
            KnowledgeClass.RESOURCE,
            "submissionMode",
            SubmissionMode.CREATE_OR_UPDATE);
  }

  @Override
  public <T extends KlabAsset> T parseAsset(URL url, Class<T> assetClass, UserScope scope) {
    var content = Utils.URLs.readUrlContents(url);
    return client
        .withScope(scope)
        .post(
            ServicesAPI.RESOURCES.PARSE_ASSET,
            content,
            assetClass,
            "assetClass",
            KlabAsset.KnowledgeClass.classify(assetClass));
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
