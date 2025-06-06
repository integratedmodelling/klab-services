package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;

import java.util.HashMap;
import java.util.Map;

/**
 * Emitter for temporal events. There's one of these per scheduler.
 *
 * <p>Should be able to emit the "next" temporal events at any resolution either in simulated or
 * real time
 */
public class TimeEmitter {

  private final SchedulerImpl scheduler;

  /**
   * Each non-regular event will put its next counterpart here, to post when the time comes.
   *
   * <p>TODO this should be synchronized
   */
  private Map<Long, SchedulerImpl.EventImpl> incoming = new HashMap<>();

  public TimeEmitter(SchedulerImpl scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Update the schedule configuration and return if anything has changed.
   * @param tStart
   * @param end
   * @param resolution
   * @return
   */
  public boolean updateEvents(long tStart, long end, Time.Resolution resolution) {
    // TODO add any needed events and post them to the scheduler
    return false;
  }

  /**
   * TODO retrieve a serializable object containing a summary of the overall schedule, including
   *  start/end times and resolutions.
   * @return
   */
  public static Schedule getSchedule() {
    var ret = new Schedule();
    // TODO
    return ret;
  }

  public void startRealtimeClock() {
    // start emitting events at the relevant resolutions, starting
  }
}
