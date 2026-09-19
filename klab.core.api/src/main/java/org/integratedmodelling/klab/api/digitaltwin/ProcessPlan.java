package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Observable;

/** Semantic bindings, not observations: CREATED bindings have no identity or INIT state. */
public record ProcessPlan(
    int version, long bearerId, String model, List<Binding> bindings, List<Obligation> obligations)
    implements Serializable {
  public static final String DATA_KEY = "klab.process.plan";
  public static final String EDGE_ROLE = "occurrenceRole";
  public static final String PREREQUISITE = "PREREQUISITE";
  public static final String INFLUENCE = "INFLUENCE";

  public enum Effect {
    INPUT,
    AFFECTED,
    CREATED
  }

  public record Binding(
      String name,
      Observable observable,
      Effect effect,
      boolean dependency,
      boolean assigned,
      List<String> relations)
      implements Serializable {
    public Binding {
      if (name == null
          || name.isBlank()
          || observable == null
          || effect == null
          || !observable.is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY))
        throw new IllegalArgumentException("Invalid process quality binding");
      relations = List.copyOf(relations);
    }

    public Binding(
        String name, Observable observable, Effect effect, boolean dependency, boolean assigned) {
      this(name, observable, effect, dependency, assigned, List.of());
    }
  }

  public record Obligation(String semantics, Effect effect, String relation)
      implements Serializable {}

  public ProcessPlan {
    if (version != 1 || bearerId == 0 || model == null)
      throw new IllegalArgumentException("Invalid process bearer plan");
    bindings = List.copyOf(bindings);
    if (bindings.stream().map(Binding::name).distinct().count() != bindings.size())
      throw new IllegalArgumentException("Duplicate process binding names");
    obligations = List.copyOf(obligations);
  }

  public Binding binding(String name) {
    return bindings.stream()
        .filter(binding -> binding.name().equals(name))
        .findFirst()
        .orElse(null);
  }
}
