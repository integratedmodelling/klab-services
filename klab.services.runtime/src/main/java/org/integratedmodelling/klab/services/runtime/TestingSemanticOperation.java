package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.runtime.ObserverProvenance;
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
    if (!(caller instanceof TestCaseBase.TestCaseScope))
      throw new IllegalArgumentException("Semantic operation execution is reserved for native test cases");
    if (!(scope instanceof ServiceContextScope nativeScope) || scope.getCurrentTransaction() != null)
      throw new IllegalArgumentException("A test operation requires a fresh native context transaction");
    if (directive == null || directive.getContextualization() != Contextualization.CLASSIFICATION)
      throw new IllegalArgumentException("The test operation capability currently supports CLASSIFICATION only");
    if (support == null || support.isEmpty() || support.size() <= 0)
      throw new IllegalArgumentException("A test operation requires explicit nonempty support");
    var context = scope.getContextObservation();
    if (context == null || context.getId() <= 0)
      throw new IllegalArgumentException("A test operation requires a committed within context");
    if (nativeScope.getObserver() == null) {
      // Null may be deliberate. An absent legacy selection cannot be inferred from defaults.
      var observer = ObserverProvenance.recover(nativeScope, context).requireKnown();
      nativeScope = nativeScope.withObserver(observer);
    }
    return nativeScope;
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
    var checks = new CopyOnWriteArrayList<Runnable>();
    final ServiceContextScope resolving;
    CompletableFuture<Dataflow> planned;
    try {
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
            var execution = new CompiledDataflow(runtime, null, resolving, plan);
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
