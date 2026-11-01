package org.integratedmodelling.klab.services.reasoner;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Conservative structural guard cases; categorical value-space policy remains explicitly unresolved. */
public final class OperandGuards {
  public static final String CONTRACT_REVISION = "countable-families-presence-support-v1";
  private final Reasoner reasoner;
  public OperandGuards(Reasoner reasoner) { this.reasoner = Objects.requireNonNull(reasoner); }
  public boolean sameCountableFamily(Semantics first, Semantics remaining) {
    var leaves = leaves(first, remaining); SemanticType family = null;
    for (var leaf : leaves) {
      var kinds = EnumSet.noneOf(SemanticType.class);
      for (var kind : List.of(SemanticType.AGENT, SemanticType.EVENT, SemanticType.RELATIONSHIP)) if (leaf.is(kind)) kinds.add(kind);
      if (kinds.isEmpty() && leaf.is(SemanticType.SUBJECT)) kinds.add(SemanticType.SUBJECT);
      if (kinds.size() != 1) return false;
      var kind = kinds.iterator().next(); if (family != null && family != kind) return false; family = kind;
    }
    return family != null;
  }
  public boolean enumerableCompatible(Semantics first, Semantics remaining, Semantics result) {
    var leaves = leaves(first, remaining);
    if (leaves.stream().anyMatch(leaf -> leaf.is(SemanticType.CLASS) || leaf.is(SemanticType.CATEGORY)))
      throw new UnsupportedOperationException("Categorical logical merge needs a reviewed scalar/set-valued result-space signature; no signature is configured");
    if (result == null || !result.is(SemanticType.PRESENCE) || result.is(SemanticType.NOTHING)
        || !reasoner.satisfiable(result.asConcept())) return false;
    if (leaves.stream().anyMatch(leaf -> !leaf.is(SemanticType.PRESENCE)
        || leaf.is(SemanticType.NOTHING) || !reasoner.satisfiable(leaf.asConcept()))) return false;
    var reference = leaves.getFirst(); String bearer = urn(reasoner.inherent(reference));
    if (bearer == null || !Objects.equals(bearer, urn(reasoner.inherent(result)))
        || !reasoner.compatible(reference, result) || !reasoner.compatible(result, reference)) return false;
    for (var leaf : leaves) {
      if (!Objects.equals(bearer, urn(reasoner.inherent(leaf)))
          || !reasoner.compatible(reference, leaf) || !reasoner.compatible(leaf, reference)) return false;
    }
    return true;
  }
  private String urn(Concept concept) { return concept == null ? null : concept.getUrn(); }
  private List<Semantics> leaves(Semantics... inputs) {
    var result = new ArrayList<Semantics>();
    for (var input : inputs) flatten(Objects.requireNonNull(input, "Guard operands cannot be null"), result, new HashSet<>(), 0);
    if (result.isEmpty()) throw new IllegalArgumentException("Logical guard has no operands"); return result;
  }
  private void flatten(Semantics input, List<Semantics> result, Set<String> ancestors, int depth) {
    if (depth > 256 || result.size() > 10000) throw new IllegalArgumentException("Logical operand graph exceeds guard limits");
    if (input.is(SemanticType.NOTHING)) throw new IllegalArgumentException("Unresolved logical operand " + input.getUrn());
    if (!input.is(SemanticType.UNION) && !input.is(SemanticType.INTERSECTION)) { result.add(input); return; }
    if (!ancestors.add(input.getUrn())) throw new IllegalArgumentException("Cyclic logical operand graph");
    try {
      var children = reasoner.operands(input);
      if (children == null || children.isEmpty()) throw new IllegalArgumentException("Logical operand closure is unavailable");
      for (var child : children) flatten(child, result, ancestors, depth + 1);
    } finally { ancestors.remove(input.getUrn()); }
  }
}
