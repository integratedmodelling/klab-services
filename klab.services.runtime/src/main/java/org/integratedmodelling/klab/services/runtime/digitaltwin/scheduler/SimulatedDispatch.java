package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule;

/** Bounded synchronous emission with backpressure: a failed commit stops the drain. */
public final class SimulatedDispatch {
  public record Registration(
      long observationId,
      String id,
      String revision,
      OccurrenceSchedule.Bound schedule,
      LongSupplier completedThrough) {
    public Registration {
      if (id == null
          || id.isBlank()
          || revision == null
          || revision.isBlank()
          || schedule == null
          || schedule.start() >= schedule.end()
          || schedule.anchor() > schedule.start())
        throw new IllegalArgumentException("Incomplete bounded dispatch registration");
      Objects.requireNonNull(completedThrough);
      new OccurrenceSchedule.Cadence(schedule.step(), schedule.unit());
    }
  }

  public record Tick(Registration registration, TimeEmitter.Period period) {
    public TransitionEvent event() {
      return new TransitionEvent(
          registration.id()
              + "/"
              + registration.revision()
              + "/"
              + period.start()
              + "/"
              + period.end(),
          period.start(),
          period.end(),
          null);
    }
  }

  public static boolean drain(
      Collection<Registration> registrations, long until, Predicate<Tick> commit) {
    var queue =
        new PriorityQueue<Tick>(
            Comparator.comparingLong((Tick t) -> t.period.end())
                .thenComparingLong(t -> t.period.start())
                .thenComparing(t -> t.registration.id()));
    for (var registration : registrations) {
      var next =
          TimeEmitter.nextPeriod(
              registration.schedule(), registration.completedThrough().getAsLong());
      if (next != null && next.end() <= until) queue.add(new Tick(registration, next));
    }
    while (!queue.isEmpty()) {
      var tick = queue.remove();
      if (!commit.test(tick)) return false;
      if (tick.registration.completedThrough().getAsLong() < tick.period.end())
        throw new IllegalStateException("Execution returned without durable temporal progress");
      var next = TimeEmitter.nextPeriod(tick.registration.schedule(), tick.period.end());
      if (next != null && next.end() <= until) queue.add(new Tick(tick.registration, next));
    }
    return true;
  }
}
