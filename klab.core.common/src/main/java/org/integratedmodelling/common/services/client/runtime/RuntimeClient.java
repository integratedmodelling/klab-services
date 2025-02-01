package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.GraphQLClient;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.AssetRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class RuntimeClient extends ServiceClient implements RuntimeService {

  private GraphQLClient graphClient;

  public RuntimeClient(
      URL url,
      Identity identity,
      List<ServiceReference> services,
      Parameters<Engine.Setting> settings,
      BiConsumer<Channel, Message>... listeners) {
    super(Type.RUNTIME, url, identity, settings, services, listeners);
  }

  public RuntimeClient(
      URL url, Identity identity, KlabService owner, Parameters<Engine.Setting> settings) {
    super(Type.RUNTIME, url, identity, List.of(), settings, owner);
  }

  @Override
  protected String establishConnection() {
    var ret = super.establishConnection();
    this.graphClient =
        new GraphQLClient(this.getUrl() + ServicesAPI.RUNTIME.DIGITAL_TWIN_GRAPH, ret);
    return ret;
  }

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
        client./*withScope(scope.getParentScope()).*/ post(
            ServicesAPI.CREATE_SESSION,
            request,
            String.class,
            "id",
            scope instanceof ServiceSideScope serviceSideScope ? serviceSideScope.getId() : null);

    var brokerURI = client.getResponseHeader(ServicesAPI.MESSAGING_URN_HEADER);
    if (brokerURI != null && scope instanceof MessagingChannelImpl messagingChannel) {
      var queues =
          getQueuesFromHeader(scope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
      messagingChannel.setupMessaging(brokerURI, ret, queues);
    }

    return ret;
  }

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

    request.getRuntimeServices().add(getUrl());

    if (isLocal()
        && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient
        && reasonerClient.isLocal()) {
      request.getReasonerServices().add(reasonerClient.getUrl());
    }

    if (hasMessaging) {
      // TODO setup desired request. This will send no header and use the defaults.
    }

    var ret =
        client
            .withScope(scope.getParentScope())
            .withHeader(ServicesAPI.SERVICE_ID_HEADER, scope.getHostServiceId())
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
      if (scope instanceof ClientContextScope clientContextScope) {
        clientContextScope.createDigitalTwin(ret);
      }
    }

    return ret;
  }

  @Override
  public long submit(Observation observation, ContextScope scope) {
    ResolutionRequest resolutionRequest = new ResolutionRequest();
    resolutionRequest.setObservation(observation);
    resolutionRequest.setAgentName(Provenance.getAgent(scope).getName());
    resolutionRequest.setResolutionConstraints(scope.getResolutionConstraints());
    return client
        .withScope(scope)
        .post(ServicesAPI.RUNTIME.SUBMIT_OBSERVATION, resolutionRequest, Long.class);
  }

  @Override
  public CompletableFuture<Observation> resolve(long id, ContextScope scope) {

    /*
    Set up the task to track the messages. We do this before invoking the method so it's guaranteed to
    not return before we can notice.
     */
    var ret =
        scope.trackMessages(
            Message.match(
                    Message.MessageClass.ObservationLifecycle,
                    Message.MessageType.ResolutionAborted,
                    Message.MessageType.ResolutionSuccessful)
                .when((message) -> message.getPayload(Observation.class).getId() == id),
            (message) -> {
              var observation = message.getPayload(Observation.class);
              if (message.getMessageType() == Message.MessageType.ResolutionSuccessful) {
                scope.info(
                    "Resolution of "
                        + observation
                        + " successful with coverage "
                        + observation.getResolvedCoverage());
                return message.getPayload(Observation.class);
              }
              scope.info("Resolution of " + observation + " failed");
              return observation;
            });

    var request = new ResolutionRequest();
    request.setResolutionConstraints(scope.getResolutionConstraints());
    request.setObservationId(id);

    // this returns the URN of the observation/task or null - we can ignore it at this stage.
    client.withScope(scope).post(ServicesAPI.RUNTIME.START_RESOLUTION, request, String.class);

    return ret;
  }

  @Override
  public Observation runDataflow(Dataflow<Observation> dataflow, ContextScope contextScope) {
    return null;
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    if (this.capabilities == null) {
      try {
        this.capabilities =
            client
                .withScope(scope)
                .get(
                    ServicesAPI.CAPABILITIES,
                    RuntimeCapabilitiesImpl.class,
                    Notification.Mode.Silent);
      } catch (Throwable t) {
        // not ready yet
        return null;
      }
    }
    return (Capabilities) this.capabilities;
  }

  @Override
  public List<SessionInfo> getSessionInfo(Scope scope) {
    return client
        .withScope(scope)
        .getCollection(ServicesAPI.RUNTIME.GET_SESSION_INFO, SessionInfo.class);
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

  @Override
  public <T extends RuntimeAsset> List<T> queryKnowledgeGraph(
      KnowledgeGraph.Query<T> knowledgeGraphQuery, ContextScope scope) {
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
  public <T extends RuntimeAsset> List<T> retrieveAssets(
      ContextScope contextScope, Class<T> assetClass, Object... queryParameters) {
    AssetRequest request = new AssetRequest();
    RuntimeAsset.Type type = RuntimeAsset.Type.forClass(assetClass);

    for (var object : queryParameters) {
      switch (object) {
        case Observable observable -> request.setObservable(observable);
        case Observation observation -> request.setContextObservation(observation);
        case Long id -> request.setId(id);
        case String string -> request.setName(string);
        case Geometry geometry -> request.setGeometry(geometry);
        case Metadata metadata -> request.getMetadata().putAll(metadata);
        case RuntimeAsset.Type assetType -> type = assetType;
        default -> throw new KlabIllegalStateException("Unexpected value: " + object);
      }
    }
    request.setKnowledgeClass(type);

    return client
        .withScope(contextScope)
        .postCollection(ServicesAPI.RUNTIME.RETRIEVE_ASSET, request, assetClass);
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

  public GraphQLClient graphClient() {
    return graphClient;
  }
}
