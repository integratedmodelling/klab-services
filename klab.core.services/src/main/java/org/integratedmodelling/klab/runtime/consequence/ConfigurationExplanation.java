package org.integratedmodelling.klab.runtime.consequence;

import java.util.Objects;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

/** Request purpose only. It grants no source, access, native-write or semantic authority. */
public final class ConfigurationExplanation {
  private ConfigurationExplanation() {}

  public static boolean requested(Observation observation, ContextScope scope) {
    var target = scope.getConstraint(ResolutionConstraint.Type.ConfigurationExplanation, Long.class);
    if (target == null) return false;
    if (target == Observation.UNASSIGNED_ID || target == Observation.QUERY_ID)
      throw new IllegalArgumentException("Configuration explanation requires an actual detected target");
    // The request purpose is inherited by ordinary model dependencies; only its exact target
    // receives the exception to native-existence reuse.
    if (observation == null || observation.getId() != target) return false;
    var cause = scope.getConsequenceCause();
    var cycle = scope.getResolutionCycleBinding();
    if (observation == null || observation.getObservable() == null
        || !observation.getObservable().is(SemanticType.CONFIGURATION)
        || cause == null || cycle == null
        || cause.kind() != Consequence.Kind.CONFIGURATION_DETECTION
        || !Objects.equals(cause.contextId(), scope.getId())
        || !Objects.equals(cycle.contextId(), scope.getId()))
      throw new IllegalArgumentException("Configuration explanation does not identify this detected target");
    return true;
  }
}
