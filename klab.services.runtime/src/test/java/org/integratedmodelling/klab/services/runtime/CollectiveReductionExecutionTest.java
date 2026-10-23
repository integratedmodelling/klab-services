package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.runtime.storage.StorageReads;
import org.junit.jupiter.api.*;

/** Actual completion query, member enumeration and scalar storage; controlled semantic/compiler I/O. */
class CollectiveReductionExecutionTest {
  CollectiveCompletionQueryTest f;
  Reasoner reasoner;
  Reasoner.Capabilities caps;
  ObservationImpl context;
  Concept base;
  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }
  @BeforeEach void setup() throws Exception {
    f = new CollectiveCompletionQueryTest(); f.setup();
    var wholeSupport=Geometry.create("t0(1){tstart=0,tend=10,ttype=PHYSICAL}");
    f.producer.setGeometry(wholeSupport);f.query.setGeometry(wholeSupport);f.cohort.setGeometry(wholeSupport);
    reasoner=mock(Reasoner.class);caps=mock(Reasoner.Capabilities.class);
    when(f.scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.capabilities(f.scope)).thenReturn(caps);
    when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(3L);
    context=new ObservationImpl();context.setId(21);context.setUrn("context:21");
    var concept=new ConceptImpl();concept.setUrn("test:Region");concept.setType(EnumSet.of(SemanticType.SUBJECT));
    var observable=new ObservableImpl();observable.setUrn("test:Region");observable.setSemantics(concept);
    context.setObservable(observable);context.setGeometry(f.producer.getGeometry());context.setEventTimestamps(List.of(0L));
    when(f.scope.getContextObservation()).thenReturn(context);when(f.scope.getObservation(21)).thenReturn(context);
    base=f.producer.getObservable().asConcept().singular();
    when(reasoner.is(any(),any())).thenAnswer(call->
        call.getArgument(0,Semantics.class).asConcept().getUrn().equals(call.getArgument(1,Semantics.class).asConcept().getUrn()));
    when(reasoner.resolveObservable(anyString())).thenReturn(f.producer.getObservable());
  }
  ObservationImpl output(CollectiveReductionBinding.Mode mode, StorageConsumerExecutionTest.Fixture storage) {
    var output=storage.quality(-1,1,Data.FillCurve.D1_LINEAR,mode==CollectiveReductionBinding.Mode.PRESENCE?Storage.Type.BOOLEAN:Storage.Type.DOUBLE,f.query.getGeometry().encode());
    var concept=new ConceptImpl();concept.setUrn("test:"+mode);concept.setName("reduced");concept.setReferenceName("reduced");
    concept.setType(EnumSet.of(SemanticType.QUALITY,mode==CollectiveReductionBinding.Mode.COUNT?SemanticType.NUMEROSITY:SemanticType.PRESENCE));
    concept.setDescriptionType(mode==CollectiveReductionBinding.Mode.COUNT?Contextualization.QUANTIFICATION:Contextualization.VERIFICATION);
    var observable=ObservableImpl.promote(concept,f.scope);output.setObservable(observable);
    doReturn(observable).when(reasoner).resolveObservable(observable.getUrn());
    when(reasoner.splitOperators(observable)).thenReturn(Pair.of(base,List.of(mode==CollectiveReductionBinding.Mode.COUNT?SemanticType.NUMEROSITY:SemanticType.PRESENCE)));
    when(f.twin.getStorageManager()).thenReturn(storage.manager);
    return output;
  }
  void complete() {
    var execution=f.execution(Activity.Type.INSTANTIATION);
    org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(execution.getActivity(),null);
    RuntimeService.recordCollectiveSemanticRevision(execution.getActivity(),f.scope);
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID,execution.commit());
  }
  ObservationImpl member(long id) {
    var member=new ObservationImpl();member.setId(id);member.setUrn("member:"+id);
    var observable=new ObservableImpl();observable.setSemantics(base);observable.setUrn(base.getUrn());
    member.setObservable(observable);member.setGeometry(f.producer.getGeometry());
    f.root.link(f.cohort,member,GraphModel.Relationship.HAS_MEMBER);
    var creator=Activity.of(Activity.Type.INSTANTIATION);
    org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(creator,null);
    f.root.link(creator,member,GraphModel.Relationship.CREATED);
    return member;
  }
  CollectiveReductionExecutor executor(ObservationImpl output) {
    assertTrue(CollectiveReductionBinding.scalarSupport(output.getGeometry()),output.getGeometry().encode());
    assertTrue(SingularContextBinding.eligible(context));assertFalse(base.isAbstract());
    assertTrue(base.is(SemanticType.SUBJECT));assertTrue(f.producer.getObservable().getSemantics().isCollective());
    var binding=CollectiveReductionBinding.capture(output,f.scope,reasoner);assertNotNull(binding);
    var actuator=new ActuatorImpl();actuator.setObservation(output);actuator.setOperationObservable(output.getObservable());
    actuator.setRequestedSupport(output.getGeometry());actuator.setActuatorType(Actuator.Type.OBSERVE);
    actuator.getData().put(CollectiveReductionBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(binding));
    var transported=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(actuator),Actuator.class);
    assertEquals(binding,CompiledDataflow.collectiveReduction(transported));
    var compiled=new CompiledDataflow(f.runtime,output,f.scope);
    var operation=compiled.new ExecutorImpl(actuator);
    assertTrue(operation.isOperational());
    return assertInstanceOf(CollectiveReductionExecutor.class,operation.executors.getFirst());
  }
  AtomicReference<Object> scalarCompiler(ObservationImpl output,StorageConsumerExecutionTest.Fixture storage) {
    var value=new AtomicReference<Object>();
    var builder=spy(org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy.builder(output,f.scope,new ActuatorImpl(),Map.of()));
    doAnswer(call->{value.set(call.getArgument(0,org.integratedmodelling.klab.api.lang.ServiceCall.class).getParameters().get("value"));return call.callRealMethod();})
        .when(builder).add(any());
    when(storage.manager.getBooleanBuffer(anyLong())).thenAnswer(call->(org.ojalgo.array.BufferArray)org.ojalgo.array.BufferArray.Z008.make(call.getArgument(0,Long.class)));
    doReturn(builder).when(f.runtime).getComputationBuilder(eq(output),eq(f.scope),any(),anyMap());
    doReturn(storage.execution).when(f.runtime).shardExecution();
    return value;
  }
  double read(Observation output,StorageConsumerExecutionTest.Fixture storage) {
    if (output.getObservable().is(SemanticType.PRESENCE)) {
      try(var read=StorageReads.open(output,storage.scope,null,Data.FillCurve.D1_LINEAR,Storage.BooleanScanner.class)) {
        var scanner=read.scanners().getFirst();assertTrue(scanner.isValid(),"False must be valid data, not no-data");return scanner.get() ? 1 : 0;
      }
    }
    try(var read=StorageReads.open(output,storage.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)) {
      var scanner=read.scanners().getFirst();assertTrue(scanner.isValid(),"Zero must be valid data, not no-data");return scanner.get();
    }
  }
  @Test void successfulEmptyDiscoveryWritesCountZeroAndPresenceFalse() {
    for(var mode:CollectiveReductionBinding.Mode.values()) try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(mode,storage);var value=scalarCompiler(output,storage);complete();
      var executor=executor(output);assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null),()->String.valueOf(executor.getCause()));
      assertEquals(mode==CollectiveReductionBinding.Mode.COUNT?(Object)0L:false,value.get());assertEquals(0,read(output,storage));
    }
  }
  @Test void actualMembersOverrideProducerCountAndAreDeduplicated() {
    try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(CollectiveReductionBinding.Mode.COUNT,storage);var value=scalarCompiler(output,storage);
      member(31);member(32);f.producer.setChildrenCount(999);complete();
      assertEquals(2,f.root.outgoing(f.cohort).stream().filter(link->link.type()==GraphModel.Relationship.HAS_MEMBER).count(),"Staged cohort membership");
      var queried=f.runtime.queryCollective(f.query,f.scope);
      assertEquals(2,f.runtime.classificationMembers(queried,queried.getGeometry(),f.scope).size(),"Geometry-clipped actual cohort members");
      assertEquals(2,f.runtime.getMembers(queried,f.scope).size(),"Semantic member eligibility");
      var executor=executor(output);assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null),()->String.valueOf(executor.getCause()));
      assertEquals(2L,value.get());assertEquals(2,read(output,storage));
    }
  }
  @Test void presenceTrueAndPendingMemberCountsUseActualIdentities() {
    for(var mode:CollectiveReductionBinding.Mode.values()) try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(mode,storage);var value=scalarCompiler(output,storage);member(-31);member(-32);complete();
      var executor=executor(output);assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null),()->String.valueOf(executor.getCause()));
      assertEquals(mode==CollectiveReductionBinding.Mode.COUNT?(Object)2L:true,value.get());
      assertEquals(mode==CollectiveReductionBinding.Mode.COUNT?2:1,read(output,storage));
    }
  }
  @Test void strictPlanningQueryRejectsLegacyProofButReusesCurrentCompletedCohort() throws Exception {
    try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(CollectiveReductionBinding.Mode.COUNT,storage);
      var binding=CollectiveReductionBinding.capture(output,f.scope,reasoner);
      f.query.getMetadata().put(CollectiveReductionBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(binding));
      var query=RuntimeService.class.getDeclaredMethod("query",Observation.class,org.integratedmodelling.klab.api.scope.ContextScope.class);query.setAccessible(true);
      f.complete();assertTrue(((Observation)query.invoke(f.runtime,f.query,f.scope)).isEmpty());
      complete();assertFalse(((Observation)query.invoke(f.runtime,f.query,f.scope)).isEmpty());
    }
  }
  @Test void incompleteOrUnknownDiscoveryCannotWriteZero() {
    try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(CollectiveReductionBinding.Mode.COUNT,storage);var value=scalarCompiler(output,storage);
      var executor=executor(output);assertFalse(executor.execute(Scheduler.Event.initialization(),f.scope,null));assertNull(value.get());
      f.complete(); // Legacy success has no recorded semantic revision/selection for this route.
      assertFalse(executor.execute(Scheduler.Event.initialization(),f.scope,null));assertNull(value.get());
    }
  }
  @Test void partialCompletionCannotWriteWholeSupportResult() {
    try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(CollectiveReductionBinding.Mode.COUNT,storage);var value=scalarCompiler(output,storage);
      var tx=f.execution(Activity.Type.INSTANTIATION,f.producer,Geometry.create("t0(1){tstart=0,tend=2,ttype=PHYSICAL}"));
      org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(tx.getActivity(),null);
      RuntimeService.recordCollectiveSemanticRevision(tx.getActivity(),f.scope);tx.commit();
      assertFalse(executor(output).execute(Scheduler.Event.initialization(),f.scope,null));assertNull(value.get());
    }
  }
  @Test void staleRevisionAndChangedObserverFailBeforeStorage() {
    try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(CollectiveReductionBinding.Mode.COUNT,storage);var value=scalarCompiler(output,storage);complete();
      var executor=executor(output);when(caps.getKnowledgeRevision()).thenReturn(4L);
      assertFalse(executor.execute(Scheduler.Event.initialization(),f.scope,null));assertNull(value.get());
      when(caps.getKnowledgeRevision()).thenReturn(3L);when(f.scope.getObserver()).thenReturn(context);
      assertFalse(executor.execute(Scheduler.Event.initialization(),f.scope,null));assertNull(value.get());
    }
  }
  @Test void memberCompatibilityUsesAgentMeaningNotActualCreatorInstance() {
    var selectedClass=new ConceptImpl();selectedClass.setUrn("test:Surveyor");selectedClass.setType(EnumSet.of(SemanticType.AGENT));
    var selectedMeaning=ObservableImpl.promote(selectedClass,f.scope);
    var selected=new ObservationImpl();selected.setId(41);selected.setUrn("observer:41");selected.setObservable(selectedMeaning);
    when(f.scope.getObserver()).thenReturn(selected);
    when(reasoner.semanticDistance(any(),any(),isNull(),eq(3L))).thenReturn(0);
    try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(CollectiveReductionBinding.Mode.COUNT,storage);var binding=CollectiveReductionBinding.capture(output,f.scope,reasoner);
      var otherCreator=member(51); // creator is deliberately recorded as absolute/null.
      ((ObservableImpl)otherCreator.getObservable()).setObserverSemantics(selectedClass);
      assertDoesNotThrow(()->f.runtime.requireReductionMemberObserver(otherCreator,f.scope,binding));
      var absolute=member(52);assertDoesNotThrow(()->f.runtime.requireReductionMemberObserver(absolute,f.scope,binding));
      when(reasoner.semanticDistance(any(),any(),isNull(),eq(3L))).thenReturn(-1);
      assertThrows(IllegalStateException.class,()->f.runtime.requireReductionMemberObserver(otherCreator,f.scope,binding));
      assertDoesNotThrow(()->f.runtime.requireReductionMemberObserver(absolute,f.scope,binding));
    }
  }
  @Test void distributedGeometryAndAnnotatedUnitsAreNotAutomaticallyReduced() {
    try(var storage=new StorageConsumerExecutionTest.Fixture()) {
      var output=output(CollectiveReductionBinding.Mode.COUNT,storage);
      output.setGeometry(Geometry.create(StorageConsumerExecutionTest.GRID));
      assertNull(CollectiveReductionBinding.capture(output,f.scope,reasoner));
      output.setGeometry(f.query.getGeometry());((ObservableImpl)output.getObservable()).setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("degree_angle"));
      assertNull(CollectiveReductionBinding.capture(output,f.scope,reasoner));
    }
  }
}
