package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
class DirectTransactionCycleTest {
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 @Test void publicDirectTransactionAcquiresFrozenCycleAndReleasesAfterCommitOrFailure() throws Exception {
   try(var f=new ConsequenceEngineTest.Fixture()) {
     var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(mock(Settings.class));
     try(var cycles=new ResolutionCycles(runtime,"context")) {
       ConsequenceEngineTest.field(f.twin,DigitalTwinImpl.class,"resolutionCycles",cycles);
       when(f.twin.getResolutionCycles()).thenReturn(cycles);
       doCallRealMethod().when(f.twin).transaction(any(),any(),any(Object[].class));
       var root=f.scope.withResolutionCycleBinding(null);
       var transaction=f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),root,org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET);
       var waiting=cycles.admit(root,false);assertFalse(waiting.isDone());
       assertEquals(900,transaction.commit());
       var next=waiting.get(5,TimeUnit.SECONDS);next.close();cycles.awaitQuiescence(5,TimeUnit.SECONDS);
       var failed=f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),root,org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET);failed.fail(new IllegalStateException("expected"));
       cycles.awaitQuiescence(5,TimeUnit.SECONDS);
     }
   }
 }
 @Test void realExecutingRootAndNestedChildKeepFrozenScopeAndDrainOnCommitOrChildAbort() throws Exception {
   for(boolean abortChild:new boolean[]{false,true}) try(var f=new ConsequenceEngineTest.Fixture()) {
     var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(mock(Settings.class));
     try(var cycles=new ResolutionCycles(runtime,"context")) {
       ConsequenceEngineTest.field(f.twin,DigitalTwinImpl.class,"resolutionCycles",cycles);
       when(f.twin.getResolutionCycles()).thenReturn(cycles);
       doCallRealMethod().when(f.twin).transaction(any(),any(),any(Object[].class));
       f.scope.setDigitalTwin(f.twin);when(f.twin.getKnowledgeGraph()).thenReturn(f.graph);
       var focus=mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
       var observer=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();observer.setId(91);
       var observable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
       when(observable.is(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT)).thenReturn(true);observer.setObservable(observable);
       var query=mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class,RETURNS_SELF);
       when(query.run(any())).thenReturn(java.util.List.of(observer));
       doReturn(query).when(f.graph).query(eq(org.integratedmodelling.klab.api.knowledge.observation.Observation.class),any());
       var neutral=f.scope.withResolutionCycleBinding(null).within(focus).withObserver(observer);
       var root=neutral.executing(Activity.of(Activity.Type.SUBMISSION));
       assertNotNull(root.getResolutionCycleBinding());assertNotNull(root.getResolutionCycle());
       assertSame(focus,root.getContextObservation());assertSame(observer,root.getObserver());assertEquals(91,root.getObserverId());
       assertTrue(root.getServices(org.integratedmodelling.klab.api.services.Reasoner.class).isEmpty());
       assertNull(neutral.getResolutionCycleBinding());
       var child=root.executing(Activity.of(Activity.Type.RESOLUTION));
       assertSame(root.getCurrentTransaction(),child.getCurrentTransaction().getParent());
       assertSame(root.getResolutionCycleBinding(),child.getResolutionCycleBinding());
       assertSame(root.getResolutionCycle(),child.getResolutionCycle());assertSame(focus,child.getContextObservation());assertSame(observer,child.getObserver());
       var waiting=cycles.admit(neutral,false);assertFalse(waiting.isDone());
       // Calling public transaction explicitly creates a separate native root, never a child.
       var explicit=f.twin.transaction(Activity.of(Activity.Type.RESOLUTION),root,
           org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET);
       assertNull(explicit.getParent());explicit.fail(new IllegalStateException("separate public root"));
       assertFalse(root.getCurrentTransaction().isFailed());assertFalse(waiting.isDone());
       if(abortChild)child.fail(new IllegalStateException("abort shared root"));
       else {assertEquals(0,child.commit());assertFalse(waiting.isDone());assertTrue(root.commit()>0);}
       try(var next=waiting.get(5,TimeUnit.SECONDS)) {assertNotEquals(root.getResolutionCycleBinding().cycleId(),next.scope().getResolutionCycleBinding().cycleId());}
       cycles.awaitQuiescence(5,TimeUnit.SECONDS);
     }
   }
 }
 @Test void queuedAdvanceCapturesSemanticNeedAfterFirstRegistrationCommits() throws Exception {
   try(var f=new ConsequenceEngineTest.Fixture()) {
     var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(mock(Settings.class));
     var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
     var status=mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(reasoner.status()).thenReturn(status);
     when(reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(reasoner);
     try(var cycles=new ResolutionCycles(runtime,"context")) {
       ConsequenceEngineTest.field(f.twin,DigitalTwinImpl.class,"resolutionCycles",cycles);
       when(f.twin.getResolutionCycles()).thenReturn(cycles);
       doCallRealMethod().when(f.twin).transaction(any(),any(),any(Object[].class));
       f.scope.setDigitalTwin(f.twin);when(f.twin.getKnowledgeGraph()).thenReturn(f.graph);
       var neutral=f.scope.withResolutionCycleBinding(null);neutral.addService(reasoner);
       try(var scheduler=new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl(neutral,f.twin)) {
         when(f.twin.getScheduler()).thenReturn(scheduler);
         when(reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenAnswer(call->{
           assertEquals(1,scheduler.getOccurrenceRegistrations().size(),"Admission sampled before preceding registration");
           return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult(
               org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult.Status.BOUND,
               new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding("semantic-cycle-v1","reasoner","session",7,"fingerprint","lease"),null);
         });
         var first=cycles.admit(neutral,false).get(5,TimeUnit.SECONDS);
         var transaction=first.scope().executing(Activity.of(Activity.Type.SUBMISSION)).getCurrentTransaction();
         var started=new java.util.concurrent.CountDownLatch(1);
         var pending=java.util.concurrent.CompletableFuture.supplyAsync(()->{started.countDown();return scheduler.advanceTo(0);});
         assertTrue(started.await(5,TimeUnit.SECONDS));
         var queued=ResolutionCycles.class.getDeclaredField("waiting");queued.setAccessible(true);
         var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean enqueued=false;
         while(System.nanoTime()<deadline){synchronized(cycles){enqueued=!((java.util.Set<?>)queued.get(cycles)).isEmpty();}
           if(enqueued)break;Thread.sleep(1);}
         assertTrue(enqueued,"Advance must already be queued before the first process registration");assertFalse(pending.isDone());
         var process=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();process.setId(77);
         var geometry=org.integratedmodelling.klab.api.geometry.Geometry.create("T1(365){ttype=GRID,tstart=1388534400000,tend=1420070400000,tscope=1,tunit=DAY,tgrid=86400000}");
         process.setGeometry(geometry);
         var declaration=new org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule(1,"","",1,
             org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution.Type.MONTH,true,
             org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule.Source.MODEL);
         var registration=new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.OccurrenceRegistration(
             1,"registration","plan",org.integratedmodelling.klab.api.services.runtime.Actuator.ExecutionRole.PROCESS,0,
             java.util.List.of(new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.OccurrenceRegistration.Schedule(
                 0,declaration,declaration.bind(org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(geometry).getTime()))),"{}");
         process.getMetadata().put(org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.OccurrenceRegistration.METADATA_KEY,
             org.integratedmodelling.klab.utilities.Utils.Json.asString(registration));
         var activate=scheduler.getClass().getDeclaredMethod("activateOccurrence",org.integratedmodelling.klab.api.knowledge.observation.Observation.class);activate.setAccessible(true);
         transaction.afterCommit(()->{try{activate.invoke(scheduler,process);}catch(Exception failure){throw new IllegalStateException(failure);}});
         assertTrue(transaction.commit()>0);first.close();assertTrue(pending.get(5,TimeUnit.SECONDS));
         cycles.awaitQuiescence(5,TimeUnit.SECONDS);
         verify(reasoner).beginSemanticCycleResult(anyString(),anyString(),anyString(),any());
         verify(reasoner).releaseSemanticCycle(any(),anyString(),anyString(),anyString(),any());
       }
     }
   }
 }

 @Test void reentrantFailureRetainsCycleUntilNativeCommitCleanupHasFinished() throws Exception {
   try(var f=new ConsequenceEngineTest.Fixture()) {
     var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(mock(Settings.class));
     try(var cycles=new ResolutionCycles(runtime,"context")) {
       ConsequenceEngineTest.field(f.twin,DigitalTwinImpl.class,"resolutionCycles",cycles);
       when(f.twin.getResolutionCycles()).thenReturn(cycles);
       doCallRealMethod().when(f.twin).transaction(any(),any(),any(Object[].class));
       var neutral=f.scope.withResolutionCycleBinding(null);
       var transaction=f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),neutral,
           org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET);
       var execution=((org.integratedmodelling.klab.runtime.lifecycle.CycleScopedTransaction)transaction).executionScope();
       var queued=cycles.admit(neutral,false);var admitted=new java.util.concurrent.CountDownLatch(1);
       queued.whenComplete((value,failure)->admitted.countDown());
       var cleaning=new java.util.concurrent.CountDownLatch(1);var finishCleanup=new java.util.concurrent.CountDownLatch(1);
       doAnswer(call->{cleaning.countDown();assertTrue(finishCleanup.await(5,TimeUnit.SECONDS));return null;}).when(f.storage).close();
       transaction.beforeCommit(()->transaction.fail(new IllegalStateException("reentrant failure")));
       var committing=java.util.concurrent.CompletableFuture.supplyAsync(transaction::commit);
       try {
         assertTrue(cleaning.await(5,TimeUnit.SECONDS));
         assertSame(execution.getResolutionCycle(),cycles.require(execution).getResolutionCycle(),
             "Committing attempt must retain its server-owned cycle throughout native cleanup");
         assertFalse(admitted.await(100,TimeUnit.MILLISECONDS),"Queued root entered before native cleanup completed");
       } finally {finishCleanup.countDown();}
       assertEquals(-1,committing.get(5,TimeUnit.SECONDS));
       try(var next=queued.get(5,TimeUnit.SECONDS)) {
         assertNotEquals(execution.getResolutionCycleBinding().cycleId(),next.scope().getResolutionCycleBinding().cycleId());
       }
       cycles.awaitQuiescence(5,TimeUnit.SECONDS);verify(f.storage,atLeastOnce()).fail(any(Exception.class));
     }
   }
 }

 @Test void noOpFailureCallbacksCannotReleaseAnotherOperationCleanupOwnership() throws Exception {
   for(boolean committed:new boolean[]{true,false}) try(var f=new ConsequenceEngineTest.Fixture()) {
     var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(mock(Settings.class));
     try(var cycles=new ResolutionCycles(runtime,"context")) {
       ConsequenceEngineTest.field(f.twin,DigitalTwinImpl.class,"resolutionCycles",cycles);
       when(f.twin.getResolutionCycles()).thenReturn(cycles);
       doCallRealMethod().when(f.twin).transaction(any(),any(),any(Object[].class));
       var neutral=f.scope.withResolutionCycleBinding(null);
       var transaction=f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),neutral,
           org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET);
       var execution=((org.integratedmodelling.klab.runtime.lifecycle.CycleScopedTransaction)transaction).executionScope();
       var queued=cycles.admit(neutral,false);var callbackFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
       Runnable callback=()->{
         try {
           transaction.fail(new IllegalStateException("reentrant no-op"));
           assertSame(execution.getResolutionCycle(),cycles.require(execution).getResolutionCycle());
           assertFalse(queued.isDone(),"Reentrant no-op cannot release the enclosing cleanup owner");
         } catch(Throwable failure) {callbackFailure.set(failure);}
       };
       if(committed){transaction.afterCommit(callback);assertTrue(transaction.commit()>0);}
       else{transaction.afterRollback(callback);transaction.fail(new IllegalStateException("outer failure"));}
       assertNull(callbackFailure.get(),()->"Cleanup callback lost ownership: "+callbackFailure.get());
       try(var next=queued.get(5,TimeUnit.SECONDS)) {assertNotEquals(execution.getResolutionCycleBinding().cycleId(),next.scope().getResolutionCycleBinding().cycleId());}
       cycles.awaitQuiescence(5,TimeUnit.SECONDS);
     }
   }
 }

}
