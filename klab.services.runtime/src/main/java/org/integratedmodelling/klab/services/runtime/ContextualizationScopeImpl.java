package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;

import java.util.ArrayList;
import java.util.List;

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
