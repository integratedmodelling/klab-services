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
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.ResolutionCycles;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationRecomputeTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  @TempDir Path directory;
  class Fixture {
    final ServiceContextScope scope = mock(ServiceContextScope.class,RETURNS_DEEP_STUBS);
    final org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl twin = mock(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.class);
    final org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j graph = mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class,RETURNS_DEEP_STUBS);
    final StorageManagerImpl manager = mock(StorageManagerImpl.class);
    final ObservationImpl quality = new ObservationImpl();
    final Data.ShardingStrategy layout = Data.ShardingStrategy.trivial(Storage.Type.DOUBLE);
    final List<Storage.Shard> committed = new ArrayList<>();
    final List<Storage.Shard> pending = new ArrayList<>();
    final List<Runnable> rollback = new ArrayList<>(), afterCommit = new ArrayList<>();
    StorageImpl storage;
    DigitalTwin.Transaction transaction;

    Fixture(boolean created) {this(created,false);}
    Fixture(boolean created,boolean timeless) {
      var concept = new ConceptImpl(); concept.setUrn("test:elevation"); concept.setName("elevation");
      concept.getType().add(SemanticType.QUALITY); concept.getType().add(SemanticType.QUANTIFIABLE);
      quality.setObservable(ObservableImpl.promote(concept,null)); quality.setId(10);
      quality.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}S2(3,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;3 1&comma;3 0&comma;0 0))}"));
      if(timeless)quality.setGeometry(Geometry.create(quality.getGeometry().dimension(Geometry.Dimension.Type.SPACE).encode()));
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
      var writes=new java.util.concurrent.atomic.AtomicReference<TemporalWriteSet>();
      when(transaction.getTemporalWrites()).thenAnswer(call->writes.get());
      doAnswer(call->{writes.set(call.getArgument(0));return null;}).when(transaction).setTemporalWrites(any());
      when(transaction.getActivity()).thenReturn(org.integratedmodelling.klab.api.provenance.Activity.of(org.integratedmodelling.klab.api.provenance.Activity.Type.DETECTION));
      doAnswer(call->{pending.add(call.getArgument(1));return null;}).when(transaction)
          .link(eq(quality),any(Storage.Shard.class),eq(GraphModel.Relationship.HAS_DATA),any(),any());
      return null;
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

  private ObservationImpl configuration() {
    var concept=new ConceptImpl();concept.setUrn("test:Configuration");concept.setName("Configuration");concept.getType().add(SemanticType.CONFIGURATION);
    var observation=new ObservationImpl();observation.setId(50);observation.setObservable(ObservableImpl.promote(concept,null));
    observation.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}"));
    observation.setEventTimestamps(new ArrayList<>(List.of(0L,5000L)));return observation;
  }
  private SchedulerImpl scheduler(Fixture f,ObservationImpl configuration) {
    when(f.graph.getScheduledObservations(f.scope)).thenReturn(List.of(configuration));
    when(f.graph.isObservationCurrent(same(configuration),same(f.scope))).thenReturn(true);
    when(f.graph.isObservationCurrent(same(f.quality),same(f.scope))).thenReturn(true);
    when(f.graph.getLinks(configuration,GraphModel.Relationship.Direction.OUTGOING,f.scope,GraphModel.Relationship.CONTEXTUALIZED_BY)).thenReturn(List.of());
    when(f.scope.of(any(Observation.class))).thenReturn(f.scope);
    var cycles=mock(ResolutionCycles.class);var admission=mock(ResolutionCycles.Admission.class);
    when(admission.scope()).thenReturn(f.scope);when(cycles.admit(f.scope,true)).thenReturn(CompletableFuture.completedFuture(admission));
    when(f.twin.getResolutionCycles()).thenReturn(cycles);
    when(f.scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"frozen-cycle","context","runtime",true,null));
    return new SchedulerImpl(f.scope,f.twin);
  }
  @Test void currentTimeRecomputationUsesActualRegisteredExecutorAndAppendsDistinctNativeRevisions() {
    var f=new Fixture(false);var config=configuration();var scheduler=scheduler(f,config);var calls=new AtomicInteger();
    scheduler.registerExecutor(config,(geometry,event,execution)->{
      assertEquals(Scheduler.Event.Type.INPUT_CHANGE,event.getType());assertNull(event.getEvent());
      assertEquals(5000,StorageScan.Slice.of(event).start());assertEquals(5000,event.getTime().getStart().getMilliseconds());assertEquals(5000,event.getTime().getEnd().getMilliseconds());
      assertSame(f.scope,execution);assertEquals("frozen-cycle",execution.getResolutionCycleBinding().cycleId());
      var scanner=execution.getCurrentTransaction().getTemporalWrites().scan(f.quality,f.layout,Storage.DoubleScanner.class,TemporalWriteSet.Access.WRITE).getFirst();
      scanner.add(10*calls.incrementAndGet());return true;
    });
    try {
      f.begin("first",5000,5000);
      assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),101,1,f.scope));
      var owner=f.transaction;when(f.scope.getCurrentTransaction()).thenReturn(mock(DigitalTwin.Transaction.class));
      assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,scheduler.recomputeConfiguration(config,config.getGeometry(),101,1,f.scope));
      when(f.scope.getCurrentTransaction()).thenReturn(owner);f.commit();
      f.begin("duplicate",5000,5000);
      assertEquals(Scheduler.ConfigurationRecomputeOutcome.ALREADY_EXECUTED,scheduler.recomputeConfiguration(config,config.getGeometry(),101,1,f.scope));assertEquals(1,calls.get());
      f.begin("second",5000,5000);
      assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),102,2,f.scope));f.commit();
      assertEquals(2,calls.get());assertEquals(5000,scheduler.currentModeledInstant().orElseThrow());
      var history=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(f.quality.getMetadata().get(TemporalHistory.KEY).toString(),TemporalHistory.class);
      assertEquals(2,history.revisions().size());assertNotEquals(history.revisions().get(0).event(),history.revisions().get(1).event());
      assertTrue(history.revisions().stream().allMatch(r->r.start()==5000&&r.end()==5000));
      var before=f.quality.getMetadata().get(TemporalHistory.KEY);f.begin("rolled-back",5000,5000);
      assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),103,3,f.scope));f.abort();
      assertEquals(before,f.quality.getMetadata().get(TemporalHistory.KEY));
      f.begin("retry",5000,5000);assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),103,3,f.scope));f.abort();
      assertEquals(4,calls.get());
    } finally {scheduler.close();f.storage.close(null);}
  }
  @Test void untimedModelRecomputationAppendsImmutableStateWithoutInventingAClock() throws Exception {
    var f=new Fixture(false,true);var config=configuration();config.setGeometry(f.quality.getGeometry());config.setEventTimestamps(new ArrayList<>());
    var scheduler=scheduler(f,config);var calls=new AtomicInteger();var initialTimestamps=List.copyOf(f.quality.getEventTimestamps());
    scheduler.registerExecutor(config,(geometry,event,execution)->{
      assertFalse(event.hasTime());assertThrows(IllegalStateException.class,event::getTime);assertThrows(IllegalStateException.class,event::getInstant);
      assertThrows(IllegalStateException.class,()->StorageScan.Slice.of(event));
      var scanner=execution.getCurrentTransaction().getTemporalWrites().scan(f.quality,f.layout,Storage.DoubleScanner.class,TemporalWriteSet.Access.WRITE).getFirst();
      scanner.add(10*calls.incrementAndGet());return true;
    });
    try {
      var initialDescriptor=f.committed.getFirst();
      var retainedWritable=(Storage.DoubleScanner)f.storage.getNativeScanner(initialDescriptor);
      var initialFile=directory.resolve(initialDescriptor.getUrn()+".dat");var original=Files.readAllBytes(initialFile);
      f.begin("untimed-one",0,0);assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),301,1,f.scope));f.commit();
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->retainedWritable.add(999));
      assertEquals(100,retainedWritable.peek());
      var oldDescriptorScanner=(Storage.DoubleScanner)f.storage.getNativeScanner(initialDescriptor);
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->oldDescriptorScanner.add(999));
      f.begin("untimed-two",0,0);assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),302,2,f.scope));f.commit();
      assertTrue(scheduler.currentModeledInstant().isEmpty());assertEquals(initialTimestamps,f.quality.getEventTimestamps());
      assertNull(f.quality.getMetadata().get(TemporalHistory.KEY));
      var encoded=f.quality.getMetadata().get(StateRevisionHistory.KEY).toString();
      var history=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(encoded,StateRevisionHistory.class);
      assertEquals(2,history.revisions().size());assertEquals(history.revisions().getFirst().id(),history.revisions().getLast().previousId());
      assertArrayEquals(original,Files.readAllBytes(initialFile));
      f.storage.close(null);f.restore();
      var reader=f.storage.scan(Scheduler.Event.initialization(),f.layout,Storage.DoubleScanner.class,true).getFirst();assertEquals(20,reader.get());assertEquals(200,reader.get());
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,()->f.storage.scan(Scheduler.Event.initialization(),f.layout,Storage.DoubleScanner.class,false));
      f.begin("untimed-rollback",0,0);assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),303,3,f.scope));f.abort();assertEquals(encoded,f.quality.getMetadata().get(StateRevisionHistory.KEY));
      f.begin("untimed-retry",0,0);assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),303,3,f.scope));f.abort();assertEquals(4,calls.get());
      scheduler.registerExecutor(config,(g,e,execution)->{e.getTime();return true;});f.begin("time-request",0,0);
      assertEquals(Scheduler.ConfigurationRecomputeOutcome.FAILED,scheduler.recomputeConfiguration(config,config.getGeometry(),304,4,f.scope));
      assertEquals(encoded,f.quality.getMetadata().get(StateRevisionHistory.KEY));
    } finally {scheduler.close();f.storage.close(null);}
  }
  @Test void anotherTransactionWithAnotherRevisionCannotCopyPendingReceiptsOrInterfereWithRollback() {
    for(boolean commitFirst:List.of(false,true)) {
      var f=new Fixture(false,true);var config=configuration();config.setGeometry(f.quality.getGeometry());config.setEventTimestamps(new ArrayList<>());
      var scheduler=scheduler(f,config);var calls=new AtomicInteger();
      scheduler.registerExecutor(config,(g,e,execution)->{
        execution.getCurrentTransaction().getTemporalWrites().scan(f.quality,f.layout,Storage.DoubleScanner.class,TemporalWriteSet.Access.WRITE).getFirst().add(calls.incrementAndGet());return true;});
      try {
        f.begin("transaction-a",0,0);
        assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),501,1,f.scope));
        assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,scheduler.recomputeConfiguration(config,config.getGeometry(),502,2,f.scope),"A different key in the same transaction cannot reuse its prepared write set");
        var commitA=List.copyOf(f.afterCommit);var abortA=List.copyOf(f.rollback);var pendingA=List.copyOf(f.pending);
        f.begin("transaction-b",0,0);
        assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,scheduler.recomputeConfiguration(config,config.getGeometry(),502,2,f.scope));
        assertEquals(1,calls.get());verify(f.transaction,never()).update(config);assertTrue(f.pending.isEmpty());
        // B commits before A resolves. Its deferred attempt has no receipt or state publication.
        f.commit();
        if(commitFirst){f.committed.addAll(pendingA);commitA.forEach(Runnable::run);}
        else abortA.reversed().forEach(Runnable::run);
        f.begin("transaction-b-retry",0,0);
        assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,scheduler.recomputeConfiguration(config,config.getGeometry(),502,2,f.scope));f.commit();
        var receipts=(Map<?,?>)config.getMetadata().get("klab.scheduler.configurationRecomputations");
        assertEquals(commitFirst?2:1,receipts.size());assertEquals(2,calls.get());
        var history=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(f.quality.getMetadata().get(StateRevisionHistory.KEY).toString(),StateRevisionHistory.class);
        assertEquals(commitFirst?2:1,history.revisions().size());assertTrue(history.revisions().getLast().id().endsWith(":2:502"));
      } finally {scheduler.close();f.storage.close(null);}
    }
  }
  @Test void untimedRecomputeRejectsTimeDependentOutputRatherThanUsingZero() {
    var f=new Fixture(false);var config=configuration();config.setGeometry(Geometry.create("S2(1,1){proj=EPSG:4326,bbox=[0 1 0 1]}"));config.setEventTimestamps(new ArrayList<>());
    var scheduler=scheduler(f,config);scheduler.registerExecutor(config,(g,e,execution)->{
      execution.getCurrentTransaction().getTemporalWrites().scan(f.quality,f.layout,Storage.DoubleScanner.class,TemporalWriteSet.Access.WRITE);return true;});
    try {f.begin("mixed-time",0,0);assertEquals(Scheduler.ConfigurationRecomputeOutcome.FAILED,scheduler.recomputeConfiguration(config,config.getGeometry(),401,1,f.scope));
      assertNull(f.quality.getMetadata().get(StateRevisionHistory.KEY));assertNull(config.getMetadata().get("klab.scheduler.configurationRecomputations"));assertTrue(scheduler.currentModeledInstant().isEmpty());}
    finally {scheduler.close();f.storage.close(null);}
  }
  @Test void missingModelFailureAndUnestablishedTimeDoNotCertifyNoModelOrInventInitialization() {
    var f=new Fixture(false);var config=configuration();var scheduler=scheduler(f,config);
    try {
      f.begin("no-model",5000,5000);assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,scheduler.recomputeConfiguration(config,config.getGeometry(),201,1,f.scope),mockingDetails(f.scope).getInvocations().toString());f.abort();
      scheduler.registerExecutor(config,(g,e,s)->false);f.begin("failed",5000,5000);
      assertEquals(Scheduler.ConfigurationRecomputeOutcome.FAILED,scheduler.recomputeConfiguration(config,config.getGeometry(),202,2,f.scope));verify(f.transaction).fail(any());
      assertNull(config.getMetadata().get("klab.scheduler.configurationRecomputations"));
    } finally {scheduler.close();f.storage.close(null);}
    var empty=new Fixture(false);var timeless=configuration();timeless.setEventTimestamps(new ArrayList<>());var fresh=scheduler(empty,timeless);
    try {empty.begin("timeless-no-model",0,0);assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,fresh.recomputeConfiguration(timeless,timeless.getGeometry(),204,4,empty.scope));empty.abort();fresh.registerExecutor(timeless,(g,e,s)->true);empty.begin("unknown-time",0,0);assertTrue(fresh.currentModeledInstant().isEmpty());assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,fresh.recomputeConfiguration(timeless,timeless.getGeometry(),203,3,empty.scope));}
    finally {fresh.close();empty.storage.close(null);}
  }
  @Test void realNewScalarStillRequiresInitUnderAnAlreadyActiveInputChangeWriteSet() {
    var f=new Fixture(true);f.quality.getMetadata().remove(TemporalHistory.EPHEMERAL);f.quality.setId(-77);
    f.begin("new",5000,5000);
    var event=new Scheduler.Event(){public boolean hasTime(){return true;}public Time getTime(){return TimePeriod.create(5000,5000);}public Type getType(){return Type.INPUT_CHANGE;}public String toKey(){return "new";}public Observation getEvent(){return null;}};
    var writes=new LocalTemporalWriteSet(event,f.scope);
    assertThrows(IllegalStateException.class,()->writes.writeLayout(f.quality),"No exception to the baseline guard for a transient ID");
    var initial=f.storage.scan(Scheduler.Event.initialization(),f.layout,Storage.DoubleScanner.class,false).getFirst();
    initial.add(7);initial.add(7);initial.add(7);f.storage.finalizeRun(initial);
    assertEquals(1,writes.writeLayout(f.quality).size());assertArrayEquals(new double[]{7,7,7},f.read(writes));
    assertFalse(f.quality.getMetadata().containsKey(TemporalHistory.EPHEMERAL));
  }

}
