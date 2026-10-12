package org.integratedmodelling.klab.api.lang.kim;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;

/**
 * Pure syntax expansion, before declaration/resolution of implied dependencies. The runtime adapter
 * must supply completed CLASSIFICATION results, not ontology descendants. No execution, namespace
 * creation, or failure/resource policy is hidden here. Returned syntax must be declared again;
 * its original source provenance is retained, while cached observable URNs are invalidated.
 */
public final class DependencyExpansion {
  private DependencyExpansion() {}

  public enum Status { SUCCESS, UNKNOWN, FAILED }

  /**
   * A successful set is the entire resolved concrete closure, including self when classified.
   * Entries are canonical predicate declarations, not compound expressions whose clauses would be
   * discarded by substitution. The classification provider must establish family membership using
   * the authoritative reasoner; this syntax helper cannot infer ancestry from names or type flags.
   */
  public record Classification(Status status, List<KimConcept> concreteClosure) {
    public Classification {
      Objects.requireNonNull(status);
      concreteClosure = List.copyOf(concreteClosure);
      if (status != Status.SUCCESS && !concreteClosure.isEmpty())
        throw new IllegalArgumentException("Unsuccessful classification cannot supply candidates");
      for (var candidate : concreteClosure) {
        if (candidate.getName() == null || candidate.getName().isBlank()
            || !candidate.is(SemanticType.PREDICATE) || candidate.is(SemanticType.NOTHING)
            || candidate.is(SemanticType.ABSTRACT) || candidate.is(SemanticType.ANY)
            || candidate.is(SemanticType.ALL) || candidate.is(SemanticType.NONE)
            || candidate.isNegated() || candidate.isCollective()
            || candidate.getSemanticModifier() != null || candidate.getObservable() != null
            || candidate.getInherent() != null || candidate.getGoal() != null
            || candidate.getCausant() != null || candidate.getCaused() != null
            || candidate.getCompresent() != null || candidate.getComparisonConcept() != null
            || candidate.getRelationshipSource() != null || candidate.getRelationshipTarget() != null
            || candidate.getCooccurrent() != null || candidate.getAdjacent() != null
            || !candidate.getTraits().isEmpty() || !candidate.getRoles().isEmpty()
            || !candidate.getOperands().isEmpty() || !candidate.getValueOperators().isEmpty())
          throw new IllegalArgumentException("Classification must return concrete named predicates");
      }
    }
  }

  /**
   * Attempt-local identity: paths encode bearer/clause roles and list positions. A fresh attempt ID
   * prevents accidental reuse across calls, including calls in different runtime contexts. A future
   * cross-attempt cache must additionally bind model/source revision, complete observable semantics,
   * scope/observer, support/event and semantic snapshot; neither predicate URN nor path alone is a key.
   */
  public record RequestIdentity(java.util.UUID attemptId, List<String> path) {
    public RequestIdentity {
      Objects.requireNonNull(attemptId);
      path = List.copyOf(path);
    }
  }

  /**
   * The detached original observable supplies complete enclosing bearer and relationship context;
   * identity.path locates this occurrence within that snapshot. Repeated text or shared syntax
   * objects do not imply co-reference. Explicit semantic binding support is separate.
   */
  public record Request(RequestIdentity identity, KimObservable originalObservable,
                        KimConcept predicate, KimConcept declaration,
                        Contextualization contextualization) {}

  /**
   * Resolve every named predicate occurrence inside the original observable. The declaration lookup
   * supplies canonical status: ANY requests classification without making its declaration abstract.
   * Unknown declarations, null results, and unsuccessful classification fail explicitly, even when
   * a different factor is successfully empty. Exceptions propagate to the caller's failure policy.
   */
  public static List<KimObservableImpl> expand(KimObservableImpl original,
      Function<String, KimConcept> declarations, Function<Request, Classification> classify) {
    Objects.requireNonNull(original);
    if (!(original.getSemantics() instanceof KimConceptImpl syntax))
      throw new IllegalArgumentException("Dependency expansion requires KIM syntax");
    // Freeze container encounter order once. Classification snapshots and every product branch
    // must address the same occurrence, even if an input map changes its iteration order per read.
    var source = new KimObservableImpl(original);
    var factors = new LinkedHashMap<List<String>, List<KimConcept>>();
    var attemptId = java.util.UUID.randomUUID();
    mapObservable(source, (path, node) -> {
      if (node.getName() == null) return;
      var declaration = Objects.requireNonNull(declarations.apply(node.getName()),
          "Unknown declaration: " + node.getName());
      if (!declaration.is(SemanticType.PREDICATE)
          || !(declaration.is(SemanticType.ABSTRACT) || node.is(SemanticType.ANY))) return;
      var classification = Objects.requireNonNull(classify.apply(
          new Request(new RequestIdentity(attemptId, path), new KimObservableImpl(source),
              node, declaration, Contextualization.CLASSIFICATION)),
          "Unknown classification: " + node.getName());
      if (classification.status() != Status.SUCCESS)
        throw new IllegalStateException("Classification " + classification.status() + ": " + node.getName());
      // Preserve deterministic order while treating the classification closure as a set.
      var candidates = new LinkedHashMap<String, KimConcept>();
      for (var candidate : classification.concreteClosure())
        candidates.putIfAbsent(candidate.getName(), candidate);
      factors.put(path, List.copyOf(candidates.values()));
    });
    if (factors.isEmpty()) return List.of(original);
    if (factors.values().stream().anyMatch(List::isEmpty)) return List.of();
    var combinations = new ArrayList<java.util.Map<List<String>, KimConcept>>();
    combinations.add(new LinkedHashMap<>());
    for (var factor : factors.entrySet()) {
      var next = new ArrayList<java.util.Map<List<String>, KimConcept>>();
      for (var combination : combinations) for (var candidate : factor.getValue()) {
        var copy = new LinkedHashMap<>(combination);
        copy.put(factor.getKey(), candidate);
        next.add(copy);
      }
      combinations = next;
    }
    var result = new ArrayList<KimObservableImpl>();
    for (var combination : combinations) {
      var dependency = mapObservable(source, (path, node) -> {
        var candidate = combination.get(path);
        if (candidate == null) return;
        var flags = EnumSet.noneOf(SemanticType.class);
        flags.addAll(candidate.getType());
        // The consumed ANY marks this expansion site only. Other selectors remain untouched.
        for (var selector : List.of(SemanticType.ALL, SemanticType.NONE))
          if (node.is(selector)) flags.add(selector);
        node.setName(candidate.getName());
        node.setType(flags);
        node.setFundamentalType(candidate.getFundamentalType());
        node.setAuthority(candidate.getAuthority());
        node.setAuthorityTerm(candidate.getAuthorityTerm());
      });
      dependency.setOptional(true);
      dependency.setUrn(null);
      result.add(dependency);
    }
    return List.copyOf(result);
  }

  private static KimObservableImpl mapObservable(KimObservableImpl original,
      java.util.function.BiConsumer<List<String>, KimConceptImpl> transform) {
    return new KimObservableImpl(original, transform);
  }
}
