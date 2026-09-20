package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy;
import org.integratedmodelling.klab.services.runtime.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.*;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.ojalgo.array.BufferArray;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class TemporalProcessIntegrationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  @TempDir Path directory;
  static long date(String date) { return Instant.parse(date+"T00:00:00Z").toEpochMilli(); }
  static final long START=date("2014-01-01"), END=date("2015-01-01");
  static void field(Object target,String name,Object value) throws Exception {
    var field=DigitalTwinImpl.class.getDeclaredField(name); field.setAccessible(true); field.set(target,value);
  }
  ObservationImpl observation(String name,SemanticType type,long id) {
    var concept=new ConceptImpl(); concept.setUrn("test:"+name); concept.setName(name); concept.getType().add(type);
    if(type==SemanticType.QUALITY) concept.getType().add(SemanticType.QUANTIFIABLE);
    var observation=new ObservationImpl(); observation.setId(id); observation.setUrn("context."+name);
    observation.setObservable(ObservableImpl.promote(concept,null));
    observation.setGeometry(Geometry.create("T1(365){ttype=GRID,tstart="+START+",tend="+END+",tscope=1,tunit=DAY,tgrid=86400000}S2(3,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;3 1&comma;3 0&comma;0 0))}"));
    return observation;
  }
  class Fixture implements AutoCloseable {
    final DigitalTwinImpl twin=mock(DigitalTwinImpl.class);
    final KnowledgeGraphNeo4j graph=mock(KnowledgeGraphNeo4j.class,RETURNS_DEEP_STUBS);
    final KnowledgeGraph.Transaction graphTx=mock(KnowledgeGraph.Transaction.class);
    final StorageManagerImpl manager=mock(StorageManagerImpl.class);
    final RuntimeService runtime=mock(RuntimeService.class);
    final ServiceContextScope root=mock(ServiceContextScope.class,RETURNS_DEEP_STUBS);
    final ObservationImpl process=observation("erosion",SemanticType.PROCESS,100);
    final ObservationImpl quality=observation("relief",SemanticType.QUALITY,101);
    final ObservationImpl bearer=observation("region",SemanticType.SUBJECT,20);
    final Data.ShardingStrategy layout=Data.ShardingStrategy.trivial(Storage.Type.DOUBLE);
    final Map<Long,Observation> observations=new HashMap<>();
    final Map<Long,StorageImpl> stores=new HashMap<>();
    final Map<Long,List<Storage.Shard>> data=new HashMap<>();
    final List<Runnable> staged=new ArrayList<>();
    final List<SchedulerJournal> journals=new ArrayList<>();
    final List<TemporalStateDelta> deltas=new ArrayList<>();
    final List<TransitionCommit> envelopes=new ArrayList<>();
    final List<KnowledgeGraph.Link> links=new ArrayList<>();
    final AtomicLong keys=new AtomicLong(1000);
    SchedulerImpl scheduler;
    boolean failCommit;
    Throwable failure;
    Observation observer;
    Fixture(String expression) throws Exception {
      observations.put(100L,process); observations.put(101L,quality); observations.put(20L,bearer);
      quality.setParentId(20); quality.setSubstantialQuality(true);
      var cd=new ObservationImpl.ContextualizationDataImpl(); cd.setNativeShardingStrategy(layout); quality.setContextualizationData(cd);
      field(twin,"knowledgeGraph",graph); field(twin,"commitCache",CacheBuilder.newBuilder().build());
      when(twin.getKnowledgeGraph()).thenReturn(graph); when(twin.getStorageManager()).thenReturn(manager);
      var user=mock(UserIdentity.class); when(user.getUsername()).thenReturn("tester");
      configureScope(root,user);
      when(root.executingFresh(any(Activity.class),any(Observation.class))).thenAnswer(call -> {
        staged.clear();
        var scope=mock(ServiceContextScope.class,RETURNS_DEEP_STUBS); configureScope(scope,user);
        var tx=twin.new TransactionImpl(call.getArgument(0),scope,RuntimeAsset.PROVENANCE_ASSET,call.getArgument(1,Observation.class));
        when(scope.getCurrentTransaction()).thenReturn(tx);
        when(scope.commit()).thenAnswer(ignored -> tx.commit());
        doAnswer(callFailure -> {failure=callFailure.getArgument(0,Throwable.class);tx.fail(failure);return null;}).when(scope).fail(any(Throwable.class));
        return scope;
      });
      when(graph.nextKey()).thenAnswer(call -> keys.incrementAndGet());
      when(graph.createTransaction(any())).thenReturn(graphTx);
      when(graph.getLinks(any(),any(),any(),eq(GraphModel.Relationship.AFFECTS))).thenAnswer(call -> {
        var asset=call.getArgument(0,RuntimeAsset.class);
        var direction=call.getArgument(1,GraphModel.Relationship.Direction.class);
        return links.stream().filter(link -> (direction==GraphModel.Relationship.Direction.INCOMING ? link.target() : link.source()).getId()==asset.getId()).toList();
      });
      when(graph.getScheduledObservations(root)).thenReturn(List.of(process));
      when(manager.isRecordHistogram()).thenReturn(true);
      when(manager.getDoubleBuffer(anyLong())).thenAnswer(call -> (BufferArray)BufferArray.R064.make(call.getArgument(0,Long.class)));
      when(manager.getStorageFile(any())).thenAnswer(call -> directory.resolve(call.getArgument(0,Storage.Shard.class).getUrn()+".dat").toFile());
      doCallRealMethod().when(manager).persistTemporalShard(any(),any());
      when(manager.loadBufferArray(any(),any(),any())).thenCallRealMethod();
      when(manager.createStorage(any())).thenAnswer(call -> store(call.getArgument(0)));
      when(manager.getStorage(any())).thenAnswer(call -> store(call.getArgument(0)));
      when(manager.finalizeStorage(anyLong(),anyLong())).thenAnswer(call -> {
        stores.put(call.getArgument(1),stores.remove(call.getArgument(0)));return true;
      });
      when(runtime.getComputationBuilder(any(),any(),any(),anyMap())).thenAnswer(call ->
          ScalarComputationGroovy.builder(call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3)));
      when(runtime.getDefaultShardingStrategy(any(),any())).thenReturn(layout);
      doAnswer(call -> {
        if(call.getArgument(0) instanceof Activity activity && activity.getMetadata().containsKey(SchedulerJournal.METADATA_KEY)) {
          var serialized=new ArrayList<>((List<String>)activity.getMetadata().get(SchedulerJournal.METADATA_KEY));
          var envelope=activity.getMetadata().get(TransitionCommit.METADATA_KEY);
          if(envelope!=null) {
            var encoded=envelope.toString();
            staged.add(() -> envelopes.add(Utils.Json.parseObject(encoded,TransitionCommit.class)));
          }
          staged.add(() -> serialized.forEach(s -> journals.add(Utils.Json.parseObject(s,SchedulerJournal.class))));
          var encodedDeltas=activity.getMetadata().get(TemporalStateDelta.METADATA_KEY);
          if(encodedDeltas instanceof List<?> values) {
            var snapshot=new ArrayList<>(values);
            staged.add(() -> snapshot.forEach(s -> deltas.add(Utils.Json.parseObject(s.toString(),TemporalStateDelta.class))));
          }
        }
        return null;
      }).when(graphTx).update(any());
      doAnswer(call -> { if(failCommit) throw new IllegalStateException("injected graph failure"); staged.forEach(Runnable::run); return null; }).when(graphTx).close();
      var nativeStorage=store(quality);
      var initial=nativeStorage.scan(Scheduler.Event.initialization(),layout,Storage.DoubleScanner.class,false).getFirst();
      initial.add(100);initial.add(200);initial.add(350); nativeStorage.finalizeRun(initial);
      manager.persistTemporalShard(initial.shard(),((StorageImpl.BaseScanner)initial).data);
      data.computeIfAbsent(101L,ignored->new ArrayList<>()).add(initial.shard());
      // Capture descriptors as graph store operations, and associate them with their owner via HAS_DATA.
      var descriptors=new HashMap<Long,Storage.Shard>();
      doAnswer(call -> {
        Object asset=call.getArgument(0);
        if(asset instanceof org.integratedmodelling.klab.common.data.impl.ShardImpl shard) { shard.setId(keys.incrementAndGet()); descriptors.put(shard.getId(),shard); }
        else if(asset instanceof ActivityImpl activity) activity.setId(keys.incrementAndGet());
        else if(asset instanceof ObservationImpl observation) { observation.setId(keys.incrementAndGet()); staged.add(() -> observations.put(observation.getId(),observation)); }
        return null;
      }).when(graphTx).store(any());
      doAnswer(call -> {
        long source=call.getArgument(0,RuntimeAsset.class).getId(),target=call.getArgument(1,RuntimeAsset.class).getId();
        if(call.getArgument(2)==GraphModel.Relationship.HAS_DATA)
          staged.add(() -> data.computeIfAbsent(source,ignored->new ArrayList<>()).add(descriptors.get(target)));
        return null;
      }).when(graphTx).link(any(),any(),any(),any(Object[].class));
      var plan=new ActuatorImpl(); plan.setName("erosion");plan.setObservation(process);plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.PROCESS);
      var child=new ActuatorImpl();child.setName("elevation");child.setObservation(quality);child.setActuatorType(Actuator.Type.REFERENCE);plan.getChildren().add(child);
      var schedule=new OccurrenceSchedule(1,"","",1,Time.Resolution.Type.MONTH,true,OccurrenceSchedule.Source.MODEL);
      plan.getOccurrenceSchedules().put(0,schedule);
      plan.getData().put(ProcessPlan.DATA_KEY,Utils.Json.asString(new ProcessPlan(2,20,"test:erosion",List.of(
          new ProcessPlan.Binding("elevation",quality.getObservable(),ProcessPlan.Effect.AFFECTED,true,true,List.of("AFFECTS"))),List.of())));
      plan.getComputation().add(new ServiceCallImpl("klab.core.expression.resolver","_targetId","elevation","expression",ExpressionCode.of(expression,"groovy")));
      process.getMetadata().put(OccurrenceRegistration.METADATA_KEY,Utils.Json.asString(new OccurrenceRegistration(1,"erosion","v1",Actuator.ExecutionRole.PROCESS,20,
          List.of(new OccurrenceRegistration.Schedule(0,schedule,schedule.bind(GeometryRepository.INSTANCE.scale(process.getGeometry()).getTime()))),Utils.Json.asString(CompiledDataflow.portableOccurrencePlan(plan)))));
      scheduler=new SchedulerImpl(root,twin);
    }
    void link(Observation source,Observation target,String role) {
      var link=mock(KnowledgeGraph.Link.class);when(link.source()).thenReturn(source);when(link.target()).thenReturn(target);
      when(link.properties()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create(ProcessPlan.EDGE_ROLE,role));links.add(link);
    }
    void createPrecipitation() {
      var registration=Utils.Json.parseObject(process.getMetadata().get(OccurrenceRegistration.METADATA_KEY).toString(),OccurrenceRegistration.class);
      var plan=Utils.Json.parseObject(registration.plan(),Actuator.class);
      var precipitation=observation("precipitation",SemanticType.QUALITY,-1).getObservable();
      plan.getData().put(ProcessPlan.DATA_KEY,Utils.Json.asString(new ProcessPlan(2,20,"test:rain",List.of(
          new ProcessPlan.Binding("elevation",quality.getObservable(),ProcessPlan.Effect.INPUT,true,false),
          new ProcessPlan.Binding("precipitation",precipitation,ProcessPlan.Effect.CREATED,false,true,List.of("CREATES"))),List.of())));
      plan.getComputation().clear();plan.getComputation().add(new ServiceCallImpl("klab.core.expression.resolver",
          "_targetId","precipitation","expression",ExpressionCode.of("0","groovy")));
      process.getMetadata().put(OccurrenceRegistration.METADATA_KEY,Utils.Json.asString(new OccurrenceRegistration(1,
          registration.id(),registration.planRevision(),registration.role(),20,registration.schedules(),Utils.Json.asString(plan))));
      scheduler.close();scheduler=new SchedulerImpl(root,twin);
    }
    Observation secondAssignment(boolean reverse) {
      var second=downstream("previous",102,quality,"elevation","elevation",0);
      links.clear();link(process,quality,ProcessPlan.INFLUENCE);link(process,second,ProcessPlan.INFLUENCE);
      var registration=Utils.Json.parseObject(process.getMetadata().get(OccurrenceRegistration.METADATA_KEY).toString(),OccurrenceRegistration.class);
      var plan=Utils.Json.parseObject(registration.plan(),Actuator.class);
      var child=new ActuatorImpl();child.setName("previous");child.setObservation(second);child.setActuatorType(Actuator.Type.REFERENCE);plan.getChildren().add(child);
      var old=Utils.Json.parseObject(plan.getData().get(ProcessPlan.DATA_KEY).toString(),ProcessPlan.class);
      var bindings=new ArrayList<>(old.bindings());bindings.add(new ProcessPlan.Binding("previous",second.getObservable(),ProcessPlan.Effect.AFFECTED,true,true,List.of("AFFECTS")));
      plan.getData().put(ProcessPlan.DATA_KEY,Utils.Json.asString(new ProcessPlan(2,20,old.model(),bindings,List.of())));
      var call=new ServiceCallImpl("klab.core.expression.resolver","_targetId","previous","expression",ExpressionCode.of("elevation","groovy"));
      if(reverse) plan.getComputation().addFirst(call);else plan.getComputation().add(call);
      plan.getOccurrenceSchedules().put(1,plan.getOccurrenceSchedules().get(0));
      var schedules=new ArrayList<>(registration.schedules());
      schedules.add(new OccurrenceRegistration.Schedule(1,schedules.getFirst().declaration(),schedules.getFirst().bound()));
      process.getMetadata().put(OccurrenceRegistration.METADATA_KEY,Utils.Json.asString(new OccurrenceRegistration(1,
          registration.id(),registration.planRevision(),registration.role(),20,schedules,Utils.Json.asString(plan))));
      scheduler.close();scheduler=new SchedulerImpl(root,twin);
      return second;
    }
    ObservationImpl downstream(String name,long id,Observation input,String inputName,String expression,double value) {
      var output=observation(name,SemanticType.QUALITY,id);output.setParentId(20);
      output.setObservable(((ObservableImpl)output.getObservable()).as(org.integratedmodelling.klab.api.knowledge.Contextualization.QUANTIFICATION));
      var cd=new ObservationImpl.ContextualizationDataImpl();cd.setNativeShardingStrategy(layout);output.setContextualizationData(cd);observations.put(id,output);
      var storage=store(output);var scanner=storage.scan(Scheduler.Event.initialization(),layout,Storage.DoubleScanner.class,false).getFirst();
      scanner.add(value);scanner.add(value);scanner.add(value);storage.finalizeRun(scanner);
      manager.persistTemporalShard(scanner.shard(),((StorageImpl.BaseScanner)scanner).data);data.put(id,new ArrayList<>(List.of(scanner.shard())));
      var plan=new ActuatorImpl();plan.setName(name);plan.setObservation(output);plan.setActuatorType(Actuator.Type.RESOLVE);
      var child=new ActuatorImpl();child.setName(inputName);child.setObservation(input);child.setActuatorType(Actuator.Type.REFERENCE);plan.getChildren().add(child);
      plan.getComputation().add(new ServiceCallImpl("klab.core.expression.resolver","expression",ExpressionCode.of(expression,"groovy")));
      output.getMetadata().put(Scheduler.PLAN_METADATA_KEY,Utils.Json.asString(CompiledDataflow.portableOccurrencePlan(plan)));
      link(input,output,ProcessPlan.PREREQUISITE);return output;
    }
    void configureScope(ServiceContextScope scope,UserIdentity user) {
      when(scope.getObserver()).thenAnswer(call -> observer);
      when(scope.getId()).thenReturn("context");when(scope.getUser()).thenReturn(user);when(scope.getContextObservation()).thenReturn(bearer);
      when(scope.within(bearer)).thenReturn(scope);
      when(scope.getDigitalTwin()).thenReturn(twin);when(scope.getConfiguration().getPersistence()).thenReturn(Persistence.EXPLICIT_ACTION);
      when(scope.getObservation(anyLong())).thenAnswer(call -> observations.get(call.getArgument(0)));
      doReturn(runtime).when(scope).getService(RuntimeService.class);
      when(scope.executing(any(Activity.class),any(Observation.class))).thenAnswer(call -> {
        var child=mock(ServiceContextScope.class,RETURNS_DEEP_STUBS);configureScope(child,user);
        var tx=scope.getCurrentTransaction().getChild(call.getArgument(0),child,call.getArgument(1,Observation.class));
        when(child.getCurrentTransaction()).thenReturn(tx);when(child.commit()).thenAnswer(ignored->tx.commit());
        doAnswer(callFailure->{failure=callFailure.getArgument(0,Throwable.class);tx.fail(failure);return null;}).when(child).fail(any(Throwable.class));
        return child;
      });
    }
    StorageImpl store(Observation observation) {
      return stores.computeIfAbsent(observation.getId(),id -> {
        when(graph.query(Storage.Shard.class,root).source(observation).along(GraphModel.Relationship.HAS_DATA).run(root))
            .thenAnswer(call -> new ArrayList<>(data.getOrDefault(observation.getId(),List.of())));
        return new StorageImpl(observation,layout,root,manager);
      });
    }
    double[] values(long at) {
      return values(quality,at);
    }
    double[] values(Observation observation,long at) {
      var s=store(observation); var shards=s.temporalBaseline(new TransitionEvent("read",at,at+1,null),false);
      return new double[]{(Double)s.nativeValue(shards.getFirst(),0),(Double)s.nativeValue(shards.getFirst(),1),(Double)s.nativeValue(shards.getFirst(),2)};
    }
    void restart() { scheduler.close();stores.values().forEach(s->s.close(null));stores.clear();scheduler=new SchedulerImpl(root,twin); }
    long files() throws Exception { try(var paths=Files.list(directory)){return paths.count();} }
    public void close(){scheduler.close();stores.values().forEach(s->s.close(null));}
  }

  @Test void registrationCommitAutomaticallyRunsMonthlyScalarTransitions() throws Exception {
    try (var f = new Fixture("elevation - 10")) {
      var registration = Utils.Json.parseObject(f.process.getMetadata()
          .remove(OccurrenceRegistration.METADATA_KEY).toString(), OccurrenceRegistration.class);
      var plan = Utils.Json.parseObject(registration.plan(), Actuator.class);
      f.scheduler.close();
      when(f.graph.getScheduledObservations(f.root)).thenReturn(List.of());
      f.scheduler = new SchedulerImpl(f.root, f.twin);
      var scope = f.root.executingFresh(Activity.of(Activity.Type.SUBMISSION), f.process);
      scope.getCurrentTransaction().resolveWith(f.process, new DigitalTwin.Executor() {
        public List<org.integratedmodelling.klab.api.lang.ServiceCall> serialized() { return plan.getComputation(); }
        public Actuator getActuator() { return plan; }
        public boolean run(Geometry geometry, Scheduler.Event event,
            org.integratedmodelling.klab.api.scope.ContextScope context) {
          assertEquals(Scheduler.Event.Type.INITIALIZATION, event.getType());
          return true; // This fixture already persisted the prerequisite's INIT storage.
        }
      });
      assertTrue(f.scheduler.submit(f.process, scope));
      assertTrue(f.journals.isEmpty());
      assertTrue(scope.commit() > 0);
      assertEquals(12, f.journals.size(), () -> Utils.Exceptions.stackTrace(f.failure));
      assertEquals(13, f.data.get(101L).size());
      assertArrayEquals(new double[]{-20,80,230}, f.values(END));
      assertArrayEquals(new double[]{100,200,350}, f.values(START));
      assertEquals(12, f.envelopes.size());
      when(f.graph.getScheduledObservations(f.root)).thenReturn(List.of(f.process));
      f.restart();
      assertTrue(f.scheduler.advanceTo(END));
      assertEquals(13, f.data.get(101L).size());
      assertEquals(12, f.journals.size());
    }
  }

  @Test void inlineMonthlyErosionPreservesDataThroughFailedCommitRetryAndRestart() throws Exception {
    try(var f=new Fixture("elevation - 10")) {
      f.failCommit=true;assertFalse(f.scheduler.advanceTo(date("2014-02-01")));
      assertArrayEquals(new double[]{100,200,350},f.values(START));assertEquals(1,f.files());assertEquals(1,f.data.get(101L).size());
      f.failCommit=false;assertTrue(f.scheduler.advanceTo(date("2014-02-01")),()->Utils.Exceptions.stackTrace(f.failure));
      assertArrayEquals(new double[]{90,190,340},f.values(date("2014-02-01")));assertEquals(2,f.files());
      assertTrue(f.scheduler.advanceTo(date("2014-02-01")));assertEquals(2,f.data.get(101L).size());
      f.restart();assertTrue(f.scheduler.advanceTo(date("2014-03-01")));
      assertArrayEquals(new double[]{80,180,330},f.values(date("2014-03-01")));
      assertArrayEquals(new double[]{100,200,350},f.values(START));assertEquals(3,f.files());assertEquals(3,f.data.get(101L).size());
      assertEquals(2,f.journals.size());assertTrue(f.journals.stream().allMatch(j->j.changedAssets().equals(List.of(101L))));
      assertEquals(2,f.deltas.size());
      assertEquals(2,f.envelopes.size());
      assertTrue(f.envelopes.stream().allMatch(e -> e.consequential() && e.contextId().equals("context")));
      for(var delta:f.deltas) {
        assertEquals(101,delta.observationId());
        assertTrue(f.data.get(101L).stream().anyMatch(s -> delta.shards().contains(s.getUrn()) && s.getGeometry().encode().equals(delta.support())));
      }
    }
  }

  @Test void inlineNoOpCommitsOnlyCompletionAndSurvivesRestart() throws Exception {
    try(var f=new Fixture("elevation")) {
      f.observer=f.bearer;
      assertTrue(f.scheduler.advanceTo(date("2014-02-01")),()->Utils.Exceptions.stackTrace(f.failure));
      f.restart();assertTrue(f.scheduler.advanceTo(date("2014-02-01")));
      assertEquals(1,f.files());assertEquals(1,f.data.get(101L).size());assertTrue(f.quality.getEventTimestamps().isEmpty());
      assertEquals(1,f.journals.size());assertTrue(f.journals.getFirst().changedAssets().isEmpty());assertFalse(f.journals.getFirst().publicationPending());
      assertTrue(f.deltas.isEmpty());
      assertEquals(1,f.envelopes.size());assertFalse(f.envelopes.getFirst().consequential());
      verify(f.graphTx,never()).perceive(any(),any());
    }
  }

  @Test void fullYearRestartedRunMatchesUninterruptedValuesAndDataSupports() throws Exception {
    double[][] reference = new double[12][];
    List<String> supports;
    try (var f = new Fixture("elevation - 10")) {
      assertTrue(f.scheduler.advanceTo(END), () -> Utils.Exceptions.stackTrace(f.failure));
      for (int month = 1; month <= 12; month++)
        reference[month - 1] = f.values(java.time.LocalDate.of(2014,1,1).plusMonths(month)
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli());
      supports = f.data.get(101L).stream().map(s -> s.getGeometry().encode()).toList();
      assertEquals(13, supports.size());
    }
    try (var f = new Fixture("elevation - 10")) {
      for (int month = 1; month <= 12; month++) {
        long end = java.time.LocalDate.of(2014,1,1).plusMonths(month)
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        f.failCommit = month == 5;
        if (f.failCommit) assertFalse(f.scheduler.advanceTo(end));
        f.failCommit = false;
        assertTrue(f.scheduler.advanceTo(end), () -> Utils.Exceptions.stackTrace(f.failure));
        f.restart();
        assertTrue(f.scheduler.advanceTo(end));
        assertArrayEquals(reference[month - 1], f.values(end));
        assertEquals(month + 1, f.data.get(101L).size());
      }
      assertEquals(supports, f.data.get(101L).stream().map(s -> s.getGeometry().encode()).toList());
      assertEquals(12, f.journals.size());
      assertArrayEquals(new double[]{100,200,350}, f.values(START));
    }
  }

  @Test void downstreamActuatorsReadPendingValuesAndStopAtUnchangedOutputs() throws Exception {
    try(var f=new Fixture("elevation - 10")) {
      f.link(f.process,f.quality,ProcessPlan.INFLUENCE);
      var difference=f.downstream("difference",102,f.quality,"elevation","elevation - elevation",0);
      var untouched=f.downstream("untouched",103,difference,"difference","difference + 1",1);
      var doubled=f.downstream("doubled",104,f.quality,"elevation","elevation * 2",0);
      assertTrue(f.scheduler.advanceTo(date("2014-02-01")),()->Utils.Exceptions.stackTrace(f.failure));
      assertEquals(1,f.data.get(102L).size());assertEquals(1,f.data.get(103L).size());
      assertEquals(List.of(101L,104L),f.journals.getFirst().changedAssets());
      assertArrayEquals(new double[]{180,380,680},f.values(doubled,date("2014-02-01")));
      assertTrue(difference.getEventTimestamps().isEmpty());assertTrue(untouched.getEventTimestamps().isEmpty());
      assertEquals(6,f.files());
    }
  }

  @Test void multipleNamedOutputsReadPriorStateRegardlessOfAssignmentOrder() throws Exception {
    for(boolean reverse : List.of(false,true)) try(var f=new Fixture("elevation - 10")) {
      var second=f.secondAssignment(reverse);
      assertTrue(f.scheduler.advanceTo(date("2014-02-01")),()->Utils.Exceptions.stackTrace(f.failure));
      assertArrayEquals(new double[]{90,190,340},f.values(date("2014-02-01")));
      assertArrayEquals(new double[]{100,200,350},f.values(second,date("2014-02-01")));
      assertEquals(List.of(101L,102L),f.journals.getFirst().changedAssets());
      f.restart();assertTrue(f.scheduler.advanceTo(date("2014-03-01")));
      assertArrayEquals(new double[]{90,190,340},f.values(second,date("2014-03-01")));
    }
  }

  @Test void expressionFailureAfterPartialWritesDoesNotBecomeANoOpReceipt() throws Exception {
    try(var f=new Fixture("elevation == 200 ? 'not a number' : elevation - 10")) {
      assertFalse(f.scheduler.advanceTo(date("2014-02-01")));
      assertEquals(1,f.files());assertEquals(1,f.data.get(101L).size());assertTrue(f.journals.isEmpty());
      assertFalse(f.process.getMetadata().containsKey(DispatchProgress.KEY));
      f.restart();assertFalse(f.scheduler.advanceTo(date("2014-02-01")));
      assertArrayEquals(new double[]{100,200,350},f.values(START));assertTrue(f.journals.isEmpty());
    }
  }

  @Test void scalarTimeParameterUsesNativeMonthlyTransition() throws Exception {
    try(var f=new Fixture("(time.end.milliseconds - time.start.milliseconds) / 86400000")) {
      assertTrue(f.scheduler.advanceTo(date("2014-02-01")),()->Utils.Exceptions.stackTrace(f.failure));
      assertArrayEquals(new double[]{31,31,31},f.values(date("2014-02-01")));
      f.restart();assertTrue(f.scheduler.advanceTo(date("2014-03-01")),()->Utils.Exceptions.stackTrace(f.failure));
      assertArrayEquals(new double[]{28,28,28},f.values(date("2014-03-01")));
    }
  }

  @Test void creationIsDeferredAtomicAndReusesTheCreatedObservationAfterRestart() throws Exception {
    try(var f=new Fixture("elevation")) {
      f.createPrecipitation();assertEquals(3,f.observations.size());
      f.failCommit=true;assertFalse(f.scheduler.advanceTo(date("2014-02-01")));
      assertEquals(3,f.observations.size());assertEquals(1,f.files());assertFalse(f.process.getMetadata().containsKey("klab.process.created.precipitation"));
      f.failCommit=false;assertTrue(f.scheduler.advanceTo(date("2014-02-01")),()->Utils.Exceptions.stackTrace(f.failure));
      long createdId=((Number)f.process.getMetadata().get("klab.process.created.precipitation")).longValue();
      var created=f.observations.get(createdId);assertNotNull(created);assertEquals(20,created.getParentId());
      assertEquals(List.of(date("2014-02-01")),created.getEventTimestamps());assertEquals(1,f.data.get(createdId).size());
      assertEquals(List.of(createdId),f.journals.getFirst().changedAssets());
      f.restart();assertTrue(f.scheduler.advanceTo(date("2014-03-01")),()->Utils.Exceptions.stackTrace(f.failure));
      assertEquals(createdId,((Number)f.process.getMetadata().get("klab.process.created.precipitation")).longValue());
      assertEquals(2,f.data.get(createdId).size());assertEquals(4,f.observations.size());assertEquals(3,f.files());
      var scanner=f.store(created).scan(new TransitionEvent("query",date("2014-02-01"),date("2014-03-01"),null),f.layout,Storage.DoubleScanner.class,true).getFirst();
      assertEquals(0,scanner.get());assertEquals(0,scanner.get());assertEquals(0,scanner.get());
    }
  }
}
