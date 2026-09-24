package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.mediation.classification.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class KeyedStorageTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static ConceptImpl concept(String urn, boolean abstractType) {
    var c=new ConceptImpl();c.setUrn(urn);c.setName(urn.substring(urn.indexOf(':')+1));c.setAbstract(abstractType);return c;
  }
  static final WorldviewCommitment WORLD = new WorldviewCommitment(1,"test-world",Map.of("test","a".repeat(64)));
  static class TestManager extends StorageManagerImpl {
    TestManager(ServiceContextScope scope,Path workspace,Path persistent) { super(scope,workspace.toFile(),persistent.toFile()); }
    @Override public synchronized org.ojalgo.array.BufferArray getIntBuffer(long size) { return (org.ojalgo.array.BufferArray)org.ojalgo.array.BufferArray.Z032.make(size); }
  }
  static class Fixture implements AutoCloseable {
    final ServiceContextScope scope=mock(ServiceContextScope.class,RETURNS_DEEP_STUBS);
    final Reasoner reasoner=mock(Reasoner.class);
    final ReasonerCapabilitiesImpl capabilities=new ReasonerCapabilitiesImpl();
    final ConceptImpl root=concept("test:Land",true), a=concept("test:A",false), b=concept("test:B",false);
    final ObservationImpl observation=new ObservationImpl();
    final Data.ShardingStrategy layout=new Data.ShardingStrategy(Data.FillCurve.D2_XY,2,0,0,Storage.Type.KEYED);
    final StorageManagerImpl manager;
    final StorageImpl storage;
    final Path directory;
    Fixture(Path directory) {
      this.directory=directory;
      var observable=concept("type of test:Land",false);observable.getType().addAll(Set.of(SemanticType.CLASS,SemanticType.QUALITY));
      observation.setObservable(ObservableImpl.promote(observable,null));observation.setId(-1);observation.setUrn("test:landcover");
      observation.setGeometry(Geometry.create(ConformantScanTest.TIME+"S2(4,2){proj=EPSG:3857,shape=EPSG:3857 POLYGON ((0 0&comma;0 2&comma;4 2&comma;4 0&comma;0 0))}"));
      var config=DigitalTwin.Configuration.builder().id("context").persistence(Persistence.EXPLICIT_ACTION).build();
      when(scope.getConfiguration()).thenReturn(config);when(scope.getId()).thenReturn("context");
      doReturn(reasoner).when(scope).getService(Reasoner.class);
      doReturn(mock(org.integratedmodelling.klab.api.services.RuntimeService.class,RETURNS_DEEP_STUBS)).when(scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
      capabilities.setConsistent(true);capabilities.setWorldviewId(WORLD.worldviewId());capabilities.setWorldviewCommitment(WORLD);
      when(reasoner.capabilities(scope)).thenReturn(capabilities);when(reasoner.describedType(observation.getObservable())).thenReturn(root);
      for(var c:List.of(a,b,root)) { when(reasoner.resolveConcept(c.getUrn())).thenReturn(c);when(reasoner.is(c,root)).thenReturn(true); }
      manager=new TestManager(scope,directory.resolve("scratch"),directory.resolve("data"));
      storage=new StorageImpl(observation,layout,scope,manager);
    }
    @SuppressWarnings("unchecked") void fill(boolean missing) {
      for(var scanner:storage.scan(Scheduler.Event.initialization(),layout,Storage.KeyScanner.class,false)) {
        var key=(Storage.KeyScanner<Concept>)scanner;
        var bounds=ConformantScanTest.bounds(scanner.shard().getGeometry().encode());
        long index=0;
        while(key.hasNext()) { double x=bounds[0]+index/2;key.add(missing && x==0 && index%2==0 ? null : x<2 ? b : a);index++; }
        storage.finalizeRun(scanner);
      }
      storage.flush();
    }
    StorageScan.Request<Storage.KeyScanner> request(String geometry,StorageScan.Sampling sampling) {
      return new StorageScan.Request<>(StorageScan.Slice.of(Scheduler.Event.initialization()),
          new StorageScan.Layout(Data.FillCurve.D2_YX,1,0,0,Storage.Type.KEYED),geometry,List.of(),null,Storage.KeyScanner.class,
          StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,StorageScan.Coverage.MISSING_OUTSIDE,sampling,StorageScan.Budget.defaults());
    }
    public void close() {storage.close(null);manager.close();}
  }
  static String grid(int x,int y,double w,double e,double s,double n) {
    return ConformantScanTest.grid(x,y,w,e,s,n).replace("4326","3857");
  }

  @Test void validationCachesCanonicalTypesAndRejectsAbstractOrUnrelatedValues() {
    var reasoner=mock(Reasoner.class);var root=concept("test:Root",true);var a=concept("test:A",false);
    when(reasoner.resolveConcept(a.getUrn())).thenReturn(a);when(reasoner.is(a,root)).thenReturn(true);
    var binds=new java.util.concurrent.atomic.AtomicInteger();var key=new ConceptDataKey(reasoner,root,WORLD,binds::incrementAndGet);
    for(int i=0;i<1000000;i++)assertEquals(1,key.code(a));
    verify(reasoner,times(1)).resolveConcept(a.getUrn());verify(reasoner,times(1)).is(a,root);assertEquals(1,binds.get());
    for(var bad:List.of(root,concept("test:Abstract",true),concept("test:Unrelated",false))) {
      when(reasoner.resolveConcept(bad.getUrn())).thenReturn(bad);
      assertThrows(IllegalArgumentException.class,()->key.code(bad));assertThrows(IllegalArgumentException.class,()->key.code(bad));
      verify(reasoner,times(1)).resolveConcept(bad.getUrn());
    }
    assertEquals(0,key.code(null));assertNull(key.lookup(0));assertThrows(IllegalStateException.class,()->key.lookup(100));
    assertThrows(UnsupportedOperationException.class,()->key.readOnly().include(a));
  }

  @Test void nativeConformantAndMajorityReadsBindDictionaryEvidence(@TempDir Path directory) {
    try(var f=new Fixture(directory)) {
      f.fill(false);
      assertEquals(WORLD,f.scope.getConfiguration().getWorldviewCommitment());
      assertEquals(WORLD,Utils.Json.parseObject(Utils.Json.asString(f.scope.getConfiguration()),org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl.class).getWorldviewCommitment());
      verify(f.scope.getDigitalTwin().getKnowledgeGraph(),times(1)).bindWorldview(WORLD);
      assertEquals(3,f.storage.getKey().size());
      var key=f.storage.getKey();
      assertEquals(key.size(),key.getLabels().size());assertEquals(key.size(),key.getConcepts().size());
      for(int i=0;i<key.size();i++)assertEquals(i,key.reverseLookup(key.lookup(i)));
      assertSame(f.a,key.getConcepts().get(key.reverseLookup(f.a)));
      var summary=f.storage.getCategoryHistograms().values().iterator().next();
      assertEquals(8,summary.histogram().counts().values().stream().mapToLong(Long::longValue).sum());
      assertEquals(summary.dictionary().fingerprint(),summary.histogram().dictionary());
      for(var sampling:List.of(StorageScan.Sampling.NEAREST,StorageScan.Sampling.MAJORITY)) {
        var plan=f.storage.plan(f.request(grid(1,1,0,4,0,2),sampling));
        assertEquals(5,plan.description().version());assertEquals(WORLD,plan.description().dictionary().worldview());
        var json=Utils.Json.parseObject(Utils.Json.asString(plan.description()),StorageScan.Description.class);
        assertEquals(plan.description().fingerprint(),json.fingerprint());
        try(var session=f.storage.open(plan)) {
          var scan=session.scanners().getFirst();assertSame(f.a,scan.peek());assertEquals("test:A",StorageScan.textValue(scan));
          assertThrows(IllegalStateException.class,()->scan.add(f.b));
        }
      }
      try(var session=f.storage.open(f.storage.plan(f.request(grid(4,2,0,4,0,2),StorageScan.Sampling.EXACT)))) {
        var scan=session.scanners().getFirst();for(int y=0;y<2;y++)for(int x=0;x<4;x++)assertSame(x<2?f.b:f.a,scan.get());
      }
      for(var shard:f.storage.allShards()) { assertNotNull(shard.getKeyDictionaryHash());assertEquals(4,shard.getCategoryHistogram().counts().values().stream().mapToLong(Long::longValue).sum()); }
    }
  }

  @Test void majorityRespectsPartialCoverageMissingnessAndDictionaryIndependentTies(@TempDir Path directory) {
    try(var f=new Fixture(directory)) {
      f.fill(true);
      for(var geometry:List.of(grid(1,1,0,4,0,2),grid(1,1,-5,-1,0,2)))
        try(var session=f.storage.open(f.storage.plan(f.request(geometry,StorageScan.Sampling.MAJORITY)))) {
          var scan=session.scanners().getFirst();assertFalse(scan.isValid());assertNull(scan.get());
        }
      try(var session=f.storage.open(f.storage.plan(f.request(grid(1,1,2,8,0,2),StorageScan.Sampling.MAJORITY)))) { assertSame(f.a,session.scanners().getFirst().get()); }
      assertThrows(UnsupportedOperationException.class,()->f.storage.plan(f.request(grid(1,1,0,4,0,2),StorageScan.Sampling.INTERPOLATE)));
      assertThrows(UnsupportedOperationException.class,()->f.storage.plan(f.request(grid(1,1,0,4,0,2),StorageScan.Sampling.CONSERVATIVE)));
    }
  }

  @Test void concurrentProducersShareOneValidatedCode() throws Exception {
    var reasoner=mock(Reasoner.class);var root=concept("test:Root",true);var a=concept("test:A",false);
    when(reasoner.resolveConcept(a.getUrn())).thenReturn(a);when(reasoner.is(a,root)).thenReturn(true);
    var key=new ConceptDataKey(reasoner,root,WORLD,()->{});
    try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var jobs=new ArrayList<java.util.concurrent.Callable<Integer>>();
      for(int i=0;i<32;i++)jobs.add(()->{int code=0;for(int j=0;j<10000;j++)code=key.code(a);return code;});
      for(var result:pool.invokeAll(jobs))assertEquals(1,result.get());
    }
    assertEquals(2,key.size());verify(reasoner,times(1)).resolveConcept(a.getUrn());verify(reasoner,times(1)).is(a,root);
  }

  @Test void keyedTemporalSnapshotsAndRollbackKeepCommittedCodes(@TempDir Path directory) {
    try(var f=new Fixture(directory)) {
      f.fill(false);
      var manager=mock(StorageManagerImpl.class);when(manager.createStorage(f.observation)).thenReturn(f.storage);
      when(f.scope.getDigitalTwin().getStorageManager()).thenReturn(manager);
      var transaction=mock(DigitalTwin.Transaction.class);when(f.scope.getCurrentTransaction()).thenReturn(transaction);
      var rollback=new ArrayList<Runnable>();
      doAnswer(call->{rollback.add(call.getArgument(0));return null;}).when(transaction).afterRollback(any());
      var event=TemporalStorageTest.event("change",1000,2000);
      var writes=new LocalTemporalWriteSet(event,f.scope);
      var writer=writes.scan(f.observation,f.layout,Storage.KeyScanner.class,TemporalWriteSet.Access.WRITE).getFirst();
      writer.add(f.a);
      var request=new StorageScan.Request<>(StorageScan.Slice.of(event),new StorageScan.Layout(Data.FillCurve.D2_XY,1,0,0,Storage.Type.KEYED),
          "S2(1,1){proj=EPSG:3857,bbox=[0 1 0 1]}",List.of(),null,Storage.KeyScanner.class,StorageScan.Access.READ_ONLY,
          StorageScan.Precision.LOSSLESS,StorageScan.Coverage.MISSING_OUTSIDE,StorageScan.Sampling.MAJORITY,StorageScan.Budget.defaults());
      try(var prior=writes.read(f.observation,request,TemporalWriteSet.Access.PRIOR);var current=writes.read(f.observation,request,TemporalWriteSet.Access.CURRENT)) {
        assertSame(f.b,prior.scanners().getFirst().get());assertSame(f.a,current.scanners().getFirst().get());
      }
      writes.prepare();rollback.reversed().forEach(Runnable::run);
      try(var session=f.storage.open(f.storage.plan(f.request(grid(4,2,0,4,0,2),StorageScan.Sampling.EXACT)))) {assertSame(f.b,session.scanners().getFirst().get());}
    }
  }

  @Test void unknownCodesAndMissingDictionariesNeverBecomeMissingValues(@TempDir Path directory) {
    try(var f=new Fixture(directory)) {
      f.fill(false);
      var buffer=(org.ojalgo.array.BufferArray)org.ojalgo.array.BufferArray.Z032.make(3);
      try(var reader=new LocalIndexedReader(buffer,Storage.Type.KEYED,3,f.storage.getKey())) {
        buffer.set(0,0);buffer.set(1,99);buffer.set(2,-1);
        assertFalse(reader.isValid(0));assertThrows(IllegalStateException.class,()->reader.isValid(1));assertThrows(IllegalStateException.class,()->reader.isValid(2));
      } finally {buffer.close();}
      assertThrows(IllegalStateException.class,()->f.manager.readDictionary(null));
      assertThrows(IllegalStateException.class,()->f.manager.readDictionary("f".repeat(64)));
      assertThrows(UnsupportedOperationException.class,()->f.storage.plan(StorageScan.Request.nativeRead(Scheduler.Event.initialization(),f.layout,Storage.IntScanner.class)));
      f.capabilities.setWorldviewCommitment(new WorldviewCommitment(1,WORLD.worldviewId(),Map.of("test","b".repeat(64))));
      assertThrows(IllegalStateException.class,()->f.storage.plan(f.request(grid(4,2,0,4,0,2),StorageScan.Sampling.EXACT)));
      assertThrows(IllegalStateException.class,()->new TestManager(f.scope,directory.resolve("reopen"),directory.resolve("data")));
    }
  }

  @Test void geographicMajorityUsesAreaRatherThanCellCounts(@TempDir Path directory) {
    try(var f=new Fixture(directory)) {
      f.fill(false);
      String source=ConformantScanTest.grid(2,2,0,2,0,80);
      var layout=new StorageScan.Layout(Data.FillCurve.D2_XY,1,0,0,Storage.Type.KEYED);
      var descriptors=List.of(new StorageScan.SourceShard("source",source,4,0,0,layout));
      var request=f.request(ConformantScanTest.grid(1,1,0,2,0,80),StorageScan.Sampling.MAJORITY);
      var mapping=SpatialScan.plan(descriptors,request,source,true);
      var data=(org.ojalgo.array.BufferArray)org.ojalgo.array.BufferArray.Z032.make(4);
      try(var reader=new LocalIndexedReader(data,Storage.Type.KEYED,4,f.storage.getKey())) {
        data.set(0,1);data.set(1,2);data.set(2,1);data.set(3,2);
        try(var mapped=mapping.reader(0,List.of(reader),4)) {
          assertTrue(mapped.isValid(0));assertSame(f.b,mapped.key().lookup(mapped.readInt(0)));
        }
      } finally {data.close();}
    }
  }

  @Test void dictionariesTranslateByDefinitionAndRejectWorldviewChanges() {
    var a=new KeyedData.Entry("test:A","same label");var b=new KeyedData.Entry("test:B","same label");
    var first=new KeyedData.Dictionary(1,WORLD,"test:Land",List.of(a,b));
    var second=new KeyedData.Dictionary(1,WORLD,"test:Land",List.of(b,a));
    assertArrayEquals(new int[]{0,2,1},first.translationTo(second));
    var different=new WorldviewCommitment(1,"test-world",Map.of("test","b".repeat(64)));
    assertNotEquals(WORLD.fingerprint(),different.fingerprint());
    assertThrows(IllegalArgumentException.class,()->first.translationTo(new KeyedData.Dictionary(1,different,"test:Land",List.of(a,b))));
  }

  @Test void persistedDictionaryAndPayloadReconstructAndRejectCorruption(@TempDir Path directory) throws Exception {
    try(var f=new Fixture(directory)) {
      f.fill(false);var shards=f.storage.allShards();
      var transported=shards.stream().map(shard->Utils.Json.parseObject(Utils.Json.asString(shard),ShardImpl.class)).toList();
      f.observation.setId(1);
      when(f.scope.getDigitalTwin().getKnowledgeGraph().query(Storage.Shard.class,f.scope).source(f.observation).along(GraphModel.Relationship.HAS_DATA).run(f.scope))
          .thenReturn(new ArrayList<Storage.Shard>(transported));
      try(var reopenedManager=new ManagerClose(new TestManager(f.scope,directory.resolve("restart"),directory.resolve("data")))) {
        var restored=new StorageImpl(f.observation,f.layout,f.scope,reopenedManager.manager);
        try {
          assertEquals(f.storage.getCategoryHistograms(),restored.getCategoryHistograms());
          try(var session=restored.open(restored.plan(f.request(grid(4,2,0,4,0,2),StorageScan.Sampling.EXACT)))) {
            assertEquals("test:B",((Concept)session.scanners().getFirst().get()).getUrn());
          }
        } finally {restored.close(null);}
        var dictionary=directory.resolve("data/context/key-"+transported.getFirst().getKeyDictionaryHash()+".json");
        Files.writeString(dictionary,Files.readString(dictionary).replace("test:B","test:Corrupt"));
        try(var corruptManager=new ManagerClose(new TestManager(f.scope,directory.resolve("corrupt"),directory.resolve("data")))) {
          assertThrows(IllegalStateException.class,()->new StorageImpl(f.observation,f.layout,f.scope,corruptManager.manager));
        }
      }
      f.capabilities.setWorldviewCommitment(new WorldviewCommitment(1,"other",Map.of("test","a".repeat(64))));
      assertThrows(IllegalStateException.class,()->new TestManager(f.scope,directory.resolve("bad"),directory.resolve("data")));
    }
  }
  record ManagerClose(StorageManagerImpl manager) implements AutoCloseable { public void close(){manager.close();} }
}
