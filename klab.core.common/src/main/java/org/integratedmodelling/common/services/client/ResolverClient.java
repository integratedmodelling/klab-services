package org.integratedmodelling.common.services.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.integratedmodelling.common.services.ResolverCapabilitiesImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.*;

public class ResolverClient extends BaseServiceClient implements Resolver {

  private Capabilities capabilities;

  ResolverClient(
      ServiceClientCatalog.ClientMonitor monitor,
      Scope userScope,
      Settings settings,
      BiConsumer<ServiceStatus, Boolean>... statusListeners) {
    super(monitor, userScope, settings, statusListeners);
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    return capabilities == null
        ? getCapabilities(scope, ResolverCapabilitiesImpl.class)
        : capabilities;
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
}
