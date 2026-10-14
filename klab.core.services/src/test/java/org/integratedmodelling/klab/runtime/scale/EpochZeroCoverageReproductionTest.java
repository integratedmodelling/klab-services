package org.integratedmodelling.klab.runtime.scale;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.time.TimeImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EpochZeroCoverageReproductionTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void boundedPhysicalPeriodAtEpochIsConcrete() {
    Time period = TimeImpl.create(0L, 5000L);
    assertEquals(Time.Type.PHYSICAL, period.getTimeType());
    assertEquals(5000.0, period.getDimensionSize());
    assertFalse(period.isGeneric(), "A bounded physical epoch-zero period is completely located");
  }

  @Test void apiPhysicalPeriodAtEpochIsAlsoConcrete() {
    Time period = new org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl.TimePeriodImpl(0L, 5000L);
    assertEquals(Time.Type.PHYSICAL, period.getTimeType());
    assertFalse(period.isGeneric());
  }

  @Test void epochZeroRawGeometryCoversHalfOfTheContext() {
    assertHalfCoverage(0L);
  }

  @Test void translatedPhysicalIntervalKeepsTheSameHalfCoverage() {
    assertHalfCoverage(1000L);
  }

  @Test void aMissingPhysicalBoundRemainsGeneric() {
    Time period = TimeImpl.create(Time.Type.PHYSICAL, null, null, null, null, null, null, null, null);
    assertTrue(period.isGeneric());
  }

  private static void assertHalfCoverage(long origin) {
    Scale context = GeometryRepository.INSTANCE.scale(Geometry.create(
        "T0(1){tstart=" + origin + ",tend=" + (origin + 10000L) + ",ttype=PHYSICAL}"));
    Geometry incoming = Geometry.create(
        "T0(1){tstart=" + origin + ",tend=" + (origin + 5000L) + ",ttype=PHYSICAL}");
    var merged = CoverageImpl.empty(context).merge(incoming, LogicalConnector.UNION);
    assertEquals(0.5, merged.getCoverage(), 1e-9);
    assertEquals(0.5, merged.getCoverage(Geometry.Dimension.Type.TIME), 1e-9);
    assertEquals(0.5, merged.getGain(), 1e-9);
  }
}
