package org.integratedmodelling.common.services.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.integratedmodelling.common.services.ResolverCapabilitiesImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
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
    request.setObservation(Observation.forTransport(observation));
    request.setResolutionConstraints(scopeForTransport(contextScope));
    return client
        .withScope(contextScope)
        .postAsync(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION, request, Dataflow.class);
  }

  public static ResolutionConstraint forTransport(ResolutionConstraint constraint) {
    var payload = constraint.payload(Object.class).stream();
    return ResolutionConstraint.of(
        constraint.getType(),
        (constraint.getType() == ResolutionConstraint.Type.UnresolvedContextObservation
                || ResolutionConstraint.isRelationshipSnapshot(constraint.getType())
                ? payload.map(value -> Observation.forTransport((Observation) value))
                : payload.map(Geometry::valueForTransport))
            .toArray());
  }

  /** Share the same pending within/between scope contract with downstream Reasoner calls. */
  public static java.util.List<ResolutionConstraint> scopeForTransport(ContextScope scope) {
    var constraints = new ArrayList<ResolutionConstraint>();
    boolean inheritedEndpoints = false;
    for (var constraint : scope.getResolutionConstraints()) {
      if (ResolutionConstraint.isRelationshipSnapshot(constraint.getType())) inheritedEndpoints = true;
      else constraints.add(forTransport(constraint));
    }
    if (scope.getContextObservation() != null && scope.getContextObservation().getId() < 0)
      constraints.add(ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedContextObservation,
          Observation.forTransport(scope.getContextObservation())));
    var source = scope.getSourceObservation(); var target = scope.getTargetObservation();
    if ((source == null) != (target == null))
      throw new IllegalArgumentException("A transported relationship scope requires both endpoints");
    if (source != null && (source.getId() == target.getId()
        || source.getId() == Observation.UNASSIGNED_ID || source.getId() == Observation.QUERY_ID
        || target.getId() == Observation.UNASSIGNED_ID || target.getId() == Observation.QUERY_ID))
      throw new IllegalArgumentException("A transported relationship scope requires distinct assigned instance IDs");
    if (source != null && (source.getId() < 0 || target.getId() < 0 || inheritedEndpoints
        || transactionOwns(scope, source.getId()) || transactionOwns(scope, target.getId()))) {
      var from = ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedSourceObservation, source);
      var to = ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedTargetObservation, target);
      var validated = ResolutionConstraint.unresolvedRelationshipScope(scope, from, to);
      constraints.add(ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedSourceObservation,
          Observation.forTransport(validated.source())));
      constraints.add(ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedTargetObservation,
          Observation.forTransport(validated.target())));
    }
    return constraints;
  }

  private static boolean transactionOwns(ContextScope scope, long id) {
    for (var transaction = scope.getCurrentTransaction(); transaction != null; transaction = transaction.getParent())
      for (var asset : transaction.assets())
        if (asset instanceof Observation observation && observation.getId() == id) return true;
    return false;
  }

  public static Observation forTransport(Observation observation) {
    return Observation.forTransport(observation);
  }

  @Override
  public String encodeDataflow(Dataflow dataflow) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Resource submitResource(Observation observation, ContextScope contextScope) {
    return client
        .withScope(contextScope)
        .post(
            ServicesAPI.RESOLVER.SUBMIT_RESOURCE,
            Observation.forTransport(observation),
            Resource.class);
  }

  @Override
  public List<Resource> getSubmittedResources(ContextScope scope) {
    return client
        .withScope(scope)
        .getCollection(ServicesAPI.RESOLVER.GET_SUBMITTED_RESOURCES, Resource.class);
  }
}
