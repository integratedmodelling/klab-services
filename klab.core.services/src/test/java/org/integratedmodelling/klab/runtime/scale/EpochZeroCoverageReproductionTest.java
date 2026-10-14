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

  @Test void halfCoverageIsInvariantAcrossNegativeAndEpochOrigins() {
    assertHalfCoverage(-10000L); assertHalfCoverage(-5000L); assertHalfCoverage(0L);
  }

  @Test void zeroBasedNonPhysicalSentinelsKeepTheirExistingClassification() {
    for (Time.Type type : Time.Type.values()) if (type != Time.Type.PHYSICAL) {
      assertTrue(runtimeTime(type, 0L, 5000L).isGeneric(), type.name());
    }
  }

  @Test void nullBoundsAndOpenEndedPhysicalSentinelsStayGeneric() {
    assertTrue(runtimeTime(Time.Type.PHYSICAL, null, 5000L).isGeneric());
    assertTrue(runtimeTime(Time.Type.PHYSICAL, 0L, null).isGeneric());
    assertTrue(runtimeTime(Time.Type.PHYSICAL, 0L, Long.MAX_VALUE).isGeneric());
    assertTrue(runtimeTime(Time.Type.PHYSICAL, 1000L, Long.MAX_VALUE).isGeneric());
    assertTrue(runtimeTime(Time.Type.PHYSICAL, 0L, 0L).isGeneric());
    assertTrue(runtimeTime(Time.Type.PHYSICAL, 0L, -5000L).isGeneric());
  }

  @Test void physicalClassificationDoesNotAlterBoundsTypeOrDuration() {
    Time period = runtimeTime(Time.Type.PHYSICAL, 0L, 5000L);
    assertFalse(period.isGeneric()); assertEquals(Time.Type.PHYSICAL, period.getTimeType());
    assertEquals(0L, period.getStart().getMilliseconds());
    assertEquals(5000L, period.getEnd().getMilliseconds()); assertEquals(5000.0, period.getDimensionSize());
  }

  @Test void zeroBasedNonPhysicalGenericCallerKeepsLegacyCoverage() {
    var checks = new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
    for (Time.Type type : Time.Type.values()) if (type != Time.Type.PHYSICAL) {
      for (boolean api : new boolean[]{false, true}) {
        Time source = api ? org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod.create(0L, 5000L, type)
            : runtimeTime(type, 0L, 5000L);
        Scale scale = new ScaleImpl(new java.util.ArrayList<>(java.util.List.of(source)));
        var wrapped = (CoverageImpl) CoverageImpl.full(scale);
        var copy = new CoverageImpl(wrapped);
        var rebuilt = CoverageImpl.full(copy.asScale());
        for (Geometry incoming : java.util.List.of(scale, wrapped, copy.asScale(), rebuilt)) {
          for (LogicalConnector how : new LogicalConnector[]{LogicalConnector.UNION, LogicalConnector.INTERSECTION}) {
            checks.add(() -> {
              Scale context = GeometryRepository.INSTANCE.scale(Geometry.create(
                  "T0(1){tstart=0,tend=10000,ttype=PHYSICAL}"));
              var seed = CoverageImpl.empty(context).merge(
                  Geometry.create("T0(1){tstart=1000,tend=2000,ttype=PHYSICAL}"), LogicalConnector.UNION);
              var merged = seed.merge(incoming, how);
              assertAll(type + " api=" + api + " " + how,
                  () -> assertEquals(1.0, merged.getCoverage(), 1e-9),
                  () -> assertEquals(1.0, merged.getCoverage(Geometry.Dimension.Type.TIME), 1e-9),
                  () -> assertEquals(0.0, merged.getGain(), 1e-9));
            });
          }
        }
        checks.add(() -> {
          assertEquals(type, source.getTimeType()); assertEquals(0L, source.getStart().getMilliseconds());
          assertEquals(5000L, source.getEnd().getMilliseconds()); assertTrue(source.isGeneric());
        });
      }
    }
    assertAll(checks);
  }

  @Test void genericContextsKeepTheirPreviousPhysicalMergeMetrics() {
    var checks = new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
    for (Time.Type type : Time.Type.values()) if (type != Time.Type.PHYSICAL) {
      for (boolean api : new boolean[]{false, true}) {
      Time source = api ? org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod.create(0L, 5000L, type)
          : runtimeTime(type, 0L, 5000L);
      Scale context = new ScaleImpl(new java.util.ArrayList<>(java.util.List.of(source)));
      for (LogicalConnector how : new LogicalConnector[]{LogicalConnector.UNION, LogicalConnector.INTERSECTION}) {
        checks.add(() -> {
          var merged = CoverageImpl.empty(context).merge(
              Geometry.create("T0(1){tstart=1000,tend=2000,ttype=PHYSICAL}"), how);
          double expected = !api && how == LogicalConnector.UNION ? 0.2 : 0.0;
          assertAll(type + " context api=" + api + " " + how,
              () -> assertEquals(expected, merged.getCoverage(), 1e-9),
              () -> assertEquals(expected, merged.getCoverage(Geometry.Dimension.Type.TIME), 1e-9),
              () -> assertEquals(expected, merged.getGain(), 1e-9));
        });
      }
      }
    }
    assertAll(checks);
  }

  @Test void genericGridRetainsCollapsedMultiplicityWithoutMutatingCaller() {
    Time grid = TimeImpl.create(Time.Type.GRID, Time.Resolution.Type.SECOND, 1.0,
        org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.create(0L),
        org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.create(5000L),
        null, null, null, null);
    Scale scale = new ScaleImpl(new java.util.ArrayList<>(java.util.List.of(grid)));
    var wrapped = (CoverageImpl) CoverageImpl.full(scale); var copy = new CoverageImpl(wrapped);
    assertAll(
        () -> assertEquals(6L, grid.size()),
        () -> assertEquals(Time.Type.GRID, grid.getTimeType()),
        () -> assertEquals(1L, wrapped.getTime().size()),
        () -> assertEquals(1L, copy.asScale().getTime().size()),
        () -> assertTrue(wrapped.getTime().isGeneric()),
        () -> assertTrue(copy.getTime().isGeneric()));
  }

  @Test void customFiniteInitializationKeepsItsExistingShortcut() {
    Time custom = org.mockito.Mockito.mock(Time.class);
    org.mockito.Mockito.when(custom.collapsed()).thenReturn(custom);
    org.mockito.Mockito.when(custom.getType()).thenReturn(Geometry.Dimension.Type.TIME);
    org.mockito.Mockito.when(custom.getTimeType()).thenReturn(Time.Type.INITIALIZATION);
    org.mockito.Mockito.when(custom.is(Time.Type.INITIALIZATION)).thenReturn(true);
    org.mockito.Mockito.when(custom.getStart()).thenReturn(
        org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.create(0L));
    org.mockito.Mockito.when(custom.getEnd()).thenReturn(
        org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.create(5000L));
    org.mockito.Mockito.when(custom.size()).thenReturn(1L);
    org.mockito.Mockito.when(custom.getDimensionSize()).thenReturn(5000.0);
    Scale context = new ScaleImpl(new java.util.ArrayList<>(java.util.List.of(custom)));
    var merged = CoverageImpl.empty(context).merge(
        Geometry.create("T0(1){tstart=1000,tend=2000,ttype=PHYSICAL}"), LogicalConnector.UNION);
    assertAll(
        () -> assertEquals(1.0, merged.getCoverage(), 1e-9),
        () -> assertEquals(1.0, merged.getCoverage(Geometry.Dimension.Type.TIME), 1e-9),
        () -> assertEquals(0.0, merged.getGain(), 1e-9));
    org.mockito.Mockito.verify(custom, org.mockito.Mockito.atLeastOnce()).collapsed();
  }

  @Test void disjointIntervalsNeverGainCoverageAcrossEpochTranslations() {
    for (long origin : new long[]{-20000L, -10000L, 0L, 1000L}) {
      Scale context = GeometryRepository.INSTANCE.scale(Geometry.create(
          "T0(1){tstart=" + origin + ",tend=" + (origin + 10000L) + ",ttype=PHYSICAL}"));
      Geometry incoming = Geometry.create(
          "T0(1){tstart=" + (origin + 20000L) + ",tend=" + (origin + 30000L) + ",ttype=PHYSICAL}");
      var merged = CoverageImpl.empty(context).merge(incoming, LogicalConnector.UNION);
      assertAll("origin=" + origin,
          () -> assertEquals(0.0, merged.getCoverage(), 1e-9),
          () -> assertEquals(0.0, merged.getCoverage(Geometry.Dimension.Type.TIME), 1e-9),
          () -> assertEquals(0.0, merged.getGain(), 1e-9));
    }
  }

  private static Time runtimeTime(Time.Type type, Long start, Long end) {
    return TimeImpl.create(type, null, null,
        start == null ? null : org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.create(start.longValue()),
        end == null ? null : org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.create(end.longValue()),
        null, null, null, null);
  }

  private static void assertHalfCoverage(long origin) {
    Scale context = GeometryRepository.INSTANCE.scale(Geometry.create(
        "T0(1){tstart=" + origin + ",tend=" + (origin + 10000L) + ",ttype=PHYSICAL}"));
    Geometry incoming = Geometry.create(
        "T0(1){tstart=" + origin + ",tend=" + (origin + 5000L) + ",ttype=PHYSICAL}");
    var merged = CoverageImpl.empty(context).merge(incoming, LogicalConnector.UNION);
    assertAll("origin=" + origin,
        () -> assertEquals(0.5, merged.getCoverage(), 1e-9),
        () -> assertEquals(0.5, merged.getCoverage(Geometry.Dimension.Type.TIME), 1e-9),
        () -> assertEquals(0.5, merged.getGain(), 1e-9));
  }
}
