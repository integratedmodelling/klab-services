package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Observable;

/** Semantic bindings, not observations: CREATED bindings have no identity or INIT state. */
public record ProcessPlan(
    int version, long bearerId, String model, List<Binding> bindings, List<Obligation> obligations,
    List<org.integratedmodelling.klab.api.knowledge.SemanticInfluence> descriptiveLinks)
    implements Serializable {
  public static final String DATA_KEY = "klab.process.plan";
  public static final String EDGE_ROLE = "occurrenceRole";
  public static final String PREREQUISITE = "PREREQUISITE";
  public static final String INFLUENCE = "INFLUENCE";
  public static final String DESCRIPTIVE = "DESCRIPTIVE";

  public ProcessPlan(int version, long bearerId, String model, List<Binding> bindings, List<Obligation> obligations) {
    this(version, bearerId, model, bindings, obligations, List.of());
  }

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
    if ((version != 1 && version != 2) || bearerId == 0 || model == null)
      throw new IllegalArgumentException("Invalid process bearer plan");
    bindings = List.copyOf(bindings);
    if (bindings.stream().map(Binding::name).distinct().count() != bindings.size())
      throw new IllegalArgumentException("Duplicate process binding names");
    obligations = List.copyOf(obligations);
    descriptiveLinks = descriptiveLinks == null ? List.of() : List.copyOf(descriptiveLinks);
    var descriptiveKinds = java.util.Arrays.stream(org.integratedmodelling.klab.api.knowledge.SemanticInfluence.Kind.values())
        .filter(org.integratedmodelling.klab.api.knowledge.SemanticInfluence.Kind::descriptive)
        .map(Enum::name).collect(java.util.stream.Collectors.toSet());
    if (bindings.stream().anyMatch(b -> b.relations().stream().anyMatch(descriptiveKinds::contains))
        || obligations.stream().anyMatch(o -> descriptiveKinds.contains(o.relation()))
        || (version == 1 && !descriptiveLinks.isEmpty()))
      throw new IllegalArgumentException("Legacy or ambiguous descriptive process evidence requires re-resolution before replay");
    for (var link : descriptiveLinks)
      if (link.source() == null || link.target() == null || !link.kind().descriptive()
          || link.provenance() == null || link.provenance().isBlank())
        throw new IllegalArgumentException("Descriptive links require source, target, kind and provenance");
  }

  public Binding binding(String name) {
    return bindings.stream()
        .filter(binding -> binding.name().equals(name))
        .findFirst()
        .orElse(null);
  }
}
