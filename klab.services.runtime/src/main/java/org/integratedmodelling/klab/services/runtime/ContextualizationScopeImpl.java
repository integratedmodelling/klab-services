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
  private final List<Observation> outcomes = new ArrayList<>();
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
    this.target = observation;
    this.event = event;
  }

  @Override
  public Observation getTarget() {
    return target;
  }

  @Override
  public Scheduler.Event getEvent() {
    return event;
  }

  @Override
  public List<Observation> getOutcomes() {
    return outcomes;
  }
}
