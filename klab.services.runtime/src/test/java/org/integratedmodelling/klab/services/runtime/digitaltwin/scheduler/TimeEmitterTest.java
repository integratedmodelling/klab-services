package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TimeEmitterTest {

  @Test
  void simulatedClockEmitsFullPeriodsAndCoalescesEquivalentRegistrations() {
    var emitter = new CapturingEmitter();
    var id = emitter.register(0L, 4_000L, 1L, TimeUnit.SECONDS);
    var duplicate = emitter.register(1_000L, 3_000L, 1L, TimeUnit.SECONDS);

    assertTrue(id > 0L);
    assertEquals(id, duplicate);
    assertEquals(1, emitter.getSchedule().getRegistrations().size());

    emitter.startSimulatedClock();

    assertEquals(
        List.of(
            new Event(0L, 1_000L, id),
            new Event(1_000L, 2_000L, id),
            new Event(2_000L, 3_000L, id),
            new Event(3_000L, 4_000L, id)),
        emitter.events);
    assertEquals(4_000L, emitter.getCurrentEpochTime());
    assertFalse(emitter.isClockRunning());
  }

  @Test
  void unregisterStopsEventsForTheRemovedRegistration() {
    var emitter = new CapturingEmitter();
    var id = emitter.register(0L, 2_000L, 1L, TimeUnit.SECONDS);

    assertTrue(emitter.unregister(id));
    assertFalse(emitter.unregister(id));

    emitter.startSimulatedClock();

    assertTrue(emitter.events.isEmpty());
  }

  @Test
  void simulatedClockUsesLastEpochTimeForUnboundedRegistrations() {
    var emitter = new CapturingEmitter();
    var id = emitter.register(0L, -1L, 1L, TimeUnit.SECONDS);

    assertThrows(IllegalStateException.class, emitter::startSimulatedClock);

    emitter.setLastEpochTime(2_500L);
    emitter.startSimulatedClock();

    assertEquals(List.of(new Event(0L, 1_000L, id), new Event(1_000L, 2_000L, id)), emitter.events);
    assertEquals(2_000L, emitter.getCurrentEpochTime());
  }

  @Test
  void scheduleJsonRoundTripRestoresRegistrationsAndCursor() {
    var emitter = new CapturingEmitter();
    var id = emitter.register(0L, 3_000L, 1L, TimeUnit.SECONDS);
    emitter.startSimulatedClock();

    var json = emitter.getSchedule().toJson();
    var restoredSchedule = TimeEmitter.Schedule.fromJson(json);
    var restored = new CapturingEmitter(restoredSchedule);

    assertEquals(1, restored.getSchedule().getRegistrations().size());
    assertEquals(id + 1L, restored.getSchedule().getNextRegistrationId());
    assertEquals(3_000L, restored.getCurrentEpochTime());

    restored.startSimulatedClock();

    assertTrue(restored.events.isEmpty());
  }

  @Test
  void realtimeClockEmitsAtPhysicalIntervalsFromExplicitEpoch() throws Exception {
    var emitter = new CapturingEmitter(3);
    var id = emitter.register(0L, 90L, 30L, TimeUnit.MILLISECONDS);

    try {
      emitter.startRealtimeClock(0L);

      assertTrue(emitter.await(1, TimeUnit.SECONDS));
      assertEquals(
          List.of(new Event(0L, 30L, id), new Event(30L, 60L, id), new Event(60L, 90L, id)),
          emitter.events);
    } finally {
      emitter.stopClock();
    }
  }

  @Test
  void simulatedClockCanSwitchToRealtimeAtCurrentSimulatedTime() throws Exception {
    var emitter = new CapturingEmitter(3) {
      @Override
      public void emitEvent(long tStart, long tEnd, long[] registrationIds) {
        super.emitEvent(tStart, tEnd, registrationIds);
        if (events.size() == 1) {
          startRealtimeClock();
        }
      }
    };
    var id = emitter.register(0L, 90L, 30L, TimeUnit.MILLISECONDS);

    try {
      emitter.startSimulatedClock();

      assertTrue(emitter.await(1, TimeUnit.SECONDS));
      assertEquals(
          List.of(new Event(0L, 30L, id), new Event(30L, 60L, id), new Event(60L, 90L, id)),
          emitter.events);
    } finally {
      emitter.stopClock();
    }
  }

  private static class CapturingEmitter extends TimeEmitter {

    protected final List<Event> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch;

    private CapturingEmitter() {
      this(0);
    }

    private CapturingEmitter(int expectedEvents) {
      this.latch = expectedEvents <= 0 ? null : new CountDownLatch(expectedEvents);
    }

    private CapturingEmitter(Schedule schedule) {
      super(schedule);
      this.latch = null;
    }

    @Override
    public void emitEvent(long tStart, long tEnd, long[] registrationIds) {
      events.add(new Event(tStart, tEnd, registrationIds));
      if (latch != null) {
        latch.countDown();
      }
    }

    boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      return latch == null || latch.await(timeout, unit);
    }
  }

  private record Event(long tStart, long tEnd, long[] registrationIds) {

    private Event(long tStart, long tEnd, long registrationId) {
      this(tStart, tEnd, new long[] {registrationId});
    }

    private Event {
      registrationIds = registrationIds.clone();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Event event
          && tStart == event.tStart
          && tEnd == event.tEnd
          && Arrays.equals(registrationIds, event.registrationIds);
    }

    @Override
    public int hashCode() {
      var result = Long.hashCode(tStart);
      result = 31 * result + Long.hashCode(tEnd);
      result = 31 * result + Arrays.hashCode(registrationIds);
      return result;
    }
  }
}
