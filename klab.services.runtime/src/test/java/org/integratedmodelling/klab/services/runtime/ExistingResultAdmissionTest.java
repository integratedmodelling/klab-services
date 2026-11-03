package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.ResolutionCycles;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.*;

class ExistingResultAdmissionTest {
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 static ObservationImpl observation(long id,boolean agent) {
   var value=new ObservationImpl();value.setId(id);value.setUrn("context."+id);value.setGeometry(Geometry.create("1"));
   var observable=mock(Observable.class);var concept=mock(Concept.class);
   when(observable.getSemantics()).thenReturn(concept);when(observable.getUrn()).thenReturn(agent?"test:Agent":"test:Height");
   when(observable.is(SemanticType.AGENT)).thenReturn(agent);when(observable.is(SemanticType.QUALITY)).thenReturn(!agent);
   when(observable.isOptional()).thenReturn(true);when(observable.asConcept()).thenReturn(concept);
   when(concept.getType()).thenReturn(EnumSet.of(agent?SemanticType.AGENT:SemanticType.QUALITY));
   when(concept.getUrn()).thenReturn(agent?"test:Agent":"test:Height");value.setObservable(observable);return value;
 }
 static class Fixture implements AutoCloseable {
   final SubmissionCancellationTest.Fixture f=new SubmissionCancellationTest.Fixture();
   final KnowledgeGraph graph=f.twin.getKnowledgeGraph();
   final KnowledgeGraph.Transaction nativeTx=mock(KnowledgeGraph.Transaction.class);
   final AtomicBoolean created=new AtomicBoolean();
   final ResolutionCycles cycles;
   final ResolutionCycles.Admission parent;
   final ServiceContextScope origin;
   Fixture() throws Exception {
     f.scope.setId("cancellation-test");when(f.scope.getUser().getUsername()).thenReturn("tester");
     when(f.runtime.settings()).thenReturn(mock(Settings.class));when(f.runtime.serviceId()).thenReturn("runtime");
     when(f.reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenReturn(
         new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,
             new SemanticCycleBinding("semantic-cycle-v1","reasoner","session",1,"fingerprint","lease"),null));
     when(f.reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(f.reasoner);
     cycles=new ResolutionCycles(f.runtime,"cancellation-test");when(f.twin.getResolutionCycles()).thenReturn(cycles);
     doCallRealMethod().when(f.runtime).withinResolutionCycle(any(),any(),any());
     parent=cycles.admit(f.scope,true).get(5,TimeUnit.SECONDS);
     origin=parent.scope().forConsequence(new Consequence.Cause(1,"cancellation-test",1,2,0,null,null,"decision",Consequence.Kind.ROLE_IMPLICATION));
     when(graph.createTransaction(any())).thenAnswer(call->{created.set(true);return nativeTx;});
   }
   ServiceContextScope observed(ServiceContextScope source,Observation observer) {
     var query=mock(KnowledgeGraph.Query.class,RETURNS_SELF);when(query.run(any())).thenReturn(List.of(observer));
     doReturn(query).when(graph).query(eq(Observation.class),any());
     when(graph.getAsset(eq(observer.getId()),any(),eq(Observation.class))).thenReturn(observer);
     var selected=source.withObserver(observer);var cause=source.getConsequenceCause();
     return cause==null?selected:selected.forConsequence(new Consequence.Cause(cause.version(),cause.contextId(),
         cause.triggeringCommitId(),cause.triggeringActivityId(),observer.getId(),cause.worldviewFingerprint(),
         cause.parentDecisionId(),cause.decisionId(),cause.kind()));
   }
   void noScientificWork(){verify(f.twin,never()).transaction(any(),any(),any(Object[].class));verify(f.resolver,never()).resolve(any(),any());}
   @Override public void close(){parent.close();cycles.close();cycles.awaitQuiescence(5,TimeUnit.SECONDS);}
 }
 @Test void existingPositiveNoPerceptionStillValidatesReadOnlyNativeTransaction() throws Exception {
   try(var f=new Fixture()) {
     var result=observation(55,false);var prepared=new AtomicBoolean();var validated=new AtomicBoolean();
     var admission=new ConsequenceSubmissionAdmission(){
       public void bind(DigitalTwin.Transaction tx,ServiceContextScope scope){fail("No invented scientific transaction");}
       public ExistingResultGuard prepareExisting(Observation value,ServiceContextScope execution){
         assertSame(result,value);assertFalse(f.created.get());prepared.set(true);
         return tx->{assertSame(f.nativeTx,tx);assertTrue(prepared.get());validated.set(true);};
       }
     };
     assertSame(result,f.f.runtime.submit(result,f.origin.withConsequenceSubmissionAdmission(admission)).get(5,TimeUnit.SECONDS));
     assertTrue(validated.get());verify(f.nativeTx).close();verify(f.nativeTx,never()).perceive(any(),any());f.noScientificWork();
   }
 }
 @Test void sourceDriftRejectsExistingResultBeforeObserverPerceptionAndFailsNativeTransaction() throws Exception {
   try(var f=new Fixture()) {
     var revision=new AtomicInteger();var result=observation(55,false);var observer=observation(91,true);
     var source=f.observed(f.origin,observer);
     var admission=new ConsequenceSubmissionAdmission(){
       public void bind(DigitalTwin.Transaction tx,ServiceContextScope scope){fail("No invented scientific transaction");}
       public ExistingResultGuard prepareExisting(Observation value,ServiceContextScope execution){
         assertFalse(f.created.get());int captured=revision.get();
         return tx->{if(revision.get()!=captured)throw new IllegalStateException("Causal source readset drifted");};
       }
     };
     doAnswer(call->{f.created.set(true);revision.incrementAndGet();return f.nativeTx;}).when(f.graph).createTransaction(any());
     var task=f.f.runtime.submit(result,source.withConsequenceSubmissionAdmission(admission));
     assertThrows(ExecutionException.class,()->task.get(5,TimeUnit.SECONDS));
     verify(f.nativeTx,never()).perceive(any(),any());verify(f.nativeTx,never()).markExplicitAgent(any());
     var ordered=inOrder(f.nativeTx);ordered.verify(f.nativeTx).fail(any(Exception.class));ordered.verify(f.nativeTx).close();f.noScientificWork();
   }
 }
 @Test void validExistingObserverPerceptionOccursOnlyAfterGuardAndReturnsKnownIdentity() throws Exception {
   try(var f=new Fixture()) {
     var validated=new AtomicBoolean();var result=observation(55,false);var observer=observation(91,true);
     var source=f.observed(f.origin,observer);
     var admission=new ConsequenceSubmissionAdmission(){
       public void bind(DigitalTwin.Transaction tx,ServiceContextScope scope){fail("No invented scientific transaction");}
       public ExistingResultGuard prepareExisting(Observation value,ServiceContextScope execution){
         assertFalse(f.created.get());return tx->validated.set(true);
       }
     };
     doAnswer(call->{assertTrue(validated.get(),"Perception preceded causal guard");return null;})
         .when(f.nativeTx).perceive(same(observer),same(result.getGeometry()));
     assertSame(result,f.f.runtime.submit(result,source.withConsequenceSubmissionAdmission(admission)).get(5,TimeUnit.SECONDS));
     verify(f.nativeTx).perceive(same(observer),same(result.getGeometry()));f.noScientificWork();
   }
 }
 @Test void defaultOrNullExistingGuardFailsClosedWhileOrdinaryKnownResultKeepsFastPath() throws Exception {
   try(var f=new Fixture()) {
     var result=observation(55,false);ConsequenceSubmissionAdmission unsupported=(tx,execution)->{};
     assertThrows(ExecutionException.class,()->f.f.runtime.submit(result,
         f.origin.withConsequenceSubmissionAdmission(unsupported)).get(5,TimeUnit.SECONDS));
     var missing=new ConsequenceSubmissionAdmission(){public void bind(DigitalTwin.Transaction tx,ServiceContextScope execution){}
       public ExistingResultGuard prepareExisting(Observation value,ServiceContextScope execution){return null;}};
     assertThrows(ExecutionException.class,()->f.f.runtime.submit(result,
         f.origin.withConsequenceSubmissionAdmission(missing)).get(5,TimeUnit.SECONDS));
     verify(f.graph,never()).createTransaction(any());
     assertSame(result,f.f.runtime.submit(result,f.origin).get(5,TimeUnit.SECONDS));
     verify(f.graph,never()).createTransaction(any());f.noScientificWork();
   }
 }
 @Test void nestedBoundRequestValidatesReadOnlyWithoutPerceptionOrNewScientificActivity() throws Exception {
   try(var f=new Fixture()) {
     var result=observation(55,false);var observer=observation(91,true);var validated=new AtomicBoolean();
     var admission=new ConsequenceSubmissionAdmission(){
       DigitalTwin.Transaction bound;
       public void bind(DigitalTwin.Transaction tx,ServiceContextScope execution){bound=tx;}
       public ExistingResultGuard prepareExisting(Observation value,ServiceContextScope execution){
         assertSame(bound,execution.getCurrentTransaction(),"Only this already-bound parent request permits derived focus");
         return tx->validated.set(true);
       }
     };
     var source=f.observed(f.origin,observer).withConsequenceSubmissionAdmission(admission).withTransaction(f.f.rootTransaction);
     admission.bind(f.f.rootTransaction,source);
     var dependency=source.within(observation(77,false));
     assertSame(result,f.f.runtime.submit(result,dependency).get(5,TimeUnit.SECONDS));
     assertTrue(validated.get());verify(f.nativeTx,never()).perceive(any(),any());verify(f.nativeTx).close();f.noScientificWork();
   }
 }
 @Test void nonemptyQueryRunsSameReadOnlyGuardWithoutChangingQueryPerceptionPolicy() throws Exception {
   try(var f=new Fixture()) {
     // Keep the ordinary query storage boundary inert; actual registry/positive paths are above.
     doAnswer(call->((java.util.function.Function<org.integratedmodelling.klab.api.scope.ContextScope,
         CompletableFuture<Observation>>)call.getArgument(2)).apply(call.getArgument(1)))
         .when(f.f.runtime).withinResolutionCycle(any(),any(),any());
     var known=observation(55,false);var request=observation(Observation.QUERY_ID,false);var observer=observation(91,true);
     var source=spy(f.observed(f.origin,observer));doReturn(known).when(source).getObservation(any(Observation.class));
     var manager=mock(org.integratedmodelling.klab.api.digitaltwin.StorageManager.class);var storage=mock(Storage.class);
     when(f.f.twin.getStorageManager()).thenReturn(manager);when(manager.getStorage(any())).thenReturn(storage);
     when(storage.getNativeShardingStrategy()).thenReturn(Data.ShardingStrategy.trivial(Storage.Type.DOUBLE));
     var validated=new AtomicBoolean();var admission=new ConsequenceSubmissionAdmission(){
       public void bind(DigitalTwin.Transaction tx,ServiceContextScope execution){fail("Query must not create scientific activity");}
       public ExistingResultGuard prepareExisting(Observation value,ServiceContextScope execution){
         assertEquals(Observation.QUERY_ID,value.getId());assertFalse(f.created.get());return tx->validated.set(true);
       }
     };
     // Attach before spying again so the ordinary getObservation stub remains on the actual query scope.
     source=spy(source.withConsequenceSubmissionAdmission(admission));doReturn(known).when(source).getObservation(any(Observation.class));
     var returned=f.f.runtime.submit(request,source).get(5,TimeUnit.SECONDS);
     assertFalse(returned.isEmpty());assertTrue(validated.get());verify(f.nativeTx,never()).perceive(any(),any());f.noScientificWork();
   }
 }
}
