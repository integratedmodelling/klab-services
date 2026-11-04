package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

class CycleAdmissionOwnershipTest {
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 static class Fixture implements AutoCloseable {
   final ConsequenceEngineTest.Fixture nativeFixture;
   final RuntimeService runtime=mock(RuntimeService.class);
   final Reasoner reasoner=mock(Reasoner.class);
   final ResolutionCycles cycles;
   final ServiceContextScope neutral;
   final AtomicBoolean releasedUnderMonitor=new AtomicBoolean();
   final AtomicInteger releases=new AtomicInteger();
   Object graphMonitor;
   Fixture() throws Exception {
     nativeFixture=new ConsequenceEngineTest.Fixture();
     when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(mock(Settings.class));
     neutral=nativeFixture.scope.withResolutionCycleBinding(null);
     var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(reasoner.status()).thenReturn(status);
     when(reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenReturn(
         new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,
             new SemanticCycleBinding("semantic-cycle-v1","reasoner","session",1,"fingerprint","lease"),null));
     when(reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(reasoner);
     doAnswer(call->{releasedUnderMonitor.set(graphMonitor!=null&&Thread.holdsLock(graphMonitor));releases.incrementAndGet();return null;})
         .when(reasoner).releaseSemanticCycle(any(),anyString(),anyString(),anyString(),any());
     neutral.addService(reasoner);cycles=new ResolutionCycles(runtime,"context");
     ConsequenceEngineTest.field(nativeFixture.twin,DigitalTwinImpl.class,"resolutionCycles",cycles);
     when(nativeFixture.twin.getResolutionCycles()).thenReturn(cycles);
     doCallRealMethod().when(nativeFixture.twin).transaction(any(),any(),any(Object[].class));
   }
   DigitalTwinImpl.TransactionImpl transaction(boolean causal,Activity.Type type) throws Exception {
     var source=neutral;
     ResolutionCycles.Admission parent=null;
     if(causal){parent=cycles.admit(neutral,true).get(5,TimeUnit.SECONDS);source=parent.scope().forConsequence(
         new Consequence.Cause(1,"context",1,100,0,null,null,"decision",Consequence.Kind.ROLE_CONFERRAL));}
     var transaction=(DigitalTwinImpl.TransactionImpl)nativeFixture.twin.transaction(Activity.of(type),source,RuntimeAsset.PROVENANCE_ASSET);
     if(parent!=null)parent.close();
     var field=transaction.getClass().getDeclaredField("graph");field.setAccessible(true);graphMonitor=field.get(transaction);
     return transaction;
   }
   void finish(CompletableFuture<ResolutionCycles.Admission> queued) throws Exception {
     try(var next=queued.get(5,TimeUnit.SECONDS)){}cycles.awaitQuiescence(5,TimeUnit.SECONDS);
     assertFalse(releasedUnderMonitor.get(),"Semantic cycle release RPC ran under native graph monitor");
   }
   @Override public void close(){cycles.close();nativeFixture.close();}
 }
 @Test void failingRootWaitsForUnlockedCommitAdmissionRpcToDrain() throws Exception {
   try(var f=new Fixture()){
     var tx=f.transaction(true,Activity.Type.CONSEQUENCE_SUBMISSION);var entered=new CountDownLatch(1);var drain=new CountDownLatch(1);
     tx.guardConsequenceCommitAdmission(()->{entered.countDown();try{assertTrue(drain.await(5,TimeUnit.SECONDS));}
       catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}});
     var queued=f.cycles.admit(f.neutral,false);var committing=CompletableFuture.supplyAsync(tx::commit);
     try{
       assertTrue(entered.await(5,TimeUnit.SECONDS));tx.fail(new IllegalStateException("cancel during admission"));
       assertFalse(queued.isDone());assertEquals(0,f.releases.get());
       assertSame(tx.executionScope().getResolutionCycle(),f.cycles.require(tx.executionScope()).getResolutionCycle());
     }finally{drain.countDown();}
     assertEquals(-1,committing.get(5,TimeUnit.SECONDS));f.finish(queued);
     verify(f.nativeFixture.graph,never()).createTransaction(any());assertEquals(1,f.releases.get());
   }
 }
 @Test void failingRootWaitsForRolePreparationRpcAndNoReleaseRunsUnderFailureMonitor() throws Exception {
   try(var f=new Fixture()){
     var tx=f.transaction(true,Activity.Type.CLASSIFICATION);var execution=tx.executionScope().withTransaction(tx);
     var entered=new CountDownLatch(1);var drain=new CountDownLatch(1);var admission=mock(ConsequenceRoleAdmission.class);
     doAnswer(call->{entered.countDown();assertTrue(drain.await(5,TimeUnit.SECONDS));return null;}).when(admission).validate(anyList(),any());
     var queued=f.cycles.admit(f.neutral,false);
     var preparing=CompletableFuture.supplyAsync(()->tx.stageConsequenceRoles(List.of(),execution,admission));
     try{
       assertTrue(entered.await(5,TimeUnit.SECONDS));tx.fail(new IllegalStateException("cancel semantic preparation"));
       assertFalse(queued.isDone());assertEquals(0,f.releases.get());
       assertSame(tx.executionScope().getResolutionCycle(),f.cycles.require(tx.executionScope()).getResolutionCycle());
     }finally{drain.countDown();}
     // Null private accounting is deliberately never treated as authority after the latch.
     assertThrows(ExecutionException.class,()->preparing.get(5,TimeUnit.SECONDS));f.finish(queued);
     verify(f.nativeFixture.graph,never()).createTransaction(any());assertEquals(1,f.releases.get());
   }
 }
 @Test void baselineMaterializationBeforePreparingCounterStillOwnsItsCycle() throws Exception {
   try(var f=new Fixture()){
     var tx=f.transaction(false,Activity.Type.CLASSIFICATION);var execution=tx.executionScope().withTransaction(tx);
     var asset=new KnowledgeGraph.ConsequenceAssetSnapshot(41,"element",List.of(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Labels.OBSERVATION),Map.of(),null,null);
     var read=new KnowledgeGraph.ConsequenceReadSet("context",List.of(asset),List.of(),List.of());
     var entered=new CountDownLatch(1);var drain=new CountDownLatch(1);var member=new ObservationImpl();member.setId(41);
     when(f.nativeFixture.graph.materializeConsequenceObservation(any(),any())).thenAnswer(call->{entered.countDown();assertTrue(drain.await(5,TimeUnit.SECONDS));return member;});
     var queued=f.cycles.admit(f.neutral,false);var preparing=CompletableFuture.runAsync(()->tx.guardConsequenceBaseline(read,execution));
     try{assertTrue(entered.await(5,TimeUnit.SECONDS));tx.fail(new IllegalStateException("cancel baseline decode"));
       assertFalse(queued.isDone());assertEquals(0,f.releases.get());}
     finally{drain.countDown();}
     assertThrows(ExecutionException.class,()->preparing.get(5,TimeUnit.SECONDS));f.finish(queued);assertEquals(1,f.releases.get());
   }
 }
 @Test void terminalPreparationCannotStartAnotherRpcAndFailedCommitKeepsItsContract() throws Exception {
   try(var f=new Fixture()){
     var tx=f.transaction(false,Activity.Type.CLASSIFICATION);var execution=tx.executionScope().withTransaction(tx);
     tx.fail(new IllegalStateException("terminal"));f.cycles.awaitQuiescence(5,TimeUnit.SECONDS);
     var asset=new KnowledgeGraph.ConsequenceAssetSnapshot(41,"element",List.of(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Labels.OBSERVATION),Map.of(),null,null);
     var read=new KnowledgeGraph.ConsequenceReadSet("context",List.of(asset),List.of(),List.of());
     assertThrows(IllegalStateException.class,()->tx.guardConsequenceBaseline(read,execution));
     verify(f.nativeFixture.graph,never()).materializeConsequenceObservation(any(),any());assertEquals(-1,tx.commit());
     assertEquals(1,f.releases.get());assertFalse(f.releasedUnderMonitor.get());
   }
 }
 @Test void failedBaselineGuardClosesPinOnlyAfterItsOuterMonitorIsUnlocked() throws Exception {
   try(var f=new Fixture()){
     var tx=f.transaction(false,Activity.Type.CLASSIFICATION);
     assertThrows(IllegalArgumentException.class,()->tx.guardClassificationBaseline(null,List.of()));
     f.cycles.awaitQuiescence(5,TimeUnit.SECONDS);assertEquals(1,f.releases.get());assertFalse(f.releasedUnderMonitor.get());
   }
 }
}
