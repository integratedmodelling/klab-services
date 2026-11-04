package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.runtime.storage.LocalTemporalWriteSet;
import org.integratedmodelling.klab.services.runtime.TemporalGeometry;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Ordinary post-commit AFFECTS propagation. It is deliberately independent of semantic-engine
 * enablement. Each derived computation is a real guarded native transaction in the source cycle.
 */
final class MembershipRecompute {
  static final String RECEIPTS = "klab.scheduler.membershipRecomputations";

  static Scheduler.MembershipPropagationOutcome execute(
      SchedulerImpl scheduler,
      DigitalTwinImpl.CommittedMembershipChange receipt,
      ServiceContextScope origin,
      org.integratedmodelling.klab.api.data.KnowledgeGraph graph,
      OptionalLong instant) {
    var change = receipt.require(origin);
    var cohort = graph.getAsset(change.cohortId(), origin, Cohort.class);
    if (cohort == null || !Objects.equals(cohort.getObservable().getUrn(), change.observable()))
      return Scheduler.MembershipPropagationOutcome.DEFERRED;
    var support = Geometry.create(change.support());
    long bearer =
        origin.getContextObservation() == null ? 0 : origin.getContextObservation().getId();
    var targets = ComputationalClosure.ordered(cohort, bearer, support, graph, origin);
    boolean worked = false;
    for (var target : targets) {
      ServiceContextScope execution = null;
      try {
        var previous = target.getMetadata().get(RECEIPTS);
        String key =
            change.cohortId()
                + ":"
                + change.afterRevision()
                + ":"
                + receipt.commitId()
                + ":"
                + target.getId();
        // Persisted metadata is descriptive, never skip authority. The actual native token cannot
        // be
        // reconstructed across restart; process-local successful receipts live only until cycle
        // drain.
        if (scheduler.membershipCompleted(origin, key, false)) continue;
        if (instant.isEmpty()
            && target.getGeometry().dimension(Geometry.Dimension.Type.TIME) != null
            && !org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE
                .scale(target.getGeometry())
                .getTime()
                .is(
                    org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Type
                        .INITIALIZATION)) return Scheduler.MembershipPropagationOutcome.DEFERRED;
        // Per-submit opaque admissions belong to the completed source request. The actual new
        // native
        // membership receipt supplies this derived transaction's source/read-set authority instead.
        var original = origin.forConsequence(null).of(target);
        if (original.isInterrupted())
          throw new InterruptedException("Membership recomputation cancelled");
        var activity =
            Activity.of(
                Activity.Type.forContextualization(target.getObservable().getContextualization()),
                target,
                "Recomputation after committed collective membership change");
        org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(
            activity, original.getObserver());
        activity.getMetadata().put("klab.scheduler.membershipCommit", receipt.commitId());
        activity.getMetadata().put("klab.scheduler.membershipActivity", receipt.activityId());
        execution = original.executingFresh(activity, target);
        var transaction = execution.getCurrentTransaction();
        receipt.guardConsumer(transaction, origin, execution, target);
        var sourceActivity = graph.getAsset(receipt.activityId(), origin, Activity.class);
        if (sourceActivity == null)
          throw new IllegalStateException("Committed membership source provenance is unavailable");
        transaction.link(sourceActivity, activity, GraphModel.Relationship.TRIGGERED);
        var event = MembershipInputChange.admitted(receipt, origin, execution, target, instant);
        var geometry = TemporalGeometry.localize(target.getGeometry(), event);
        var executor = scheduler.restoreExecutor(target, execution);
        if (executor == null)
          throw new IllegalStateException(
              "Collective consumer has no restorable source-admitted computation");
        var writes = new LocalTemporalWriteSet(event, execution);
        event.requireInvocation(execution, target, target.getGeometry(), geometry);
        if (!executor.apply(geometry, event, execution))
          throw new IllegalStateException("Collective membership computation failed");
        writes.prepare();
        target
            .getMetadata()
            .put(RECEIPTS, Utils.Json.asString(Map.of("receipt", key, "outcome", "APPLIED")));
        transaction.afterRollback(
            () -> {
              if (previous == null) target.getMetadata().remove(RECEIPTS);
              else target.getMetadata().put(RECEIPTS, previous);
            });
        transaction.update(target);
        if (execution.commit() <= 0)
          throw new IllegalStateException("Collective membership recomputation did not commit");
        scheduler.membershipCompleted(origin, key, true);
        worked = true;
      } catch (Throwable failure) {
        if (execution != null)
          try {
            execution.fail(failure);
          } catch (Throwable cleanup) {
            failure.addSuppressed(cleanup);
          }
        origin.error(failure);
        return Scheduler.MembershipPropagationOutcome.FAILED;
      }
    }
    return worked
        ? Scheduler.MembershipPropagationOutcome.APPLIED
        : Scheduler.MembershipPropagationOutcome.ALREADY_EXECUTED;
  }
}
