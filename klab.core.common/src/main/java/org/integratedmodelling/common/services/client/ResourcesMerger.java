package org.integratedmodelling.common.services.client;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * A service that merges and prioritizes resource queries from all available services under a scope
 * into a single {@link ResourceSet} whenever the operation requires merging results from different
 * services. It is the default implementation of the {@link ResourcesService} when retrieved through
 * a client scope using {@link Scope#getService(Class)} whenever more than one service is available.
 *
 * <p>Operations that cannot be meaningfully aggregated, including writes and service-management
 * calls, are forwarded to the first service exposed by the scope. Client scopes order these with
 * the local service first. Callers that need another specific service should select it directly
 * from {@link Scope#getServices(Class)}.
 */
public class ResourcesMerger implements ResourcesService {

  private final Scope owningScope;

  public ResourcesMerger(Scope owningScope) {
    this.owningScope = Objects.requireNonNull(owningScope);
  }

  private List<ResourcesService> services() {
    var available = owningScope.getServices(ResourcesService.class);
    if (available == null) {
      return List.of();
    }
    return available.stream().filter(Objects::nonNull).filter(service -> service != this).toList();
  }

  private ResourcesService primary() {
    return services().stream()
        .findFirst()
        .orElseThrow(
            () ->
                new KlabServiceAccessException(
                    "No resources service is available in the owning scope"));
  }

  /**
   * Run a ResourceSet query against a stable snapshot of all available services. Each invocation is
   * isolated from failures in the others and all successful responses are prioritized by the
   * canonical ResourceSet merge utility.
   */
  private ResourceSet query(Function<ResourcesService, ResourceSet> operation) {
    var services = services();
    var responses = new ArrayList<CompletableFuture<ResourceSet>>(services.size());
    for (var service : services) {
      responses.add(
          CompletableFuture.supplyAsync(() -> operation.apply(service))
              .exceptionally(
                  failure ->
                      ResourceSet.empty(
                          Notification.warning(
                              "Resource query failed on "
                                  + service.getClass().getSimpleName()
                                  + ": "
                                  + failureMessage(failure)))));
    }
    CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new)).join();
    return Utils.Resources.merge(
        responses.stream().map(CompletableFuture::join).toArray(ResourceSet[]::new));
  }

  private static String failureMessage(Throwable failure) {
    var cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  private <T> List<T> collect(Function<ResourcesService, List<T>> operation) {
    var snapshot = services();
    var responses =
        snapshot.stream()
            .map(
                service ->
                    CompletableFuture.supplyAsync(() -> operation.apply(service))
                        .exceptionally(failure -> List.of()))
            .toList();
    CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new)).join();
    var merged = new LinkedHashSet<T>();
    responses.stream().map(CompletableFuture::join).forEach(merged::addAll);
    return List.copyOf(merged);
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    return primary().capabilities(scope);
  }

  @Override
  public Workflow getWorkflow(String workflowId, UserScope scope) {
    return primary().getWorkflow(workflowId, scope);
  }

  @Override
  public Flow createFlow(String workflowId, Flow.State initialState, UserScope scope) {
    return primary().createFlow(workflowId, initialState, scope);
  }

  @Override
  public Flow getFlow(String flowId, UserScope scope) {
    return primary().getFlow(flowId, scope);
  }

  @Override
  public Flow reopenFlow(String flowId, UserScope scope) {
    return primary().reopenFlow(flowId, scope);
  }

  @Override
  public List<Flow> getFlows(boolean includeClosed, UserScope scope) {
    return primary().getFlows(includeClosed, scope);
  }

  @Override
  public Flow.State createFlowState(String flowId, Flow.State state, UserScope scope) {
    return primary().createFlowState(flowId, state, scope);
  }

  @Override
  public Flow.State updateFlowState(String flowId, String stateId, Flow.State state, UserScope scope) {
    return primary().updateFlowState(flowId, stateId, state, scope);
  }

  @Override
  public boolean deleteFlowState(String flowId, String stateId, UserScope scope) {
    return primary().deleteFlowState(flowId, stateId, scope);
  }

  @Override
  public Flow transitionFlow(String flowId, Flow.TransitionRequest request, UserScope scope) {
    return primary().transitionFlow(flowId, request, scope);
  }

  @Override
  public Flow.Attachment addFlowAttachment(
      String flowId, String stateId, Flow.AttachmentUpload upload, UserScope scope) {
    return primary().addFlowAttachment(flowId, stateId, upload, scope);
  }

  @Override
  public byte[] getFlowAttachment(String flowId, String attachmentId, UserScope scope) {
    return primary().getFlowAttachment(flowId, attachmentId, scope);
  }

  @Override
  public ServiceStatus status() {
    return primary().status();
  }

  @Override
  public URL getUrl() {
    return primary().getUrl();
  }

  @Override
  public String serviceName() {
    return primary().serviceName();
  }

  @Override
  public String serviceId() {
    return primary().serviceId();
  }

  @Override
  public Settings settings() {
    return primary().settings();
  }

  @Override
  public Scope serviceScope() {
    return primary().serviceScope();
  }

  @Override
  public boolean shutdown() {
    return primary().shutdown();
  }

  @Override
  public String declareSessionScope(
      SessionScope sessionScope, UserScope userScope, KActorsBehavior behavior) {
    return primary().declareSessionScope(sessionScope, userScope, behavior);
  }

  @Override
  public DigitalTwin.Configuration declareContextScope(
      ContextScope contextScope, SessionScope sessionScope, UserScope userScope) {
    return primary().declareContextScope(contextScope, sessionScope, userScope);
  }

  @Override
  public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
    return primary().getRights(resourceUrn, scope);
  }

  @Override
  public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
    return primary().setRights(resourceUrn, resourcePrivileges, scope);
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
    return primary().getCredentialInfo(scope);
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    return primary().addCredentials(host, credentials, scope);
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    return primary().retrieveAsset(urn, locator, assetClass, scope);
  }

  @Override
  public boolean loadResources(ResourceSet resourceSet, Scope scope) {
    return primary().loadResources(resourceSet, scope);
  }

  @Override
  public InputStream exportAsset(
      String urn,
      KlabAsset.KnowledgeClass knowledgeClass,
      String mediaType,
      Parameters<String> parameters,
      Scope scope) {
    return primary().exportAsset(urn, knowledgeClass, mediaType, parameters, scope);
  }

  @Override
  public CompletableFuture<ResourceSet> importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope) {
    return primary().importAsset(schema, assetCoordinates, suggestedUrn, scope);
  }

  @Override
  public <T extends KlabAsset> T retrieve(String urn, Class<T> assetClass, UserScope scope) {
    return primary().retrieve(urn, assetClass, scope);
  }

  @Override
  public List<ResourceSet> delete(
      String urn, KlabAsset.KnowledgeClass knowledgeClass, UserScope scope) {
    return primary().delete(urn, knowledgeClass, scope);
  }

  @Override
  public <T extends KlabAsset> List<T> list(Class<T> assetClass, UserScope scope) {
    return collect(service -> service.list(assetClass, scope));
  }

  @Override
  public ResourceSet resolve(String urn, KlabAsset.KnowledgeClass assetClass, UserScope scope) {
    return query(service -> service.resolve(urn, assetClass, scope));
  }

  @Override
  public <T extends KlabAsset> List<ResourceSet> submit(
      T asset, SubmissionMode submissionMode, UserScope scope) {
    return primary().submit(asset, submissionMode, scope);
  }

  @Override
  public <T> T info(
      String urn, KlabAsset.KnowledgeClass assetClass, Class<T> infoClass, UserScope scope) {
    return primary().info(urn, assetClass, infoClass, scope);
  }

  @Override
  public <T> List<T> query(
      Parameters<String> query,
      KlabAsset.KnowledgeClass assetClass,
      Class<T> infoClass,
      UserScope scope) {
    return collect(service -> service.query(query, assetClass, infoClass, scope));
  }

  @Override
  public Resource contextualizeResource(Resource resource, Geometry geometry, Scope scope) {
    return primary().contextualizeResource(resource, geometry, scope);
  }

  @Override
  public KimObservable declareObservable(String definition) {
    return primary().declareObservable(definition);
  }

  @Override
  public KimConcept declareConcept(String definition) {
    return primary().declareConcept(definition);
  }

  @Override
  public CompletableFuture<Data> contextualize(
      Resource contextualizedResource,
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      Data input,
      Scope scope) {
    return primary()
        .contextualize(contextualizedResource, observation, geometry, event, input, scope);
  }

  @Override
  public Future<ResourceSet> importResource(Resource resource, UserScope scope) {
    return primary().importResource(resource, scope);
  }

  @Override
  public <T extends KlabAsset> T parseAsset(URL url, Class<T> assetClass, UserScope scope) {
    return primary().parseAsset(url, assetClass, scope);
  }

  @Override
  public List<ResourceSet> manageRepository(
      String projectName, RepositoryState.Operation operation, String... arguments) {
    return primary().manageRepository(projectName, operation, arguments);
  }

  @Override
  public CompletableFuture<Resource> publishObservation(
      Observation observation, ContextScope scope) {
    return primary().publishObservation(observation, scope);
  }

  @Override
  public boolean lockProject(String urn, UserScope scope) {
    return primary().lockProject(urn, scope);
  }

  @Override
  public boolean unlockProject(String urn, UserScope scope) {
    return primary().unlockProject(urn, scope);
  }
}
