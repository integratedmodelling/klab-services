package org.integratedmodelling.klab.services.runtime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
class ConsequenceAdmissionTest {
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 @Test void admissionBindsActualRootBeforeResolverAndFailureCannotCommit(){
   for(boolean rejected:new boolean[]{false,true}) {
     var f=new SubmissionCancellationTest.Fixture();var bound=new AtomicBoolean();
     var source=f.scope.withConsequenceSubmissionAdmission((tx,execution)->{
       assertSame(f.rootTransaction,tx);assertSame(tx,execution.getCurrentTransaction());
       verifyNoInteractions(f.resolver);bound.set(true);if(rejected)throw new IllegalStateException("source drift");
     });
     var observable=mock(Observable.class);var semantics=mock(Concept.class);
     when(observable.getSemantics()).thenReturn(semantics);when(observable.getUrn()).thenReturn("test:process");
     when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.PROCESS));
     var observation=new ObservationImpl();observation.setObservable(observable);observation.setGeometry(Geometry.create("1"));
     var task=f.runtime.submit(observation,source);assertTrue(bound.get());
     if(rejected){assertEquals(Dataflow.ResolutionOutcome.FAILED,ResolutionOutcomeReceipt.read(task.join()).orElseThrow());
       verify(f.resolver,never()).resolve(any(),any());verify(f.rootTransaction,never()).commit();}
     else{f.resolution.complete(f.dataflow);assertFalse(task.join().isEmpty());verify(f.rootTransaction).commit();}
     assertNull(source.forConsequence(null).getConsequenceSubmissionAdmission());
   }
 }
}
