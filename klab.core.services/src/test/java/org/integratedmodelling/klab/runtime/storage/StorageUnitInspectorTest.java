package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real unit views must not turn IEEE nonfinite comparisons into successful assertions. */
class StorageUnitInspectorTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private static class Fixture implements AutoCloseable {
    final ConformantScanTest.Fixture nativeData =
        new ConformantScanTest.Fixture(1, Storage.Type.DOUBLE, Data.FillCurve.D2_XY);
    final ObservationImpl observation = (ObservationImpl) nativeData.storage.temporalOwner();
    final ContextScope scope = mock(ContextScope.class, RETURNS_DEEP_STUBS);
    Fixture() {
      observation.setId(42);
      ((ObservableImpl) observation.getObservable()).setUnit(new UnitImpl("m"));
      when(scope.getObservation(42L)).thenReturn(observation);
      when(scope.getDigitalTwin().getStorageManager().getStorage(observation)).thenReturn(nativeData.storage);
    }
    void first(double value) {
      var shard = nativeData.storage.getNativeShards(Scheduler.Event.initialization()).getFirst();
      var scanner = (Storage.DoubleScanner) nativeData.storage.getNativeScanner(shard);
      scanner.add(value); nativeData.storage.finalizeRun(scanner);
    }
    void allMissing() {
      for (var shard : nativeData.storage.getNativeShards(Scheduler.Event.initialization())) {
        var scanner = (Storage.DoubleScanner) nativeData.storage.getNativeScanner(shard);
        while (scanner.hasNext()) scanner.add(Double.NaN);
        nativeData.storage.finalizeRun(scanner);
      }
    }
    boolean check(String unit, double factor, double offset) {
      return StorageReadInspector.unitCheck(scope, observation, unit, factor, offset);
    }
    public void close() { nativeData.close(); }
  }

  @Test void nonfiniteFactorsAreRejectedBeforeAnySourceOrStorageAccess() {
    var scope = mock(ContextScope.class);
    for (double factor : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
      assertThrows(IllegalArgumentException.class,
          () -> StorageReadInspector.unitCheck(scope, null, "mm", factor, 0));
    verifyNoInteractions(scope);
  }

  @Test void nonfiniteOffsetsAreRejectedBeforeAnySourceOrStorageAccess() {
    var scope = mock(ContextScope.class);
    for (double offset : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
      assertThrows(IllegalArgumentException.class,
          () -> StorageReadInspector.unitCheck(scope, null, "mm", 1000, offset));
    verifyNoInteractions(scope);
  }

  @Test void allTwentyFiniteNativeValuesStillConvertFromMetersToMillimeters() {
    try (var f = new Fixture()) {
      assertTrue(f.check("mm", 1000, 0));
      assertEquals("m", ((UnitImpl) f.observation.getObservable().getUnit()).getDefinition());
      verify(f.scope.getDigitalTwin().getStorageManager(), never()).createStorage(any());
    }
  }

  @Test void overflowOfBothExpectedAndActualValuesCannotPassAsInfinityMinusInfinity() {
    try (var f = new Fixture()) {
      f.first(Double.MAX_VALUE / 2);
      var failure = assertThrows(IllegalStateException.class, () -> f.check("mm", 1000, 0));
      assertTrue(failure.getMessage().contains("Non-finite unit conversion"));
    }
  }

  @Test void expectedOverflowCannotMakeInfiniteToleranceAcceptAnIncorrectFiniteActualValue() {
    try (var f = new Fixture()) {
      f.first(2);
      var failure = assertThrows(IllegalStateException.class, () -> f.check("m", Double.MAX_VALUE, 0));
      assertTrue(failure.getMessage().contains("Non-finite unit conversion"));
    }
  }

  @Test void actualOverflowIsRejectedEvenWhenTheIndependentExpectedValueIsFinite() {
    try (var f = new Fixture()) {
      f.first(Double.MAX_VALUE / 2);
      var failure = assertThrows(IllegalStateException.class, () -> f.check("mm", 1, 0));
      assertTrue(failure.getMessage().contains("Non-finite unit conversion"));
    }
  }

  @Test void nativeInfinityIsNotAcceptedAsValidUnitEvidenceWhileMissingnessRemainsSupported() {
    try (var f = new Fixture()) {
      f.first(Double.POSITIVE_INFINITY);
      assertThrows(IllegalStateException.class, () -> f.check("m", 1, 0));
    }
    try (var f = new Fixture()) {
      f.first(Double.NaN);
      assertTrue(f.check("mm", 1000, 0), "Ordinary no-data retains its validity mask across conversion");
    }
  }

  @Test void entirelyMissingRealStorageCannotCertifyTheExpectedUnitMapping() {
    try (var f = new Fixture()) {
      f.allMissing();
      assertFalse(f.check("mm", 1000, 0), "A mapping requires at least one finite compared value");
    }
  }
}
