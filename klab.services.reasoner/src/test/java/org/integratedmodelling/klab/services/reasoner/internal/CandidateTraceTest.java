package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CandidateTraceTest {
  @Test void rawValuesCannotCrossTheLogBoundary() {
    String secret="Bearer SECRET_TOKEN https://private.example/?token=SECRET_PARAMETER";
    var line=CandidateTrace.format(CandidateTrace.Event.PREFLIGHT_ALLOWED,7,8,2,
        secret,"person@example.org",CandidateTrace.Reason.NEW_KEY);
    assertFalse(line.contains("SECRET")); assertFalse(line.contains("Bearer"));
    assertFalse(line.contains("https:")); assertFalse(line.contains("person@"));
    assertTrue(line.contains("generation=7 current=8 epoch=2"));
    assertTrue(line.matches(".* key=[a-f0-9]{64} related=[a-f0-9]{64} reason=NEW_KEY"));
  }
  @Test void fieldDigestsAreCanonicalAndOpaque() {
    assertEquals(CandidateTrace.digest(Map.of("a",1,"b",2)),
        CandidateTrace.digest(Map.of("b",2,"a",1)));
    assertNotEquals(CandidateTrace.digest(Map.of("a",1)),CandidateTrace.digest(Map.of("a",2)));
    assertFalse(CandidateTrace.digest(Map.of("password","secret-value")).contains("secret"));
  }
  @Test void checkpointPreservesInvocationCountAndExceptionIdentity() {
    var count=new AtomicInteger(); var original=new CancellationException("private exception text");
    CandidatePublicationCoordinator.Cancellation check=()->{count.incrementAndGet();throw original;};
    assertSame(original,assertThrows(CancellationException.class,()->
        CandidateTrace.checkpoint(check,null,1,CandidateTrace.Reason.POST_COMPONENTS)));
    assertEquals(1,count.get());
  }
  @Test void checkpointDoesNotAddCancellationReads() {
    var count=new AtomicInteger();
    CandidateTrace.checkpoint(()->{count.incrementAndGet();return false;},null,1,CandidateTrace.Reason.POST_COMPONENTS);
    assertEquals(1,count.get());
  }
  @Test void boundedRecordSizeAndEveryLogicalStatusSupported() {
    for(var status:CandidatePublicationCoordinator.LogicalStatus.values())
      assertNotNull(CandidateTrace.Reason.valueOf(status.name()));
    var line=CandidateTrace.format(CandidateTrace.Event.BOOTSTRAP_FAILURE,Long.MAX_VALUE,
        Long.MAX_VALUE,Long.MAX_VALUE,"x".repeat(100000),null,CandidateTrace.Reason.FAILURE);
    assertTrue(line.length()<450);
  }
  @Test void disabledTraceDoesNotChangeProperty() throws Exception {
    String prior=System.getProperty(CandidateTrace.PROPERTY);
    try {
      System.clearProperty(CandidateTrace.PROPERTY);
      CandidateTrace.emit(CandidateTrace.Event.JOB_START,1,1,1,null,null,CandidateTrace.Reason.NONE);
      assertNull(System.getProperty(CandidateTrace.PROPERTY));
      assertFalse(CandidateTrace.enabled());
    } finally {
      if(prior==null)System.clearProperty(CandidateTrace.PROPERTY);else System.setProperty(CandidateTrace.PROPERTY,prior);
    }
  }
  @Test void disabledLifecycleHasNoJobContextOrWriter() {
    String prior=System.getProperty(CandidateTrace.PROPERTY);
    try {
      System.clearProperty(CandidateTrace.PROPERTY);
      CandidateTrace.job(42);
      CandidateTrace.stage(CandidateTrace.Event.JOB_START,null,0,CandidateTrace.Reason.NONE);
      assertEquals(-1,CandidateTrace.job());
      CandidateTrace.clearJob();
      // A different enabled test may already own a writer; this test never starts one.
      assertFalse(CandidateTrace.enabled());
    } finally {
      if(prior==null)System.clearProperty(CandidateTrace.PROPERTY);else System.setProperty(CandidateTrace.PROPERTY,prior);
    }
  }
}
