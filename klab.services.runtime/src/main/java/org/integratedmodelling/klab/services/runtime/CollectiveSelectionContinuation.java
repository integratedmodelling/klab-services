package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Resolve virtual model inputs in the parent's live attempt, never as separate submissions. */
final class CollectiveSelectionContinuation {
  private final RuntimeService runtime;
  private final List<Runnable> checks;
  private final List<Pending> pending = new ArrayList<>();
  private final Set<String> active = new HashSet<>();
  private final Map<String, Observation> completed = new HashMap<>();
  private record Pending(Actuator node, Observation requested, Dataflow plan, ServiceContextScope scope) {}
  private record Draft(RuntimeService runtime, Pending pending) {}
  // Capabilities for live inputs are private Java object identities, never transported metadata.
  // A completed/failed root releases them; a copied or replayed receipt follows strict query rules.
  private static final Map<DigitalTwin.Transaction, IdentityHashMap<Observation, Draft>> DRAFTS =
      Collections.synchronizedMap(new WeakHashMap<>());

  CollectiveSelectionContinuation(RuntimeService runtime, List<Runnable> checks) {
    this.runtime = runtime; this.checks = checks;
  }

  boolean hasPending() { return !pending.isEmpty(); }

  CompletableFuture<Dataflow> prepare(Dataflow incoming, ServiceContextScope scope) {
    if (!hasSelections(incoming)) return CompletableFuture.completedFuture(incoming);
    try {
      var mapper = JacksonConfiguration.newObjectMapper();
      var plan = mapper.readValue(mapper.writeValueAsString(incoming), Dataflow.class);
      reattach(plan);
      CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
      for (var node : plan.getComputation())
        chain = chain.thenCompose(ignored -> expand(node, scope));
      return chain.thenApply(ignored -> {
        checkCancellation(scope);
        ((DataflowImpl) plan).setResolutionOutcome(hasClassifierObligations(plan)
            ? Dataflow.ResolutionOutcome.DEFERRED : Dataflow.ResolutionOutcome.RESOLVED);
        return plan;
      });
    } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
  }

  /** Complete a selected plan's ordinary prerequisites before any of its producers compile.
   * Classifier expansion may reveal another selected model input, so both continuations use
   * this same coordinator, active-key set and parent transaction until the plan is executable. */
  private CompletableFuture<Dataflow> prerequisites(Dataflow incoming, ServiceContextScope scope) {
    return prepare(incoming, scope).thenCompose(prepared -> {
      ((DataflowImpl) prepared).setResolutionOutcome(hasClassifierObligations(prepared)
          ? Dataflow.ResolutionOutcome.DEFERRED : Dataflow.ResolutionOutcome.RESOLVED);
      return SelectedModelClassifierContinuation.resumeClassifiers(runtime, prepared, scope, checks, this::reattach);
    }).thenCompose(resolved -> hasSelections(resolved)
        ? prerequisites(resolved, scope) : CompletableFuture.completedFuture(resolved));
  }

  private CompletableFuture<Void> expand(Actuator node, ServiceContextScope scope) {
    try {
      checkCancellation(scope);
      var selection = node.getCollectiveSelectionDependency();
      if (selection != null) {
        if (node.getActuatorType() != Actuator.Type.REFERENCE || node.getObservation() == null
            || node.getObservation().getId() != Observation.QUERY_ID)
          throw new IllegalArgumentException("Selected model input requires a virtual reference");
        var requested = node.getObservation();
        var binding = selection.getMetadata().get(org.integratedmodelling.klab.api.knowledge.CollectiveSelectionBinding.DATA_KEY);
        if (binding == null) throw new IllegalArgumentException("Selected model input has no captured admission");
        String key = binding.toString();
        if (!active.add(key)) throw new IllegalStateException("Recursive selected model input");
        var cached = completed.get(key);
        CompletableFuture<Dataflow> work = cached == null
            ? prerequisites(selection, scope) : CompletableFuture.completedFuture(selection);
        return work.thenAccept(executable -> {
          checkCancellation(scope);
          var view = cached;
          if (view == null) {
            view = CollectiveSelectionExecution.execute(runtime, executable, requested, scope, checks, true);
            completed.put(key, view);
          }
          ((ActuatorImpl) node).setObservation(view);
          ((ActuatorImpl) node).setId(Observation.QUERY_ID);
          ((ActuatorImpl) node).setCollectiveSelectionDependency(null);
          var input = new Pending(node, requested, executable, scope);
          pending.add(input);
          trackDraft(view, input);
        }).whenComplete((ignored, failure) -> active.remove(key));
      }
      var nativeScope = modelScope(runtime, node, scope);
      CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
      for (var child : node.getChildren())
        chain = chain.thenCompose(ignored -> expand(child, nativeScope));
      return chain;
    } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
  }

  /** Same model-native context rule as Resolver, shared by both prerequisite continuations. */
  static ServiceContextScope modelScope(RuntimeService runtime, Actuator node, ServiceContextScope scope) {
    var observation = node.getObservation();
    var operation = node.getOperationObservable();
    if (node.getActuatorType() != Actuator.Type.REFERENCE && observation != null
        && operation != null && !observation.getObservable().is(SemanticType.EVENT)
        && SemanticType.isEnumerableSubstantial(observation.getObservable().getSemantics().getType())
        && Objects.equals(operation.getUrn(), observation.getObservable().getUrn())) {
      var actual = runtime.register(observation, scope);
      ((ActuatorImpl) node).setObservation(actual);
      ((ActuatorImpl) node).setId(actual.getId());
      return (ServiceContextScope) scope.within(actual);
    }
    return scope;
  }

  private static void checkCancellation(ServiceContextScope scope) {
    if (scope.isInterrupted() || Thread.currentThread().isInterrupted())
      throw new CancellationException("Selected model input cancelled");
  }

  /** Freeze views only after every selection/classifier prerequisite has contributed its effects. */
  Dataflow finish(Dataflow plan) {
    if (pending.isEmpty()) return plan;
    var nodes = new HashMap<Long, Actuator>();
    for (var root : plan.getComputation()) collect(root, nodes);
    for (var entry : pending) {
      var node = nodes.getOrDefault(entry.node().getTransientId(), entry.node());
      var original = entry.node().getObservation();
      refreshDraft(runtime, original, entry.scope());
      install(node, original);
      if (node != entry.node()) install(entry.node(), original);
    }
    return plan;
  }

  private static DigitalTwin.Transaction root(ServiceContextScope scope) {
    var tx = scope.getCurrentTransaction();
    if (tx == null) return null;
    while (tx.getParent() != null) tx = tx.getParent();
    return tx;
  }

  private void trackDraft(Observation view, Pending input) {
    var tx = root(input.scope());
    if (tx == null) throw new IllegalStateException("Live selected input requires its parent attempt");
    synchronized (DRAFTS) {
      var registry = DRAFTS.get(tx);
      if (registry == null) {
        registry = new IdentityHashMap<>();
        DRAFTS.put(tx, registry);
        var owner = tx;
        Runnable release = () -> { synchronized (DRAFTS) { DRAFTS.remove(owner); } };
        tx.afterCommit(release);
        tx.afterRollback(release);
      }
      registry.put(view, new Draft(runtime, input));
    }
  }

  /** Refresh only a live input owned by this exact root and Java object. Ordinary replay stays
   * immutable. Other families may enrich member meanings, but a previously bound input may not
   * silently change its membership, support, sources or geometry during the consuming attempt. */
  static void refreshDraft(RuntimeService runtime, Observation view, ServiceContextScope caller) {
    var tx = root(caller);
    if (tx == null) return;
    Draft draft;
    synchronized (DRAFTS) {
      var registry = DRAFTS.get(tx);
      draft = registry == null ? null : registry.get(view);
    }
    if (draft == null) return;
    var input = draft.pending();
    if (runtime != draft.runtime() || caller.getDigitalTwin() != input.scope().getDigitalTwin())
      throw new IllegalStateException("Selected input belongs to another runtime or twin");
    checkCancellation(caller);
    var refreshed = CollectiveSelectionExecution.seal(runtime, input.plan(), input.requested(), input.scope());
    var previous = proof(view); var current = proof(refreshed);
    if (!Objects.equals(previous.requested(), current.requested())
        || !Objects.equals(previous.nativeMembers(), current.nativeMembers())
        || !Objects.equals(previous.predicates(), current.predicates())
        || !Objects.equals(previous.families(), current.families())
        || !Objects.equals(previous.reasonerId(), current.reasonerId())
        || previous.knowledgeRevision() != current.knowledgeRevision()
        || !Objects.equals(previous.semanticFingerprint(), current.semanticFingerprint())
        || previous.observerId() != current.observerId()
        || !Objects.equals(previous.observerUrn(), current.observerUrn())
        || !Objects.equals(previous.observerClass(), current.observerClass())
        || !Objects.equals(previous.geometry(), current.geometry())
        || previous.cohortId() != current.cohortId()
        || !Objects.equals(previous.sourceIds(), current.sourceIds())
        || !Objects.equals(previous.selectedIds(), current.selectedIds())
        || !Objects.equals(memberGeometry(previous), memberGeometry(current)))
      throw new IllegalStateException("Selected dependency membership or support changed during its attempt");
    if (!(view instanceof org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl mutable))
      throw new IllegalStateException("Live selected input is immutable");
    mutable.setMetadata(refreshed.getMetadata());
    mutable.setGeometry(refreshed.getGeometry());
    mutable.setChildrenCount(refreshed.getChildrenCount());
    mutable.setResolvedCoverage(refreshed.getResolvedCoverage());
    SemanticExecutionEvidence.issue(view, current.semanticEvidence(), input.scope());
  }

  private static CollectiveSelectionView proof(Observation view) {
    return Utils.Json.parseObject(view.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString(),
        CollectiveSelectionView.class);
  }
  private static Map<Long, String> memberGeometry(CollectiveSelectionView proof) {
    var result = new TreeMap<Long, String>();
    for (var member : proof.members())
      if (result.put(member.id(), member.geometry()) != null)
        throw new IllegalStateException("Duplicate live selected input member");
    return result;
  }

  /** Rebind only ports known to this coordinator after the internal classifier clone. The
   * serialized copy is not itself a live capability and must match its admitted original. */
  void reattach(Dataflow plan) {
    var nodes = new HashMap<Long, Actuator>();
    for (var root : plan.getComputation()) collect(root, nodes);
    for (var entry : pending) {
      var copy = nodes.get(entry.node().getTransientId());
      if (copy == null || copy == entry.node()) continue;
      var original = entry.node().getObservation();
      if (copy.getActuatorType() != Actuator.Type.REFERENCE || copy.getObservation() == null
          || copy.getObservation().getId() != Observation.QUERY_ID
          || !Objects.equals(copy.getName(), entry.node().getName())
          || !Objects.equals(proof(copy.getObservation()), proof(original)))
        throw new IllegalStateException("Internal selected input clone changed its binding");
      install(copy, original);
    }
  }

  private static void install(Actuator node, Observation sealed) {
    // Executor closures and receipt callbacks share this exact privately tracked input object.
    ((ActuatorImpl) node).setObservation(sealed);
    ((ActuatorImpl) node).setId(Observation.QUERY_ID);
  }

  private static void collect(Actuator node, Map<Long, Actuator> nodes) {
    if (nodes.put(node.getTransientId(), node) != null)
      throw new IllegalStateException("Duplicate selected model input node identity");
    for (var child : node.getChildren()) collect(child, nodes);
  }
  static boolean hasSelections(Dataflow plan) { return plan.getComputation().stream().anyMatch(CollectiveSelectionContinuation::hasSelections); }
  private static boolean hasSelections(Actuator node) {
    return node.getCollectiveSelectionDependency() != null || node.getChildren().stream().anyMatch(CollectiveSelectionContinuation::hasSelections);
  }
  private static boolean hasClassifierObligations(Dataflow plan) { return plan.getComputation().stream().anyMatch(CollectiveSelectionContinuation::hasClassifierObligations); }
  private static boolean hasClassifierObligations(Actuator node) {
    return !node.getClassifierDependencies().isEmpty() || node.getChildren().stream().anyMatch(CollectiveSelectionContinuation::hasClassifierObligations);
  }
}
