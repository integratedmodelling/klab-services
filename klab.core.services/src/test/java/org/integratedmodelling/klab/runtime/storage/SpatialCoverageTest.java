package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.Data.FillCurve;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.Coordinate;

class SpatialCoverageTest {
  private static volatile double allocationSink;
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static String geometry(String wkt) {
    return ConformantScanTest.grid(5,4,0,5,0,4)
        .replace("bbox=[0.0 5.0 0.0 4.0]", "bbox=[0.0 5.0 0.0 4.0],shape=EPSG:4326 " + wkt.replace(",","&comma;"));
  }
  static final String TRIANGLE = "POLYGON ((0 0,5 0,0 4,0 0))";
  static final String HOLE = "POLYGON ((0 0,5 0,5 4,0 4,0 0),(1.5 1.5,1.5 3.5,3.5 3.5,3.5 1.5,1.5 1.5))";
  static final String ISLANDS = "MULTIPOLYGON (((0 0,1 0,1 1,0 1,0 0)),((4 3,5 3,5 4,4 4,4 3)))";

  @Test void maskMatchesIndependentPointInPolygonAcrossCurvesPartitionsAndPrimitiveTypes() {
    for (String wkt : List.of(TRIANGLE, HOLE, ISLANDS))
      for (var curve : List.of(FillCurve.D2_XY, FillCurve.D2_YX, FillCurve.D2_XInvY))
        for (var type : List.of(Storage.Type.DOUBLE, Storage.Type.FLOAT, Storage.Type.INTEGER, Storage.Type.LONG, Storage.Type.BOOLEAN)) {
          var polygon = ShapeImpl.create("EPSG:4326 " + wkt).getJTSGeometry();
          var expected = new HashSet<Long>();
          for (long x=0;x<5;x++) for (long y=0;y<4;y++)
            if (polygon.covers(polygon.getFactory().createPoint(new Coordinate(x+.5,y+.5)))) expected.add(100*x+y);
          try (var f = new ConformantScanTest.Fixture(3,type,FillCurve.D2_XY,geometry(wkt))) {
            var plan = f.storage.plan(f.request(2,curve,0,0,List.of(),null));
            assertEquals(7,plan.description().version());
            var decoded = Utils.Json.parseObject(Utils.Json.asString(plan.description()), StorageScan.Description.class);
            assertEquals(plan.description().fingerprint(),decoded.fingerprint());
            var found = new HashSet<Long>();
            try (var session = f.storage.open(plan)) {
              for (var scanner : session.scanners()) {
                while (scanner.hasNext()) {
                  var cell = scanner.cell();
                  long[] xy = new long[2]; scanner.spatialCoordinates(xy);
                  assertEquals(cell.x(),xy[0]); assertEquals(cell.y(),xy[1]);
                  var b = cell.bounds().getFirst();
                  long code = 100*(long)b.minX()+(long)b.minY();
                  assertTrue(found.add(code));
                  ConformantScanTest.check(scanner,type,code);
                }
                assertEquals(scanner.size(),scanner.position());
                assertThrows(NoSuchElementException.class,scanner::cell);
              }
            }
            assertEquals(expected,found,wkt+" "+curve+" "+type);
          }
        }
  }

  @Test void targetSupportSkipsCellsWhileSourceSupportGatesMissingValues() {
    try (var full = new ConformantScanTest.Fixture(1,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      var plan = full.storage.plan(full.request(1,FillCurve.D2_XY,0,0,List.of(),geometry(TRIANGLE)));
      try (var session = full.storage.open(plan)) {
        var scan = session.scanners().getFirst();
        scan.seek(19); assertFalse(scan.hasNext()); assertEquals(20,scan.position());
        scan.seek(0); assertEquals(0,scan.position());
        assertEquals(0,scan.nextLong()); assertEquals(1,scan.nextLong());
        scan.seek(7); assertEquals(8,scan.position());
        assertEquals(200.25,((Storage.DoubleScanner)scan).get());
      }
    }
    try (var masked = new ConformantScanTest.Fixture(1,Storage.Type.DOUBLE,FillCurve.D2_XY,geometry(TRIANGLE))) {
      var plan = masked.storage.plan(masked.request(1,FillCurve.D2_XY,0,0,List.of(),ConformantScanTest.grid(5,4,0,5,0,4)));
      try (var session = masked.storage.open(plan)) {
        var scan = (Storage.DoubleScanner)session.scanners().getFirst();
        scan.seek(19); assertEquals(19,scan.position()); assertFalse(scan.isValid()); assertTrue(Double.isNaN(scan.get()));
        scan.seek(0); assertTrue(scan.isValid()); assertEquals(.25,scan.get());
      }
    }
  }

  @Test void fullRectangleRetainsNativeScannerAndCoordinatesFollowTheCurve() {
    for (var curve : List.of(FillCurve.D2_XY,FillCurve.D2_YX,FillCurve.D2_XInvY))
      try (var f = new ConformantScanTest.Fixture(1,Storage.Type.FLOAT,curve)) {
        var scan = f.storage.scan(Scheduler.Event.initialization(),f.strategy,Storage.DoubleScanner.class,true).getFirst();
        scan.seek(6); long[] xy = new long[2]; scan.spatialCoordinates(xy);
        var expected = ConformantScanTest.cells(scan.shard().getGeometry().encode(),curve).get(6);
        assertArrayEquals(expected,xy); assertEquals(xy[0],scan.cell().x());
        assertEquals(ConformantScanTest.code(expected)+.25,scan.get());
        var nativeScanner = f.storage.getNativeScanner(scan.shard());
        assertInstanceOf(StorageImpl.BaseScanner.class,nativeScanner);
        assertNull(f.storage.plan(f.request(1,curve,0,0,List.of(),null)).description().mask());
      }
  }

  @Test void trillionCellSparseCoverageJumpsEmptyRunsWithoutCellSizedState() {
    String text = "S2(1000000,1000000){proj=EPSG:32634,bbox=[0 1000000 0 1000000],shape=EPSG:32634 "
        + "MULTIPOLYGON (((0 0,1 0,1 1,0 1,0 0)),((999999 999999,1000000 999999,1000000 1000000,999999 1000000,999999 999999)))}";
    var support = SpatialCoverage.support(text);
    for (var curve : List.of(FillCurve.D2_XY,FillCurve.D2_YX,FillCurve.D2_XInvY)) {
      var coverage = new SpatialCoverage(text,null,curve,support);
      assertEquals(2,coverage.coveredSize());
      var cursor = coverage.new Cursor();
      long first = cursor.next(0), last = cursor.next(first+1);
      assertTrue(last>first); assertEquals(1_000_000_000_000L,cursor.next(last+1));
      assertTrue(cursor.rangesTested<300,"Range queries: "+cursor.rangesTested);
    }
  }

  @Test void wrappedAndClippedCellFootprintsUsePhysicalCentres() {
    String text = "S2(4,2){proj=EPSG:4326,bbox=[1 361 -91 89],world=[-180 180 -90 90]}";
    var coverage = new SpatialCoverage(text,null,FillCurve.D2_XY,null);
    var cell = coverage.cell(2);
    assertEquals(1,cell.x()); assertEquals(0,cell.y()); assertEquals(2,cell.bounds().size());
    assertEquals(-90,cell.bounds().getFirst().minY());
    assertTrue(cell.wkt().startsWith("MULTIPOLYGON"));
    assertEquals(-45.5,coverage.centre(0,1));
    assertEquals(2,ShapeImpl.promote(cell.shape()).getJTSGeometry().getNumGeometries());
  }

  @Test void explicitOutputPartitionsKeepInputAndOutputCursorsInStep() {
    try (var input = new ConformantScanTest.Fixture(1,Storage.Type.DOUBLE,FillCurve.D2_XY);
         var output = new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY,geometry(TRIANGLE))) {
      var writers = output.storage.scan(Scheduler.Event.initialization(),output.strategy,Storage.DoubleScanner.class,false);
      var partitions = new ArrayList<StorageScan.Partition>();
      for (int i=0;i<writers.size();i++) partitions.add(new StorageScan.Partition("output-"+i,
          writers.get(i).shard().getGeometry().encode(),writers.get(i).size()));
      var plan = input.storage.plan(input.request(3,FillCurve.D2_XY,0,0,partitions,geometry(TRIANGLE)));
      try (var session = input.storage.open(plan)) {
        for (int i=0;i<writers.size();i++) {
          var writer=writers.get(i); var reader=(Storage.DoubleScanner)session.scanners().get(i);
          while (writer.hasNext()) {
            assertTrue(reader.hasNext()); assertEquals(writer.position(),reader.position());
            assertEquals(writer.cell().bounds(),reader.cell().bounds()); writer.add(reader.get());
          }
          assertFalse(reader.hasNext()); output.storage.finalizeRun(writer);
        }
      }
    }
  }

  @Test void tileCoveragePreservesHoleBoundariesCopiesAndTransport() {
    var shape=ShapeImpl.create("EPSG:4326 "+HOLE);
    var grid=new org.integratedmodelling.klab.runtime.scale.space.GridImpl(shape.getEnvelope(),shape,5,4);
    var tile=new org.integratedmodelling.klab.runtime.scale.space.TileImpl(shape,grid,false);
    assertTrue(tile.isCellCovered(1,1)); // Centre lies on the hole boundary.
    assertFalse(tile.isCellCovered(2,2));
    var copy=tile.copy();
    assertTrue(copy.isCellCovered(1,1)); assertFalse(copy.isCellCovered(2,2));
    var transported=SpatialCoverage.support(tile.encode());
    assertEquals(19,new SpatialCoverage(tile.encode(),null,FillCurve.D2_XY,transported).coveredSize());
    assertEquals(tile.encode(),copy.encode());
  }

  @Test void acceptedInteriorRunHasNoPerCellAllocation() {
    var management=java.lang.management.ManagementFactory.getThreadMXBean();
    Assumptions.assumeTrue(management instanceof com.sun.management.ThreadMXBean);
    var allocations=(com.sun.management.ThreadMXBean)management;
    Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
    allocations.setThreadAllocatedMemoryEnabled(true);
    String text="S2(1000000,2){proj=EPSG:32634,bbox=[0 1000000 0 2],shape=EPSG:32634 "
        +"POLYGON ((0 0,1000000 0,1000000 1,900000 1,900000 2,0 2,0 0))}";
    var coverage=new SpatialCoverage(text,null,FillCurve.D2_XY,SpatialCoverage.support(text));
    var dense=new Storage.DoubleScanner() {
      long index;
      public long size() { return 2000000; }
      public long position() { return index; }
      public boolean hasNext() { return index<size(); }
      public void seek(long offset) { index=offset; }
      public boolean isValid() { return true; }
      public long nextLong() { return index++; }
      public Storage.Shard shard() { throw new UnsupportedOperationException(); }
      public double get() { index++; return 1; }
      public double peek() { return 1; }
      public void add(double value) { index++; }
    };
    var scan=(Storage.DoubleScanner)CoveredScanner.wrap(dense,coverage);
    for(int i=0;i<200000;i++) { assertTrue(scan.hasNext()); allocationSink+=scan.get(); }
    scan.seek(0); assertTrue(scan.hasNext());
    long thread=Thread.currentThread().threadId(), before=allocations.getThreadAllocatedBytes(thread);
    double total=0;
    for(int i=0;i<1000000;i++) { if(scan.hasNext()) total+=scan.get(); }
    long bytes=allocations.getThreadAllocatedBytes(thread)-before; allocationSink=total;
    assertEquals(1000000,total); assertTrue(bytes<65536,"Masked interior allocated "+bytes);
    System.out.println("Masked interior allocation: "+bytes+" bytes / 1000000 advancing reads");
  }
}
