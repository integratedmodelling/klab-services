package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Runtime-owned child lifecycle. Called once after staging, before classification can commit. */
final class CharacterizationLifecycle {
  static void run(
      List<MemberClassifierExecutor.PendingAttribution> pending,
      ServiceContextScope classificationScope,
      RuntimeService runtime) {
    var visited = new HashSet<String>();
    for (var attribution : pending) {
      var key = attribution.member().getId() + ":" + attribution.predicate().getUrn();
      if (!visited.add(key)) continue;
      var member = classificationScope.getObservation(attribution.member().getId());
      if (member == null) throw new IllegalStateException("Missing staged classified member");
      var memberScope = classificationScope.within(member);
      var observable =
          Observable.promote(attribution.predicate())
              .builder(memberScope)
              .of(attribution.originalObservable().getSemantics().singular())
              .buildObservable();
      if (observable == null
          || observable.getContextualization() != Contextualization.CHARACTERIZATION)
        throw new IllegalStateException("Invalid concrete characterization observable");
      var activity =
          Activity.of(
              Activity.Type.RESOLUTION,
              classificationScope.getActivity(),
              "Characterization resolution of " + observable);
      var resolutionScope = memberScope.executing(activity);
      try {
        var builder = new Observation.NaiveBuilder(observable, resolutionScope);
        builder.geometry(member.getGeometry());
        var request = builder.make();
        var plan =
            resolutionScope.getService(Resolver.class).resolve(request, resolutionScope).join();
        validate(plan);
        RuntimeService.attachResolutionDiagnostics(plan, activity);
        activity.getMetadata().put("resolutionOutcome", plan.getResolutionOutcome().name());
        if (plan.getResolutionOutcome() != Dataflow.ResolutionOutcome.NO_MODEL) {
          runtime.executeCharacterization(plan, member, attribution.event(), resolutionScope);
        }
        if (resolutionScope.commit() < 0)
          throw new IllegalStateException("Characterization resolution commit failed");
      } catch (Throwable failure) {
        resolutionScope.fail(failure);
        throw new IllegalStateException("Member characterization failed", failure);
      }
    }
  }

  static void validate(Dataflow plan) {
    if (plan == null
        || plan.isEmpty()
        || plan.getResolutionOutcome() == Dataflow.ResolutionOutcome.FAILED
        || org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(
            plan.getNotifications()))
      throw new IllegalStateException("Characterization resolution failed");
    if (plan.getResolutionOutcome() == Dataflow.ResolutionOutcome.NO_MODEL) {
      if (!plan.getComputation().isEmpty())
        throw new IllegalStateException("NO_MODEL plan contains work");
    } else if (plan.getComputation().isEmpty()) {
      throw new IllegalStateException("Missing explicit no-model outcome");
    }
  }
}
