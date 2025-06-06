package org.integratedmodelling.common.services.client.resolver;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.ResolverCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class ResolverClient extends ServiceClient implements Resolver {

  public static ResolverClient create(
      URL url, Identity identity, Parameters<Engine.Setting> settings) {
    return new ResolverClient(url, identity, settings);
  }

  public static ResolverClient createOffline(
      URL url, Identity identity, Parameters<Engine.Setting> settings) {
    return new ResolverClient(url, identity, settings, false);
  }

  public static ResolverClient createLocal(Identity identity, Parameters<Engine.Setting> settings) {
    return new ResolverClient(Type.RESOLVER.localServiceUrl(), identity, settings);
  }

  public static ResolverClient createLocalOffline(
      Identity identity, Parameters<Engine.Setting> settings) {
    return new ResolverClient(Type.RESOLVER.localServiceUrl(), identity, settings, false);
  }

  public ResolverClient(
      URL url, Identity identity, KlabService owner, Parameters<Engine.Setting> settings) {
    super(Type.RESOLVER, url, identity, settings, owner);
  }

  public ResolverClient(URL url, Identity identity, Parameters<Engine.Setting> settings) {
    super(Type.RESOLVER, url, identity, settings, true);
  }

  private ResolverClient(
      URL url, Identity identity, Parameters<Engine.Setting> settings, boolean connect) {
    super(Type.RESOLVER, url, identity, settings, connect);
  }

  public ResolverClient(URL url, Parameters<Engine.Setting> settings) {
    super(url, settings);
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
                    ResolverCapabilitiesImpl.class,
                    Notification.Mode.Silent);

      } catch (Throwable t) {
        // not ready yet
        return null;
      }
    }
    return (Capabilities) this.capabilities;
  }

  @Override
  public CompletableFuture<Dataflow> resolve(Observation observation, ContextScope contextScope) {
    ResolutionRequest request = new ResolutionRequest();
    request.setObservation(observation);
    request.getResolutionConstraints().addAll(contextScope.getResolutionConstraints());
    if (contextScope.getContextObservation() != null
        && contextScope.getContextObservation().getId() < 0) {
      request
          .getResolutionConstraints()
          .add(
              ResolutionConstraint.of(
                  ResolutionConstraint.Type.UnresolvedContextObservation,
                  contextScope.getContextObservation()));
    }
    return client
        .withScope(contextScope)
        .postAsync(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION, request, Dataflow.class);
  }

  @Override
  public String encodeDataflow(Dataflow dataflow) {
    // TODO Auto-generated method stub
    return null;
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
  public String registerSession(SessionScope scope, Federation federation) {
    ScopeRequest request = new ScopeRequest();
    request.setName(scope.getName());

    var hasMessaging =
        scope.getParentScope() instanceof MessagingChannel messagingChannel
            && messagingChannel.hasMessaging()
            && federation != null;

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

  /**
   * When called as a slave from a service, add the sessionId parameter to build a peer scope at the
   * remote service side. Send the calling service ID from the scope so that communication can be
   * reconstructed if the call comes from a runtime service.
   *
   * @param scope a client scope that should record the ID for future communication. If the ID is
   *     null, the call has failed.
   * @return
   */
  @Override
  public String registerContext(ContextScope scope, Federation federation) {

    ScopeRequest request = new ScopeRequest();
    request.setName(scope.getName());

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

    return ret;
  }
}
