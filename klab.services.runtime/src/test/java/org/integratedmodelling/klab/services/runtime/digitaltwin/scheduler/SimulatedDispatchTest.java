package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class SimulatedDispatchTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static long date(String s) { return Instant.parse(s + "T00:00:00Z").toEpochMilli(); }
  static final long START = date("2014-01-01"), END = date("2015-01-01");
  static class Store {
    final Map<String, String> progress = new HashMap<>();
    final Map<String, Long> results = new TreeMap<>();
    SimulatedDispatch.Registration registration(String id, Time.Resolution.Type unit, double step) {
      var dailyGeometry = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(
          org.integratedmodelling.klab.api.geometry.Geometry.create(
              "T1(365){ttype=GRID,tstart=" + START + ",tend=" + END + ",tscope=1,tunit=DAY,tgrid=86400000}"));
      var bound = new OccurrenceSchedule(1,"","",step,unit,true,OccurrenceSchedule.Source.MODEL).bind(dailyGeometry.getTime());
      return new SimulatedDispatch.Registration(id.hashCode(), id, "v1",
          bound,
          () -> progress.containsKey(id) ? Utils.Json.parseObject(progress.get(id), DispatchProgress.class).through() : Long.MIN_VALUE);
    }
    boolean commit(SimulatedDispatch.Tick tick) {
      assertFalse(results.containsKey(tick.event().id()), "No duplicate committed result");
      results.put(tick.event().id(), tick.period().end() - tick.period().start());
      progress.put(tick.registration().id(), Utils.Json.asString(new DispatchProgress(1,
          tick.registration().id(), "v1", tick.period().end(), tick.event().id())));
      return true;
    }
  }

  @Test void lateMonthlyCadenceRestartAndRetryEqualUninterruptedDailyFixture() {
    var reference = new Store();
    assertTrue(SimulatedDispatch.drain(List.of(reference.registration("daily", Time.Resolution.Type.DAY, 1),
        reference.registration("monthly", Time.Resolution.Type.MONTH, 1)), END, reference::commit));
    var resumed = new Store();
    assertTrue(SimulatedDispatch.drain(List.of(resumed.registration("daily", Time.Resolution.Type.DAY, 1)),
        END, resumed::commit));
    // Newly introduced cadence must emit its own past periods despite the daily stream's horizon.
    assertFalse(SimulatedDispatch.drain(List.of(resumed.registration("monthly", Time.Resolution.Type.MONTH, 1)),
        END, tick -> false));
    assertFalse(resumed.progress.containsKey("monthly"));
    // Simulate an acknowledgement lost after a durable commit.
    assertFalse(SimulatedDispatch.drain(List.of(resumed.registration("monthly", Time.Resolution.Type.MONTH, 1)),
        END, tick -> { resumed.commit(tick); return false; }));
    var restarted = new Store();
    restarted.progress.putAll(resumed.progress); restarted.results.putAll(resumed.results);
    assertTrue(SimulatedDispatch.drain(List.of(restarted.registration("daily", Time.Resolution.Type.DAY, 1),
        restarted.registration("monthly", Time.Resolution.Type.MONTH, 1)), END, restarted::commit));
    assertEquals(reference.results, restarted.results);
    assertEquals(377, restarted.results.size());
    assertEquals(31L * 86400000, restarted.results.get("monthly/v1/" + START + "/" + date("2014-02-01")));
    assertEquals(28L * 86400000, restarted.results.get("monthly/v1/" + date("2014-02-01") + "/" + date("2014-03-01")));
  }

  @Test void clippingFilteringEqualTimesAndIndependentRemoval() {
    var store = new Store();
    var first = store.registration("first", Time.Resolution.Type.DAY, 1);
    var second = store.registration("second", Time.Resolution.Type.DAY, 1);
    assertTrue(SimulatedDispatch.drain(List.of(first, second), START + 86400000, store::commit));
    assertEquals(2, store.results.size());
    assertTrue(SimulatedDispatch.drain(List.of(second), START + 2 * 86400000, store::commit));
    assertEquals(3, store.results.size());
    var phase = new OccurrenceSchedule.Bound(date("2014-01-15"), date("2014-03-12"), START, 1, Time.Resolution.Type.MONTH);
    assertEquals(new TimeEmitter.Period(date("2014-01-15"),date("2014-02-01")),TimeEmitter.nextPeriod(phase,Long.MIN_VALUE));
    assertEquals(new TimeEmitter.Period(date("2014-03-01"),date("2014-03-12")),TimeEmitter.nextPeriod(phase,date("2014-03-01")));
    assertNull(TimeEmitter.nextPeriod(phase,phase.end()));
    assertNotEquals(new TransitionEvent("observed:1",1,2,null).toKey(),new TransitionEvent("observed:2",1,2,null).toKey());
  }
}
