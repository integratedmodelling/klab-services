package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

/** Binds typed targets after all prerequisite producers complete. Does not execute or mutate. */
final class SemanticUpdateTargets {
  private SemanticUpdateTargets() {}

  private record Identity(boolean durable, long value) {}

  static List<Observation> bind(
      Actuator.TargetBinding binding, Map<String, List<Observation>> completedMembers) {
    var result = new LinkedHashMap<Identity, Observation>();
    if (binding.getKind() == Actuator.TargetBinding.Kind.OBSERVATION) {
      add(result, Objects.requireNonNull(binding.getTarget(), "Missing observation target"));
    } else if (binding.getKind() == Actuator.TargetBinding.Kind.COHORT_MEMBERS) {
      if (binding.getSources().isEmpty())
        throw new IllegalArgumentException("Missing cohort producers");
      for (var source : binding.getSources()) {
        var members = completedMembers.get(source);
        if (members == null)
          throw new IllegalStateException("Cohort producer has not completed: " + source);
        for (var member : members) add(result, member);
      }
    } else throw new IllegalArgumentException("Missing target binding kind");
    return List.copyOf(result.values());
  }

  private static void add(Map<Identity, Observation> result, Observation member) {
    Objects.requireNonNull(member, "Null member");
    boolean durable = member.getId() > 0;
    long identity = durable ? member.getId() : member.getTransientId();
    if (!durable && identity == 0)
      throw new IllegalArgumentException("Member has no transaction identity");
    result.putIfAbsent(new Identity(durable, identity), member);
  }
}
