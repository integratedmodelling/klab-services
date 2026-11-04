package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.configuration.*;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuntimeResolutionCycleTest {
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 @Test void publicAsyncSubmissionWaitsForQueuedRecursiveEngineWorkThenStartsNewFrozenCycle() throws Exception {
   var f=new SubmissionCancellationTest.Fixture();
   var disabled=new AtomicBoolean();var generation=new AtomicLong(1);
   var cycles=cycles(f,disabled,generation);
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var recursive=new CountDownLatch(1);
   var calls=new AtomicInteger();
   try(var engine=new ConsequenceEngine(f.scope,"cancellation-test")) {
     for(var kind:Consequence.Kind.values())engine.register(kind,(cause,changes,scope)->{
       if(kind==Consequence.Kind.ROLE_CONFERRAL){
         calls.incrementAndGet();
         assertEquals(1,scope.getResolutionCycleBinding().semanticBinding().knowledgeRevision());
         assertTrue(scope.getResolutionCycleBinding().consequencesEnabled());
         if(changes.getId()==1){
           entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));
           try(var child=cycles.admit(scope,true).get(5,TimeUnit.SECONDS)){
             var commit=new CommitImpl();commit.setId(2);
             engine.committed(commit,child.scope(),101,0);
           }
         } else recursive.countDown();
       }
       return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION,"checked",List.of());
     });
     var commitCount=new AtomicLong();
     when(f.rootTransaction.commit()).thenAnswer(c->{
       var commit=new CommitImpl();commit.setId(commitCount.addAndGet(2)-1);
       engine.committed(commit,f.resolutionScope.get(),100,0);return commit.getId();
     });
     var first=f.submit();
     var initialDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
     while(f.resolutionScope.get()==null && System.nanoTime()<initialDeadline) Thread.sleep(1);
     assertNotNull(f.resolutionScope.get());
     var resolution=f.resolution;resolution.complete(f.dataflow);
     assertFalse(first.get(5,TimeUnit.SECONDS).isEmpty());assertTrue(entered.await(5,TimeUnit.SECONDS));
     disabled.set(true);generation.set(2);f.resolution=new CompletableFuture<>();
     var second=f.submit();assertFalse(second.isDone());verify(f.resolver).resolve(any(),any());
     release.countDown();assertTrue(recursive.await(5,TimeUnit.SECONDS));
     var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
     while(f.resolutionScope.get().getResolutionCycleBinding().semanticBinding().knowledgeRevision()!=2
         && System.nanoTime()<deadline) Thread.sleep(1);
     assertEquals(2,f.resolutionScope.get().getResolutionCycleBinding().semanticBinding().knowledgeRevision());
     assertFalse(f.resolutionScope.get().getResolutionCycleBinding().consequencesEnabled());
     f.resolution.complete(f.dataflow);assertFalse(second.get(5,TimeUnit.SECONDS).isEmpty());
     cycles.awaitQuiescence(5,TimeUnit.SECONDS);assertEquals(2,calls.get());
     verify(f.reasoner,times(2)).releaseSemanticCycle(any(),anyString(),anyString(),anyString(),any());
   } finally {release.countDown();cycles.close();cycles.awaitQuiescence(5,TimeUnit.SECONDS);}
 }
 private static ResolutionCycles cycles(SubmissionCancellationTest.Fixture f,AtomicBoolean disabled,AtomicLong generation) {
   f.scope.setId("cancellation-test");when(f.scope.getUser().getUsername()).thenReturn("tester");
   var settings=mock(Settings.class);
   when(f.runtime.settings()).thenReturn(settings);when(f.runtime.serviceId()).thenReturn("runtime");
   when(settings.get(Setting.DISABLE_CONSEQUENCE_ENGINE,Boolean.class)).thenAnswer(c->disabled.get());
   when(f.reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenAnswer(c->
       new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult(
           org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult.Status.BOUND,
           new SemanticCycleBinding("semantic-cycle-v1","reasoner","session",generation.get(),"fingerprint","lease-"+c.getArgument(2)),null));
   when(f.reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(f.reasoner);
   var cycles=new ResolutionCycles(f.runtime,"cancellation-test");when(f.twin.getResolutionCycles()).thenReturn(cycles);
   doCallRealMethod().when(f.runtime).withinResolutionCycle(any(),any(),any());
   return cycles;
 }
 @Test void cancelledPublicSubscriberKeepsCycleUntilUnderlyingResolverDrains() throws Exception {
   var f=new SubmissionCancellationTest.Fixture();
   try(var cycles=cycles(f,new AtomicBoolean(),new AtomicLong(1))) {
     var first=f.submit();var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
     while(f.resolutionScope.get()==null && System.nanoTime()<deadline) Thread.sleep(1);
     assertNotNull(f.resolutionScope.get());var firstScope=f.resolutionScope.get();var firstResolution=f.resolution;
     assertTrue(first.cancel(false));assertTrue(firstScope.isInterrupted());
     f.resolution=new CompletableFuture<>();var second=f.submit();assertFalse(second.isDone());
     assertSame(firstScope,f.resolutionScope.get());
     firstResolution.complete(f.dataflow);
     deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
     while(f.resolutionScope.get()==firstScope && System.nanoTime()<deadline) Thread.sleep(1);
     assertNotSame(firstScope,f.resolutionScope.get());assertFalse(f.resolutionScope.get().isInterrupted());
     verify(f.rootTransaction,never()).commit();f.resolution.complete(f.dataflow);
     assertFalse(second.get(5,TimeUnit.SECONDS).isEmpty());cycles.awaitQuiescence(5,TimeUnit.SECONDS);
   }
 }

 private static org.integratedmodelling.klab.api.knowledge.observation.Observation subject() {
   var observable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
   var concept=mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
   when(observable.getSemantics()).thenReturn(concept);when(observable.getUrn()).thenReturn("test:subject");
   when(concept.getType()).thenReturn(java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT));
   var observation=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
   observation.setObservable(observable);observation.setUrn("same-subject");
   observation.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));return observation;
 }
 @Test void admittedSameCycleCoalescedSubscriberDetachesButParentCancellationStillReachesDriver() throws Exception {
   for(boolean cancelParent:new boolean[]{false,true}) {
     var f=new SubmissionCancellationTest.Fixture();
     try(var cycles=cycles(f,new AtomicBoolean(),new AtomicLong(1))) {
       var registry=RuntimeService.class.getDeclaredField("inFlightSubmissions");registry.setAccessible(true);
       registry.set(f.runtime,new ConcurrentHashMap<>());
       var base=mock(org.integratedmodelling.klab.api.knowledge.Concept.class);when(base.getUrn()).thenReturn("test:subject");
       when(f.reasoner.baseSubstantialType(any(),any())).thenReturn(base);
       var parent=cycles.admit(f.scope,true).get(5,TimeUnit.SECONDS);var parentCancelled=new AtomicBoolean();
       var source=parent.scope().withCancellation(parentCancelled::get);
       var first=f.runtime.submit(subject(),source);var second=f.runtime.submit(subject(),source);
       verify(f.resolver).resolve(any(),any());parent.close();
       assertTrue(first.cancel(false));assertFalse(f.resolutionScope.get().isInterrupted());
       assertFalse(parent.scope().getResolutionCycle().quiescence().toCompletableFuture().isDone());
       if(cancelParent){parentCancelled.set(true);assertTrue(f.resolutionScope.get().isInterrupted());}
       f.resolution.complete(f.dataflow);var result=second.get(5,TimeUnit.SECONDS);
       assertEquals(cancelParent,result.isEmpty());
       if(cancelParent)verify(f.rootTransaction,never()).commit();else verify(f.rootTransaction).commit();
       cycles.awaitQuiescence(5,TimeUnit.SECONDS);
       verify(f.reasoner).releaseSemanticCycle(any(),anyString(),anyString(),anyString(),any());
     }
   }
 }

}
