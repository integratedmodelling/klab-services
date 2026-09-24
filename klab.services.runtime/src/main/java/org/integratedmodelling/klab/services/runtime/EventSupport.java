package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;

/** Validation shared by event instantiation and durable lifecycle registration. */
public final class EventSupport {
  private EventSupport() {}
  public static boolean individual(Observation observation) {
    return observation.getObservable().is(SemanticType.EVENT)
        && !observation.getObservable().getSemantics().isCollective();
  }
  public static Time validate(Observation observation, Scheduler.Event invocation) {
    if (!individual(observation)) throw new IllegalArgumentException("Expected an individual event");
    var time = GeometryRepository.INSTANCE.scale(observation.getGeometry()).getTime();
    if (time == null || time.getStart() == null || time.getEnd() == null
        || time.getStart().getMilliseconds() >= time.getEnd().getMilliseconds()
        || time.size() != 1 || time.getTimeType() == Time.Type.INITIALIZATION
        || time.getTimeType() == Time.Type.GRID || time.getStep() != null)
      throw new IllegalArgumentException("An event requires one bounded atomic period of positive duration");
    if (invocation != null) {
      if (invocation.getType() == Scheduler.Event.Type.INITIALIZATION)
        throw new IllegalArgumentException("Events cannot be instantiated at INIT");
      long now = invocation.getBoundary() == Scheduler.Event.Boundary.NONE
          ? invocation.getTime().getStart().getMilliseconds() : invocation.getInstant().getMilliseconds();
      if (time.getStart().getMilliseconds() < now)
        throw new IllegalArgumentException("An event cannot start before its instantiation time");
    }
    return time;
  }
}
