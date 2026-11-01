package org.integratedmodelling.klab.api.knowledge.observation.scale.time;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl.TimePeriodImpl;
import org.junit.jupiter.api.Test;

class TimePeriodGenericityTest {
  @Test void boundedPhysicalPeriodsAreLocatedAcrossTheEpoch() {
    for (long start : new long[]{-10000L, -5000L, 0L, 1000L}) {
      Time period = TimePeriod.create(start, start + 5000L);
      assertFalse(period.isGeneric(), "start=" + start);
      assertEquals(Time.Type.PHYSICAL, period.getTimeType());
      assertEquals(start, period.getStart().getMilliseconds());
      assertEquals(start + 5000L, period.getEnd().getMilliseconds());
    }
  }
  @Test void legacyZeroBasedNonPhysicalKindsStayGeneric() {
    for (Time.Type type : Time.Type.values()) if (type != Time.Type.PHYSICAL) {
      assertTrue(TimePeriod.create(0L, 5000L, type).isGeneric(), type.name());
    }
  }
  @Test void absentBoundsAndLegacyEmptyEpochSentinelStayGeneric() {
    assertTrue(new TimePeriodImpl().isGeneric());
    assertTrue(TimePeriod.create(0L, 0L).isGeneric());
    assertTrue(TimePeriod.create(0L, -5000L).isGeneric());
  }
  @Test void openEndedSentinelStaysGenericWithOrWithoutEpochStart() {
    assertTrue(TimePeriod.create(0L, Long.MAX_VALUE).isGeneric());
    assertTrue(TimePeriod.create(1000L, Long.MAX_VALUE).isGeneric());
  }
  @Test void ordinaryPositivePhysicalPeriodsKeepTheirBounds() {
    var period = TimePeriod.create(1000L, 6000L);
    assertFalse(period.isGeneric());
    assertEquals(1000L, period.getStart().getMilliseconds());
    assertEquals(6000L, period.getEnd().getMilliseconds());
  }
}
