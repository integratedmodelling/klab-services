package org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl;

import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;

import java.io.Serializable;

/** Simple temporal transition for remote contextualization. */
public class SchedulerEventImpl implements Scheduler.Event, Serializable {

  private TimePeriod time;
  private Type type = Type.TEMPORAL_TRANSITION;

  @Override
  public Time getTime() {
    return time;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public Observation getEvent() {
    return null;
  }

  public void setTime(TimePeriod time) {
    this.time = time;
  }

  public void setType(Type type) {
    this.type = type;
  }
}
