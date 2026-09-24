package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.ojalgo.array.BufferArray;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class TemporalStorageTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  @TempDir Path directory;
  static Scheduler.Event event(String id,long start,long end) {
    return new Scheduler.Event() {
      public Time getTime() { return TimePeriod.create(start,end); }
      public Type getType() { return Type.TEMPORAL_TRANSITION; }
      public String toKey() { return id; }
      public Observation getEvent() { return null; }
    };
  }

  class Fixture {
    final ServiceContextScope scope = mock(ServiceContextScope.class,RETURNS_DEEP_STUBS);
    final DigitalTwin twin = mock(DigitalTwin.class);
    final KnowledgeGraph graph = mock(KnowledgeGraph.class,RETURNS_DEEP_STUBS);
    final StorageManagerImpl manager = mock(StorageManagerImpl.class);
    final ObservationImpl quality = new ObservationImpl();
    final Data.ShardingStrategy layout = Data.ShardingStrategy.trivial(Storage.Type.DOUBLE);
    final List<Storage.Shard> committed = new ArrayList<>();
    final List<Storage.Shard> pending = new ArrayList<>();
    final List<Runnable> rollback = new ArrayList<>(), afterCommit = new ArrayList<>();
    StorageImpl storage;
    DigitalTwin.Transaction transaction;

    Fixture(boolean created) {
      var concept = new ConceptImpl(); concept.setUrn("test:elevation"); concept.setName("elevation");
      concept.getType().add(SemanticType.QUALITY); concept.getType().add(SemanticType.QUANTIFIABLE);
      quality.setObservable(ObservableImpl.promote(concept,null)); quality.setId(10);
      quality.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}S2(3,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;3 1&comma;3 0&comma;0 0))}"));
      var cd = new ObservationImpl.ContextualizationDataImpl(); cd.setNativeShardingStrategy(layout);
      quality.setContextualizationData(cd);
      if (created) quality.getMetadata().put(TemporalHistory.EPHEMERAL,true);
      when(scope.getDigitalTwin()).thenReturn(twin);
      when(scope.getConfiguration().getPersistence()).thenReturn(Persistence.EXPLICIT_ACTION);
      when(twin.getKnowledgeGraph()).thenReturn(graph); when(twin.getStorageManager()).thenReturn(manager);
      when(manager.isRecordHistogram()).thenReturn(true);
      when(manager.getDoubleBuffer(anyLong())).thenAnswer(call -> (BufferArray)BufferArray.R064.make(call.getArgument(0,Long.class)));
      when(manager.getStorageFile(any())).thenAnswer(call -> directory.resolve(call.getArgument(0,Storage.Shard.class).getUrn()+".dat").toFile());
      doCallRealMethod().when(manager).persistTemporalShard(any(),any());
      when(manager.loadBufferArray(any(),any(),any())).thenCallRealMethod();
      when(graph.query(Storage.Shard.class,scope).source(quality).along(GraphModel.Relationship.HAS_DATA).run(scope))
          .thenAnswer(call -> new ArrayList<>(committed));
      restore();
      if (!created) {
        var scanner = storage.scan(Scheduler.Event.initialization(),layout,Storage.DoubleScanner.class,false).getFirst();
        assertEquals(3,scanner.size()); scanner.add(100); scanner.add(200); scanner.add(350);
        storage.finalizeRun(scanner);
        committed.add(scanner.shard());
        manager.persistTemporalShard(scanner.shard(),((StorageImpl.BaseScanner)scanner).data);
      }
    }
    void restore() {
      storage = new StorageImpl(quality,layout,scope,manager);
      when(manager.createStorage(quality)).thenReturn(storage);
    }
    LocalTemporalWriteSet begin(String id,long start,long end) {
      rollback.clear(); afterCommit.clear(); pending.clear();
      transaction = mock(DigitalTwin.Transaction.class);
      when(scope.getCurrentTransaction()).thenReturn(transaction);
      doAnswer(call -> { rollback.add(call.getArgument(0)); return null; }).when(transaction).afterRollback(any());
      doAnswer(call -> { afterCommit.add(call.getArgument(0)); return null; }).when(transaction).afterCommit(any());
      doAnswer(call -> { pending.add(call.getArgument(1)); return null; })
          .when(transaction).link(eq(quality),any(Storage.Shard.class),eq(GraphModel.Relationship.HAS_DATA),any(),any(),any(),any(),any(),any());
      return new LocalTemporalWriteSet(event(id,start,end),scope);
    }
    void commit() { committed.addAll(pending); afterCommit.forEach(Runnable::run); }
    void abort() { rollback.reversed().forEach(Runnable::run); }
    Storage.DoubleScanner scan(LocalTemporalWriteSet writes,TemporalWriteSet.Access access) {
      return writes.scan(quality,layout,Storage.DoubleScanner.class,access).getFirst();
    }
    long files() throws Exception { try(var paths=Files.list(directory)) { return paths.count(); } }
    double[] read(LocalTemporalWriteSet writes) {
      var scanner=scan(writes,TemporalWriteSet.Access.PRIOR);
      return new double[]{scanner.get(),scanner.get(),scanner.get()};
    }
  }

  @Test void eventRelativeSpatialReadPinsPriorAndCurrentOverPartialTargetExtent() {
    var f=new Fixture(false);
    org.mockito.Mockito.doReturn(mock(org.integratedmodelling.klab.api.services.RuntimeService.class,RETURNS_DEEP_STUBS)).when(f.scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class).settings()
        .get(org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS,Boolean.class)).thenReturn(true);
    var writes=f.begin("earthquake",1000,2000);
    var writer=f.scan(writes,TemporalWriteSet.Access.WRITE);writer.add(90);
    var request=new StorageScan.Request<>(StorageScan.Slice.of(event("earthquake",1000,2000)),
        new StorageScan.Layout(Data.FillCurve.D2_YX,1,0,0,null),"S2(4,1){proj=EPSG:4326,bbox=[-1 3 0 1]}",
        List.of(),null,Storage.DoubleScanner.class,StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,
        StorageScan.Coverage.MISSING_OUTSIDE,StorageScan.Sampling.NEAREST,StorageScan.Budget.defaults());
    try(var prior=writes.read(f.quality,request,TemporalWriteSet.Access.PRIOR);
        var current=writes.read(f.quality,request,TemporalWriteSet.Access.CURRENT)) {
      var before=prior.scanners().getFirst();var now=current.scanners().getFirst();
      assertFalse(before.isValid());assertTrue(Double.isNaN(before.get()));
      now.seek(1);assertEquals(90,now.peek());assertEquals(100,before.get());
      writer.add(80);now.seek(2);assertEquals(200,now.get());
      assertEquals(4,current.description().version());
      f.abort();assertTrue(prior.isClosed());assertTrue(current.isClosed());
    } finally {f.storage.close(null);}
  }

  @Test void temporalConversionsUsePinnedPriorAndCurrentValuesAndReleaseOnRollback() {
    var f = new Fixture(false);
    var original = (ObservableImpl) f.quality.getObservable();
    original.setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m"));
    var target = new ObservableImpl(original); target.setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("mm"));
    var writes = f.begin("converted",1000,2000);
    var writer = f.scan(writes,TemporalWriteSet.Access.WRITE); writer.add(90);
    var request = new StorageScan.Request<>(StorageScan.Slice.of(event("converted",1000,2000)),
        new StorageScan.Layout(Data.FillCurve.D2_YX,2,0,0,null),null,List.of(),StorageScan.semantics(target),Storage.DoubleScanner.class,
        StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,StorageScan.Coverage.EXACT,
        StorageScan.Sampling.EXACT,StorageScan.Budget.defaults());
    try (var prior = writes.read(f.quality,request,TemporalWriteSet.Access.PRIOR);
         var current = writes.read(f.quality,request,TemporalWriteSet.Access.CURRENT)) {
      assertEquals(100000,prior.scanners().getFirst().peek());
      assertEquals(90000,current.scanners().getFirst().peek());
      assertEquals(3,current.description().version());
      writer.add(80);
      assertEquals(90000,current.scanners().getFirst().peek());
      f.abort(); assertTrue(prior.isClosed()); assertTrue(current.isClosed());
    }
    assertSame(original,f.quality.getObservable()); f.storage.close(null);
  }

  @Test void transactionViewsRemapPriorAndSnapshotCurrentWithoutPublishing() {
    var f = new Fixture(false); var writes = f.begin("mapped",1000,2000);
    var writer = f.scan(writes,TemporalWriteSet.Access.WRITE); writer.add(999);
    var request = new StorageScan.Request<>(StorageScan.Slice.of(event("mapped",1000,2000)),
        new StorageScan.Layout(Data.FillCurve.D2_YX,2,0,0,null),null,List.of(),null,Storage.DoubleScanner.class,
        StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,StorageScan.Coverage.EXACT,
        StorageScan.Sampling.EXACT,StorageScan.Budget.defaults());
    try (var prior = writes.read(f.quality,request,TemporalWriteSet.Access.PRIOR);
         var current = writes.read(f.quality,request,TemporalWriteSet.Access.CURRENT)) {
      assertEquals(100,prior.scanners().getFirst().get());
      assertEquals(999,current.scanners().getFirst().get());
      writer.add(777);
      var values = new ArrayList<Double>();
      for (var scanner : current.scanners()) { scanner.seek(0); while(scanner.hasNext()) values.add(scanner.get()); }
      assertEquals(List.of(999.0,200.0,350.0),values,"CURRENT is pinned when read is opened");
      assertEquals(0,f.pending.size());
      f.abort(); assertTrue(prior.isClosed()); assertTrue(current.isClosed());
    }
    f.storage.close(null);
  }

  @Test void plannedSessionKeepsItsCommittedRevisionWhileLaterDataIsPublished() {
    var f = new Fixture(false);
    var writes = f.begin("first", 1000, 2000);
    f.scan(writes, TemporalWriteSet.Access.WRITE).add(90);
    writes.prepare(); f.commit();
    var request = StorageScan.Request.nativeRead(event("first", 1000, 2000),
        f.layout, Storage.DoubleScanner.class);
    try (var session = f.storage.open(f.storage.plan(request))) {
      writes = f.begin("second", 2000, 3000);
      f.scan(writes, TemporalWriteSet.Access.WRITE).add(80);
      writes.prepare(); f.commit();
      assertEquals(90, session.scanners().getFirst().get());
      try (var latest = f.storage.open(f.storage.plan(StorageScan.Request.nativeRead(
          event("second", 2000, 3000), f.layout, Storage.DoubleScanner.class)))) {
        assertEquals(80, latest.scanners().getFirst().get());
        assertNotEquals(session.scanners().getFirst().shard().getUrn(),
            latest.scanners().getFirst().shard().getUrn());
      }
    } finally { f.storage.close(null); }
  }

  @Test void unchangedReadersWritersAndRevertedWritesAllocateNothing() throws Exception {
    var f=new Fixture(false); var writes=f.begin("no-op",1000,2000);
    assertArrayEquals(new double[]{100,200,350},f.read(writes));
    var scanner=f.scan(writes,TemporalWriteSet.Access.WRITE);
    scanner.add(100); scanner.nextLong(); scanner.add(350);
    assertFalse(writes.changed(f.quality));
    scanner=f.scan(writes,TemporalWriteSet.Access.WRITE); scanner.add(90);
    assertTrue(writes.changed(f.quality));
    scanner=f.scan(writes,TemporalWriteSet.Access.WRITE); scanner.add(100);
    assertFalse(writes.changed(f.quality));
    writes.prepare(); f.commit();
    assertEquals(1,f.files()); assertTrue(f.pending.isEmpty());
    assertFalse(f.quality.getMetadata().containsKey(TemporalHistory.KEY));
  }

  @Test void immutableValuesFilesAndLinksSurviveRollbackRetryAndReconstruction() throws Exception {
    var f=new Fixture(false); var writes=f.begin("first",1000,2000);
    f.scan(writes,TemporalWriteSet.Access.WRITE).add(90);
    assertArrayEquals(new double[]{100,200,350},f.read(writes));
    assertEquals(90,f.scan(writes,TemporalWriteSet.Access.CURRENT).get());
    writes.prepare(); assertEquals(2,f.files()); f.abort();
    assertEquals(1,f.files()); assertEquals(1,f.committed.size());
    assertFalse(f.quality.getMetadata().containsKey(TemporalHistory.KEY));
    writes=f.begin("first",1000,2000); f.scan(writes,TemporalWriteSet.Access.WRITE).add(90);
    writes.prepare(); f.commit(); assertEquals(2,f.committed.size());
    f.storage.close(null); f.restore();
    assertArrayEquals(new double[]{90,200,350},f.read(f.begin("next",2000,3000)));
    assertArrayEquals(new double[]{100,200,350},f.read(f.begin("historical",1000,1500)));
    writes=f.begin("second",2000,3000); f.scan(writes,TemporalWriteSet.Access.WRITE).add(80);
    writes.prepare(); f.commit(); f.storage.close(null); f.restore();
    assertArrayEquals(new double[]{80,200,350},f.read(f.begin("latest",3000,4000)));
    assertArrayEquals(new double[]{90,200,350},f.read(f.begin("historical-second",2000,2500)));
    assertEquals(3,f.files()); assertEquals(3,f.committed.size());
  }

  @Test void createdZeroIsAnObservationButUntouchedAndIncompleteCreationAreNot() throws Exception {
    var f=new Fixture(true); var writes=f.begin("empty",1000,2000);
    f.scan(writes,TemporalWriteSet.Access.WRITE); writes.prepare(); f.commit();
    assertEquals(0,f.files()); assertTrue(f.committed.isEmpty());
    writes=f.begin("partial",1000,2000); f.scan(writes,TemporalWriteSet.Access.WRITE).add(0);
    assertThrows(IllegalStateException.class,writes::prepare); f.abort(); assertEquals(0,f.files());
    writes=f.begin("zero",1000,2000); var scanner=f.scan(writes,TemporalWriteSet.Access.WRITE);
    scanner.add(0); scanner.add(0); scanner.add(0); writes.prepare(); f.commit();
    assertEquals(1,f.files()); assertEquals(1,f.committed.size());
  }

  @Test void equalTimeEventsRemainDistinctAndReadOnlyAfterReconstruction() throws Exception {
    var f = new Fixture(false);
    var first = f.begin("a",1000,2000); f.scan(first,TemporalWriteSet.Access.WRITE).add(90);
    first.prepare(); f.commit();
    var second = f.begin("b",1000,2000); f.scan(second,TemporalWriteSet.Access.WRITE).add(80);
    second.prepare(); f.commit(); f.storage.close(null); f.restore();
    assertEquals(3,f.committed.size()); assertEquals(3,f.files());
    var a = f.storage.scan(event("a",1000,2000),f.layout,Storage.DoubleScanner.class,true).getFirst();
    var b = f.storage.scan(event("b",1000,2000),f.layout,Storage.DoubleScanner.class,true).getFirst();
    assertEquals(90,a.get()); assertEquals(80,b.get());
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class, () -> b.add(2));
    assertArrayEquals(new double[]{80,200,350},f.read(f.begin("next",2000,3000)));
    assertEquals(2,f.storage.getHistograms().size());
  }

  @Test void nativeNoDataEqualityAndSignedZeroArePreserved() throws Exception {
    var f = new Fixture(false);
    var writes=f.begin("native",1000,2000); var out=f.scan(writes,TemporalWriteSet.Access.WRITE);
    out.add(Double.NaN); out.add(-0.0); out.add(350); writes.prepare();f.commit();
    writes=f.begin("same",2000,3000);out=f.scan(writes,TemporalWriteSet.Access.WRITE);
    out.add(Double.longBitsToDouble(0x7ff8000000000001L));out.add(-0.0);
    assertFalse(writes.changed(f.quality));writes.prepare();f.commit();assertEquals(2,f.files());
    writes=f.begin("zero",2000,3000);out=f.scan(writes,TemporalWriteSet.Access.WRITE);
    out.nextLong();out.add(0.0);assertTrue(writes.changed(f.quality));writes.prepare();f.commit();
    f.storage.close(null);f.restore();
    assertEquals(Double.doubleToLongBits(0.0),Double.doubleToLongBits(f.read(f.begin("next",3000,4000))[1]));
  }

  @Test void graphCopiesUseTheCachedOwnerForDataMetadataAndDeltas() {
    var f=new Fixture(false);
    var copy=new ObservationImpl();copy.setId(f.quality.getId());copy.setObservable(f.quality.getObservable());
    copy.setGeometry(f.quality.getGeometry());copy.setContextualizationData(f.quality.getContextualizationData());
    when(f.manager.createStorage(copy)).thenReturn(f.storage);
    var writes=f.begin("copy",1000,2000);
    writes.scan(copy,f.layout,Storage.DoubleScanner.class,TemporalWriteSet.Access.WRITE).getFirst().add(90);
    assertTrue(writes.changed(copy));assertTrue(writes.changed(f.quality));
    assertSame(f.quality,writes.changedObservations().iterator().next());
    writes.prepare();f.commit();
    assertTrue(f.quality.getMetadata().containsKey(TemporalHistory.KEY));
    assertEquals(List.of(2000L),f.quality.getEventTimestamps());
    assertArrayEquals(new double[]{90,200,350},f.read(f.begin("next",2000,3000)));
  }
}
