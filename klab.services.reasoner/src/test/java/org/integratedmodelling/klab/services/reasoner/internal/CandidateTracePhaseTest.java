package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CandidateTracePhaseTest {
  @Test void disabledSpansAreNullAndDoNotHashOrFormat() {
    String prior=System.getProperty(CandidateTrace.PROPERTY);
    try (var mocked=mockStatic(CandidateTrace.class,CALLS_REAL_METHODS)) {
      System.clearProperty(CandidateTrace.PROPERTY);
      assertNull(CandidateTrace.phase(CandidateTrace.Phase.FINAL_CLASSIFY,"private-key"));
      assertNull(CandidateTrace.phaseAt(CandidateTrace.Phase.PRIVATE_DISPOSE,"private-key",9));
      mocked.verify(()->CandidateTrace.digest(any()),never());
      mocked.verify(()->CandidateTrace.formatPhase(any(),any(),any(),anyLong(),anyLong()),never());
    } finally { restore(prior); }
  }

  @Test void phaseRecordsUseOpaqueIdentityAndMonotonicNonnegativeDuration() {
    var line=CandidateTrace.formatPhase(CandidateTrace.Event.PHASE_END,CandidateTrace.Phase.DOCUMENT_VALIDATE,
        "Bearer SECRET https://private.example/document",17,-2);
    assertTrue(line.contains("generation=17"));
    assertTrue(line.endsWith("phase=DOCUMENT_VALIDATE elapsedNanos=0"));
    assertFalse(line.contains("SECRET")); assertFalse(line.contains("https:"));
    assertTrue(line.matches(".* key=[a-f0-9]{64} .*"));
  }

  @Test void phaseExitCannotReplaceThePrimaryCancellation() {
    String prior=System.getProperty(CandidateTrace.PROPERTY);
    var original=new CancellationException("primary");
    try (var mocked=mockStatic(CandidateTrace.class,CALLS_REAL_METHODS)) {
      System.setProperty(CandidateTrace.PROPERTY,"true");
      mocked.when(()->CandidateTrace.formatPhase(eq(CandidateTrace.Event.PHASE_END),any(),any(),anyLong(),anyLong()))
          .thenThrow(new IllegalStateException("trace-only failure"));
      assertSame(original,assertThrows(CancellationException.class,()->{
        try (var phase=CandidateTrace.phase(CandidateTrace.Phase.OWL_INITIALIZE,null)) { throw original; }
      }));
    } finally { restore(prior); }
  }

  @Test void spanExitIsIdempotentAndRetainsExplicitGeneration() {
    String prior=System.getProperty(CandidateTrace.PROPERTY);
    try (var mocked=mockStatic(CandidateTrace.class,CALLS_REAL_METHODS)) {
      System.setProperty(CandidateTrace.PROPERTY,"true");
      var span=CandidateTrace.phaseAt(CandidateTrace.Phase.PRIVATE_DISPOSE,null,42);
      assertNotNull(span); span.close(); span.close();
      mocked.verify(()->CandidateTrace.formatPhase(eq(CandidateTrace.Event.PHASE_END),
          eq(CandidateTrace.Phase.PRIVATE_DISPOSE),any(),eq(42L),anyLong()),times(1));
    } finally { restore(prior); }
  }

  @Test void phaseCheckpointCallsTheOriginalOnceAndPreservesExceptionIdentity() {
    var count=new AtomicInteger(); var original=new CancellationException("primary");
    assertSame(original,assertThrows(CancellationException.class,()->CandidateTrace.checkpoint(
        ()->{count.incrementAndGet();throw original;},CandidateTrace.Phase.NAMED_DECLARATIONS)));
    assertEquals(1,count.get());
  }
  @Test void traceFailureCannotPreventEnvironmentOwnershipRegistration() throws Exception {
    String prior=System.getProperty(CandidateTrace.PROPERTY);
    var actor=mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class);
    var assets=java.nio.file.Files.createTempDirectory("trace-ownership-");
    try (var mocked=mockStatic(CandidateTrace.class,CALLS_REAL_METHODS)) {
      System.setProperty(CandidateTrace.PROPERTY,"true");
      mocked.when(()->CandidateTrace.digest(any())).thenThrow(new IllegalStateException("trace-only failure"));
      var environment=new SemanticEnvironment(actor,assets);
      environment.close();
      verify(actor,times(1)).disposeSemanticEnvironment();
      assertFalse(java.nio.file.Files.exists(assets));
    } finally { restore(prior); }
  }
  private void restore(String prior) {
    if(prior==null)System.clearProperty(CandidateTrace.PROPERTY);else System.setProperty(CandidateTrace.PROPERTY,prior);
  }
}
