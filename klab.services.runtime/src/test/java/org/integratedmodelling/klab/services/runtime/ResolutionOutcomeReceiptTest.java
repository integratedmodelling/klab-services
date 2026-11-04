package org.integratedmodelling.klab.services.runtime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
class ResolutionOutcomeReceiptTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  @Test void actualAsyncSubmissionDistinguishesTypedEmptyOutcomes(){
    for(var expected:new Dataflow.ResolutionOutcome[]{Dataflow.ResolutionOutcome.NO_MODEL,
        Dataflow.ResolutionOutcome.DEFERRED,Dataflow.ResolutionOutcome.FAILED}){
      var f=new SubmissionCancellationTest.Fixture();
      when(f.dataflow.isEmpty()).thenReturn(true);when(f.dataflow.getResolutionOutcome()).thenReturn(expected);
      var task=f.submit();assertFalse(task.isDone());f.resolution.complete(f.dataflow);
      var result=task.join();assertTrue(result.isEmpty());assertEquals(expected == Dataflow.ResolutionOutcome.DEFERRED ? Dataflow.ResolutionOutcome.FAILED : expected,ResolutionOutcomeReceipt.read(result).orElseThrow());
      verify(f.scheduler,never()).submit(any(),any());
    }
    var deferred=ResolutionOutcomeReceipt.terminal(Observation.empty(),Dataflow.ResolutionOutcome.DEFERRED);
    assertEquals(Dataflow.ResolutionOutcome.DEFERRED,ResolutionOutcomeReceipt.read(Observation.forTransport(deferred)).orElseThrow());
    assertTrue(ResolutionOutcomeReceipt.read(Observation.empty()).isEmpty());
  }
  @Test void nonemptyContextualizationOrCommitFailureIsFailedAndSuccessKeepsIdentity(){
    var f=new SubmissionCancellationTest.Fixture();when(f.scheduler.submit(any(),any())).thenReturn(false);
    var task=f.submit();f.resolution.complete(f.dataflow);
    assertEquals(Dataflow.ResolutionOutcome.FAILED,ResolutionOutcomeReceipt.read(task.join()).orElseThrow());
    var success=new SubmissionCancellationTest.Fixture();var valid=success.submit();success.resolution.complete(success.dataflow);
    assertFalse(valid.join().isEmpty());assertTrue(ResolutionOutcomeReceipt.read(valid.join()).isEmpty());
  }
}
