package org.integratedmodelling.common.services.client.runtime;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.GraphQLClient;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;

public class RuntimeClient extends ServiceClient implements RuntimeService {

  private GraphQLClient graphClient;

  public static RuntimeClient create(
      URL url, Identity identity, Parameters<Engine.Setting> settings) {
    return new RuntimeClient(url, identity, settings);
  }

  public static RuntimeClient createOffline(
          URL url, Identity identity, Parameters<Engine.Setting> settings) {
    return new RuntimeClient(url, identity, settings, false);
  }

  public static RuntimeClient createLocal(Identity identity, Parameters<Engine.Setting> settings) {
    return new RuntimeClient(Type.RUNTIME.localServiceUrl(), identity, settings);
  }

  public static RuntimeClient createLocalOffline(
      Identity identity, Parameters<Engine.Setting> settings) {
    return new RuntimeClient(Type.RUNTIME.localServiceUrl(), identity, settings, false);
  }

  public RuntimeClient(URL url, Identity identity, Parameters<Engine.Setting> settings /*,
      BiConsumer<Channel, Message>... listeners*/) {
    super(Type.RUNTIME, url, identity, settings, true);
  }

  private RuntimeClient(
      URL url, Identity identity, Parameters<Engine.Setting> settings, boolean connect) {
    super(Type.RUNTIME, url, identity, settings, connect);
  }

  public RuntimeClient(
      URL url, Identity identity, KlabService owner, Parameters<Engine.Setting> settings) {
    super(Type.RUNTIME, url, identity, settings, owner);
  }

  @SafeVarargs
  @Override
  public final String connect(BiConsumer<Channel, Message>... messageBiConsumers) {
    var ret = super.connect(messageBiConsumers);
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
  public CompletableFuture<Observation> submit(Observation observation, ContextScope scope) {

    if (observation.getId() > 0) {
      return CompletableFuture.completedFuture(observation);
    }

    ResolutionRequest resolutionRequest = new ResolutionRequest();
    resolutionRequest.setObservation(observation);
    resolutionRequest.setAgentName(Provenance.getAgent(scope).getName());
    resolutionRequest.setResolutionConstraints(scope.getResolutionConstraints());
    return client
        .withScope(scope)
        .postAsync(ServicesAPI.RUNTIME.SUBMIT_OBSERVATION, resolutionRequest, Observation.class);
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

  public GraphQLClient graphClient() {
    return graphClient;
  }
}
