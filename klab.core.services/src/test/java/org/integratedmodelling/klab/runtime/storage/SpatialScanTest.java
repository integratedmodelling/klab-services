package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.Data.FillCurve;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class SpatialScanTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static String grid(int x, int y, double w, double e, double s, double n) {
    return ConformantScanTest.grid(x, y, w, e, s, n);
  }
  static StorageScan.Request<Storage.Scanner> request(String geometry, FillCurve curve,
      StorageScan.Sampling sampling, StorageScan.Coverage coverage, List<StorageScan.Partition> partitions) {
    return new StorageScan.Request<>(StorageScan.Slice.of(Scheduler.Event.initialization()),
        new StorageScan.Layout(curve, 1, 0, 0, null), geometry, partitions, null, Storage.Scanner.class,
        StorageScan.Access.READ_ONLY, StorageScan.Precision.LOSSLESS, coverage, sampling, new StorageScan.Budget(32, 4));
  }
  static void gate(ConformantScanTest.Fixture f, boolean value) {
    when(f.scope.getService(RuntimeService.class).settings().get(Setting.ACCEPT_LOSSY_MEDIATIONS, Boolean.class)).thenReturn(value);
  }

  @Test void nearestAcrossShardsCurvesAndPrimitiveTypesRetainsExactValues() {
    for (var type : List.of(Storage.Type.DOUBLE, Storage.Type.FLOAT, Storage.Type.INTEGER, Storage.Type.LONG, Storage.Type.BOOLEAN))
      for (var curve : List.of(FillCurve.D2_XY, FillCurve.D2_YX, FillCurve.D2_XInvY))
        try (var f = new ConformantScanTest.Fixture(3, type, curve)) {
          gate(f, true);
          var plan = f.storage.plan(request(grid(2,2,0,4,0,4), FillCurve.D2_XY,
              StorageScan.Sampling.NEAREST, StorageScan.Coverage.MISSING_OUTSIDE, List.of()));
          assertEquals(4, plan.description().version());
          assertSame(plan, f.storage.plan(request(grid(2,2,0,4,0,4), FillCurve.D2_XY,
              StorageScan.Sampling.NEAREST, StorageScan.Coverage.MISSING_OUTSIDE, List.of())));
          var restored = Utils.Json.parseObject(Utils.Json.asString(plan.description()), StorageScan.Description.class);
          assertEquals(plan.description(), restored);
          assertEquals(plan.description().fingerprint(), restored.fingerprint());
          try (var session = f.storage.open(plan)) {
            var scan = session.scanners().getFirst();
            assertThrows(UnsupportedOperationException.class, scan::shard);
            for (long code : new long[]{101,103,301,303}) ConformantScanTest.check(scan,type,code);
            assertFalse(scan.hasNext());
          }
        }
  }

  @Test void resolverClientRefreshesTheRuntimeSettingInsteadOfUsingItsOldSnapshot() {
    var client=mock(org.integratedmodelling.common.services.client.RuntimeClient.class);
    var settings=org.integratedmodelling.common.services.client.engine.SettingsImpl.forService(client,
        org.integratedmodelling.klab.api.services.KlabService.Type.RUNTIME);
    when(client.settings()).thenReturn(settings);
    when(client.readSettings()).thenReturn(Map.of(Setting.ACCEPT_LOSSY_MEDIATIONS.name(),true),
        Map.of(Setting.ACCEPT_LOSSY_MEDIATIONS.name(),false));
    var scope=mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(scope.getService(RuntimeService.class)).thenReturn(client);
    assertTrue(StorageReads.acceptsLossy(scope));assertFalse(StorageReads.acceptsLossy(scope));
    verify(client,times(2)).readSettings();
  }

  @Test void policyChangesRejectCachedPlansAndPreviouslyIssuedHandles() {
    try (var f = new ConformantScanTest.Fixture(3, Storage.Type.DOUBLE, FillCurve.D2_XY)) {
      gate(f, true);
      var request = request(grid(2,2,0,4,0,4), FillCurve.D2_XY, StorageScan.Sampling.NEAREST,
          StorageScan.Coverage.MISSING_OUTSIDE,List.of());
      var plan = f.storage.plan(request);
      gate(f, false);
      var failure = assertThrows(UnsupportedOperationException.class, () -> f.storage.plan(request));
      assertTrue(failure.getMessage().contains("ACCEPT_LOSSY_MEDIATIONS=false"));
      assertTrue(failure.getMessage().contains("resolution differs"));
      assertThrows(UnsupportedOperationException.class, () -> f.storage.open(plan));
      verifyNoInteractions(f.manager);
      assertEquals(2,f.storage.plan(request(grid(5,4,0,5,0,4),FillCurve.D2_YX,
          StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,List.of())).description().version());
      assertDoesNotThrow(() -> f.storage.plan(StorageScan.Request.nativeRead(Scheduler.Event.initialization(), f.strategy, Storage.Scanner.class)));
    }
  }

  @Test void partialAndNoOverlapHaveValidityAndRepeatableCursorWithoutExtrapolation() {
    for (var type : List.of(Storage.Type.DOUBLE, Storage.Type.LONG, Storage.Type.BOOLEAN))
      try (var f = new ConformantScanTest.Fixture(3,type,FillCurve.D2_XY)) {
        gate(f,true);
        var geometry = grid(3,1,-1,2,0,1);
        assertThrows(UnsupportedOperationException.class, () -> f.storage.plan(request(geometry,FillCurve.D2_XY,
            StorageScan.Sampling.NEAREST,StorageScan.Coverage.EXACT,List.of())));
        var plan = f.storage.plan(request(geometry,FillCurve.D2_XY,StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,List.of()));
        try (var session = f.storage.open(plan)) {
          var scan = session.scanners().getFirst();
          assertFalse(scan.isValid()); assertEquals("null",StorageScan.textValue(scan)); assertEquals(0,scan.position());
          if (scan instanceof Storage.LongScanner s) assertThrows(IllegalStateException.class,s::get);
          scan.seek(1); ConformantScanTest.check(scan,type,0);
          scan.seek(1); ConformantScanTest.check(scan,type,0);
        }
        try (var session = f.storage.open(f.storage.plan(request(grid(1,1,10,11,10,11),FillCurve.D2_XY,
            StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
          assertFalse(session.scanners().getFirst().isValid());
          assertTrue(session.scanners().getFirst().view().sources().isEmpty());
        }
      }
  }

  @Test void bilinearRampAndMissingKernelAreIndependentOfPartitionBoundaries() {
    for (var type : List.of(Storage.Type.DOUBLE,Storage.Type.FLOAT))
      try (var f = new ConformantScanTest.Fixture(3,type,FillCurve.D2_XInvY)) {
        gate(f,true);
        try (var session=f.storage.open(f.storage.plan(request(grid(2,2,.5,2.5,.5,2.5),FillCurve.D2_YX,
            StorageScan.Sampling.INTERPOLATE,StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
          var scan=session.scanners().getFirst();
          for (double expected:new double[]{50.75,150.75,51.75,151.75}) {
            assertTrue(scan.isValid());
            assertEquals(expected, Double.parseDouble(StorageScan.textValue(scan)),1e-6);
            scan.nextLong();
          }
        }
        try (var session=f.storage.open(f.storage.plan(request(grid(1,1,0,.5,0,.5),FillCurve.D2_XY,
            StorageScan.Sampling.INTERPOLATE,StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
          assertFalse(session.scanners().getFirst().isValid());
        }
      }
  }

  @Test void conservativeTotalsConserveCoarseningRefinementAndPartialOverlap() {
    try (var f=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_YX)) {
      gate(f,true);
      for (String geometry:List.of(grid(1,1,0,5,0,4),grid(10,8,0,5,0,4))) {
        try (var session=f.storage.open(f.storage.plan(request(geometry,FillCurve.D2_XY,
            StorageScan.Sampling.CONSERVATIVE_TOTAL,StorageScan.Coverage.EXACT,List.of())))) {
          var scan=(Storage.DoubleScanner)session.scanners().getFirst(); double sum=0;
          while(scan.hasNext())sum+=scan.get();
          assertEquals(4035,sum,1e-9);
        }
      }
      try(var session=f.storage.open(f.storage.plan(request(grid(1,1,-1,1,0,4),FillCurve.D2_XY,
          StorageScan.Sampling.CONSERVATIVE_TOTAL,StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
        assertEquals(7,((Storage.DoubleScanner)session.scanners().getFirst()).get(),1e-9);
      }
    }
  }

  @Test void conservativeDensitiesUseLatitudeWeightedAreaAndKeepConstants() {
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      gate(f,true);
      double sum=0,weights=0;
      for(int x=0;x<5;x++)for(int y=0;y<4;y++) {
        double area=Math.sin(Math.toRadians(y+1))-Math.sin(Math.toRadians(y));
        sum+=(100*x+y+.25)*area; weights+=area;
      }
      try(var session=f.storage.open(f.storage.plan(request(grid(1,1,0,5,0,4),FillCurve.D2_XY,
          StorageScan.Sampling.CONSERVATIVE,StorageScan.Coverage.EXACT,List.of())))) {
        assertEquals(sum/weights,((Storage.DoubleScanner)session.scanners().getFirst()).get(),1e-10);
      }
      for(var scanner:f.storage.scan(Scheduler.Event.initialization(),f.strategy,Storage.DoubleScanner.class,false)) {
        while(scanner.hasNext())scanner.add(7);
        f.storage.finalizeRun(scanner);
      }
      try(var session=f.storage.open(f.storage.plan(request(grid(3,2,-1,5,0,4),FillCurve.D2_XY,
          StorageScan.Sampling.CONSERVATIVE,StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
        var scan=(Storage.DoubleScanner)session.scanners().getFirst();
        while(scan.hasNext())assertEquals(7,scan.get(),1e-12);
      }
    }
  }

  @Test void floatResamplingRetainsDoublePrecisionUntilConsumerNarrowing() {
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.FLOAT,FillCurve.D2_YX)) {
      gate(f,true);
      var base=request(grid(1,1,.6,1.6,.6,1.6),FillCurve.D2_XY,StorageScan.Sampling.INTERPOLATE,
          StorageScan.Coverage.MISSING_OUTSIDE,List.of());
      var widened=new StorageScan.Request<>(base.slice(),base.layout(),base.geometry(),base.partitions(),null,
          Storage.DoubleScanner.class,base.access(),base.precision(),base.coverage(),base.sampling(),base.budget());
      try(var session=f.storage.open(f.storage.plan(widened))) {
        assertEquals(60.85,session.scanners().getFirst().get(),1e-12);
      }
    }
  }

  private static volatile double sink;
  @Test void movingSpatialCursorsDoNotAllocatePerValue() {
    var bean=java.lang.management.ManagementFactory.getThreadMXBean();
    Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
    var allocations=(com.sun.management.ThreadMXBean)bean;
    allocations.setThreadAllocatedMemoryEnabled(true);
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      gate(f,true);
      for(var sampling:List.of(StorageScan.Sampling.NEAREST,StorageScan.Sampling.INTERPOLATE,StorageScan.Sampling.CONSERVATIVE))
        try(var session=f.storage.open(f.storage.plan(request(grid(4,4,.5,4.5,.5,3.5),FillCurve.D2_XY,
            sampling,StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
          var scan=(Storage.DoubleScanner)session.scanners().getFirst();double sum=0;
          for(int i=0;i<100000;i++){scan.seek(i%16);sum+=scan.peek();}
          long before=allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
          for(int i=0;i<1000000;i++){scan.seek(i%16);sum+=scan.peek();}
          long bytes=allocations.getThreadAllocatedBytes(Thread.currentThread().threadId())-before;
          sink=sum;assertTrue(bytes<65536,sampling+" allocated "+bytes+" bytes");
        }
    }
  }

  @Test void sourceMissingnessRemainsMissingAcrossNearestInterpolationAndAggregation() {
    try(var f=new ConformantScanTest.Fixture(1,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      gate(f,true);
      var writer=f.storage.scan(Scheduler.Event.initialization(),f.strategy,Storage.DoubleScanner.class,false).getFirst();
      writer.add(Double.NaN);while(writer.hasNext())writer.add(10);f.storage.finalizeRun(writer);
      for(var sampling:List.of(StorageScan.Sampling.NEAREST,StorageScan.Sampling.INTERPOLATE,
          StorageScan.Sampling.CONSERVATIVE,StorageScan.Sampling.CONSERVATIVE_TOTAL)) {
        String geometry=sampling==StorageScan.Sampling.NEAREST?grid(1,1,0,.5,0,.5):grid(1,1,0,2,0,2);
        try(var session=f.storage.open(f.storage.plan(request(geometry,FillCurve.D2_XY,sampling,
            StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
          var scan=(Storage.DoubleScanner)session.scanners().getFirst();
          assertFalse(scan.isValid());assertTrue(Double.isNaN(scan.peek()));assertTrue(Double.isNaN(scan.get()));
        }
      }
    }
  }

  @Test void explicitTargetPartitionsNeedNotMatchTheSourcesCoverage() {
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      gate(f,true);
      var parts=List.of(new StorageScan.Partition("right",grid(1,2,2,4,0,4),2),
          new StorageScan.Partition("left",grid(1,2,0,2,0,4),2));
      var plan=f.storage.plan(request(null,FillCurve.D2_XY,StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,parts));
      assertEquals(parts,plan.description().partitions());
      try(var session=f.storage.open(plan)) {
        assertEquals(301.25,((Storage.DoubleScanner)session.scanners().getFirst()).get());
        assertEquals(101.25,((Storage.DoubleScanner)session.scanners().get(1)).get());
      }
      var gap=List.of(parts.getFirst(),new StorageScan.Partition("gap",grid(1,2,-2,0,0,4),2));
      assertThrows(UnsupportedOperationException.class,()->f.storage.plan(request(null,FillCurve.D2_XY,
          StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,gap)));
    }
  }

  @Test void targetOwnerCrsOverridesTheImplicitSourceCrsOfBareOutputPartitions() {
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      gate(f,true);
      double x=6378137*Math.toRadians(1.5),y=6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(1.5)/2));
      String geometry=grid(1,1,x-10,x+10,y-10,y+10).replace("4326","3857");
      var partitions=List.of(new StorageScan.Partition("output",geometry.replace("proj=EPSG:3857,",""),1));
      var plan=f.storage.plan(request(geometry,FillCurve.D2_XY,StorageScan.Sampling.NEAREST,
          StorageScan.Coverage.MISSING_OUTSIDE,partitions));
      assertEquals(new StorageScan.Spatial("EPSG:4326","EPSG:3857"),plan.description().spatial());
      assertEquals(partitions,plan.description().partitions());
      var json=Utils.Json.parseObject(Utils.Json.asString(plan.description()),StorageScan.Description.class);
      assertEquals(plan.description().fingerprint(),json.fingerprint());
      try(var session=f.storage.open(plan)) {
        assertEquals(plan.description().spatial(),session.scanners().getFirst().view().spatial());
        assertEquals(101.25,((Storage.DoubleScanner)session.scanners().getFirst()).get());
      }
      // An implicit target without a CRS inherits the source CRS as in exact remapping.
      assertDoesNotThrow(()->f.storage.plan(request(grid(1,1,0,5,0,4).replace("proj=EPSG:4326,",""),
          FillCurve.D2_XY,StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,List.of())));
    }
  }

  @Test void mercatorReprojectionUsesXYAndRejectsUnsupportedDomainsAndPolicies() {
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      gate(f,true);
      // Independent Web Mercator formula, target center is lon/lat (1.5, 1.5).
      double x=6378137*Math.toRadians(1.5),y=6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(1.5)/2));
      String projected=grid(1,1,x-10,x+10,y-10,y+10).replace("4326","3857");
      try(var session=f.storage.open(f.storage.plan(request(projected,FillCurve.D2_XY,
          StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,List.of())))) {
        assertEquals(101.25,((Storage.DoubleScanner)session.scanners().getFirst()).get());
      }
      for(String geometry:List.of(grid(1,1,179,181,0,1),grid(1,1,0,1,85,90),projected.replace("3857","32630"),
          grid(1,1,0,5,0,4).replace("tstart=1000","tstart=2000"),
          grid(5,4,0,5,0,4).replace("proj=EPSG:4326","rotation=30,proj=EPSG:4326")))
        assertThrows(UnsupportedOperationException.class,()->f.storage.plan(request(geometry,FillCurve.D2_XY,
            StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,List.of())));
      assertDoesNotThrow(()->f.storage.plan(request(grid(1,1,0,5,0,4).replace("proj=EPSG:4326","sgrid=1,proj=EPSG:4326"),
          FillCurve.D2_XY,StorageScan.Sampling.NEAREST,StorageScan.Coverage.MISSING_OUTSIDE,List.of())));
      assertThrows(UnsupportedOperationException.class,()->f.storage.plan(request(projected,FillCurve.D2_XY,
          StorageScan.Sampling.CONSERVATIVE_TOTAL,StorageScan.Coverage.MISSING_OUTSIDE,List.of())));
    }
    try(var f=new ConformantScanTest.Fixture(1,Storage.Type.BOOLEAN,FillCurve.D2_XY)) {
      gate(f,true);
      assertThrows(UnsupportedOperationException.class,()->f.storage.plan(request(grid(1,1,0,5,0,4),FillCurve.D2_XY,
          StorageScan.Sampling.CONSERVATIVE,StorageScan.Coverage.MISSING_OUTSIDE,List.of())));
    }
  }
}
