package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.Data.FillCurve;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;
import org.ojalgo.array.BufferArray;

class ConformantScanTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static final String TIME = "T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}";
  static String grid(int x, int y, double west, double east, double south, double north) {
    return TIME + "S2(" + x + "," + y + "){proj=EPSG:4326,bbox=[" + west + " " + east + " " + south + " " + north + "]}";
  }
  static double[] bounds(String geometry) {
    var value = StorageScan.parseGeometry(geometry).dimension(Geometry.Dimension.Type.SPACE).getParameters().get("bbox");
    return Arrays.stream(value.toString().replace('[',' ').replace(']',' ').trim().split("[\\s,]+"))
        .mapToDouble(Double::parseDouble).toArray();
  }
  // Independent small-grid oracle: explicit traversal enumeration rather than the production codec.
  static List<long[]> cells(String geometry, FillCurve curve) {
    var space = StorageScan.parseGeometry(geometry).dimension(Geometry.Dimension.Type.SPACE);
    long nx = space.getShape().get(0), ny = space.getShape().get(1);
    var b = bounds(geometry); var result = new ArrayList<long[]>();
    if (curve == FillCurve.D2_YX) {
      for (long y=0; y<ny; y++) for (long x=0; x<nx; x++) result.add(new long[]{(long)b[0]+x,(long)b[2]+y});
    } else {
      for (long x=0; x<nx; x++) for (long j=0; j<ny; j++)
        result.add(new long[]{(long)b[0]+x,(long)b[2]+(curve==FillCurve.D2_XInvY ? ny-1-j : j)});
    }
    return result;
  }
  static long code(long[] cell) { return 100 * cell[0] + cell[1]; }
  static void write(Storage.Scanner scanner, Storage.Type type, long code) {
    switch (type) {
      case DOUBLE -> ((Storage.DoubleScanner)scanner).add(code + 0.25);
      case FLOAT -> ((Storage.FloatScanner)scanner).add(code + 0.25f);
      case INTEGER -> ((Storage.IntScanner)scanner).add((int)code);
      case LONG -> ((Storage.LongScanner)scanner).add(Long.MAX_VALUE-code);
      case BOOLEAN -> ((Storage.BooleanScanner)scanner).add(code%2==0);
      default -> throw new AssertionError(type);
    }
  }
  static void check(Storage.Scanner scanner, Storage.Type type, long code) {
    assertTrue(scanner.isValid());
    switch (type) {
      case DOUBLE -> assertEquals(code+0.25, ((Storage.DoubleScanner)scanner).get());
      case FLOAT -> assertEquals(code+0.25f, ((Storage.FloatScanner)scanner).get());
      case INTEGER -> assertEquals((int)code, ((Storage.IntScanner)scanner).get());
      case LONG -> assertEquals(Long.MAX_VALUE-code, ((Storage.LongScanner)scanner).get());
      case BOOLEAN -> assertEquals(code%2==0, ((Storage.BooleanScanner)scanner).get());
      default -> throw new AssertionError(type);
    }
  }
  static class Fixture implements AutoCloseable {
    final StorageManagerImpl manager = mock(StorageManagerImpl.class);
    final Data.ShardingStrategy strategy;
    final StorageImpl storage;
    Fixture(int splits, Storage.Type type, FillCurve curve) {
      var observation = new ObservationImpl(); var concept = new ConceptImpl();
      concept.setUrn("test:quality"); concept.setName("quality"); concept.getType().add(SemanticType.QUALITY);
      observation.setObservable(ObservableImpl.promote(concept,null)); observation.setId(-1); observation.setUrn("test:grid");
      observation.setGeometry(Geometry.create(TIME + "S2(5,4){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 4&comma;5 4&comma;5 0&comma;0 0))}"));
      strategy = new Data.ShardingStrategy(curve,splits,0,0,type);
      var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
      when(scope.getConfiguration().getPersistence()).thenReturn(Persistence.EXPLICIT_ACTION);
      when(manager.getDoubleBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.R064.make(c.getArgument(0,Long.class)));
      when(manager.getFloatBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.R032.make(c.getArgument(0,Long.class)));
      when(manager.getIntBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.Z032.make(c.getArgument(0,Long.class)));
      when(manager.getLongBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.Z064.make(c.getArgument(0,Long.class)));
      when(manager.getBooleanBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.Z008.make(c.getArgument(0,Long.class)));
      storage = new StorageImpl(observation,strategy,scope,manager);
      for (var scanner : storage.scan(Scheduler.Event.initialization(),strategy,Storage.Scanner.class,false)) {
        for (var cell : cells(scanner.shard().getGeometry().encode(), curve)) write(scanner,type,code(cell));
        storage.finalizeRun(scanner);
      }
      clearInvocations(manager);
    }
    StorageScan.Request<Storage.Scanner> request(int splits, FillCurve curve, long minimum, long maximum,
        List<StorageScan.Partition> partitions, String geometry) {
      return new StorageScan.Request<>(StorageScan.Slice.of(Scheduler.Event.initialization()),
          new StorageScan.Layout(curve,splits,minimum,maximum,strategy.getDataType()), geometry, partitions, null,
          Storage.Scanner.class,StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,
          StorageScan.Coverage.EXACT,StorageScan.Sampling.EXACT,StorageScan.Budget.defaults());
    }
    public void close() { storage.close(null); }
  }

  @Test void splitMergeAndPermutationPreserveEveryPrimitiveCell() {
    for (var type : new Storage.Type[]{Storage.Type.DOUBLE,Storage.Type.FLOAT,Storage.Type.INTEGER,Storage.Type.LONG,Storage.Type.BOOLEAN})
      for (var sourceCurve : new FillCurve[]{FillCurve.D2_XY,FillCurve.D2_YX,FillCurve.D2_XInvY})
        for (int nativeSplits : new int[]{1,3}) try (var f = new Fixture(nativeSplits,type,sourceCurve)) {
          for (var targetCurve : new FillCurve[]{FillCurve.D2_XY,FillCurve.D2_YX,FillCurve.D2_XInvY})
            for (int targetSplits : new int[]{1,4}) {
              var request = f.request(targetSplits,targetCurve,0,0,List.of(),null);
              var plan = f.storage.plan(request);
              assertSame(plan, f.storage.plan(request));
              verifyNoInteractions(f.manager);
              try (var session = f.storage.open(plan)) {
                boolean[] seen = new boolean[20];
                for (var scanner : session.scanners()) {
                  for (var cell : cells(scanner.view().partition().geometry(),targetCurve)) {
                    int index = (int)(cell[0]*4+cell[1]); assertFalse(seen[index]); seen[index]=true;
                    check(scanner,type,code(cell));
                  }
                  assertFalse(scanner.hasNext());
                }
                for (boolean visited : seen) assertTrue(visited);
              }
            }
        }
  }

  @Test void explicitUnevenAndReorderedPartitionsUseGeographicIdentity() {
    try (var f = new Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      var parts = List.of(new StorageScan.Partition("right",grid(3,4,2,5,0,4).replace(TIME,""),12),
          new StorageScan.Partition("left",grid(2,4,0,2,0,4).replace(TIME,""),8));
      var plan = f.storage.plan(f.request(2,FillCurve.D2_XInvY,0,12,parts,grid(5,4,0,5,0,4).replace(TIME,"")));
      assertEquals(2,plan.description().version());
      var roundtrip = Utils.Json.parseObject(Utils.Json.asString(plan.description()),StorageScan.Description.class);
      assertEquals(plan.description(),roundtrip); assertEquals(plan.description().fingerprint(),roundtrip.fingerprint());
      try (var session = f.storage.open(plan)) {
        assertEquals("right",session.scanners().getFirst().view().partition().id());
        for (var scanner : session.scanners()) {
          if (scanner.view().sources().size()>1) assertThrows(UnsupportedOperationException.class,scanner::shard);
          for (var cell : cells(scanner.view().partition().geometry(),FillCurve.D2_XInvY)) check(scanner,Storage.Type.DOUBLE,code(cell));
        }
      }
    }
  }

  @Test void sizeHintsProduceBoundedPartitionsWithoutChangingNativeLayout() {
    try (var f = new Fixture(1,Storage.Type.INTEGER,FillCurve.D2_XY)) {
      var plan = f.storage.plan(f.request(1,FillCurve.D2_XY,2,3,List.of(),null));
      assertTrue(plan.description().partitions().size()>1);
      assertTrue(plan.description().partitions().stream().allMatch(p -> p.size()<=3));
      assertEquals(1,f.storage.getNativeShardingStrategy().getSuggestedSplits());
      var soft = f.storage.plan(f.request(20,FillCurve.D2_XY,10,0,List.of(),null));
      assertEquals(2,soft.description().partitions().size());
      assertNotEquals(plan.description().fingerprint(),soft.description().fingerprint());
      assertEquals(20,f.storage.plan(f.request(20,FillCurve.D2_XY,0,1,List.of(),null)).description().partitions().size());
    }
  }

  @Test void nonconformanceAndPartitionDefectsRejectBeforeOpening() {
    try (var f = new Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      for (String geometry : List.of(grid(5,4,0.5,5.5,0,4),grid(10,4,0,5,0,4),grid(4,4,0,4,0,4),
          grid(5,4,0,5,0,4).replace("4326","3857"),
          "S2(5,4){proj=EPSG:4326,shape=EPSG:3857 POLYGON ((0 0,0 4,5 4,5 0,0 0))}")) {
        assertThrows(UnsupportedOperationException.class, () -> f.storage.plan(f.request(1,FillCurve.D2_XY,0,0,List.of(),geometry)));
      }
      var overlap = List.of(new StorageScan.Partition("a",grid(3,4,0,3,0,4),12),new StorageScan.Partition("b",grid(3,4,2,5,0,4),12));
      var gap = List.of(new StorageScan.Partition("a",grid(2,4,0,2,0,4),8),new StorageScan.Partition("b",grid(2,4,3,5,0,4),8));
      for (var parts : List.of(overlap,gap)) assertThrows(UnsupportedOperationException.class,
          () -> f.storage.plan(f.request(2,FillCurve.D2_XY,0,0,parts,null)));
      verifyNoInteractions(f.manager);
      try (var nativeSession = f.storage.open(f.storage.plan(StorageScan.Request.nativeRead(Scheduler.Event.initialization(),f.strategy,Storage.Scanner.class)))) {
        for (var scanner : nativeSession.scanners()) for (var cell : cells(scanner.view().partition().geometry(),FillCurve.D2_XY))
          check(scanner,Storage.Type.DOUBLE,code(cell));
      }
    }
  }

  @Test void primitiveBlockGatherAndContiguousRunsCrossUnevenSourceBoundaries() {
    for (var type : new Storage.Type[]{Storage.Type.DOUBLE,Storage.Type.FLOAT,Storage.Type.INTEGER,Storage.Type.LONG,Storage.Type.BOOLEAN})
      for (var curve : new FillCurve[]{FillCurve.D2_XY,FillCurve.D2_YX,FillCurve.D2_XInvY}) {
        var layout = new StorageScan.Layout(FillCurve.D2_XY,2,0,0,type);
        var descriptors = List.of(new StorageScan.SourceShard("a",grid(2,4,0,2,0,4),8,0,0,layout),
            new StorageScan.SourceShard("b",grid(3,4,2,5,0,4),12,1,0,layout));
        var request = new StorageScan.Request<>(StorageScan.Slice.of(Scheduler.Event.initialization()),
            new StorageScan.Layout(curve,1,0,0,type),null,List.of(),null,Storage.Scanner.class,
            StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,StorageScan.Coverage.EXACT,
            StorageScan.Sampling.EXACT,new StorageScan.Budget(8,20));
        var plan = ConformantScan.compile(descriptors,request,grid(5,4,0,5,0,4));
        var buffers = new ArrayList<BufferArray>(); var readers = new ArrayList<IndexedStorageReader>();
        try {
          for (var descriptor : descriptors) {
            BufferArray buffer = switch (type) {
              case DOUBLE -> (BufferArray)BufferArray.R064.make(descriptor.size());
              case FLOAT -> (BufferArray)BufferArray.R032.make(descriptor.size());
              case INTEGER -> (BufferArray)BufferArray.Z032.make(descriptor.size());
              case LONG -> (BufferArray)BufferArray.Z064.make(descriptor.size());
              case BOOLEAN -> (BufferArray)BufferArray.Z008.make(descriptor.size());
              default -> throw new AssertionError(type);
            };
            buffers.add(buffer); long i=0;
            for (var cell : cells(descriptor.geometry(),FillCurve.D2_XY)) {
              long code=code(cell);
              if (type==Storage.Type.LONG) buffer.set(i++,Long.MAX_VALUE-code);
              else if (type==Storage.Type.BOOLEAN) buffer.set(i++,(byte)(code%2==0 ? 1 : 0));
              else buffer.set(i++,code);
            }
            readers.add(new LocalIndexedReader(buffer,type,20));
          }
          try (var reader = new ConformantReader(plan,0,readers,20)) {
            var expected = cells(plan.partitions.getFirst().geometry(),curve);
            switch (type) {
              case DOUBLE -> { double[] values=new double[20]; reader.readDoubles(0,values,0,20);
                for(int i=0;i<20;i++) assertEquals(code(expected.get(i)),values[i]); }
              case FLOAT -> { float[] values=new float[20]; reader.readFloats(0,values,0,20);
                for(int i=0;i<20;i++) assertEquals(code(expected.get(i)),values[i]); }
              case INTEGER -> { int[] values=new int[20]; reader.readInts(0,values,0,20);
                for(int i=0;i<20;i++) assertEquals(code(expected.get(i)),values[i]); }
              case LONG -> { long[] values=new long[20]; reader.readLongs(0,values,0,20);
                for(int i=0;i<20;i++) assertEquals(Long.MAX_VALUE-code(expected.get(i)),values[i]); }
              case BOOLEAN -> { boolean[] values=new boolean[20]; reader.readBooleans(0,values,0,20);
                for(int i=0;i<20;i++) assertEquals(code(expected.get(i))%2==0,values[i]); }
              default -> throw new AssertionError(type);
            }
            assertThrows(IndexOutOfBoundsException.class, () -> reader.readLong(-1));
            if (type==Storage.Type.DOUBLE) assertThrows(IndexOutOfBoundsException.class, () -> reader.readDoubles(0,new double[21],0,21));
          }
        } finally { readers.forEach(IndexedStorageReader::close); buffers.forEach(BufferArray::close); }
      }
  }

  private static volatile double allocationSink;
  @Test void mappedFloatWideningRemainsPrimitiveAndSessionOwned() {
    var management=java.lang.management.ManagementFactory.getThreadMXBean();
    Assumptions.assumeTrue(management instanceof com.sun.management.ThreadMXBean);
    var allocations=(com.sun.management.ThreadMXBean)management;
    Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
    allocations.setThreadAllocatedMemoryEnabled(true);
    try(var f=new Fixture(3,Storage.Type.FLOAT,FillCurve.D2_XY)) {
      var original=f.request(1,FillCurve.D2_YX,0,0,List.of(),null);
      var request=new StorageScan.Request<>(original.slice(),original.layout(),null,List.of(),null,
          Storage.DoubleScanner.class,original.access(),original.precision(),original.coverage(),original.sampling(),original.budget());
      var plan=f.storage.plan(request); var first=f.storage.open(plan); var second=f.storage.open(plan);
      var cursor=first.scanners().getFirst(); double total=0;
      for(int i=0;i<200000;i++) if(cursor.isValid()) total+=cursor.peek();
      long thread=Thread.currentThread().threadId(), before=allocations.getThreadAllocatedBytes(thread);
      for(int i=0;i<1000000;i++) if(cursor.isValid()) total+=cursor.peek();
      long allocated=allocations.getThreadAllocatedBytes(thread)-before; allocationSink=total;
      assertEquals(300000,allocationSink); assertTrue(allocated<65536,"Allocated "+allocated);
      System.out.println("Mapped scanner allocation: "+allocated+" bytes / 1000000 reads");
      first.cancel(); assertThrows(IllegalStateException.class,cursor::get);
      assertEquals(0.25,second.scanners().getFirst().get()); second.close();
      var writer=(Storage.FloatScanner)f.storage.getNativeScanner(f.storage.getNativeShards(Scheduler.Event.initialization()).getFirst());
      writer.add(Float.NaN); f.storage.finalizeRun(writer);
      assertThrows(IllegalStateException.class,()->f.storage.open(plan));
      var updated=f.storage.plan(request); assertNotSame(plan,updated);
      try(var session=f.storage.open(updated)) {
        int missing=0; var scan=session.scanners().getFirst();
        while(scan.hasNext()) { if(!scan.isValid()) missing++; scan.get(); }
        assertEquals(1,missing);
      }
    }
  }

  @Test void emptyIdentityAndSingletonPartitionsHaveStrictCursors() {
    var strategy=Data.ShardingStrategy.trivial(Storage.Type.DOUBLE);
    String geometry="S1(0){proj=EPSG:4326,bbox=[0 0]}";
    var layout=StorageScan.Layout.of(strategy);
    var semantics=new StorageScan.Semantics("test:empty","","","","");
    var slice=StorageScan.Slice.of(Scheduler.Event.initialization());
    var source=new StorageScan.SourceShard("empty",geometry,0,0,0,layout);
    var description=new StorageScan.Description(1,"empty-revision",1,"empty",slice,semantics,semantics,
        layout,layout,List.of(source),List.of(new StorageScan.Partition("empty",geometry,0)),Storage.Type.DOUBLE,
        StorageScan.Precision.LOSSLESS,StorageScan.Coverage.EXACT,StorageScan.Sampling.EXACT,
        StorageScan.Budget.defaults(),List.of(StorageScan.Operation.IDENTITY),StorageScan.HistogramPolicy.UNAVAILABLE);
    var plan=new StorageScan.Plan<Storage.DoubleScanner>() {
      public StorageScan.Description description() { return description; }
      public Class<Storage.DoubleScanner> scannerClass() { return Storage.DoubleScanner.class; }
    };
    var reader=mock(IndexedStorageReader.class);
    try(var session=new LocalScanSession<>(plan,List.of(mock(Storage.Shard.class)),List.of(reader),null,()->{})) {
      var cursor=session.scanners().getFirst(); assertFalse(cursor.hasNext()); assertEquals(0,cursor.position());
      assertThrows(NoSuchElementException.class,cursor::get); assertThrows(NoSuchElementException.class,cursor::isValid);
      verifyNoInteractions(reader);
    }
    verify(reader).close();
    var box=new ConformantScan.Box(new long[]{7},new long[]{1}); long[] point=new long[1];
    box.decode(0,FillCurve.D1_LINEAR,point); assertEquals(7,point[0]); assertEquals(0,box.encode(point,FillCurve.D1_LINEAR));
    assertThrows(IndexOutOfBoundsException.class,()->box.decode(1,FillCurve.D1_LINEAR,point));
  }

  @Test void movingIndexedGatherUsesFixedScratchSpace() {
    var management=java.lang.management.ManagementFactory.getThreadMXBean();
    Assumptions.assumeTrue(management instanceof com.sun.management.ThreadMXBean);
    var allocations=(com.sun.management.ThreadMXBean)management;
    Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported()); allocations.setThreadAllocatedMemoryEnabled(true);
    var layout=new StorageScan.Layout(FillCurve.D2_XY,2,0,0,Storage.Type.DOUBLE);
    var descriptors=List.of(new StorageScan.SourceShard("a",grid(2,4,0,2,0,4),8,0,0,layout),
        new StorageScan.SourceShard("b",grid(3,4,2,5,0,4),12,1,0,layout));
    var request=StorageScan.Request.nativeRead(Scheduler.Event.initialization(),
        new Data.ShardingStrategy(FillCurve.D2_YX,1,0,0,Storage.Type.DOUBLE),Storage.DoubleScanner.class);
    var mapping=ConformantScan.compile(descriptors,request,grid(5,4,0,5,0,4));
    var buffers=new ArrayList<BufferArray>(); var readers=new ArrayList<IndexedStorageReader>();
    try {
      for(var descriptor:descriptors) {
        var buffer=(BufferArray)BufferArray.R064.make(descriptor.size()); buffers.add(buffer); long i=0;
        for(var cell:cells(descriptor.geometry(),FillCurve.D2_XY)) buffer.set(i++, (double)code(cell));
        readers.add(new LocalIndexedReader(buffer,Storage.Type.DOUBLE,20));
      }
      try(var reader=new ConformantReader(mapping,0,readers,20)) {
        double total=0;
        for(int i=0;i<200000;i++) total+=reader.readDouble(i%20);
        long thread=Thread.currentThread().threadId(), before=allocations.getThreadAllocatedBytes(thread);
        for(int i=0;i<1000000;i++) total+=reader.readDouble(i%20);
        long bytes=allocations.getThreadAllocatedBytes(thread)-before; allocationSink=total;
        assertEquals(241800000,allocationSink); assertTrue(bytes<65536,"Gather allocated "+bytes);
        System.out.println("Moving gather allocation: "+bytes+" bytes / 1000000 indexed reads");
      }
    } finally { readers.forEach(IndexedStorageReader::close); buffers.forEach(BufferArray::close); }
  }

  @Test void portableDefinitionsRetainWktAndUnspecifiedTypeWithoutLosingIdentity() {
    String wkt="S2(5,4){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0,0 4,5 4,5 0,0 0))}";
    var partition=new StorageScan.Partition("wkt",wkt,20);
    assertEquals(partition,new StorageScan.Partition("wkt",partition.geometry(),20));
    try(var f=new Fixture(3,Storage.Type.DOUBLE,FillCurve.D2_XY)) {
      var original=f.request(1,FillCurve.D2_YX,0,0,List.of(),null);
      var request=new StorageScan.Request<>(original.slice(),new StorageScan.Layout(FillCurve.D2_YX,1,0,0,null),
          null,List.of(),null,Storage.DoubleScanner.class,original.access(),original.precision(),original.coverage(),original.sampling(),original.budget());
      var description=f.storage.plan(request).description();
      var restored=Utils.Json.parseObject(Utils.Json.asString(description),StorageScan.Description.class);
      assertEquals(description.fingerprint(),restored.fingerprint());
    }
  }

  @Test void persistedSourceDirectoryCannotOmitAnOuterStripOrOverlap() {
    var layout=new StorageScan.Layout(FillCurve.D2_XY,2,0,0,Storage.Type.DOUBLE);
    var request=StorageScan.Request.nativeRead(Scheduler.Event.initialization(),
        new Data.ShardingStrategy(FillCurve.D2_YX,1,0,0,Storage.Type.DOUBLE),Storage.DoubleScanner.class);
    for(String right:List.of(grid(2,4,2,4,0,4),grid(3,4,1,4,0,4))) {
      var descriptors=List.of(new StorageScan.SourceShard("left",grid(2,4,0,2,0,4),8,0,0,layout),
          new StorageScan.SourceShard("right",right,StorageScan.parseGeometry(right).size(),1,0,layout));
      assertThrows(UnsupportedOperationException.class,()->ConformantScan.compile(descriptors,request,grid(5,4,0,5,0,4)));
    }
  }

  @Test void checkedLongCodecsAndThreeDimensionalMappingDoNotNarrowIndices() {
    var large = new ConformantScan.Box(new long[]{0,0},new long[]{100000,100000});
    for (var curve : new FillCurve[]{FillCurve.D1_LINEAR,FillCurve.D2_XY,FillCurve.D2_YX,FillCurve.D2_XInvY}) {
      long[] point = new long[2]; long index = 9000000007L;
      large.decode(index,curve,point); assertEquals(index,large.encode(point,curve));
      assertTrue(point[0]>=0 && point[1]>=0);
    }
    assertThrows(ArithmeticException.class, () -> new ConformantScan.Box(new long[]{0,0},new long[]{Long.MAX_VALUE,2}));
    var layout = new StorageScan.Layout(FillCurve.D3_XYZ,1,0,0,Storage.Type.LONG);
    String geometry = "S3(3,2,4){proj=EPSG:4979,bbox=[0 3 0 2 0 4]}";
    var source = new StorageScan.SourceShard("three",geometry,24,0,0,layout);
    var request = new StorageScan.Request<>(StorageScan.Slice.of(Scheduler.Event.initialization()),
        new StorageScan.Layout(FillCurve.D1_LINEAR,3,0,0,Storage.Type.LONG),null,List.of(),null,
        Storage.LongScanner.class,StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,
        StorageScan.Coverage.EXACT,StorageScan.Sampling.EXACT,StorageScan.Budget.defaults());
    var plan = ConformantScan.compile(List.of(source),request,geometry);
    boolean[] seen = new boolean[24]; long[] point = new long[3];
    for (var target : plan.targets) for (long i=0; i<target.size; i++) {
      target.decode(i,FillCurve.D1_LINEAR,point);
      long offset = plan.sources[0].encode(point,FillCurve.D3_XYZ);
      assertEquals((point[0]*2+point[1])*4+point[2],offset);
      assertFalse(seen[(int)offset]); seen[(int)offset]=true;
    }
    for (boolean value : seen) assertTrue(value);
  }
}
