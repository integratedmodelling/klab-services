package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
class SchedulerCurrentValidityTest {
 @BeforeAll static void setup(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 @Test void authoritativeDeactivationSuppressesCachedDependencyAndRestorationDespiteStaleLivePayload() {
  var scope=mock(ServiceContextScope.class);var twin=mock(DigitalTwinImpl.class);var graph=mock(KnowledgeGraph.class);
  when(twin.getKnowledgeGraph()).thenReturn(graph);when(scope.getDigitalTwin()).thenReturn(twin);
  when(graph.getScheduledObservations(scope)).thenReturn(List.of());
  var target=new ObservationImpl();target.setId(42);target.setGeometry(Geometry.create("1"));
  var current=new AtomicBoolean(true);when(graph.isObservationCurrent(same(target),same(scope))).thenAnswer(c->current.get());
  var scheduler=new SchedulerImpl(scope,twin);var calls=new AtomicInteger();
  scheduler.registerExecutor(target,(g,e,s)->{calls.incrementAndGet();return true;});
  try {
   current.set(false);
   assertFalse(scheduler.submit(target,scope));
   assertFalse(scheduler.executeDependency(target,target.getGeometry(),Scheduler.Event.initialization(),scope));
   assertNull(scheduler.restoreExecutor(target,scope));
   assertEquals(0,calls.get());verify(graph,atLeastOnce()).isObservationCurrent(target,scope);
  } finally {scheduler.close();}
 }
 @Test void forgedMembershipMarkerCannotStartOrdinaryCausalPropagation() {
  var scope=mock(ServiceContextScope.class);var twin=mock(DigitalTwinImpl.class);var graph=mock(KnowledgeGraph.class);
  when(twin.getKnowledgeGraph()).thenReturn(graph);when(graph.getScheduledObservations(scope)).thenReturn(List.of());
  var scheduler=new SchedulerImpl(scope,twin);
  try {assertEquals(Scheduler.MembershipPropagationOutcome.FAILED,
    scheduler.membershipChanged(new DigitalTwin.CommittedMembershipChange(){},scope));
    verify(twin,never()).getResolutionCycles();
  } finally {scheduler.close();}
 }
 @Test void alreadyQueuedInactiveOccurrenceIsSuppressedWithoutCommittingOrAdvancingModeledTime() throws Exception {
  var scope=mock(ServiceContextScope.class);var twin=mock(DigitalTwinImpl.class);var graph=mock(KnowledgeGraph.class);
  when(twin.getKnowledgeGraph()).thenReturn(graph);when(graph.getScheduledObservations(scope)).thenReturn(List.of());
  var target=new ObservationImpl();target.setId(42);target.setGeometry(Geometry.create("1"));
  when(graph.isObservationCurrent(target,scope)).thenReturn(false);
  var scheduler=new SchedulerImpl(scope,twin);var calls=new AtomicInteger();scheduler.registerExecutor(target,(g,e,s)->{calls.incrementAndGet();return true;});
  try {
   var dispatch=SchedulerImpl.class.getDeclaredMethod("dispatch",Observation.class,OccurrenceRegistration.class,TransitionEvent.class,boolean.class);
   dispatch.setAccessible(true);
   assertEquals(true,dispatch.invoke(scheduler,target,null,new TransitionEvent("oldqueued",100,200,null),true));
   assertEquals(0,calls.get());assertTrue(scheduler.currentModeledInstant().isEmpty());
   verify(scope,never()).executingFresh(any(),any(Object[].class));
  } finally {scheduler.close();}
 }

 @Test void realStagedTransactionAdmitsOnlyItsOwnedNegativeBirthAndOwnedReadView() {
  var scope=mock(ServiceContextScope.class);var twin=mock(DigitalTwinImpl.class);
  var concept=new org.integratedmodelling.common.knowledge.ConceptImpl();concept.setUrn("test:Subject");
  concept.setType(java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT));
  var born=new ObservationImpl();born.setId(-7);born.setObservable(org.integratedmodelling.common.knowledge.ObservableImpl.promote(concept,null));
  born.setGeometry(Geometry.create("1"));
  var transaction=twin.new TransactionImpl(org.integratedmodelling.klab.api.provenance.Activity.of(
      org.integratedmodelling.klab.api.provenance.Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET,born);
  when(scope.getCurrentTransaction()).thenReturn(transaction);
  try {
   assertEquals(java.util.Optional.of(true),transaction.currentStatusFor(born));
   var unowned=new ObservationImpl();unowned.setId(-7);assertTrue(transaction.currentStatusFor(unowned).isEmpty());
   var foreignScope=mock(ServiceContextScope.class);
   var foreign=twin.new TransactionImpl(org.integratedmodelling.klab.api.provenance.Activity.of(
       org.integratedmodelling.klab.api.provenance.Activity.Type.SUBMISSION),foreignScope,RuntimeAsset.PROVENANCE_ASSET,born);
   try {var other=foreign.readObservation(born);assertFalse(transaction.ownsObservationReadView(other));
     assertTrue(transaction.currentStatusFor(other).isEmpty(),"Another root's readview grants no local staged ownership");
   } finally {foreign.fail(new java.util.concurrent.CancellationException("foreign fixture cleanup"));}
   var read=transaction.readObservation(born);assertTrue(transaction.ownsObservationReadView(read));
   assertEquals(java.util.Optional.of(true),transaction.currentStatusFor(read),"A genuine owned transient read view retains staged ownership");
  } finally {transaction.fail(new java.util.concurrent.CancellationException("fixture cleanup"));}
 }

}
