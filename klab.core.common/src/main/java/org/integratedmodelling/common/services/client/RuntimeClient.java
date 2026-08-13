package org.integratedmodelling.common.services.client;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.common.services.client.scope.ClientScopeManager;
import org.integratedmodelling.common.services.client.scope.ClientSessionScope;
import org.integratedmodelling.common.services.client.scope.ClientUserScope;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.AgentInstantiationRequest;

public class RuntimeClient extends BaseServiceClient
    implements RuntimeService, RuntimeService.Admin {

  private Capabilities capabilities = null;

  RuntimeClient(
      ServiceClientCatalog.ClientMonitor monitor,
      Scope userScope,
      Settings settings,
      BiConsumer<KlabService.ServiceStatus, Boolean>... statusListeners) {
    super(monitor, userScope, settings, statusListeners);
  }

  @Override
  public Data.ShardingStrategy getDefaultShardingStrategy(
      Observation observation, ContextScope scope) {
    return client
        .withScope(scope)
        .post(
            ServicesAPI.RUNTIME.GET_SHARDING_STRATEGY,
            Observation.forTransport(observation),
            Data.ShardingStrategy.class);
  }

  @Override
  public CompletableFuture<Observation> submit(Observation observation, ContextScope scope) {

    if (observation.getId() > 0) {
      return CompletableFuture.completedFuture(observation);
    }

    ResolutionRequest resolutionRequest = new ResolutionRequest();
    resolutionRequest.setObservation(Observation.forTransport(observation));
    resolutionRequest.setAgentName(Objects.requireNonNull(Provenance.getAgent(scope)).getName());
    resolutionRequest.setResolutionConstraints(
        scope.getResolutionConstraints().stream().map(ResolverClient::forTransport).toList());
    return client
        .withScope(scope)
        .postAsync(ServicesAPI.RUNTIME.SUBMIT_OBSERVATION, resolutionRequest, Observation.class)
        .thenApply(
            resolved -> {
              /*
               * The HTTP completion and the ObservationSubmissionFinished event race each other.
               * Ingest the returned observation before exposing it to callers so its Commit is
               * attached regardless of which transport wins. Event ingestion is idempotent by
               * commit ID and will either do the work first or become a no-op.
              */
              try {
                if (scope.getDigitalTwin() instanceof ClientDigitalTwin clientDigitalTwin) {
                  clientDigitalTwin.getKnowledgeGraph().ingest(resolved);
                }
              } catch (Throwable failure) {
                // Commit synchronization is auxiliary: never turn a successful observation
                // submission into a failed future because its visualization delta was unavailable.
                scope.warn("Cannot synchronize the submission commit", failure);
              }
              return resolved;
            });
  }

  @Override
  public Observation register(Observation observation, ContextScope scope) {

    if (observation.getId() > 0 || observation.getId() < Observation.UNASSIGNED_ID) {
      return observation;
    }
    return client
        .withScope(scope)
        .post(
            ServicesAPI.RUNTIME.REGISTER_OBSERVATION,
            Observation.forTransport(observation),
            Observation.class);
  }

  @Override
  public Agent createAgent(
      KActorsBehavior behavior,
      String suggestedAgentName,
      Collection<RuntimeAgent.CompilationOptions> options,
      UserScope scope) {
    var requestedOptions =
        options == null
            ? java.util.Set.<RuntimeAgent.CompilationOptions>of()
            : java.util.Set.copyOf(options);
    var request = new AgentInstantiationRequest();
    request.setBehavior(behavior);
    request.setCompileOnly(
        requestedOptions.contains(RuntimeAgent.CompilationOptions.DO_NOT_COMPILE_JAVA));
    request.setReportJavaCode(
        requestedOptions.contains(RuntimeAgent.CompilationOptions.INCLUDE_JAVA_CODE));
    request.setDoNotStart(requestedOptions.contains(RuntimeAgent.CompilationOptions.DO_NOT_START));
    request.setDoNotBindObservation(
        requestedOptions.contains(RuntimeAgent.CompilationOptions.DO_NOT_BIND_OBSERVATION));
    request.setDoNotBindSession(
        requestedOptions.contains(RuntimeAgent.CompilationOptions.DO_NOT_BIND_SESSION));
    request.setSuggestedName(suggestedAgentName);
    if (scope instanceof ContextScope contextScope
        && contextScope.getContextObservation() != null
        && contextScope.getContextObservation().getObservable().is(SemanticType.AGENT)) {
      request.setObservationId(contextScope.getContextObservation().getId());
    }

    var agent =
        client
            .withScope(scope)
            .withHeader(
                ServicesAPI.MESSAGING_QUEUES_HEADER,
                Utils.Strings.join(scope.defaultQueues(), ", "))
            .post(ServicesAPI.RUNTIME.INSTANTIATE_AGENT, request, AgentImpl.class);
    boolean usesAgentSession =
        !requestedOptions.contains(RuntimeAgent.CompilationOptions.DO_NOT_COMPILE_JAVA)
            && (behavior.getBehaviorType() == KActorsBehavior.Type.SCRIPT
                || behavior.getBehaviorType() == KActorsBehavior.Type.APP
                || behavior.getBehaviorType() == KActorsBehavior.Type.UNITTEST);
    if (agent != null && agent.getUrn() != null && usesAgentSession) {
      if (agent.isMessagingConnected() && agent.getScopeId() != null) {
        connectAgentSession(agent, scope);
      } else {
        agent
            .getNotifications()
            .add(
                Notification.info(
                    "Agent messaging is disabled because its dedicated session is not connected"));
      }
    } else if (agent != null
        && agent.getUrn() != null
        && scope instanceof MessagingChannel channel) {
      agent.connect(channel);
    } else if (agent != null && agent.getUrn() != null) {
      agent
          .getNotifications()
          .add(
              Notification.info(
                  "Agent messaging is disabled because its creating scope is not connected"));
    }
    return agent;
  }

  private void connectAgentSession(AgentImpl agent, UserScope requestScope) {
    ClientUserScope userScope =
        requestScope.getType() == Scope.Type.USER
                && requestScope instanceof ClientUserScope clientUser
            ? clientUser
            : requestScope.getParentScope(Scope.Type.USER, ClientUserScope.class);
    var federation = Klab.INSTANCE.getFederationData(requestScope.getUser());
    if (userScope == null || federation == null) {
      agent
          .getNotifications()
          .add(
              Notification.info(
                  "Agent messaging is disabled because no client federation is available"));
      return;
    }

    var agentSession =
        new ClientSessionScope(userScope, agent.getName(), this).withId(agent.getScopeId());
    var queues =
        getQueuesFromHeader(
            agentSession, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
    agentSession.setupMessaging(federation, agent.getScopeId(), queues);
    ClientScopeManager.INSTANCE.register(agentSession);
    agent.connectOwned(agentSession, agentSession::closePeer);
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    return capabilities == null
        ? getCapabilities(scope, RuntimeCapabilitiesImpl.class)
        : capabilities;
  }

  @Override
  public List<ContextInfo> getContextInfo(Scope scope) {
    return client
        .withScope(scope)
        .getCollection(ServicesAPI.RUNTIME.GET_CONTEXT_INFO, ContextInfo.class);
  }

  @Override
  public ContextScope connectContext(DigitalTwin.Configuration configuration, UserScope userScope) {

    var ret = ClientScopeManager.INSTANCE.getScope(configuration.getId(), ClientContextScope.class);
    if (ret != null) {
      return ret;
    }

    ScopeRequest request = new ScopeRequest();
    request.setConfiguration(configuration);
    userScope.getServices(KlabService.class).stream()
        .forEach(
            s -> {
              if (s.serviceId() != null) request.getServiceIds().add(s.serviceId());
            });

    var descriptor =
        client
            .withScope(userScope)
            .post(ServicesAPI.RUNTIME.CONNECT, request, DigitalTwin.Configuration.class);

    if (descriptor != null && !Utils.Notifications.hasErrors(descriptor.getNotifications())) {

      final var service = this;
      descriptor.getNotifications().forEach(n -> userScope.send(n));

      var sessionId = Utils.Paths.getLeading(configuration.getId(), '.');
      var sessionScope = ClientScopeManager.INSTANCE.getScope(sessionId, ClientSessionScope.class);
      if (sessionScope == null) {
        sessionScope = (ClientSessionScope) userScope.getUserSession(this);
        ClientScopeManager.INSTANCE.register(sessionScope);
      }

      // Add the known data that are null; notify for anything that isn't and differs.
      configuration.defineFromExisting(descriptor);

      ret = new ClientContextScope(sessionScope, this, configuration);
      ret.setId(descriptor.getId());
      var federation = Klab.INSTANCE.getFederationData(userScope.getUser());
      if (federation != null && ret instanceof MessagingChannelImpl messagingChannel) {
        var queues =
            getQueuesFromHeader(ret, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
        if (queues == null) {
          // TODO error recovery
          Logging.INSTANCE.error("no queues found in messaging header");
        }
        messagingChannel.setupMessaging(federation, ret.getId(), queues);
        Logging.INSTANCE.info("Connected to queue for context scope " + ret.getId());
      }
      ret.createDigitalTwin(descriptor.getId());
      return ret;
    }

    return null;
  }

  @Override
  public DigitalTwin.Configuration getConfiguration(String scopeId, UserScope scope) {
    return client
        .withScope(scope)
        .get(
            ServicesAPI.RUNTIME.GET_DIGITAL_TWIN_CONFIGURATION,
            DigitalTwin.Configuration.class,
            "id",
            scopeId);
  }

  @Override
  public boolean releaseSession(SessionScope scope) {
    try {
      return client.withScope(scope).get(ServicesAPI.RELEASE_SESSION, Boolean.class);
    } catch (Throwable t) {
      // just return false
    }
    return false;
  }

  @Override
  public boolean releaseContext(ContextScope scope) {
    try {
      return client.withScope(scope).get(ServicesAPI.RELEASE_CONTEXT, Boolean.class);
    } catch (Throwable t) {
      // just return false
    }
    return false;
  }

  //  @Override
  //  public GraphModel.KnowledgeGraph retrieveSubgraph(
  //      long focalNodeId,
  //      int depth,
  //      Collection<Long> requiredNodes,
  //      Collection<RuntimeAsset.Type> acceptedTypes,
  //      Collection<GraphModel.Relationship> acceptedRelationships,
  //      GraphModel.KnowledgeGraph.Detail detail,
  //      ContextScope scope) {
  //    return client
  //        .withScope(scope)
  //        .get(
  //            ServicesAPI.RUNTIME.RETRIEVE_SUBGRAPH,
  //            GraphModel.KnowledgeGraph.class,
  //            "focus",
  //            focalNodeId,
  //            "depth",
  //            depth,
  //            "detail",
  //            detail,
  //            "links",
  //            (acceptedRelationships == null || acceptedRelationships.isEmpty()
  //                ? "all"
  //                : Utils.Strings.join(acceptedRelationships, ",")),
  //            "types",
  //            (acceptedTypes == null || acceptedTypes.isEmpty()
  //                ? "all"
  //                : Utils.Strings.join(acceptedTypes, ",")),
  //            "include",
  //            (requiredNodes == null || requiredNodes.isEmpty()
  //                ? "none"
  //                : Utils.Strings.join(requiredNodes, ",")));
  //  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends RuntimeAsset> List<T> queryKnowledgeGraph(
      KnowledgeGraph.Query<T> knowledgeGraphQuery, Scope scope) {
    if (knowledgeGraphQuery instanceof KnowledgeGraphQuery<T> knowledgeGraphQuery1) {
      return (List<T>)
          client
              .withScope(scope)
              .postCollection(
                  ServicesAPI.RUNTIME.QUERY,
                  knowledgeGraphQuery,
                  knowledgeGraphQuery1.getResultType().getAssetClass());
    }
    throw new KlabIllegalStateException("Knowledge graph query using unexpected implementation");
  }

  @Override
  public ResourceSet resolveContextualizables(
      List<Contextualizable> contextualizables, ContextScope scope) {

    if (contextualizables.isEmpty()) {
      return new ResourceSet();
    }

    /**
     * Only send over those that will need resolution at the runtime side. No need to send a lookup
     * table or classification asset.
     */
    List<Contextualizable> request =
        contextualizables.stream()
            .filter(
                contextualizable ->
                    !contextualizable.getResourceUrns().isEmpty()
                        || contextualizable.getServiceCall() != null)
            .toList();

    return client
        .withScope(scope)
        .post(ServicesAPI.RUNTIME.RESOLVE_CONTEXTUALIZERS, request, ResourceSet.class);
  }

  @Override
  public void submitContextualizationResult(
      ContextualizationScope scope, ContextScope contextScope, Activity.Outcome outcome) {
    throw new KlabIllegalStateException(
        "Submission of contextualization results should never be called on a client");
  }

  @Override
  public Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting) {
    return Map.of();
  }

  @Override
  public KnowledgeGraph.Commit getCommit(long commitId, ContextScope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RUNTIME.GET_COMMIT_INFO, KnowledgeGraph.Commit.class, "id", commitId);
  }

  @Override
  public ServiceInfo getServiceInfo(String urn, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RUNTIME.GET_SERVICE_INFO, ServiceInfo.class, "urn", urn);
  }

  public <T extends RuntimeAsset> T getAsset(long id, Class<T> assetClass, Scope scope) {
    return client
        .withScope(scope)
        .get(ServicesAPI.RUNTIME.RETRIEVE_KNOWLEDGE_GRAPH_ASSET, assetClass, "id", id);
  }

  public Collection<KnowledgeGraph.LinkInfo> getLinkInfo(
      RuntimeAsset asset,
      GraphModel.Relationship.Direction direction,
      ContextScope scope,
      GraphModel.Relationship[] relationship) {
    return client
        .withScope(scope)
        .getCollection(
            ServicesAPI.RUNTIME.RETRIEVE_KNOWLEDGE_GRAPH_LINKS,
            KnowledgeGraph.LinkInfo.class,
            "sourceId",
            asset.getId(),
            "direction",
            direction,
            "types",
            relationship == null || relationship.length == 0
                ? null
                : org.integratedmodelling.common.utils.Utils.Strings.join(
                    Arrays.asList(relationship), ","));
  }

  //  public GraphQLClient graphClient() {
  //    return graphClient;
  //  }
}
