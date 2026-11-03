package org.integratedmodelling.klab.services.runtime;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * One of these is created before an observation is contextualized and is available to all executors
 * to report their results. Upon completion of the contextualization, the result is passed to the
 * runtime to trigger any further resolutions (for collective observables) or to clean up after
 * failure.
 *
 * <p>TODO to be completed with classification, transformation, monitoring and notification logic
 * for statistics, provenance etc
 */
public class ContextualizationScopeImpl
    implements org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope {

  private final Observation target;
  private final Scheduler.Event event;
  private final org.integratedmodelling.klab.api.scope.ContextScope ownerExecution;
  private final org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction ownerTransaction;
  private final org.integratedmodelling.klab.api.geometry.Geometry executionGeometry;
  private final List<Observation> outcomes = new ArrayList<>();
  private final List<Observation> cessations = new ArrayList<>();
  private final java.util.Map<Observation, List<org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint>>
      outcomeConstraints = new java.util.IdentityHashMap<>();

  public void bindOutcomes(int first, List<org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint> constraints) {
    for (int i = first; i < outcomes.size(); i++) outcomeConstraints.put(outcomes.get(i), List.copyOf(constraints));
  }

  @Override
  public List<org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint> getResolutionConstraints(Observation outcome) {
    return outcomeConstraints.getOrDefault(outcome, List.of());
  }

  public ContextualizationScopeImpl(Observation observation, Scheduler.Event event) {
    this(observation, event, null);
  }

  ContextualizationScopeImpl(Observation observation, Scheduler.Event event,
      org.integratedmodelling.klab.api.geometry.Geometry executionGeometry) {
    this(observation, event, executionGeometry, null);
  }

  /** Only Runtime's actual invocation binds a carrier to an execution; wire/legacy carriers
   * retain ordinary outputs but cannot acquire modeled-effect reporting authority. */
  ContextualizationScopeImpl(Observation observation, Scheduler.Event event,
      org.integratedmodelling.klab.api.geometry.Geometry executionGeometry,
      org.integratedmodelling.klab.api.scope.ContextScope ownerExecution) {
    this.target = observation;
    this.event = event;
    this.executionGeometry = executionGeometry;
    this.ownerExecution = ownerExecution;
    this.ownerTransaction = ownerExecution == null ? null : ownerExecution.getCurrentTransaction();
  }

  /** Invocation support is distinct from the producer's declared observation geometry. */
  org.integratedmodelling.klab.api.geometry.Geometry executionGeometry() {
    return executionGeometry;
  }

  @Override
  public Observation getTarget() {
    return target;
  }

  @Override
  public Scheduler.Event getEvent() {
    return event;
  }

  @Override public List<Observation> getCessations(){return cessations;}

  @Override public void reportCessations(List<Observation> requests,
      org.integratedmodelling.klab.api.scope.ContextScope supplied) {
    if (requests.isEmpty()) return;
    if (ownerExecution == null || supplied != ownerExecution
        || ownerTransaction == null || supplied.getCurrentTransaction() != ownerTransaction)
      throw new IllegalStateException("Cessation output belongs to another execution or transaction");
    if (!(supplied instanceof org.integratedmodelling.klab.services.scopes.ServiceContextScope execution)
        || execution.isInterrupted() || execution.getResolutionCycleBinding() == null
        || !(execution.getCurrentTransaction() instanceof
            org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl current)
        || current.isFailed() || current.assets().stream().noneMatch(asset -> asset == target))
      throw new IllegalStateException("Cessation output requires its actual owning execution");
    var root = current;
    while (root.getParent() != null) {
      if (!(root.getParent() instanceof org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl parent))
        throw new IllegalStateException("Cessation output requires a native execution root");
      root = parent;
    }
    if (root.isFailed() || !root.ownsExecutionActivity(execution.getActivity()))
      throw new IllegalStateException("Cessation output requires an open owned Activity");
    var graph = execution.getDigitalTwin().getKnowledgeGraph();
    var batch = List.copyOf(requests);
    for (var requested : batch) {
      if (requested == null || requested.getId() <= 0 || requested.isEmpty()
          || requested.getObservable() == null || requested.getObservable().getSemantics() == null
          || requested.getObservable().getSemantics().isCollective()
          || requested.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.CONFIGURATION)
          || !(requested.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT)
              || requested.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT))
          || !root.ownsModeledCessationInput(requested, execution)
          || !graph.isObservationCurrent(requested, execution))
        throw new IllegalStateException("Cessation output target is not an accessible owned current substantial");
    }
    synchronized (cessations) {
      for (var requested : batch) if (cessations.stream().noneMatch(existing -> existing == requested)) cessations.add(requested);
    }
    // No mutation or receipt is issued here. Only CompiledDataflow's private successful
    // source/semantic accounting can subsequently admit this intent at native publication.
  }

  @Override
  public List<Observation> getOutcomes() {
    return outcomes;
  }
}
