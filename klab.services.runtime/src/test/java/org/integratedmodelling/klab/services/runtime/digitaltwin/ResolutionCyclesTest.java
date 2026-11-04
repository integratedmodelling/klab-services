package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.configuration.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class ResolutionCyclesTest {
  static class Fixture implements AutoCloseable {
    final RuntimeService runtime=mock(RuntimeService.class);
    final Reasoner reasoner=mock(Reasoner.class);
    final Settings settings=mock(Settings.class);
    final AtomicBoolean disabled=new AtomicBoolean();
    final AtomicLong generation=new AtomicLong(1);
    final ServiceContextScope scope;
    final ResolutionCycles cycles;
    Fixture(){
      when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(settings);
      when(settings.get(Setting.DISABLE_CONSEQUENCE_ENGINE,Boolean.class)).thenAnswer(c->disabled.get());
      var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(user.getId()).thenReturn("tester");
      scope=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,runtime)),
          DigitalTwin.Configuration.builder().id("context").name("Twin").build(),user);scope.setId("context");
      var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(reasoner.status()).thenReturn(status);
      scope.addService(reasoner);
      when(reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenAnswer(c->
          new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult(
              org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult.Status.BOUND,
              new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",generation.get(),"fingerprint","lease-"+c.getArgument(2)),null));
      when(reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(reasoner);
      cycles=new ResolutionCycles(runtime,"context");
    }
    @Override public void close(){cycles.close();cycles.awaitQuiescence(5,TimeUnit.SECONDS);}
  }
  @Test void rootsWaitForRecursiveChainAndNextRootCapturesChangedSettingsAndSemantics() throws Exception {
    try(var f=new Fixture()){
      var first=f.cycles.admit(f.scope,true).get(5,TimeUnit.SECONDS);
      var queued=first.scope().getResolutionCycle().retain();
      f.disabled.set(true);f.generation.set(2);
      var next=f.cycles.admit(f.scope,true);first.close();assertFalse(next.isDone());
      var child=f.cycles.admit(first.scope(),true).get(5,TimeUnit.SECONDS);
      assertTrue(child.scope().getResolutionCycleBinding().consequencesEnabled());
      assertEquals(1,child.scope().getResolutionCycleBinding().semanticBinding().knowledgeRevision());
      queued.close();assertFalse(next.isDone());child.close();
      var second=next.get(5,TimeUnit.SECONDS);
      assertFalse(second.scope().getResolutionCycleBinding().consequencesEnabled());
      assertEquals(2,second.scope().getResolutionCycleBinding().semanticBinding().knowledgeRevision());
      second.close();f.cycles.awaitQuiescence(5,TimeUnit.SECONDS);
      verify(f.reasoner,times(2)).beginSemanticCycleResult(anyString(),anyString(),anyString(),any());
      verify(f.reasoner,times(2)).releaseSemanticCycle(any(),anyString(),anyString(),anyString(),any());
    }
  }
  @Test void queuedCancellationDoesNotOpenPinAndUnrelatedTwinDoesNotWait() throws Exception {
    try(var f=new Fixture();var other=new Fixture()){
      var first=f.cycles.admit(f.scope,true).get(5,TimeUnit.SECONDS);
      var cancelled=f.cycles.admit(f.scope,true);assertTrue(cancelled.cancel(false));
      var independent=other.cycles.admit(other.scope,false).get(5,TimeUnit.SECONDS);
      assertNull(independent.scope().getResolutionCycleBinding().semanticBinding());independent.close();
      first.close();f.cycles.awaitQuiescence(5,TimeUnit.SECONDS);
      verify(f.reasoner).beginSemanticCycleResult(anyString(),anyString(),anyString(),any());
      verifyNoInteractions(other.reasoner);
    }
  }
  @Test void subscriberCancellationCannotReleaseRunningOperationOwnership() throws Exception {
    try(var f=new Fixture()){
      var started=new CountDownLatch(1);var actual=new CompletableFuture<String>();
      var response=f.cycles.submit(f.scope,false,s->{started.countDown();return actual;});
      assertTrue(started.await(5,TimeUnit.SECONDS));response.cancel(false);
      var next=f.cycles.admit(f.scope,false);assertFalse(next.isDone());assertFalse(actual.isCancelled());
      actual.complete("done");var second=next.get(5,TimeUnit.SECONDS);second.close();
    }
  }
  @Test void expiredBindingCannotBecomeFreshRoot() throws Exception {
    try(var f=new Fixture()){
      var admission=f.cycles.admit(f.scope,false).get(5,TimeUnit.SECONDS);var stale=admission.scope();admission.close();
      f.cycles.awaitQuiescence(5,TimeUnit.SECONDS);
      assertThrows(ExecutionException.class,()->f.cycles.admit(stale,false).get(5,TimeUnit.SECONDS));
    }
  }
  @Test void unboundCycleNeverLateBindsAndDisabledChoiceChangesOnlyForNextRoot() throws Exception {
    try(var f=new Fixture()){
      f.disabled.set(true);
      var first=f.cycles.admit(f.scope,false).get(5,TimeUnit.SECONDS);
      f.disabled.set(false);f.generation.set(2);
      try(var child=f.cycles.admit(first.scope(),true).get(5,TimeUnit.SECONDS)){
        assertFalse(child.scope().getResolutionCycleBinding().consequencesEnabled());
        assertNull(child.scope().getResolutionCycleBinding().semanticBinding());
        assertTrue(child.scope().getServices(Reasoner.class).isEmpty());
      }
      first.close();
      try(var second=f.cycles.admit(f.scope,true).get(5,TimeUnit.SECONDS)){
        assertTrue(second.scope().getResolutionCycleBinding().consequencesEnabled());
        assertEquals(2,second.scope().getResolutionCycleBinding().semanticBinding().knowledgeRevision());
      }
      f.cycles.awaitQuiescence(5,TimeUnit.SECONDS);
      verify(f.reasoner).beginSemanticCycleResult(anyString(),anyString(),anyString(),any());
    }
  }

  @Test void synchronousCompletionCallbackCanSubmitAndWaitForNextRoot() throws Exception {
    try(var f=new Fixture()){
      var actual=new CompletableFuture<String>();var started=new CountDownLatch(1);
      var first=f.cycles.submit(f.scope,false,scope->{started.countDown();return actual;});
      assertTrue(started.await(5,TimeUnit.SECONDS));
      var callback=first.thenApply(value->f.cycles.submit(f.scope,false,
          scope->CompletableFuture.completedFuture(value+"-next")).join());
      var completion=CompletableFuture.runAsync(()->actual.complete("first"));
      assertEquals("first-next",callback.get(5,TimeUnit.SECONDS));completion.get(5,TimeUnit.SECONDS);
    }
  }
  @Test void leaseCleanupFailureCannotRetroactivelyFailCompletedNativeOperation() throws Exception {
    try(var f=new Fixture()){
      doThrow(new IllegalStateException("cleanup failure")).when(f.reasoner)
          .releaseSemanticCycle(any(),anyString(),anyString(),anyString(),any());
      assertEquals("committed",f.cycles.submit(f.scope,true,
          scope->CompletableFuture.completedFuture("committed")).get(5,TimeUnit.SECONDS));
      assertEquals("next",f.cycles.submit(f.scope,false,
          scope->CompletableFuture.completedFuture("next")).get(5,TimeUnit.SECONDS));
    }
  }

  @Test void explicitNoAcceptedViewIsFrozenAndItsNegativeAdmissionIsReleased() throws Exception {
    try(var f=new Fixture()){
      when(f.reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenReturn(
          new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult(
              org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBeginResult.Status.NO_ACCEPTED_VIEW,
              null,"No accepted semantic actor"));
      var first=f.cycles.admit(f.scope,true).get(5,TimeUnit.SECONDS);
      assertNull(first.scope().getResolutionCycleBinding().semanticBinding());
      assertTrue(first.scope().getServices(Reasoner.class).isEmpty());
      when(f.reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenThrow(
          new AssertionError("Old cycle tried to acquire a newer semantic view"));
      try(var child=f.cycles.admit(first.scope(),true).get(5,TimeUnit.SECONDS)) {
        assertNull(child.scope().getResolutionCycleBinding().semanticBinding());
      }
      first.close();f.cycles.awaitQuiescence(5,TimeUnit.SECONDS);
      verify(f.reasoner).releaseSemanticCycle(isNull(),eq("runtime"),eq("context"),anyString(),same(f.scope));
      verify(f.reasoner,never()).bindSemanticCycle(any(),anyString(),anyString(),anyString(),any());
    }
  }
  @Test void authorizationOrProtocolFailureCannotBecomeSemanticUnavailability() throws Exception {
    try(var f=new Fixture()){
      when(f.reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenThrow(
          new SecurityException("Unauthorized cycle owner"));
      var calls=new AtomicInteger();
      var denied=f.cycles.submit(f.scope,true,scope->{calls.incrementAndGet();return CompletableFuture.completedFuture("wrong");});
      var failure=assertThrows(ExecutionException.class,()->denied.get(5,TimeUnit.SECONDS));
      assertInstanceOf(SecurityException.class,failure.getCause());assertEquals(0,calls.get());
      assertEquals("native",f.cycles.submit(f.scope,false,scope->CompletableFuture.completedFuture("native")).get(5,TimeUnit.SECONDS));
    }
  }

}
