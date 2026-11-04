package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.junit.jupiter.api.*;
class GuardedConfigurationPublisherTest {
 @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 ConfigurationTransactionTest f;Scheduler scheduler;GuardedConfigurationPublisher.Admission admission;GuardedConfigurationPublisher publisher;
 @BeforeEach void setup()throws Exception {
  f=new ConfigurationTransactionTest();f.setup();scheduler=mock(Scheduler.class);admission=mock(GuardedConfigurationPublisher.Admission.class);
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class));
  when(f.f.scope.forConsequence(any())).thenReturn(f.f.scope);when(f.f.scope.getDigitalTwin()).thenReturn(f.f.twin);
  when(f.f.twin.getScheduler()).thenReturn(scheduler);
  when(f.f.twin.transaction(any(Activity.class),eq(f.f.scope),eq(RuntimeAsset.PROVENANCE_ASSET))).thenReturn(f.f.root);
  when(f.f.scope.executing(any(Activity.class))).thenReturn(f.f.scope);
  when(f.f.scope.getCurrentTransaction()).thenReturn(f.f.root);
  when(f.f.scope.commit()).thenAnswer(call->f.f.root.commit());
  doAnswer(call->{f.f.root.fail(call.getArgument(0,Throwable.class));return null;}).when(f.f.scope).fail(any(Throwable.class));
  when(scheduler.recomputeConfiguration(any(),any(),anyLong(),anyLong(),eq(f.f.scope))).thenReturn(Scheduler.ConfigurationRecomputeOutcome.RESOLVED);
 }
 @Test void explanationRunsInTheSelectedDetectionTransactionRatherThanTheUnexecutingCauseScope()throws Exception {
  var entered=new java.util.concurrent.atomic.AtomicBoolean();
  when(f.f.scope.getCurrentTransaction()).thenAnswer(call->entered.get()?f.f.root:null);
  when(f.f.scope.executing(any(Activity.class))).thenAnswer(call->{
   assertEquals(Activity.Type.DETECTION,call.getArgument(0,Activity.class).getType());entered.set(true);return f.f.scope;
  });
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class),
   (observation,execution)->{
    assertTrue(entered.get());assertSame(f.f.root,execution.getCurrentTransaction());
    return new GuardedConfigurationPublisher.Prepared(GuardedConfigurationPublisher.Preparation.NO_MODEL,observation.getGeometry());
   });
  assertEquals(Consequence.Outcome.APPLIED,publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope).outcome());
  verify(f.f.scope).executing(any(Activity.class));
 }
 @Test void actualRecomputeAndNativeRevisionAreOwnedByTheSameRootTransaction()throws Exception {
  var result=publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope);assertEquals(Consequence.Outcome.APPLIED,result.outcome());assertEquals(List.of(41L),result.observations());
  verify(scheduler).recomputeConfiguration(any(),any(),eq(10L),eq(2L),eq(f.f.scope));verify(admission,times(2)).admit(f.update,f.f.scope);
  verify(f.f.scope).commit();verify(f.f.scope,never()).fail(any(Throwable.class));
  verify(f.f.storage).updateConfigurations(argThat(rows->rows.size()==1&&rows.getFirst().participants().equals(List.of(43L))&&rows.getFirst().revision()==2));
 }
 @Test void cancelledNestedScopeCannotOpenPublicationTransaction() {
  when(f.f.scope.isInterrupted()).thenReturn(true);
  assertThrows(InterruptedException.class,()->publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope));
  verify(f.f.scope,never()).executing(any(Activity.class));
  verifyNoInteractions(admission,scheduler);
 }
 @Test void failedOrUnavailableExplanationRollsBackTheRevision()throws Exception {
  for(var outcome:List.of(Scheduler.ConfigurationRecomputeOutcome.FAILED,Scheduler.ConfigurationRecomputeOutcome.DEFERRED)) {
   setup();when(scheduler.recomputeConfiguration(any(),any(),anyLong(),anyLong(),any())).thenReturn(outcome);
   var result=publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope);
   assertEquals(outcome==Scheduler.ConfigurationRecomputeOutcome.FAILED?Consequence.Outcome.FAILED:Consequence.Outcome.DEFERRED,result.outcome());
   verify(f.f.storage,never()).updateConfigurations(anyList());assertFalse(f.configuration.getMetadata().containsKey("klab.configuration.current"));
  }
 }
 @Test void sourceAuthorityIsReadmittedOutsideGraphMonitorBeforeNativePublication()throws Exception {
  var field=DigitalTwinImpl.TransactionImpl.class.getDeclaredField("graph");field.setAccessible(true);var graph=field.get(f.f.root);
  var count=new java.util.concurrent.atomic.AtomicInteger();doAnswer(call->{assertFalse(Thread.holdsLock(graph));
   if(count.incrementAndGet()==2)throw new IllegalStateException("source provider changed");return null;}).when(admission).admit(any(),any());
  assertThrows(IllegalStateException.class,()->publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope));
  verify(f.f.storage,never()).updateConfigurations(anyList());assertFalse(f.configuration.getMetadata().containsKey("klab.configuration.current"));
 }

 @Test void authenticatedNoModelPreparationPublishesWithoutInventingSchedulerExecution()throws Exception {
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class),
    (observation,execution)->new GuardedConfigurationPublisher.Prepared(GuardedConfigurationPublisher.Preparation.NO_MODEL,observation.getGeometry()));
  var result=publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope);
  assertEquals(Consequence.Outcome.APPLIED,result.outcome());verifyNoInteractions(scheduler);verify(f.f.storage).updateConfigurations(anyList());
 }
 @Test void unavailablePreparationRollsBackBeforeSchedulerOrNativePublication()throws Exception {
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class),
    (observation,execution)->new GuardedConfigurationPublisher.Prepared(GuardedConfigurationPublisher.Preparation.DEFERRED,null));
  assertEquals(Consequence.Outcome.DEFERRED,publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope).outcome());
  verifyNoInteractions(scheduler);verify(f.f.storage,never()).updateConfigurations(anyList());
  verify(f.f.scope,times(1)).fail(any(Throwable.class));verify(f.f.scope,never()).commit();
 }
 @Test void failedFailureNotificationDoesNotRepeatTerminalWorkOrHideOriginalFailure()throws Exception {
  var failure=new IllegalStateException("explanatory computation failed");var notification=new IllegalStateException("activity delivery failed");
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class),(observation,execution)->{throw failure;});
  doAnswer(call->{f.f.root.fail(call.getArgument(0,Throwable.class));throw notification;}).when(f.f.scope).fail(any(Throwable.class));
  assertSame(failure,assertThrows(IllegalStateException.class,()->publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope)));
  assertEquals(List.of(notification),List.of(failure.getSuppressed()));verify(f.f.scope,times(1)).fail(same(failure));verify(f.f.scope,never()).commit();verify(f.f.storage,never()).updateConfigurations(anyList());
 }
 @Test void throwingCommitDoesNotRepeatScopeCompletionAndNativeCleanupRespectsDurablePublication()throws Exception {
  for(boolean durable:List.of(false,true)) {
   setup();var failure=new IllegalStateException("commit boundary failed");
   doAnswer(call->{if(durable)assertTrue(f.f.root.commit()>0);throw failure;}).when(f.f.scope).commit();
   assertSame(failure,assertThrows(IllegalStateException.class,()->publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope)));
   verify(f.f.scope,times(1)).commit();verify(f.f.scope,never()).fail(any(Throwable.class));
   if(durable){assertFalse(f.f.root.isFailed());verify(f.f.storage).updateConfigurations(anyList());}
   else{assertTrue(f.f.root.isFailed());verify(f.f.storage,never()).updateConfigurations(anyList());}
  }
 }

 @Test void completionReceivesExactPreparationOutsideMonitorAfterComputationBeforePublication()throws Exception {
  var receipt=new Object();var completed=new java.util.concurrent.atomic.AtomicBoolean();
  when(scheduler.recomputeConfiguration(any(),any(),eq(10L),eq(2L),eq(f.f.scope),same(receipt))).thenReturn(Scheduler.ConfigurationRecomputeOutcome.RESOLVED);
  var field=DigitalTwinImpl.TransactionImpl.class.getDeclaredField("graph");field.setAccessible(true);var graph=field.get(f.f.root);
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class),
    (observation,execution)->new GuardedConfigurationPublisher.Prepared(GuardedConfigurationPublisher.Preparation.READY,observation.getGeometry(),receipt),
    (transaction,observations,prepared,updates,births,execution)->{
     assertSame(f.f.root,transaction);assertFalse(Thread.holdsLock(graph));assertSame(receipt,prepared.getFirst().receipt());assertEquals(List.of(f.update),updates);
     verify(scheduler).recomputeConfiguration(any(),any(),eq(10L),eq(2L),eq(f.f.scope),same(receipt));verify(f.f.storage,never()).updateConfigurations(anyList());completed.set(true);
    });
  assertEquals(Consequence.Outcome.APPLIED,publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope).outcome());assertTrue(completed.get());
 }
 @Test void failedScientificCompletionCannotPublishDetectedConfigurationRevision()throws Exception {
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class),
    (observation,execution)->new GuardedConfigurationPublisher.Prepared(GuardedConfigurationPublisher.Preparation.NO_MODEL,observation.getGeometry(),new Object()),
    (transaction,observations,prepared,updates,births,execution)->{throw new SecurityException("No authentic scientific accounting");});
  assertThrows(SecurityException.class,()->publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope));verify(f.f.storage,never()).updateConfigurations(anyList());verifyNoInteractions(scheduler);
 }
 @Test void unknownPreparedSchedulerAnswerCannotMeanSuccessfulComputation()throws Exception {
  var receipt=new Object();var completion=mock(GuardedConfigurationPublisher.Completion.class);
  publisher=new GuardedConfigurationPublisher(admission,mock(GuardedConfigurationPublisher.BirthAdmission.class),
    (observation,execution)->new GuardedConfigurationPublisher.Prepared(GuardedConfigurationPublisher.Preparation.READY,observation.getGeometry(),receipt),completion);
  assertEquals(Consequence.Outcome.DEFERRED,publisher.publish(f.update,f.f.scope.getConsequenceCause(),f.f.scope).outcome());
  verifyNoInteractions(completion);verify(f.f.storage,never()).updateConfigurations(anyList());
 }

}
