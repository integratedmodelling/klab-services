package org.integratedmodelling.common.services.client.runtime;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.common.services.client.scope.ClientScopeManager;
import org.integratedmodelling.common.services.client.scope.ClientSessionScope;
import org.integratedmodelling.common.services.client.scope.ClientUserScope;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.utils.Utils;

public class RuntimeClient extends ServiceClient implements RuntimeService {

  //  private GraphQLClient graphClient;

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
  public final String connect(BiConsumer<ServiceStatus, Boolean>... messageBiConsumers) {
    var ret = super.connect(messageBiConsumers);
    //    this.graphClient =
    //        new GraphQLClient(this.getUrl() + ServicesAPI.RUNTIME.DIGITAL_TWIN_GRAPH, ret);
    return ret;
  }

  @Override
  public String registerNewSession(SessionScope scope, Federation federation) {

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
        client
            .withHeader(
                ServicesAPI.MESSAGING_URL_HEADER,
                federation == null ? null : federation.getBroker())
            .withHeader(
                ServicesAPI.FEDERATION_ID_HEADER, federation == null ? null : federation.getId())
            .post(
                ServicesAPI.CREATE_SESSION,
                request,
                String.class,
                "id",
                scope instanceof ServiceSideScope serviceSideScope
                    ? serviceSideScope.getId()
                    : null);

    if (federation != null && scope instanceof MessagingChannelImpl messagingChannel) {
      var queues =
          getQueuesFromHeader(scope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
      messagingChannel.setupMessaging(federation, ret, queues);
    }

    return ret;
  }

  @Override
  public String registerNewContext(ContextScope scope, Federation federation) {

    ScopeRequest request = new ScopeRequest();
    request.setName(scope.getName());
    request.setConfiguration(scope.getDigitalTwinConfiguration());

    var runtime = scope.getService(RuntimeService.class);
    var hasMessaging =
        scope.getParentScope() instanceof MessagingChannel messagingChannel
            && messagingChannel.hasMessaging()
            && federation != null;

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

    var ret =
        client
            .withScope(scope.getParentScope())
            .withHeader(ServicesAPI.SERVICE_ID_HEADER, scope.getHostServiceId())
            .withHeader(
                ServicesAPI.MESSAGING_URL_HEADER,
                federation == null ? null : federation.getBroker())
            .withHeader(
                ServicesAPI.FEDERATION_ID_HEADER, federation == null ? null : federation.getId())
            .post(
                ServicesAPI.CREATE_CONTEXT,
                request,
                String.class,
                "id",
                scope instanceof ServiceSideScope serviceSideScope
                    ? serviceSideScope.getId()
                    : null);

    if (hasMessaging) {
      if (scope instanceof MessagingChannelImpl messagingChannel) {
        var queues =
            getQueuesFromHeader(
                scope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
        messagingChannel.setupMessaging(federation, ret, queues);
      }
    }
    if (scope instanceof ClientContextScope clientContextScope) {
      clientContextScope.createDigitalTwin(ret);
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
  public ContextScope connectContext(DigitalTwin.Configuration configuration, UserScope userScope) {

    var ret = ClientScopeManager.INSTANCE.getScope(configuration.getId(), ClientContextScope.class);
    if (ret != null) {
      return ret;
    }

    ScopeRequest request = new ScopeRequest();
    request.setConfiguration(configuration);
    request.getRuntimeServices().add(getUrl());
    request
        .getResolverServices()
        .addAll(userScope.getServices(Resolver.class).stream().map(s -> s.getUrl()).toList());
    request
        .getReasonerServices()
        .addAll(userScope.getServices(Reasoner.class).stream().map(s -> s.getUrl()).toList());
    request
        .getResourceServices()
        .addAll(
            userScope.getServices(ResourcesService.class).stream().map(s -> s.getUrl()).toList());

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

      ret =
          new ClientContextScope(sessionScope, this, configuration) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
              return RuntimeService.class.equals(serviceClass)
                  ? (T) service
                  : userScope.getService(serviceClass);
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
              return RuntimeService.class.equals(serviceClass)
                  ? List.of((T) service)
                  : userScope.getServices(serviceClass);
            }
          };
      ret.setId(configuration.getId());
      ClientScopeManager.INSTANCE.register(ret);
      return ret;
    }

    return null;
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

  //  public GraphQLClient graphClient() {
  //    return graphClient;
  //  }
}
