package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.runtime.ObserverResolution;
import org.integratedmodelling.klab.runtime.ObserverReuseSupport;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Resolve change for committed qualities once their digital twin contains observed occurrence. */
public final class ChangeProcessHandler implements ConsequenceEngine.Handler {
  @Override
  public ConsequenceEngine.Result evaluate(
      Consequence.Cause cause, KnowledgeGraph.Commit changes, ServiceContextScope scope) throws Exception {
    if (changes.getAddedObservations().isEmpty() && changes.getModifiedAssets().isEmpty())
      return result(Consequence.Outcome.NO_ACTION, "No observations changed", List.of());
    checkCancelled(scope);
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    // Revisit old qualities when the first occurrence arrives, and new qualities thereafter.
    var observations = graph.query(Observation.class, scope).run(scope).stream()
        .filter(o -> o.getId() > 0 && !o.isEmpty() && o.getObservable() != null).toList();
    checkCancelled(scope);
    if (observations.stream().noneMatch(ChangeProcessHandler::occurrent))
      return result(Consequence.Outcome.NO_ACTION, "The context has no observed occurrence", List.of());

    var qualities = new LinkedHashMap<Long, List<Observation>>();
    var bearers = new LinkedHashMap<Long, Observation>();
    int deferred = 0;
    for (var quality : observations.stream().sorted(Comparator.comparingLong(Observation::getId)).toList()) {
      checkCancelled(scope);
      if (!quality.getObservable().is(SemanticType.QUALITY)
          || quality.getObservable().isAbstract() || quality.getResolvedCoverage() <= 0) continue;
      if (!(scope.getParentOf(quality) instanceof Observation bearer) || bearer.getId() <= 0) {
        deferred++;
        continue;
      }
      bearers.put(bearer.getId(), bearer);
      qualities.computeIfAbsent(bearer.getId(), id -> new ArrayList<>()).add(quality);
    }
    var submitted = new ArrayList<Long>();
    int failures = 0;
    for (var entry : qualities.entrySet()) {
      var bearer = bearers.get(entry.getKey());
      var targetScope = scope.within(bearer).withResolutionConstraints(ResolutionConstraint.of(
          ResolutionConstraint.Type.ChangeQualities,
          entry.getValue().stream().map(Observation::getObservable).toArray()));
      for (var quality : entry.getValue()) {
        checkCancelled(scope);
        var support = grid(bearer.getGeometry()) ? bearer.getGeometry() : quality.getGeometry();
        if (support == null || support.isEmpty()) { deferred++; continue; }
        var requestScope = targetScope.withResolutionConstraints(
            ResolutionConstraint.of(ResolutionConstraint.Type.Geometry, support));
        // Re-read durable writer links: a joint process selected for the first quality may
        // already provide change for the next. Do not maintain a competing resolution cache.
        if (coveredByProcess(quality, support, requestScope)) continue;
        var change = quality.getObservable().builder(requestScope)
            .as(UnarySemanticOperator.CHANGE).optional(true).buildObservable();
        var request = requestScope.observation(change).geometry(support).build();
        if (request.isEmpty()) { failures++; continue; }
        var existing = requestScope.getObservation(request);
        var coverage = existing == null ? null : ObserverReuseSupport.coverage(existing, support);
        if (coverage != null && coverage.isComplete()
            && !ObserverResolution.deferred(existing, change, requestScope)) continue;
        checkCancelled(scope);
        var task = requestScope.observation(change).geometry(support).submit();
        try {
          var resolved = awaitSubmissionDrain(task);
          checkCancelled(scope);
          if (resolved == null || resolved.isEmpty() || resolved.getId() <= 0) {
            if (resolved != null && org.integratedmodelling.klab.api.utils.Utils.Notifications
                .hasErrors(resolved.getNotifications())) failures++;
            else deferred++;
          } else submitted.add(quality.getId());
        } catch (ExecutionException failure) {
          failures++;
        }
      }
    }
    return result(failures > 0 ? Consequence.Outcome.FAILED
            : deferred > 0 ? Consequence.Outcome.DEFERRED
            : submitted.isEmpty() ? Consequence.Outcome.NO_ACTION : Consequence.Outcome.APPLIED,
        "Change resolution: " + submitted.size() + " qualities resolved, " + deferred
            + " deferred, " + failures + " failed", submitted);
  }

  /**
   * An uncancelled local submission settles only after its commit/fail continuation has drained.
   * Cancelling its exposed CompletableFuture merely signals cancellation; it does not join native
   * work. Keep owning this operation through interruption, letting the scope token cancel it.
   */
  static Observation awaitSubmissionDrain(java.util.concurrent.CompletableFuture<Observation> task)
      throws ExecutionException {
    boolean interrupted = false;
    try {
      for (;;) {
        try { return task.get(); }
        catch (InterruptedException cancellation) { interrupted = true; }
        catch (java.util.concurrent.CancellationException noDrainReceipt) {
          // A third party cancelled the wrapper, destroying our completion receipt. There is no
          // safe quiescence inference: retain ownership so close refuses graph/storage disposal.
          var unknownDrain = new java.util.concurrent.CountDownLatch(1);
          for (;;) {
            try { unknownDrain.await(); }
            catch (InterruptedException cancellation) { interrupted = true; }
          }
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }
  private static void checkCancelled(ServiceContextScope scope) throws InterruptedException {
    if (scope.isInterrupted() || Thread.currentThread().isInterrupted())
      throw new InterruptedException("Consequence evaluation cancelled");
  }

  private static ConsequenceEngine.Result result(Consequence.Outcome outcome, String text, List<Long> ids) {
    return new ConsequenceEngine.Result(outcome, text, ids);
  }

  static boolean occurrent(Observation observation) {
    var observable = observation.getObservable();
    return grid(observation.getGeometry())
        || observable != null && observable.is(SemanticType.AGENT)
            && grid(observation.geometry(Observation.GeometryRelationship.PERCEIVES))
        || observable != null && !observable.getSemantics().isCollective()
            && (observable.is(SemanticType.PROCESS) || observable.is(SemanticType.EVENT));
  }

  static boolean grid(Geometry geometry) {
    if (geometry == null || geometry.isEmpty() || geometry.dimension(Geometry.Dimension.Type.TIME) == null)
      return false;
    var time = GeometryRepository.INSTANCE.scale(geometry).getTime();
    return time != null && (time.getTimeType() == Time.Type.GRID || time.getTimeType() == Time.Type.REAL);
  }

  static boolean coveredByProcess(Observation quality, Geometry support, org.integratedmodelling.klab.api.scope.ContextScope scope) {
    for (var link : scope.getDigitalTwin().getKnowledgeGraph().getLinks(
        quality, GraphModel.Relationship.Direction.INCOMING, scope, GraphModel.Relationship.AFFECTS)) {
      if (!ProcessPlan.INFLUENCE.equals(link.properties().get(ProcessPlan.EDGE_ROLE))
          || !(link.source() instanceof Observation process)
          || !process.getObservable().is(SemanticType.PROCESS)
          || process.getResolvedCoverage() <= 0
          || !samePerspective(process, quality, scope))
        continue;
      var parent = scope.getParentOf(process);
      if (parent == null || parent.getId() != scope.getContextObservation().getId()) continue;
      var coverage = ObserverReuseSupport.coverage(process, support);
      if (coverage != null && coverage.isComplete()) return true;
    }
    return false;
  }

  private static boolean samePerspective(Observation process, Observation quality,
      org.integratedmodelling.klab.api.scope.ContextScope scope) {
    var requested = quality.getObservable().getObserverSemantics();
    if (requested == null && scope.getObserver() != null)
      requested = scope.getObserver().getObservable().asConcept();
    var provided = process.getObservable().getObserverSemantics();
    if (requested == null) return provided == null;
    if (provided == null) return false; // normal resolver decides whether absolute fallback wins
    var reasoner = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    var capabilities = reasoner.capabilities(scope);
    return capabilities != null
        && reasoner.semanticDistance(provided, requested, null, capabilities.getKnowledgeRevision()) >= 0;
  }
}


