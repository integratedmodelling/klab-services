package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Committed event identity and duration for client timeline lanes, independent of quality deltas. */
public record ObservedEvent(long observationId, String type, String name, long start, long end,
    String geometry, Scheduler.Event.Boundary boundary) implements Serializable {
  public static final String PENDING = "klab.event.pending";
  public static final String STARTED = "klab.event.started";
  public static final String ENDED = "klab.event.ended";
  public ObservedEvent {
    if (observationId <= 0 || type == null || start >= end || geometry == null
        || boundary == null || boundary == Scheduler.Event.Boundary.NONE)
      throw new IllegalArgumentException("Invalid observed event boundary");
  }
  public static boolean pending(Observation observation) {
    return Boolean.TRUE.equals(observation.getMetadata().get(PENDING));
  }
}
