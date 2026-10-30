package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

class GridInspectorTest {
  private static final String EXPECTED = "S2(5,4){proj=EPSG:4326,bbox=[0 5 0 4]}";
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private static class Fixture implements AutoCloseable {
    final ConformantScanTest.Fixture nativeData;
    final StorageImpl storage;
    final ObservationImpl observation;
    final List<Storage.Shard> descriptors;
    Fixture(int splits, Data.FillCurve curve) {
      this(splits, curve, null);
    }
    Fixture(int splits, Data.FillCurve curve, String geometry) {
      nativeData = geometry == null ? new ConformantScanTest.Fixture(splits, Storage.Type.DOUBLE, curve)
          : new ConformantScanTest.Fixture(splits, Storage.Type.DOUBLE, curve, geometry);
      observation = (ObservationImpl) nativeData.storage.temporalOwner(); observation.setId(42);
      storage = spy(nativeData.storage);
      descriptors = nativeData.storage.getNativeShards(Scheduler.Event.initialization());
      when(nativeData.scope.getDigitalTwin().getStorageManager().getStorage(observation)).thenReturn(storage);
      descriptors(descriptors);
      clearInvocations(storage, nativeData.manager);
    }
    void descriptors(List<Storage.Shard> shards) {
      when(nativeData.scope.getDigitalTwin().getKnowledgeGraph().query(Storage.Shard.class, nativeData.scope)
          .source(observation).along(GraphModel.Relationship.HAS_DATA).run(nativeData.scope)).thenReturn(shards);
    }
    boolean check(String expected) { return GridInspector.check(nativeData.scope, observation, expected); }
    public void close() { nativeData.close(); }
  }

  @Test void allCellsMatchIndependentNativeCoordinateValuesAcrossCurvesAndPartitions() {
    // Native data were filled by ConformantScanTest.cells' explicit nested loops, with value
    // 100*x+y+0.25. The checker independently enumerates all cells again and every consumer view.
    for (var curve : List.of(Data.FillCurve.D2_XY, Data.FillCurve.D2_YX, Data.FillCurve.D2_XInvY))
      for (int splits : new int[] {1, 3}) try (var f = new Fixture(splits, curve)) {
        assertTrue(f.check(EXPECTED));
        verify(f.storage, never()).getNativeShards(any());
        verifyNoInteractions(f.nativeData.manager);
      }
  }

  @Test void wrongDimensionsBoundsOrCrsCannotPassAnOtherwiseConsistentScan() {
    try (var f = new Fixture(3, Data.FillCurve.D2_XY)) {
      assertFalse(f.check(EXPECTED.replace("5,4", "4,5")));
      assertFalse(f.check(EXPECTED.replace("0 5 0 4", "1 6 0 4")));
      assertFalse(f.check(EXPECTED.replace("4326", "3857")));
      assertTrue(f.check(EXPECTED));
    }
  }

  @Test void missingOrDuplicatedCommittedShardDescriptorsCannotPass() {
    try (var f = new Fixture(3, Data.FillCurve.D2_XY)) {
      f.descriptors(f.descriptors.subList(1, f.descriptors.size()));
      assertFalse(f.check(EXPECTED));
      var duplicate = new ArrayList<>(f.descriptors); duplicate.add(duplicate.getFirst());
      f.descriptors(duplicate); assertFalse(f.check(EXPECTED));
      f.descriptors(f.descriptors); assertTrue(f.check(EXPECTED));
    }
  }

  @Test void infiniteOrEntirelyMissingPayloadsDoNotProvideNumericEvidence() {
    try (var f = new Fixture(1, Data.FillCurve.D2_XY)) {
      var scanner = (Storage.DoubleScanner) f.nativeData.storage.getNativeScanner(f.descriptors.getFirst());
      scanner.add(Double.POSITIVE_INFINITY);
      assertFalse(f.check(EXPECTED));
      scanner.seek(0); while (scanner.hasNext()) scanner.add(Double.NaN);
      assertFalse(f.check(EXPECTED));
    }
  }

  private static void corruptConsumer(Fixture f, boolean corruptValue) {
    doAnswer(call -> {
      @SuppressWarnings("unchecked") var real =
          (StorageScan.Session<Storage.Scanner>) call.callRealMethod();
      var original = (Storage.DoubleScanner) real.scanners().getFirst();
      var altered = mock(Storage.DoubleScanner.class, AdditionalAnswers.delegatesTo(original));
      if (corruptValue) doAnswer(ignored -> original.peek() + 1).when(altered).peek();
      else doAnswer(ignored -> {
        var cell = original.cell(); var b = cell.bounds().getFirst();
        return new StorageScan.Cell(cell.x(), cell.y(), cell.projection(),
            List.of(new StorageScan.CellBounds(b.minX()+1, b.maxX()+1, b.minY(), b.maxY())));
      }).when(altered).cell();
      var scanners = new ArrayList<Storage.Scanner>(real.scanners()); scanners.set(0, altered);
      return new StorageScan.Session<Storage.Scanner>() {
        public List<Storage.Scanner> scanners() { return scanners; }
        public boolean isClosed() { return real.isClosed(); }
        public void close() { real.close(); }
      };
    }).when(f.storage).open(any());
  }

  @Test void consumerValueCorruptionCannotPassJustBecauseOtherPlannerRoutesAgree() {
    try (var f = new Fixture(1, Data.FillCurve.D2_XY)) {
      corruptConsumer(f, true); assertFalse(f.check(EXPECTED));
    }
  }

  @Test void wrongPhysicalCellFootprintCannotPassWithCorrectValues() {
    try (var f = new Fixture(1, Data.FillCurve.D2_XY)) {
      corruptConsumer(f, false); assertFalse(f.check(EXPECTED));
    }
  }

  @Test void inspectorRejectsUnboundedOrNonSpatialExpectedGridsBeforeReadingData() {
    try (var f = new Fixture(1, Data.FillCurve.D2_XY)) {
      assertThrows(IllegalArgumentException.class, () -> f.check("S2(1000,1000){proj=EPSG:4326,bbox=[0 5 0 4]}"));
      assertThrows(IllegalArgumentException.class, () -> f.check("T0(1){ttype=PHYSICAL,tstart=0,tend=1000}"));
      f.observation.setGeometry(Geometry.create(ConformantScanTest.TIME + "S2(5,4){bbox=[0 5 0 4]}"));
      assertThrows(UnsupportedOperationException.class, () -> f.check(EXPECTED));
    }
  }

  @Test void projectedNativeAndIdentityPlannedCellsInheritOwnerCrsWithoutChangingPayload() {
    String projected = "S2(5,4){proj=EPSG:3857,bbox=[200000 200500 6000000 6000400]}";
    try (var f = new Fixture(3, Data.FillCurve.D2_XY, ConformantScanTest.TIME + projected)) {
      assertTrue(f.descriptors.stream().anyMatch(s ->
          !s.getGeometry().dimension(Geometry.Dimension.Type.SPACE).getParameters().containsKey("proj")),
          "Exercise partial descriptors that actually omit the inherited CRS");
      long cells = 0;
      for (var shard : f.descriptors) {
        var geometry = shard.getGeometry().dimension(Geometry.Dimension.Type.SPACE);
        var b = ConformantScanTest.bounds(shard.getGeometry().encode());
        long ny = geometry.getShape().get(1);
        var scanner = f.storage.getNativeScanner(shard);
        while (scanner.hasNext()) {
          long p = scanner.position(), x = p / ny, y = p % ny;
          long[] xy = new long[2]; scanner.spatialCoordinates(xy);
          assertArrayEquals(new long[] {x,y}, xy);
          var cell = scanner.cell(); assertEquals("EPSG:3857", cell.projection());
          var bounds = cell.bounds().getFirst();
          assertEquals(b[0] + x * 100, bounds.minX());
          assertEquals(b[0] + (x+1) * 100, bounds.maxX());
          assertEquals(b[2] + y * 100, bounds.minY());
          assertEquals(b[2] + (y+1) * 100, bounds.maxY());
          scanner.nextLong(); cells++;
        }
      }
      assertEquals(20, cells);
      assertTrue(f.check(projected)); // Includes identity fast path plus remapping and all20 raw values.
      verify(f.storage, never()).getNativeShards(any());
    }
  }

  private static LocalScanSession<Storage.DoubleScanner> singleSession(String geometry, String fallback) {
    var layout = new StorageScan.Layout(Data.FillCurve.D2_XY,1,0,0,Storage.Type.DOUBLE);
    var source = new StorageScan.SourceShard("single", geometry, 1, 0, 0, layout);
    var semantics = new StorageScan.Semantics("test:single","","","","");
    var description = new StorageScan.Description(1,"revision",42,"test:single",
        StorageScan.Slice.of(Scheduler.Event.initialization()),semantics,semantics,layout,layout,
        List.of(source),List.of(new StorageScan.Partition("single",geometry,1)),Storage.Type.DOUBLE,
        StorageScan.Precision.LOSSLESS,StorageScan.Coverage.EXACT,StorageScan.Sampling.EXACT,
        StorageScan.Budget.defaults(),List.of(StorageScan.Operation.IDENTITY),StorageScan.HistogramPolicy.UNAVAILABLE);
    var plan = new StorageScan.Plan<Storage.DoubleScanner>() {
      public StorageScan.Description description() { return description; }
      public Class<Storage.DoubleScanner> scannerClass() { return Storage.DoubleScanner.class; }
    };
    var reader = mock(IndexedStorageReader.class);
    when(reader.type()).thenReturn(Storage.Type.DOUBLE); when(reader.readDouble(0)).thenReturn(42d);
    return new LocalScanSession<>(plan,List.of(mock(Storage.Shard.class)),List.of(reader),null,()->{},
        null,null,fallback);
  }

  @Test void explicitSourceCrsTakesPrecedenceOverOwnerFallback() {
    try (var session = singleSession("S2(1,1){proj=EPSG:4326,bbox=[0 1 0 1]}", "EPSG:3857")) {
      var scanner = session.scanners().getFirst();
      assertEquals(42d, scanner.peek()); assertEquals("EPSG:4326", scanner.cell().projection());
    }
  }

  @Test void absentCrsFailsOnlyWhenSpatialInformationIsRequestedWithoutGuessingDefault() {
    try (var session = singleSession("S2(1,1){bbox=[0 1 0 1]}", null)) {
      var scanner = session.scanners().getFirst();
      assertEquals(42d, scanner.peek(), "Ordinary primitive reads need no spatial promotion");
      assertThrows(UnsupportedOperationException.class, scanner::cell);
      assertEquals(42d, scanner.peek());
    }
  }
}
