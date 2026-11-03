package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;

/** Change-driven source-rule discovery. This request does not execute or prove a consequence. */
public record ConsequenceRuleQuery(
    List<Concept> ownerTriggers,
    List<Concept> ingredientTriggers,
    Set<KimConsequenceRule.Kind> kinds,
    ConsequenceRuleResponse.Epoch expectedEpoch) {
  public ConsequenceRuleQuery {
    ownerTriggers = ownerTriggers == null ? List.of() : List.copyOf(ownerTriggers);
    ingredientTriggers = ingredientTriggers == null ? List.of() : List.copyOf(ingredientTriggers);
    kinds = kinds == null ? Set.of(KimConsequenceRule.Kind.values()) : Set.copyOf(kinds);
  }
}
