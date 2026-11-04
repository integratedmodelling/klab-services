package org.integratedmodelling.klab.runtime.consequence;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.ConsequenceRuleResponse;

/**
 * Positive detection over one caller-captured local domain. No graph queries, observation creation
 * or retraction occur here. A returned match must still be revalidated with its original native
 * read set at the commit boundary; semantic/local fingerprints are not native read guards.
 */
public final class ConsequencePatternMatcher {
  public enum Outcome {
    MATCH,
    NO_MATCH,
    DEFERRED
  }

  public enum Decision {
    YES,
    NO,
    UNAVAILABLE
  }

  public enum PlacementKind {
    DEPENDENT_BEARER,
    ROOT_COHORT,
    ROOT_CONTEXT
  }

  public record Placement(PlacementKind kind, long id, String contextId) {
    public Placement(PlacementKind kind, long id) {
      this(kind, id, null);
    }

    public static Placement context(String context) {
      return new Placement(PlacementKind.ROOT_CONTEXT, 0, context);
    }

    public Placement {
      Objects.requireNonNull(kind);
      if (kind == PlacementKind.ROOT_CONTEXT) {
        if (id != 0 || contextId == null || contextId.isBlank())
          throw new IllegalArgumentException("Actual string context required");
      } else if (id <= 0 || contextId != null)
        throw new IllegalArgumentException("Durable observation/cohort placement required");
    }
  }

  /** Facts must be projected from the supplied committed native capture, not speculative views. */
  public record Fact(
      long id,
      String semantics,
      String resolvedSupport,
      boolean dependent,
      long bearerId,
      long cohortId) {
    public Fact {
      if (id <= 0) throw new IllegalArgumentException("Durable ingredient required");
      Objects.requireNonNull(semantics);
    }
  }

  public record Snapshot(
      String contextId,
      long observerId,
      ConsequenceRuleResponse.Epoch epoch,
      List<Fact> facts,
      Map<Placement, Fact> contexts,
      boolean complete,
      KnowledgeGraph.ConsequenceReadSet reads,
      KnowledgeGraph.ContextCohortCatalogue catalogue) {
    public Snapshot(
        String contextId,
        long observerId,
        ConsequenceRuleResponse.Epoch epoch,
        List<Fact> facts,
        Map<Placement, Fact> contexts,
        boolean complete,
        KnowledgeGraph.ConsequenceReadSet reads) {
      this(contextId, observerId, epoch, facts, contexts, complete, reads, null);
    }

    public Snapshot {
      Objects.requireNonNull(contextId);
      Objects.requireNonNull(epoch);
      Objects.requireNonNull(reads);
      if (!epoch.valid() || observerId < 0)
        throw new IllegalArgumentException(
            "Valid cycle semantics and durable observer identity required");
      facts = List.copyOf(facts);
      contexts = Map.copyOf(contexts);
      if (!contextId.equals(reads.contextId()))
        throw new IllegalArgumentException("Native read set belongs to another context");
      if (catalogue != null
          && (!contextId.equals(catalogue.contextId())
              || facts.stream()
                  .filter(f -> !f.dependent())
                  .anyMatch(f -> !catalogue.cohortIds().contains(f.cohortId()))))
        throw new IllegalArgumentException(
            "Context domain must retain every source cohort in its native catalogue");
      var captured = new HashSet<>(reads.assetIds());
      if (observerId > 0 && !captured.contains(observerId))
        throw new IllegalArgumentException("Selected observer is absent from native capture");
      if (facts.stream().anyMatch(f -> !captured.contains(f.id()))
          || contexts.values().stream().anyMatch(f -> !captured.contains(f.id())))
        throw new IllegalArgumentException(
            "Detection facts must retain their native captured assets");
      if (facts.stream().map(Fact::id).distinct().count() != facts.size())
        throw new IllegalArgumentException("Duplicate fact identity");
    }
  }

  public interface Semantics {
    ConsequenceRuleResponse.Epoch epoch();

    Decision matches(String actual, KimConcept expected);

    /** Must honor the configuration's complete applicability contract, not only its ingredients. */
    Decision applicable(ConsequenceRuleResponse.Entry recipe, Placement placement, Fact context);
  }

  /**
   * Native support policy remains separate from positive ingredient matching. No inferred geometry.
   */
  @FunctionalInterface
  public interface Support {
    String resolve(
        KimConcept expression, Map<String, List<Fact>> matchedClauses, Placement placement);
  }

  public record Match(
      String ruleId,
      String contextId,
      long observerId,
      ConsequenceRuleResponse.Epoch epoch,
      Placement placement,
      Long contextObservationId,
      List<Long> ingredientIds,
      Map<String, List<Long>> matchedClauses,
      String support,
      KnowledgeGraph.ConsequenceReadSet reads) {
    public Match {
      ingredientIds = List.copyOf(ingredientIds);
      var copy = new LinkedHashMap<String, List<Long>>();
      matchedClauses.forEach((k, v) -> copy.put(k, List.copyOf(v)));
      matchedClauses = Collections.unmodifiableMap(copy);
    }
  }

  public record Result(Outcome outcome, String reason, List<Match> matches) {
    public Result {
      matches = List.copyOf(matches);
      if (outcome != Outcome.MATCH && !matches.isEmpty())
        throw new IllegalArgumentException("Deferred/no-match cannot publish partial matches");
    }
  }

  private record Evaluation(boolean matches, boolean unknown, Map<String, List<Fact>> clauses) {}

  public Result evaluate(
      ConsequenceRuleResponse.Entry recipe,
      Snapshot snapshot,
      Semantics semantics,
      Support support) {
    Objects.requireNonNull(recipe);
    Objects.requireNonNull(snapshot);
    Objects.requireNonNull(semantics);
    if (recipe.rule().kind() != KimConsequenceRule.Kind.EMERGES_FROM)
      return deferred("Recipe is not an emergence declaration");
    if (!snapshot.complete())
      return deferred("Local observation domain is incomplete, not successfully empty");
    if (!snapshot.epoch().equals(semantics.epoch()))
      return deferred("Detection semantics do not belong to the captured cycle");
    try {
      ConsequencePatterns.positiveLeaves(recipe.rule().target());
    } catch (IllegalArgumentException unsupported) {
      return deferred(unsupported.getMessage());
    }
    var groups = new LinkedHashMap<Placement, List<Fact>>();
    var unplaced = new ArrayList<Fact>();
    for (var fact : snapshot.facts()) {
      long id = fact.dependent() ? fact.bearerId() : fact.cohortId();
      if (id <= 0) {
        unplaced.add(fact);
        continue;
      }
      var placement =
          !fact.dependent() && snapshot.catalogue() != null
              ? Placement.context(snapshot.contextId())
              : new Placement(
                  fact.dependent() ? PlacementKind.DEPENDENT_BEARER : PlacementKind.ROOT_COHORT,
                  id);
      groups.computeIfAbsent(placement, ignored -> new ArrayList<>()).add(fact);
    }
    var matches = new ArrayList<Match>();
    boolean anyLocalIngredientMatch = false;
    try {
      for (var group : groups.entrySet()) {
        var evaluation = matches(recipe.rule().target(), group.getValue(), semantics, "0");
        if (evaluation.unknown())
          return deferred("Ingredient meaning is unavailable in the captured semantics");
        if (!evaluation.matches()) continue;
        anyLocalIngredientMatch = true;
        var placement = group.getKey();
        var context = snapshot.contexts().get(placement);
        if (placement.kind() == PlacementKind.DEPENDENT_BEARER
            && (context == null || context.id() != placement.id()))
          return deferred("Common dependent bearer is missing from committed evidence");
        if (recipe.rule().within() != null) {
          if (context == null)
            return deferred("Context specialization has no unambiguous observed target");
          var within = semantics.matches(context.semantics(), recipe.rule().within());
          if (within == Decision.UNAVAILABLE)
            return deferred("Context specialization is unavailable");
          if (within == Decision.NO) continue;
        }
        var applies = semantics.applicable(recipe, placement, context);
        if (applies == Decision.UNAVAILABLE)
          return deferred("Configuration applicability lacks complete target evidence");
        if (applies == Decision.NO) continue;
        if (support == null)
          return deferred("Actual support requires a native scope support resolver");
        var shared = support.resolve(recipe.rule().target(), evaluation.clauses(), placement);
        if (shared == null) return deferred("Ingredient support is unknown");
        var geometry = Geometry.create(shared);
        if (geometry.isEmpty()) continue;
        var byClause = new LinkedHashMap<String, List<Long>>();
        var ids = new TreeSet<Long>();
        evaluation
            .clauses()
            .forEach(
                (path, facts) -> {
                  var values = facts.stream().map(Fact::id).distinct().sorted().toList();
                  byClause.put(path, values);
                  ids.addAll(values);
                });
        matches.add(
            new Match(
                recipe.ruleId(),
                snapshot.contextId(),
                snapshot.observerId(),
                snapshot.epoch(),
                placement,
                context == null ? null : context.id(),
                List.copyOf(ids),
                byClause,
                geometry.encode(),
                snapshot.reads()));
      }
      if (!snapshot.epoch().equals(semantics.epoch()))
        return deferred("Semantic cycle changed while matching");
      if (matches.isEmpty()) {
        var combined = matches(recipe.rule().target(), snapshot.facts(), semantics, "0");
        if (!snapshot.epoch().equals(semantics.epoch()))
          return deferred("Semantic cycle changed while matching");
        if (combined.unknown()) return deferred("Ingredient meaning is unavailable");
        if (!anyLocalIngredientMatch
            && combined.matches()
            && (groups.size() > 1 || !unplaced.isEmpty()))
          return deferred(
              "Matching ingredients have mixed or missing placement; no bearer/cohort is invented");
        return new Result(
            Outcome.NO_MATCH, "No complete positive match in the captured local domain", List.of());
      }
      return new Result(
          Outcome.MATCH,
          "Positive source pattern, applicability and actual support are established",
          matches);
    } catch (RuntimeException unavailable) {
      return deferred("Detection proof unavailable: " + unavailable.getMessage());
    }
  }

  private Evaluation matches(
      KimConcept expression, List<Fact> facts, Semantics semantics, String path) {
    expression = ConsequencePatterns.unwrapped(expression);
    if (expression.getOperands().isEmpty()) {
      var selected = new ArrayList<Fact>();
      boolean unknown = false;
      for (var fact : facts) {
        var decision = semantics.matches(fact.semantics(), expression);
        if (decision == Decision.YES) selected.add(fact);
        else if (decision == Decision.UNAVAILABLE) unknown = true;
      }
      return new Evaluation(
          !selected.isEmpty(),
          unknown,
          selected.isEmpty() ? Map.of() : Map.of(path, List.copyOf(selected)));
    }
    if (!(expression instanceof KimConceptImpl implementation))
      throw new IllegalArgumentException("Unsupported Boolean syntax implementation");
    var first = (KimConceptImpl) implementation.removeComponents();
    first.setOperands(new ArrayList<>());
    first.setExpressionType(KimConcept.Expression.SINGLETON);
    first.getType().remove(org.integratedmodelling.klab.api.knowledge.SemanticType.UNION);
    first.getType().remove(org.integratedmodelling.klab.api.knowledge.SemanticType.INTERSECTION);
    first.resetDefinition();
    var operands = new ArrayList<KimConcept>();
    operands.add(first);
    operands.addAll(expression.getOperands());
    boolean conjunction = expression.getExpressionType() == KimConcept.Expression.INTERSECTION;
    boolean successful = conjunction, unknown = false;
    var clauses = new LinkedHashMap<String, List<Fact>>();
    for (int i = 0; i < operands.size(); i++) {
      var evaluated = matches(operands.get(i), facts, semantics, path + "/" + i);
      unknown |= evaluated.unknown();
      successful =
          conjunction ? successful && evaluated.matches() : successful || evaluated.matches();
      if (evaluated.matches()) clauses.putAll(evaluated.clauses());
    }
    return new Evaluation(
        successful, unknown, successful ? Collections.unmodifiableMap(clauses) : Map.of());
  }

  private static Result deferred(String reason) {
    return new Result(Outcome.DEFERRED, reason, List.of());
  }
}
