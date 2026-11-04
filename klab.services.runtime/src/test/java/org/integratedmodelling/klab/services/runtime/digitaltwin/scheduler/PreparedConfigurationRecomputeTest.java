package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Helper boundary controls; private Runtime receipt issuance is exercised by integrated tests. */
class PreparedConfigurationRecomputeTest {
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 static class Fixture implements AutoCloseable {
  final ServiceContextScope scope=mock(ServiceContextScope.class);
  final DigitalTwinImpl twin=mock(DigitalTwinImpl.class);
  final KnowledgeGraph graph=mock(KnowledgeGraph.class);
  final ObservationImpl configuration=new ObservationImpl();
  final ObservationImpl bearer=new ObservationImpl();
  final List<Runnable> committed=new ArrayList<>(),rollback=new ArrayList<>(),before=new ArrayList<>();
  final SchedulerImpl scheduler;
  DigitalTwinImpl.TransactionImpl ownership;
  DigitalTwin.Transaction transaction;
  DigitalTwin.Executor preparedExecutor;
  void register(org.integratedmodelling.klab.api.lang.TriFunction<Geometry,Scheduler.Event,
      org.integratedmodelling.klab.api.scope.ContextScope,Boolean> computation) {
   scheduler.registerExecutor(configuration,computation);
   preparedExecutor=mock(DigitalTwin.Executor.class);
   when(preparedExecutor.executionScope(any())).thenAnswer(call->call.getArgument(0));
   when(preparedExecutor.run(any(),any(),any())).thenAnswer(call->computation.apply(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
  }
  final List<Geometry> actualSupports=new ArrayList<>();
  final List<Scheduler.Event.Type> supportEvents=new ArrayList<>();
  final java.util.concurrent.atomic.AtomicInteger childClockReads=new java.util.concurrent.atomic.AtomicInteger();
  Fixture(){this(false);}
  Fixture(boolean forbidChildClock) {
   var owner=Thread.currentThread();
   var semantic=new ConceptImpl();semantic.setUrn("test:Arrangement");semantic.setName("Arrangement");
   semantic.getType().add(SemanticType.CONFIGURATION);
   configuration.setId(-7);configuration.setParentId(45);configuration.setUrn("context.-7");
   configuration.setObservable(ObservableImpl.promote(semantic,null));configuration.setGeometry(Geometry.create("1"));
   bearer.setId(45);
   when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
   when(scope.getContextObservation()).thenReturn(bearer);
   when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,null));
   when(graph.getScheduledObservations(scope)).thenReturn(List.of());
   when(graph.getLinks(any(),any(),same(scope),any())).thenReturn(List.of());
   when(scope.of(any())).thenThrow(new AssertionError("Validated prepared scope must not be reconstructed from staged ID"));
   scheduler=forbidChildClock?new SchedulerImpl(scope,twin){
    @Override public OptionalLong currentModeledInstant(){
     if(Thread.currentThread()!=owner){childClockReads.incrementAndGet();throw new AssertionError("Prerequisite cannot read the parent's locked clock");}
     return OptionalLong.empty();
    }
   }:new SchedulerImpl(scope,twin);begin();
  }
  void begin() {
   committed.clear();rollback.clear();before.clear();transaction=mock(DigitalTwin.Transaction.class);
   when(scope.getCurrentTransaction()).thenReturn(transaction);
   when(transaction.getExecutor(configuration)).thenAnswer(call->preparedExecutor);
   var activity=Activity.of(Activity.Type.DETECTION);activity.setId(90);
   when(transaction.getActivity()).thenReturn(activity);
   when(transaction.assets()).thenReturn(List.of(configuration));
   ownership=twin.new TransactionImpl(Activity.of(Activity.Type.DETECTION),scope,
       org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET,configuration);
   // This exact negative-ID birth is owned by the fixture's current staging transaction.
    when(graph.isObservationCurrent(any(),same(scope))).thenAnswer(call->
        scope.getCurrentTransaction()==transaction && (call.getArgument(0)==configuration && configuration.getId()>0
            || ownership.currentStatusFor(call.getArgument(0)).orElse(false)));
   doAnswer(c->{before.add(c.getArgument(0));return null;}).when(transaction).beforeCommit(any());
   doAnswer(c->{committed.add(c.getArgument(0));return null;}).when(transaction).afterCommit(any());
   doAnswer(c->{rollback.add(c.getArgument(0));return null;}).when(transaction).afterRollback(any());
  }
  Scheduler.ConfigurationRecomputeOutcome run(long trigger,long revision,OptionalLong instant,boolean initial) {
   return ConfigurationRecompute.executePrepared(scheduler,configuration,configuration.getGeometry(),trigger,revision,
       scope,instant,graph,initial,(actual,event)->{actualSupports.add(actual);supportEvents.add(event.getType());},
       (geometry,event,execution)->preparedExecutor==null?true:preparedExecutor.run(geometry,event,execution));
  }
  void commit(){List.copyOf(before).forEach(Runnable::run);List.copyOf(committed).forEach(Runnable::run);}
  public void close(){scheduler.close();}
 }
 // Exercise the production native encoder rather than serializing the in-memory map directly.
 static Map<String,Object> encoded(Object asset) throws Exception {
  var graph=mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
  var method=org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph.class
      .getDeclaredMethod("asParameters",Object.class,Object[].class);method.setAccessible(true);
  var properties=(Map<String,Object>)method.invoke(graph,asset,new Object[0]);
  return org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
      properties.get(GraphModel.Fields.METADATA).toString(),Map.class);
 }
 @Test void allocatedBirthReceiptsAndActivitySurviveNativeEncodingAndDuplicateLookup() throws Exception {
  try(var f=new Fixture()) {
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,f.run(101,1,OptionalLong.empty(),true));
   f.configuration.setId(700);
   f.commit();
   assertTrue(f.scheduler.configurationsInFlight.isEmpty());assertTrue(f.scheduler.inputChangesInFlight.isEmpty());
   var metadata=encoded(f.configuration);
   var persisted=metadata.get(ConfigurationRecompute.RECEIPTS);
   assertEquals(Map.of("configuration-input:700:1:101","RESOLVED"),ConfigurationRecompute.readReceipts(persisted));
   var provenance=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
       encoded(f.transaction.getActivity()).get(ConfigurationRecompute.PROVENANCE).toString(),Map.class);
   assertEquals(700,((Number)provenance.get("configuration")).longValue());
   assertEquals(1,((Number)provenance.get("version")).intValue());
   // Replace warm metadata with only its native representation, as on a restored observation.
   f.configuration.getMetadata().clear();f.configuration.getMetadata().put(ConfigurationRecompute.RECEIPTS,persisted);
   f.begin();
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.ALREADY_EXECUTED,f.run(101,1,OptionalLong.empty(),true));
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,f.run(101,2,OptionalLong.empty(),false));
   List.copyOf(f.rollback).reversed().forEach(Runnable::run);
   assertTrue(f.scheduler.configurationsInFlight.isEmpty());assertTrue(f.scheduler.inputChangesInFlight.isEmpty());
   assertEquals(persisted,f.configuration.getMetadata().get(ConfigurationRecompute.RECEIPTS));
   assertFalse(f.transaction.getActivity().getMetadata().containsKey(ConfigurationRecompute.PROVENANCE));
  }
 }
 @Test void nativeTransactionAllocatesBirthBeforeEncodingReboundReceiptAndDrainsRegistries() throws Exception {
  try(var f=new Fixture()) {
   var graph=mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class);
   var storage=mock(KnowledgeGraph.Transaction.class);
   when(graph.nextKey()).thenReturn(900L);when(graph.createTransaction(f.scope)).thenReturn(storage);
   var graphField=DigitalTwinImpl.class.getDeclaredField("knowledgeGraph");graphField.setAccessible(true);graphField.set(f.twin,graph);
   var cacheField=DigitalTwinImpl.class.getDeclaredField("commitCache");cacheField.setAccessible(true);cacheField.set(f.twin,com.google.common.cache.CacheBuilder.newBuilder().build());
   when(f.twin.getKnowledgeGraph()).thenReturn(graph);
   var user=mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(f.scope.getUser()).thenReturn(user);when(f.scope.getId()).thenReturn("context");
   var root=f.twin.new TransactionImpl(Activity.of(Activity.Type.DETECTION),f.scope,
       org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET,f.configuration);
   when(f.scope.getCurrentTransaction()).thenReturn(root);
   when(graph.isObservationCurrent(any(),same(f.scope))).thenAnswer(call->root.currentStatusFor(call.getArgument(0)).orElse(false));
   when(f.graph.isObservationCurrent(any(),same(f.scope))).thenAnswer(call->root.currentStatusFor(call.getArgument(0)).orElse(false));
   var persisted=new java.util.HashMap<Long,Map<String,Object>>();
   var sequence=new java.util.concurrent.atomic.AtomicLong(700);
   doAnswer(call->{
    var asset=call.getArgument(0,org.integratedmodelling.klab.api.data.RuntimeAsset.class);
    if(asset instanceof ObservationImpl observation){observation.setId(sequence.incrementAndGet());observation.setUrn("context."+observation.getId());}
    if(asset instanceof org.integratedmodelling.klab.api.provenance.impl.ActivityImpl activity)activity.setId(sequence.incrementAndGet());
    persisted.put(asset.getId(),encoded(asset));return null;
   }).when(storage).store(any());
   doAnswer(call->{var asset=call.getArgument(0,org.integratedmodelling.klab.api.data.RuntimeAsset.class);
    persisted.put(asset.getId(),encoded(asset));return null;}).when(storage).update(any());
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,ConfigurationRecompute.executePrepared(
       f.scheduler,f.configuration,f.configuration.getGeometry(),101,1,f.scope,OptionalLong.empty(),graph,true,(g,e)->{},(g,e,c)->true),()->mockingDetails(f.scope).getInvocations().toString());
   assertTrue(root.commit()>0);
   assertTrue(f.scheduler.configurationsInFlight.isEmpty());assertTrue(f.scheduler.inputChangesInFlight.isEmpty());
   assertEquals(Map.of("configuration-input:"+f.configuration.getId()+":1:101","RESOLVED"),
       ConfigurationRecompute.readReceipts(persisted.get(f.configuration.getId()).get(ConfigurationRecompute.RECEIPTS)));
   var evidence=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
       persisted.get(root.getActivity().getId()).get(ConfigurationRecompute.PROVENANCE).toString(),Map.class);
   assertEquals(f.configuration.getId(),((Number)evidence.get("configuration")).longValue());
  }
 }

 @Test void boundedLegacyReceiptDecodingDoesNotAuthorizeInactiveConfiguration() {
  try(var f=new Fixture()) {
   var old=Map.of("configuration-input:-7:1:101","RESOLVED");
   f.configuration.getMetadata().put(ConfigurationRecompute.RECEIPTS,old);
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.ALREADY_EXECUTED,f.run(101,1,OptionalLong.empty(),true));
   when(f.graph.isObservationCurrent(f.configuration,f.scope)).thenReturn(false);
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,f.run(101,1,OptionalLong.empty(),true));
   assertTrue(ConfigurationRecompute.readReceipts("{\"version\":2,\"receipts\":{}}").isEmpty());
   assertTrue(ConfigurationRecompute.readReceipts(Map.of("unrelated","RESOLVED")).isEmpty());
  }
 }

 @Test void transientDependentBirthInitializesOnceThenRecomputesAtCurrentTimeWithoutLosingBearer() {
  try(var f=new Fixture()) {
   var events=new ArrayList<Scheduler.Event.Type>();
   f.register((geometry,event,execution)->{
    assertSame(f.scope,execution);assertSame(f.bearer,execution.getContextObservation());
    assertEquals("cycle",execution.getResolutionCycleBinding().cycleId());events.add(event.getType());return true;
   });
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,f.run(101,1,OptionalLong.empty(),true));f.commit();
   assertEquals(List.of(Scheduler.Event.Type.INITIALIZATION),events);
   assertSame(f.configuration.getGeometry(),f.actualSupports.getFirst());
   f.begin();assertEquals(Scheduler.ConfigurationRecomputeOutcome.ALREADY_EXECUTED,f.run(101,1,OptionalLong.empty(),true));
   f.begin();assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,f.run(102,2,OptionalLong.of(5000),false));f.commit();
   assertEquals(List.of(Scheduler.Event.Type.INITIALIZATION,Scheduler.Event.Type.INPUT_CHANGE),events);
   assertEquals(events,f.supportEvents);
   var actualTime=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(f.actualSupports.getLast()).getTime();
   assertEquals(5000,actualTime.getStart().getMilliseconds());assertEquals(5000,actualTime.getEnd().getMilliseconds());
   assertTrue(f.scheduler.currentModeledInstant().isEmpty(),"Recomputation must not advance scheduler time");
   verify(f.scope,never()).of(any());
  }
 }
 @Test void untimedPreparedRevisionDoesNotCreateClockAndRollbackRemovesReceipt() {
  try(var f=new Fixture()) {
   f.register((geometry,event,execution)->{
    assertEquals(Scheduler.Event.Type.INPUT_CHANGE,event.getType());assertFalse(event.hasTime());
    assertThrows(IllegalStateException.class,event::getTime);return true;
   });
   var timestamps=List.copyOf(f.configuration.getEventTimestamps());
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,f.run(201,2,OptionalLong.empty(),false));
   assertEquals(timestamps,f.configuration.getEventTimestamps());assertTrue(f.scheduler.currentModeledInstant().isEmpty());
   List.copyOf(f.rollback).reversed().forEach(Runnable::run);
   assertFalse(f.configuration.getMetadata().containsKey(ConfigurationRecompute.RECEIPTS));
  }
 }
 @Test void privatePreparedNoOpRootDoesNotRequirePerActuatorExecutor() {
  try(var f=new Fixture()) {assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,
    f.run(301,1,OptionalLong.empty(),true));}
 }
 @Test void legacyInitializationWithoutScientificMarkerDefersBeforeExecutionOrSupportReceipt() {
  try(var f=new Fixture()) {
   f.configuration.setEventTimestamps(new ArrayList<>(List.of(0L)));
   var calls=new java.util.concurrent.atomic.AtomicInteger();
   f.register((geometry,event,execution)->{calls.incrementAndGet();return true;});
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.DEFERRED,f.run(401,1,OptionalLong.empty(),true));
   assertEquals(0,calls.get());assertTrue(f.actualSupports.isEmpty());verify(f.transaction,never()).update(f.configuration);
  }
 }

 @Test void privatePreparedInputChangeNeverUsesCommittedCacheOrRequiresPerActuatorExecutor() {
  try(var f=new Fixture()) {
   var staleCalls=new java.util.concurrent.atomic.AtomicInteger();
   f.scheduler.registerExecutor(f.configuration,(geometry,event,scope)->{staleCalls.incrementAndGet();return true;});
   assertEquals(Scheduler.ConfigurationRecomputeOutcome.RESOLVED,f.run(501,2,OptionalLong.empty(),false));
   assertEquals(0,staleCalls.get());assertEquals(1,f.actualSupports.size());
  }
 }

 void freshPrerequisite(long id,boolean owned,boolean failInitialization,boolean expectResolved) {freshPrerequisite(id,owned,failInitialization,expectResolved,"");}
 void freshPrerequisite(long id,boolean owned,boolean failInitialization,boolean expectResolved,String mode) {
  try(var f=new Fixture(mode.equals("clock-lock"))) {
   var input=new ObservationImpl();input.setId(id);input.setGeometry(Geometry.create("1"));
   var meaning=new ConceptImpl();meaning.setUrn("test:NewScalar");meaning.getType().add(SemanticType.QUALITY);
   input.setObservable(ObservableImpl.promote(meaning,null));
   var plan=new org.integratedmodelling.common.runtime.ActuatorImpl();plan.setObservation(input);plan.setId(id);
   plan.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.OBSERVE);
   plan.setExecutionRole(org.integratedmodelling.klab.api.services.runtime.Actuator.ExecutionRole.INITIALIZATION);
   var producer=mock(DigitalTwin.Executor.class);when(producer.getActuator()).thenReturn(plan);when(producer.executionScope(any())).thenReturn(f.scope);
   var events=new ArrayList<Scheduler.Event.Type>();
   when(producer.run(any(),any(),any())).thenAnswer(call->{
    var event=call.getArgument(1,Scheduler.Event.class);events.add(event.getType());
    if(event.getType()!=Scheduler.Event.Type.INITIALIZATION)throw new IllegalStateException("Affected quality has no committed baseline: null");
    return !failInitialization;
   });
   when(f.twin.getStorageManager()).thenReturn(mock(StorageManager.class));
   if(mode.equals("reference"))plan.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.REFERENCE);
   if(mode.equals("foreign-user")) {
    var selected=mock(ServiceContextScope.class,org.mockito.AdditionalAnswers.delegatesTo(f.scope));
    when(selected.getUser()).thenReturn(mock(org.integratedmodelling.klab.api.identities.UserIdentity.class));
    when(producer.executionScope(any())).thenReturn(selected);
   }
   when(f.transaction.getExecutor(input)).thenReturn(mode.equals("missing")?null:producer);
   if(mode.equals("missing"))f.scheduler.registerExecutor(input,(g,e,c)->{throw new AssertionError("Cached producer must not run");});
   when(f.transaction.assets()).thenReturn(owned?List.of(f.configuration,input):List.of(f.configuration));
   if(owned)f.ownership.add(input);
   if(id>0)when(f.graph.isObservationCurrent(same(input),same(f.scope))).thenReturn(true);
   var link=mock(KnowledgeGraph.Link.class);when(link.source()).thenReturn(input);when(link.target()).thenReturn(f.configuration);
   when(link.properties()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
   when(f.graph.getLinks(any(),eq(GraphModel.Relationship.Direction.INCOMING),same(f.scope),eq(GraphModel.Relationship.AFFECTS)))
       .thenAnswer(call->call.getArgument(0)==f.configuration?List.of(link):List.of());
   var rootCalls=new java.util.concurrent.atomic.AtomicInteger();f.register((geometry,event,scope)->{rootCalls.incrementAndGet();return true;});
   Scheduler.ConfigurationRecomputeOutcome result;
   if(mode.equals("clock-lock")){synchronized(f.scheduler){result=f.run(601,2,OptionalLong.of(5000),false);}}
   else result=f.run(601,2,OptionalLong.of(5000),false);
   assertEquals(expectResolved?Scheduler.ConfigurationRecomputeOutcome.RESOLVED:Scheduler.ConfigurationRecomputeOutcome.FAILED,result);
   assertEquals(expectResolved?1:0,rootCalls.get());
   var expectedEvents=id<Observation.UNASSIGNED_ID?owned&&(mode.isEmpty()||mode.equals("clock-lock"))?List.of(Scheduler.Event.Type.INITIALIZATION):List.<Scheduler.Event.Type>of():List.of(Scheduler.Event.Type.INPUT_CHANGE);
   assertEquals(expectedEvents,events);
   assertTrue(f.scheduler.currentModeledInstant().isEmpty(),"No clock advance during prepared prerequisite initialization");
  }
 }
 @Test void newlyPreparedScalarMustInitializeBeforeExistingConfigurationInputChange(){freshPrerequisite(-77,true,false,true);}
 @Test void failedNewScalarInitializationDoesNotInvokePreparedRoot(){freshPrerequisite(-77,true,true,false);}
 @Test void persistedMissingBaselineCannotBeBlessedAsNewInitialization(){freshPrerequisite(77,true,false,false);}
 @Test void unownedTransientDependencyCannotAcquireAnInitializationPass(){freshPrerequisite(-77,false,false,false);}
 @Test void missingProducerCannotFallBackToCommittedCache(){freshPrerequisite(-77,true,false,false,"missing");}
 @Test void foreignUserScopeCannotInitializePreparedInput(){freshPrerequisite(-77,true,false,false,"foreign-user");}
 @Test void referenceCannotActAsFreshProducingModel(){freshPrerequisite(-77,true,false,false,"reference");}
 @Test void preparedPrerequisiteDoesNotReenterClockMonitorHeldByItsWaitingParent(){freshPrerequisite(-77,true,false,true,"clock-lock");}
 @Test void enabledDiagnosticsAlsoAvoidParentsClockMonitor(){
  var key="klab.diagnostics.configurationRecompute";var old=System.getProperty(key);
  try{System.setProperty(key,"true");freshPrerequisite(-77,true,false,true,"clock-lock");}
  finally{if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
 }


 void recipeEdge(String classification,String occurrenceRole,boolean substantial,boolean shouldRun) {
  try(var f=new Fixture()) {
    var input=new ObservationImpl();input.setId(77);input.setGeometry(Geometry.create("1"));
    when(f.graph.isObservationCurrent(same(input),same(f.scope))).thenReturn(true);
   var meaning=new ConceptImpl();meaning.setUrn(substantial?"test:Bearer":"test:Width");meaning.getType().add(substantial?SemanticType.SUBJECT:SemanticType.QUALITY);
   input.setObservable(ObservableImpl.promote(meaning,null));
   var producer=mock(DigitalTwin.Executor.class);when(producer.executionScope(any())).thenReturn(f.scope);
   var inputCalls=new java.util.concurrent.atomic.AtomicInteger();when(producer.run(any(),any(),any())).thenAnswer(call->{inputCalls.incrementAndGet();return false;});
   when(f.transaction.getExecutor(input)).thenReturn(producer);
   var link=mock(KnowledgeGraph.Link.class);when(link.source()).thenReturn(input);when(link.target()).thenReturn(f.configuration);
   var properties=org.integratedmodelling.klab.api.collections.Parameters.<String>create();
   if(classification!=null)properties.put("role",classification);
   if(occurrenceRole!=null)properties.put(ProcessPlan.EDGE_ROLE,occurrenceRole);
   when(link.properties()).thenReturn(properties);
   when(f.graph.getLinks(any(),eq(GraphModel.Relationship.Direction.INCOMING),same(f.scope),eq(GraphModel.Relationship.AFFECTS)))
       .thenAnswer(call->call.getArgument(0)==f.configuration?List.of(link):List.of());
   var roots=new java.util.concurrent.atomic.AtomicInteger();f.register((g,e,c)->{roots.incrementAndGet();return true;});
   assertEquals(shouldRun?Scheduler.ConfigurationRecomputeOutcome.FAILED:Scheduler.ConfigurationRecomputeOutcome.RESOLVED,
       f.run(701,2,OptionalLong.of(5000),false));
   assertEquals(shouldRun?1:0,inputCalls.get());assertEquals(shouldRun?0:1,roots.get());
   if(!shouldRun)verify(f.graph,never()).getLinks(input,GraphModel.Relationship.Direction.INCOMING,f.scope,GraphModel.Relationship.AFFECTS);
  }
 }
 @Test void configurationTriggerDoesNotReexecuteItsIngredient(){recipeEdge("configuration-input",null,false,false);}
 @Test void configurationContextTriggerDoesNotEnterItsModelClosure(){recipeEdge("configuration-input",null,true,false);}
 @Test void failedRealComputationalPrerequisiteStillBlocksRoot(){recipeEdge(null,ProcessPlan.PREREQUISITE,false,true);}
 @Test void dualPurposeConfigurationIngredientRetainsRealPrerequisiteGuard(){recipeEdge("configuration-input",ProcessPlan.PREREQUISITE,false,true);}
 @Test void untaggedLegacyDependencyRetainsExistingExecution(){recipeEdge(null,null,false,true);}

 @Test void nestedFailureDiagnosticAlsoAvoidsWaitingParentsClock() {
  try(var f=new Fixture(true)) {
   var fresh=new ObservationImpl();fresh.setId(-77);fresh.setGeometry(Geometry.create("1"));
   var meaning=new ConceptImpl();meaning.setUrn("test:Fresh");meaning.getType().add(SemanticType.QUALITY);fresh.setObservable(ObservableImpl.promote(meaning,null));
   var nested=new ObservationImpl();nested.setId(88);nested.setGeometry(Geometry.create("1"));nested.setObservable(fresh.getObservable());
   var plan=new org.integratedmodelling.common.runtime.ActuatorImpl();plan.setId(-77);plan.setObservation(fresh);plan.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.OBSERVE);plan.setExecutionRole(org.integratedmodelling.klab.api.services.runtime.Actuator.ExecutionRole.INITIALIZATION);
   var producer=mock(DigitalTwin.Executor.class);when(producer.getActuator()).thenReturn(plan);when(producer.executionScope(any())).thenReturn(f.scope);
   var broken=mock(DigitalTwin.Executor.class);when(broken.executionScope(any())).thenReturn(f.scope);when(broken.run(any(),any(),any())).thenThrow(new IllegalStateException("nested original failure"));
   f.ownership.add(fresh);f.ownership.add(nested);
   when(f.transaction.getExecutor(fresh)).thenReturn(producer);when(f.transaction.getExecutor(nested)).thenReturn(broken);when(f.transaction.assets()).thenReturn(List.of(f.configuration,fresh,nested));
   var top=mock(KnowledgeGraph.Link.class);when(top.source()).thenReturn(fresh);when(top.target()).thenReturn(f.configuration);when(top.properties()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
   var inner=mock(KnowledgeGraph.Link.class);when(inner.source()).thenReturn(nested);when(inner.target()).thenReturn(fresh);when(inner.properties()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
   when(f.graph.getLinks(any(),eq(GraphModel.Relationship.Direction.INCOMING),same(f.scope),eq(GraphModel.Relationship.AFFECTS)))
       .thenAnswer(call->call.getArgument(0)==f.configuration?List.of(top):call.getArgument(0)==fresh?List.of(inner):List.of());
   var roots=new java.util.concurrent.atomic.AtomicInteger();f.register((g,e,c)->{roots.incrementAndGet();return true;});
   synchronized(f.scheduler){assertEquals(Scheduler.ConfigurationRecomputeOutcome.FAILED,f.run(801,2,OptionalLong.of(5000),false));}
   assertEquals(0,f.childClockReads.get());assertEquals(0,roots.get());verify(producer,never()).run(any(),any(),any());
  }
 }

}
