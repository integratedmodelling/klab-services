package org.integratedmodelling.klab.runtime.language;

import java.util.LinkedHashMap;
import java.util.List;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Shared semantics for occurrence binding and implicit process outputs. */
public final class OccurrentSemantics {
  private OccurrentSemantics() {}

  public static ProcessPlan.Bearer bearer(
      Observable quality, Observable occurrent, Observable context, Reasoner reasoner) {
    var inherent = reasoner.inherent(quality);
    // Inherency to the occurrence takes precedence, including inherited restrictions.
    if (inherent != null && (occurrent.getSemantics().equals(inherent)
        || reasoner.is(occurrent, inherent))) return ProcessPlan.Bearer.OCCURRENT;
    if (context != null && inherent != null
        && (context.getSemantics().equals(inherent) || reasoner.is(context, inherent))
        && (reasoner.affectedBy(quality, occurrent) || reasoner.createdBy(quality, occurrent)))
      return ProcessPlan.Bearer.CONTEXT;
    throw new KlabValidationException("Quality " + quality.getUrn()
        + " must inhere to " + occurrent.getUrn()
        + " or to its context with an affects/creates relation");
  }

  /** Only declared, semantically affected/created qualities advertise change resolution. */
  public static List<Observable> changes(
      Observable process, List<Observable> qualities, Scope scope) {
    if (!process.is(SemanticType.PROCESS)) return List.of();
    var reasoner = scope.getService(Reasoner.class);
    var changes = new LinkedHashMap<String, Observable>();
    for (var quality : qualities) {
      if (!quality.is(SemanticType.QUALITY)
          || !(reasoner.affectedBy(quality, process) || reasoner.createdBy(quality, process))) continue;
      var change = quality.builder(scope).as(UnarySemanticOperator.CHANGE).buildObservable();
      if (change == null) {
        throw new KlabValidationException("Cannot infer change in " + quality.getUrn()
            + " for process " + process.getUrn());
      }
      changes.putIfAbsent(change.getUrn(), change);
    }
    return List.copyOf(changes.values());
  }
}
