package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;

/** Stable causal identity; equal timestamps do not imply equal events. */
public record TransitionEvent(String id, long start, long end, Observation observed, Boundary boundary)
    implements Scheduler.Event {
  public TransitionEvent(String id, long start, long end, Observation observed) {
    this(id, start, end, observed, Boundary.NONE);
  }
  public TransitionEvent {
    if (id == null || id.isBlank() || end < start || boundary == null
        || boundary != Boundary.NONE && (observed == null || start != end))
      throw new IllegalArgumentException("Invalid transition");
  }

  public Time getTime() {
    return TimePeriod.create(start, end);
  }

  public Boundary getBoundary() { return boundary; }

  public Type getType() {
    return observed == null ? Type.TEMPORAL_TRANSITION : Type.EVENT;
  }

  public String toKey() {
    return id;
  }

  public Observation getEvent() {
    return observed;
  }
}
