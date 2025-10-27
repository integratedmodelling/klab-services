package org.integratedmodelling.klab.api.digitaltwin.impl;

import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;

public class EventImpl implements Scheduler.Event {
  /**
   * Event should have a type enum INITIALIZATION, TIME or EVENT (extendible: can have VISIT when a
   * new DT is connected for example).
   */
  private long start;

  private long end;
  private final Type type;
  private final Observation event;

  public EventImpl() {
    type = Type.INITIALIZATION;
    event = null;
  }

  public EventImpl(long start, long end, Time.Resolution resolution) {
    type = Type.TEMPORAL_TRANSITION;
    this.start = start;
    this.end = end;
    event = null;
  }

  public long getStart() {
    return start;
  }

  public void setStart(long start) {
    this.start = start;
  }

  public long getEnd() {
    return end;
  }

  public void setEnd(long end) {
    this.end = end;
  }

  @Override
  public Time getTime() {
    return TimePeriod.create(
        start,
        end,
        this.type == Type.INITIALIZATION ? Time.Type.INITIALIZATION : Time.Type.PHYSICAL);
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String toKey() {
    return start + "-" + end;
  }

  @Override
  public Observation getEvent() {
    return event;
  }

  @Override
  public String toString() {
    return type.toString();
  }
}
