package org.integratedmodelling.klab.api.lang.kim;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
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
      concreteClosure = concreteClosure.stream().map(DependencyExpansion::copyConcept).toList();
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
            || !candidate.getAbout().isEmpty()
            || !candidate.getOperands().isEmpty() || !candidate.getValueOperators().isEmpty())
          throw new IllegalArgumentException("Classification must return concrete named predicates");
      }
    }

    /** Syntax beans are mutable; a completed outcome never lends its private candidates. */
    @Override public List<KimConcept> concreteClosure() {
      return concreteClosure.stream().map(DependencyExpansion::copyConcept).toList();
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

  /** An unsuccessful completed factor, preserving its status and exact occurrence identity. */
  public static final class ClassificationException extends IllegalStateException {
    private final RequestIdentity identity;
    private final Status status;
    private ClassificationException(RequestIdentity identity, Status status) {
      super("Classification " + status + " at " + identity.path());
      this.identity = identity;
      this.status = status;
    }
    public RequestIdentity identity() { return identity; }
    public Status status() { return status; }
  }

  private record Occurrence(RequestIdentity identity, KimConcept predicate, KimConcept declaration) {}

  /**
   * One frozen syntax attempt. Accessors return detached snapshots; only occurrence identities are
   * shared. This is not a Runtime attempt, membership certificate or cross-request cache entry.
   */
  public static final class Prepared {
    private final KimObservableImpl source;
    private final java.util.UUID attemptId;
    private final List<Occurrence> occurrences;

    private Prepared(KimObservableImpl source, java.util.UUID attemptId, List<Occurrence> occurrences) {
      this.source = source;
      this.attemptId = attemptId;
      this.occurrences = List.copyOf(occurrences);
    }

    public java.util.UUID attemptId() { return attemptId; }

    /** Stable identities and fresh source/predicate/declaration snapshots on every access. */
    public List<Request> requests() {
      return occurrences.stream().map(occurrence -> new Request(occurrence.identity(),
          new KimObservableImpl(source), copyConcept(occurrence.predicate()),
          copyConcept(occurrence.declaration()), Contextualization.CLASSIFICATION)).toList();
    }

    private <T> Map<RequestIdentity, T> checked(Map<RequestIdentity, ? extends T> supplied) {
      Objects.requireNonNull(supplied, "Missing occurrence outcomes");
      var snapshot = new LinkedHashMap<RequestIdentity, T>();
      supplied.forEach((key, value) -> snapshot.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
      var expected = new java.util.LinkedHashSet<RequestIdentity>();
      for (var occurrence : occurrences) expected.add(occurrence.identity());
      if (!snapshot.keySet().equals(expected))
        throw new IllegalArgumentException("Outcomes must match the exact prepared occurrence identities");
      return snapshot;
    }

    /**
     * Apply completed outcomes to this exact snapshot. All factors must be SUCCESS before an empty
     * factor can produce an empty product. Semantic family/eligibility proof remains the provider's
     * responsibility; generated syntax must be declared and validated before distance or execution.
     */
    public List<KimObservableImpl> apply(Map<RequestIdentity, Classification> completed) {
      var supplied = checked(completed);
      var factors = new LinkedHashMap<List<String>, List<KimConcept>>();
      for (var occurrence : occurrences) {
        var classification = snapshot(supplied.get(occurrence.identity()));
        if (classification.status() != Status.SUCCESS)
          throw new ClassificationException(occurrence.identity(), classification.status());
        var candidates = new LinkedHashMap<String, KimConcept>();
        for (var candidate : classification.concreteClosure())
          candidates.putIfAbsent(candidate.getName(), candidate);
        factors.put(occurrence.identity().path(), List.copyOf(candidates.values()));
      }
      if (factors.isEmpty()) return List.of(new KimObservableImpl(source));
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
          flags.addAll(ExpressionStatus.declarationTypes(candidate));
          // The consumed ANY marks this expansion site only. Other selectors remain untouched.
          for (var selector : List.of(SemanticType.ALL, SemanticType.NONE))
            if (node.is(selector)) flags.add(selector);
          node.setName(candidate.getName());
          node.setType(flags);
          // Substitution changes the declaration, not just its displayed name and cached flags.
          node.setDeclaredSubjective(flags.contains(SemanticType.SUBJECTIVE));
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

    /**
     * Nonblocking composition of caller-owned prerequisite stages, keyed to this plan. Completion
     * snapshots are captured immediately. Cancellation remains cancellation; no borrowed stage is
     * cancelled, no classifier is invoked, and no executor, timeout or retry policy is introduced.
     * Invalid identity maps are rejected before callbacks are registered.
     */
    public CompletableFuture<List<KimObservableImpl>> applyAsync(
        Map<RequestIdentity, ? extends CompletionStage<Classification>> completed) {
      var supplied = checked(completed);
      var result = new CompletableFuture<List<KimObservableImpl>>();
      var captured = new LinkedHashMap<RequestIdentity, CompletableFuture<Classification>>();
      for (var occurrence : occurrences) captured.put(occurrence.identity(), new CompletableFuture<>());
      for (var entry : supplied.entrySet()) {
        var capture = captured.get(entry.getKey());
        try {
          entry.getValue().whenComplete((classification, error) -> {
            if (error != null) {
              var cause = unwrap(error);
              capture.completeExceptionally(cause);
              if (cause instanceof CancellationException) result.cancel(false);
            } else {
              try { capture.complete(snapshot(classification)); }
              catch (RuntimeException invalid) { capture.completeExceptionally(invalid); }
            }
          });
        } catch (RuntimeException registrationFailure) {
          capture.completeExceptionally(registrationFailure);
          if (registrationFailure instanceof CancellationException) result.cancel(false);
        }
      }
      CompletableFuture.allOf(captured.values().toArray(CompletableFuture[]::new))
          .whenComplete((unused, error) -> {
            if (result.isDone()) return;
            if (error != null) {
              var cause = unwrap(error);
              if (cause instanceof CancellationException) result.cancel(false);
              else result.completeExceptionally(cause);
              return;
            }
            var outcomes = new LinkedHashMap<RequestIdentity, Classification>();
            captured.forEach((identity, future) -> outcomes.put(identity, future.getNow(null)));
            try { result.complete(apply(outcomes)); }
            catch (RuntimeException invalid) { result.completeExceptionally(invalid); }
          });
      return result;
    }
  }

  /**
   * Freeze one original expression and its canonical declaration/occurrence snapshots without
   * invoking classifiers. Equal text or shared syntax objects never imply shared substitution.
   */
  public static Prepared prepare(KimObservableImpl original, Function<String, KimConcept> declarations) {
    Objects.requireNonNull(original);
    Objects.requireNonNull(declarations);
    if (!(original.getSemantics() instanceof KimConceptImpl))
      throw new IllegalArgumentException("Dependency expansion requires KIM syntax");
    var source = new KimObservableImpl(original);
    var occurrences = new ArrayList<Occurrence>();
    var attemptId = java.util.UUID.randomUUID();
    mapObservable(source, (path, node) -> {
      if (node.getName() == null) return;
      var declaration = Objects.requireNonNull(declarations.apply(node.getName()),
          "Unknown declaration: " + node.getName());
      if (!declaration.is(SemanticType.PREDICATE)
          || !(declaration.is(SemanticType.ABSTRACT) || node.is(SemanticType.ANY))) return;
      occurrences.add(new Occurrence(new RequestIdentity(attemptId, path),
          copyConcept(node), copyConcept(declaration)));
    });
    return new Prepared(source, attemptId, occurrences);
  }

  /** Backward-compatible synchronous adapter; no-factor expansion retains original identity. */
  public static List<KimObservableImpl> expand(KimObservableImpl original,
      Function<String, KimConcept> declarations, Function<Request, Classification> classify) {
    Objects.requireNonNull(classify);
    var prepared = prepare(original, declarations);
    var requests = prepared.requests();
    if (requests.isEmpty()) return List.of(original);
    var completed = new LinkedHashMap<RequestIdentity, Classification>();
    for (var request : requests) {
      var classification = snapshot(Objects.requireNonNull(classify.apply(request),
          "Unknown classification: " + request.predicate().getName()));
      if (classification.status() != Status.SUCCESS)
        throw new ClassificationException(request.identity(), classification.status());
      completed.put(request.identity(), classification);
    }
    return prepared.apply(completed);
  }

  private static Classification snapshot(Classification classification) {
    Objects.requireNonNull(classification, "Unknown classification");
    return new Classification(classification.status(), classification.concreteClosure());
  }

  private static KimConcept copyConcept(KimConcept concept) {
    return (KimConcept) KimConceptImpl.copyValue(concept);
  }

  private static Throwable unwrap(Throwable error) {
    while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
    return error;
  }

  private static KimObservableImpl mapObservable(KimObservableImpl original,
      java.util.function.BiConsumer<List<String>, KimConceptImpl> transform) {
    return new KimObservableImpl(original, transform);
  }
}
