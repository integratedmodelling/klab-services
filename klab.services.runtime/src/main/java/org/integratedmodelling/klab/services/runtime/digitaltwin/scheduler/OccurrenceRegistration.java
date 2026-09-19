package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

/** Durable registration, stored on its observation in the same transaction as the executable plan. */
public record OccurrenceRegistration(int version, String id, String planRevision,
    Actuator.ExecutionRole role, long bearerId, List<Schedule> schedules, String plan)
    implements Serializable {
  public static final String METADATA_KEY = "klab.scheduler.occurrence";
  public record Schedule(int computation, OccurrenceSchedule declaration, OccurrenceSchedule.Bound bound)
      implements Serializable {}
  public OccurrenceRegistration {
    if (version != 1 || id == null || id.isBlank() || planRevision == null || planRevision.isBlank()
        || role == null || role == Actuator.ExecutionRole.INITIALIZATION || plan == null || plan.isBlank()
        || schedules == null || schedules.isEmpty()) {
      throw new IllegalArgumentException("Incomplete occurrence registration");
    }
    schedules = List.copyOf(schedules);
  }
}
