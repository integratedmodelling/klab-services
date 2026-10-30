package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.runtime.ObserverProvenance;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Test-only entry into the existing UPDATE lifecycle; ordinary submit/query remain unchanged. */
final class TestingSemanticOperation {
  private TestingSemanticOperation() {}

  static ServiceContextScope validate(Observable directive, Geometry support,
      ContextScope scope, RuntimeAgent.Scope caller) {
    requireCaller(caller, scope);
    if (!(scope instanceof ServiceContextScope nativeScope) || scope.getCurrentTransaction() != null)
      throw new IllegalArgumentException("A test operation requires a fresh native context transaction");
    if (directive == null || directive.getContextualization() != Contextualization.CLASSIFICATION)
      throw new IllegalArgumentException("The test operation capability currently supports CLASSIFICATION only");
    if (support == null || support.isEmpty() || support.getDimensions().isEmpty()
        || support.size() <= 0 || support.size() == Long.MAX_VALUE)
      throw new IllegalArgumentException("A test operation requires explicit nonempty support");
    var context = scope.getContextObservation();
    if (context != null && context.getId() <= 0)
      throw new IllegalArgumentException("A focused test operation requires a committed within context");
    if (context == null) {
      var observer = nativeScope.getObserver();
      if (observer == null || observer.getId() <= 0 || observer.getObservable() == null
          || !observer.getObservable().is(SemanticType.AGENT))
        throw new IllegalArgumentException("A contextless test operation requires an explicitly selected persisted Agent");
    } else if (nativeScope.getObserver() == null) {
      // Null may be deliberate. An absent legacy selection cannot be inferred from defaults.
      var observer = ObserverProvenance.recover(nativeScope, context).requireKnown();
      nativeScope = nativeScope.withObserver(observer);
    }
    var observer = nativeScope.getObserver();
    if (observer != null && (observer.getId() <= 0 || observer.getObservable() == null
        || !observer.getObservable().is(SemanticType.AGENT)))
      throw new IllegalArgumentException("A test operation observer must be a persisted Agent");
    return nativeScope;
  }

  private static void requireCaller(RuntimeAgent.Scope caller, ContextScope scope) {
    if (!(caller instanceof TestCaseBase.TestCaseScope test) || test.isDone()
        || test.getAgent() == null || "stopped".equals(test.getAgent().status())
        || !test.ownsContext(scope))
      throw new IllegalArgumentException("Semantic operation execution requires an active testcase-owned digital twin");
  }

  static CompletableFuture<org.integratedmodelling.klab.api.services.RuntimeService.OperationReceipt>
      apply(RuntimeService runtime, Observable directive, Geometry support,
          ContextScope scope, RuntimeAgent.Scope caller) {
    final ServiceContextScope selected;
    try { selected = validate(directive, support, scope, caller); }
    catch (Throwable failure) { return CompletableFuture.failedFuture(failure); }
    var submission = Activity.of(Activity.Type.SUBMISSION, selected.getActivity(),
        "Test semantic operation " + directive.getUrn());
    ObserverProvenance.recordSelection(submission, selected.getObserver());
    var provenance = selected.getConstraint(ResolutionConstraint.Type.Provenance, Agent.class);
    if (provenance == null)
      return CompletableFuture.failedFuture(new IllegalArgumentException("A test operation requires its calling provenance agent"));
    var result = new CompletableFuture<org.integratedmodelling.klab.api.services.RuntimeService.OperationReceipt>();
    var attempt = selected.withCancellation(result::isCancelled).executingFresh(submission, provenance);
    org.integratedmodelling.klab.runtime.ObserverSemanticUse.start(attempt);
    var checks = new CopyOnWriteArrayList<Runnable>();
    checks.add(() -> requireCaller(caller, selected));
    final ServiceContextScope resolving;
    CompletableFuture<Dataflow> planned;
    try {
      // Pin the actual selected Agent before planning reads it. The observer's observed-as
      // wrapper in a model is a different contract and cannot guard this native payload.
      var observerCheck = observerWitness(selected);
      observerCheck.run();
      checks.add(observerCheck);
      attempt.getCurrentTransaction().beforeCommit(observerCheck);
      if (selected.getObserver() != null) {
        // The existing observer API proves actual HAS_MEMBER membership in this twin.
        // A positive ID on a detached Agent bean is not authority to select an observer.
        var durable = runtime.getObserverGeometry(selected.getObserver().getId(), selected).getObserver();
        if (durable == null || !org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
            .persistedObservationProperties(Observation.forTransport(selected.getObserver())).equals(
                org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
                    .persistedObservationProperties(durable)))
          throw new IllegalStateException("Test operation observer differs from its durable native input");
        runtime.captureSelectionBaseline(selected.getObserver().getObservable(), attempt);
      }
      if (selected.getContextObservation() != null)
        attempt.getCurrentTransaction().link(submission, selected.getContextObservation(), GraphModel.Relationship.HAS_CONTEXT);
      if (selected.getObserver() != null)
        attempt.getCurrentTransaction().link(submission, selected.getObserver(), GraphModel.Relationship.HAS_OBSERVER);
      var resolution = Activity.of(Activity.Type.RESOLUTION, submission, "Resolve test semantic operation");
      resolving = attempt.executing(resolution);
      var reasoner = selected.getService(Reasoner.class);
      var generation = reasoner.capabilities(selected);
      String service = generation.getServiceId();
      long revision = generation.getKnowledgeRevision();
      if (service == null || service.isBlank() || revision < 0)
        throw new IllegalStateException("Test semantic operation requires a committed semantic generation");
      checks.add(() -> {
        var current = reasoner.capabilities(selected);
        if (!Objects.equals(service, current.getServiceId()) || revision != current.getKnowledgeRevision())
          throw new IllegalStateException("Test semantic operation generation changed");
      });
      // Detached Resolver request only: no registration, graph identity or observation receipt.
      var builder = new Observation.NaiveBuilder(directive, resolving);
      builder.geometry(support);
      planned = selected.getService(Resolver.class).resolve(builder.make(), resolving);
    }
    catch (Throwable failure) { failAttempt(attempt, failure); return CompletableFuture.failedFuture(failure); }
    planned.thenCompose(plan -> SelectedModelClassifierContinuation.resume(runtime, plan, resolving, checks))
        .thenApply(plan -> {
          requireActive(attempt); checks.forEach(Runnable::run);
          if (plan.isEmpty() || plan.getComputation().isEmpty())
            throw new IllegalStateException("Test semantic operation has no executable UPDATE plan");
          var compiled = new ArrayList<CompiledDataflow>();
          for (var root : plan.getComputation()) {
            if (root.getActuatorType() != Actuator.Type.UPDATE
                || root.getEffect() != Actuator.Effect.SEMANTIC_UPDATE
                || root.getContextualization() != directive.getContextualization()
                || root.getObservation() != null
                || !org.integratedmodelling.klab.runtime.language.DependencyContract.matches(
                    directive, root.getOperationObservable()))
              throw new IllegalArgumentException("Test semantic operation requires observation-free UPDATE roots");
            var owner = ownership(runtime, root, support, resolving, checks);
            var execution = new CompiledDataflow(runtime, owner, resolving, plan);
            if (!execution.compile(root)
                || !execution.store((DigitalTwinImpl.TransactionImpl) resolving.getCurrentTransaction()))
              throw new IllegalStateException("Cannot compile test semantic operation");
            compiled.add(execution);
          }
          requireActive(attempt); checks.forEach(Runnable::run);
          if (resolving.commit() < 0) throw new IllegalStateException("Test operation resolution failed");
          attempt.getCurrentTransaction().registerExecutors();
          for (var execution : compiled)
            if (!execution.executeRoot(support, Scheduler.Event.initialization(), attempt))
              throw new IllegalStateException("Test semantic operation execution failed");
          requireActive(attempt); checks.forEach(Runnable::run);
          long commit = attempt.commit();
          if (commit <= 0) throw new IllegalStateException("Test semantic operation did not commit");
          return new org.integratedmodelling.klab.api.services.RuntimeService.OperationReceipt(
              selected.getId(), commit, submission.getId(), directive.getUrn());
        }).whenComplete((receipt, failure) -> {
          if (failure != null) { failAttempt(attempt, failure); result.completeExceptionally(failure); }
          else result.complete(receipt);
        });
    return result;
  }

  /** An effect-only root borrows ownership only from its real cohort-producing input.
   * A query view is not an observation owner and its serialized source IDs are not proof. */
  static Observation ownership(RuntimeService runtime, Actuator root, Geometry support,
      ServiceContextScope scope, List<Runnable> checks) {
    if (root.getTargetBindings().size() != 1
        || root.getTargetBindings().getFirst().getKind() != Actuator.TargetBinding.Kind.COHORT_MEMBERS)
      throw new IllegalArgumentException("Test classification requires a cohort-member target binding");
    var names = root.getTargetBindings().getFirst().getSources();
    if (names == null || names.isEmpty() || new HashSet<>(names).size() != names.size())
      throw new IllegalArgumentException("Test classification requires unique native source ports");
    Observation owner = null;
    for (var name : names) {
      var ports = root.getChildren().stream().filter(child -> Objects.equals(name, child.getName())).toList();
      if (ports.size() != 1) throw new IllegalArgumentException("Missing or duplicate classification source port");
      var port = ports.getFirst();
      var input = port.getObservation();
      requireNative(input);
      if (port.getActuatorType() == Actuator.Type.OBSERVE) {
        if (input.getId() == Observation.QUERY_ID)
          throw new IllegalArgumentException("A native producer cannot be a virtual query");
        var actual = runtime.register(input, scope);
        requireNative(actual);
        if (actual.getId() == Observation.UNASSIGNED_ID || actual.getId() == Observation.QUERY_ID)
          throw new IllegalArgumentException("Native producer has no registered identity");
        ((ActuatorImpl) port).setObservation(actual); ((ActuatorImpl) port).setId(actual.getId());
        if (owner == null) owner = actual;
      } else if (port.getActuatorType() == Actuator.Type.REFERENCE) {
        // Re-query the actual requested support with no untrusted completion metadata attached.
        var query = new Observation.NaiveBuilder(input.getObservable(), scope).geometry(support).query().build();
        var checked = runtime.completedCohortReference(query, scope);
        var ids = checkedSources(checked);
        if (input.getId() > 0 && !ids.contains(input.getId()))
          throw new IllegalStateException("Classification reference is not a current contributing producer");
        var stated = input.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS);
        if (stated != null && !Objects.equals(new TreeSet<>(input.getMetadata().getList(Metadata.IM_QUERY_SOURCE_IDS, Long.class)), new TreeSet<>(ids)))
          throw new IllegalStateException("Classification query source witness changed");
        for (var id : ids) {
          var actual = actual(id, scope); requireNative(actual);
          if (!Objects.equals(actual.getObservable().getSemantics(), input.getObservable().getSemantics()))
            throw new IllegalStateException("Contributing producer has another native meaning");
          if (owner == null) owner = actual;
        }
        ((ActuatorImpl) port).setObservation(checked); ((ActuatorImpl) port).setId(Observation.QUERY_ID);
        var cohort = checked.getMetadata().get(Metadata.IM_QUERY_COHORT_ID);
        checks.add(() -> {
          var current = runtime.completedCohortReference(query, scope);
          if (!Objects.equals(ids, checkedSources(current))
              || !Objects.equals(cohort, current.getMetadata().get(Metadata.IM_QUERY_COHORT_ID)))
            throw new IllegalStateException("Classification native completion changed before commit");
        });
      } else throw new IllegalArgumentException("Classification ownership requires a native producer or checked reference");
    }
    if (owner == null) throw new IllegalStateException("Classification has no real native ownership witness");
    return owner;
  }

  private static List<Long> checkedSources(Observation checked) {
    if (checked == null || checked.isEmpty() || checked.getId() != Observation.QUERY_ID
        || !Double.isFinite(checked.getResolvedCoverage())
        || checked.getResolvedCoverage() < 1.0 - 1.0e-9)
      throw new IllegalStateException("Classification has no complete native query witness");
    var cohort = checked.getMetadata().get(Metadata.IM_QUERY_COHORT_ID);
    if (!(cohort instanceof Number nativeCohort) || nativeCohort.longValue() == 0
        || nativeCohort.longValue() == Observation.UNASSIGNED_ID)
      throw new IllegalStateException("Classification query has no native cohort witness");
    var ids = checked.getMetadata().getList(Metadata.IM_QUERY_SOURCE_IDS, Long.class);
    if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()
        || ids.stream().anyMatch(id -> id == null || id == 0 || id == Observation.UNASSIGNED_ID))
      throw new IllegalStateException("Classification query has invalid contributing producer IDs");
    return ids.stream().sorted().toList();
  }
  /** Immutable typed identity/support witness; durable payload is guarded by the native baseline. */
  static Runnable observerWitness(ServiceContextScope scope) {
    var observer = scope.getObserver();
    if (observer == null) return () -> {
      if (scope.getObserver() != null) throw new IllegalStateException("Test operation observer changed");
    };
    long id = observer.getId();
    String urn = observer.getUrn();
    var semantics = observer.getObservable().getSemantics();
    String meaning = semantics.getUrn();
    var types = Set.copyOf(semantics.getType());
    String geometry = observerGeometry(observer.getGeometry());
    String perceived = observerGeometry(observer.geometry(Observation.GeometryRelationship.PERCEIVES));
    var payload = org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
        .persistedObservationProperties(Observation.forTransport(observer));
    return () -> {
      var current = scope.getObserver();
      if (current == null || current.getId() != id || !Objects.equals(urn, current.getUrn())
          || current.getObservable() == null || current.getObservable().getSemantics() == null
          || !Objects.equals(meaning, current.getObservable().getSemantics().getUrn())
          || !types.equals(current.getObservable().getSemantics().getType())
          || !payload.equals(org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
              .persistedObservationProperties(Observation.forTransport(current)))
          || !Objects.equals(geometry, observerGeometry(current.getGeometry()))
          || !Objects.equals(perceived, observerGeometry(current.geometry(Observation.GeometryRelationship.PERCEIVES))))
        throw new IllegalStateException("Test operation observer identity, meaning, payload or support changed");
    };
  }
  private static String observerGeometry(Geometry geometry) {
    return geometry == null ? null : Geometry.forTransport(geometry).encode();
  }
  private static void requireNative(Observation input) {
    if (input == null || input.isEmpty() || input.getObservable() == null
        || !input.getObservable().getSemantics().isCollective()
        || !SemanticType.isEnumerableSubstantial(input.getObservable().getSemantics().getType()))
      throw new IllegalArgumentException("Classification ownership requires a real native collective");
  }
  private static Observation actual(long id, ServiceContextScope scope) {
    for (var tx = scope.getCurrentTransaction(); tx != null; tx = tx.getParent())
      for (var asset : tx.assets())
        if (asset instanceof Observation observation && observation.getId() == id) return observation;
    var result = id > 0 ? scope.getObservation(id) : null;
    if (result == null || result.isEmpty() || result.getId() != id)
      throw new IllegalStateException("Contributing native producer is unavailable");
    return result;
  }

  private static void requireActive(ServiceContextScope scope) {
    if (scope.isInterrupted() || Thread.currentThread().isInterrupted()
        || scope.getCurrentTransaction() == null || scope.getCurrentTransaction().isFailed())
      throw new CancellationException("Test semantic operation cancelled or failed");
  }

  /** Reporting failure cannot strand the caller if ActivityFinished delivery itself fails. */
  private static void failAttempt(ServiceContextScope attempt, Throwable failure) {
    try { attempt.fail(failure); }
    catch (Throwable reportingFailure) {
      if (reportingFailure != failure) failure.addSuppressed(reportingFailure);
    }
  }
}
