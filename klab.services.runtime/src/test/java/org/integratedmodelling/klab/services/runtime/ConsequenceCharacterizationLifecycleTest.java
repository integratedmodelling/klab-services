package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.*;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.*;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.*;

/** Actual retained-cycle registry, ServiceContextScope root/child lifecycle and Resolver call.
 * Native transaction and remote scientific execution are controlled; this is not live acceptance. */
class ConsequenceCharacterizationLifecycleTest {
  @BeforeAll static void configure(){SubmissionCancellationTest.configure();}
  static class Fixture implements AutoCloseable {
    final SubmissionCancellationTest.Fixture f=new SubmissionCancellationTest.Fixture();
    final ResolutionCycles cycles;
    final ResolutionCycles.Admission owned;
    final ObservationImpl member=new ObservationImpl();
    final ObservableImpl requested=new ObservableImpl();
    final ConsequenceSubmissionAdmission authority=mock(ConsequenceSubmissionAdmission.class);
    final ServiceContextScope admitted;
    Fixture()throws Exception {
      when(f.scope.getUser().getUsername()).thenReturn("owner");f.scope.setId("cancellation-test");
      when(f.runtime.serviceId()).thenReturn("runtime");when(f.runtime.settings()).thenReturn(mock(Settings.class));
      when(f.reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenReturn(
          new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,
              new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,"semantic-content-v2:"+"a".repeat(64),"lease"),null));
      when(f.reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(f.reasoner);
      cycles=new ResolutionCycles(f.runtime,"cancellation-test");when(f.twin.getResolutionCycles()).thenReturn(cycles);
      var subject=new ConceptImpl();subject.setUrn("test:Bearer");subject.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE));
      var observable=new ObservableImpl();observable.setSemantics(subject);observable.setUrn(subject.getUrn());
      member.setId(42);member.setObservable(observable);member.setGeometry(Geometry.create("1"));
      var role=new ConceptImpl();role.setUrn("test:Role");role.setType(EnumSet.of(SemanticType.ROLE,SemanticType.PREDICATE));
      requested.setSemantics(role);requested.setUrn("test:Role of test:Bearer");requested.setDescriptionType(Contextualization.CHARACTERIZATION);
      var optional=new ObservableImpl(requested);optional.setOptional(true);optional.setUrn(requested.getUrn()+" optional");
      when(f.reasoner.buildObservable(any(),any())).thenReturn(optional);
      when(f.twin.getKnowledgeGraph().getAsset(eq(42L),any(),eq(Observation.class))).thenReturn(member);
      owned=cycles.admit(f.scope.within(member),true).get(5,TimeUnit.SECONDS);
      admitted=owned.scope().forConsequence(new Consequence.Cause(1,"cancellation-test",1,2,0,null,null,"role-implication",Consequence.Kind.ROLE_IMPLICATION))
          .withConsequenceSubmissionAdmission(authority);
      doCallRealMethod().when(f.runtime).characterizeConsequence(any(),any(),any());
      doAnswer(call->{assertSame(requested,call.getArgument(0));return null;}).when(authority).requireTarget(any(),any());
    }
    Dataflow.ResolutionOutcome run(Dataflow plan)throws Exception {f.resolution.complete(plan);return f.runtime.characterizeConsequence(member,requested,admitted);}
    Dataflow update(long target) {
      var node=new ActuatorImpl();node.setActuatorType(Actuator.Type.UPDATE);node.setContextualization(Contextualization.CHARACTERIZATION);
      var binding=new ActuatorImpl.TargetBindingImpl();binding.setKind(Actuator.TargetBinding.Kind.OBSERVATION);
      var recipient=new ObservationImpl();recipient.setId(target);binding.setTarget(recipient);node.getTargetBindings().add(binding);
      var plan=new DataflowImpl();plan.getComputation().add(node);return plan;
    }
    @Override public void close(){owned.close();cycles.close();}
  }
  @Test void ordinaryRegistrationStillRejectsTheDirective()throws Exception {
    try(var f=new Fixture()) {
      doCallRealMethod().when(f.f.runtime).register(any(),any());
      var directive=new ObservationImpl();directive.setObservable(f.requested);
      assertThrows(IllegalArgumentException.class,()->f.f.runtime.register(directive,f.admitted));
    }
  }
  @Test void actualDirectNoModelLifecycleBindsBeforeResolvingAndCommitsWithoutRegistering()throws Exception {
    try(var f=new Fixture()) {
      assertEquals(Dataflow.ResolutionOutcome.NO_MODEL,f.run(Dataflow.noModel(List.of())));
      var order=inOrder(f.authority,f.f.resolver,f.f.resolutionTransaction,f.f.rootTransaction);
      order.verify(f.authority).requireTarget(eq(f.requested),any());
      order.verify(f.authority).bind(eq(f.f.rootTransaction),any());
      order.verify(f.f.resolver).resolve(any(),any());order.verify(f.f.resolutionTransaction).commit();order.verify(f.f.rootTransaction).commit();
      assertEquals("NO_MODEL",f.f.resolutionTransaction.getActivity().getMetadata().get("resolutionOutcome"));
      verify(f.f.runtime,never()).register(any(),any());verify(f.f.runtime,never()).submit(any(),any());
      verify(f.f.runtime,never()).executeCharacterization(any(),any(),any(),any());
      for(var invocation:mockingDetails(f.f.twin).getInvocations())if(invocation.getMethod().getName().equals("transaction"))
        for(var argument:invocation.getArguments()) {
          assertFalse(argument instanceof Observation,"A characterization must not record the existing recipient as CREATED");
          if(argument instanceof Object[] assets)assertTrue(Arrays.stream(assets).noneMatch(Observation.class::isInstance));
        }
      assertNull(f.f.rootTransaction.getActivity().getMetadata().get(SemanticEvidenceBinding.DATA_KEY));
    }
  }
  @Test void resolvedUpdateDelegatesTheExistingExecutorWithInitializationEvent()throws Exception {
    try(var f=new Fixture()) {
      var plan=f.update(42);assertEquals(Dataflow.ResolutionOutcome.RESOLVED,f.run(plan));
      verify(f.f.runtime).executeCharacterization(eq(plan),eq(f.member),argThat(event->event.getType()==Scheduler.Event.Type.INITIALIZATION),any());
    }
  }
  @Test void wrongUpdateRootIsRejectedByExistingValidatorBeforeCompilation()throws Exception {
    try(var f=new Fixture()) {
      doCallRealMethod().when(f.f.runtime).executeCharacterization(any(),any(),any(),any());
      assertThrows(IllegalArgumentException.class,()->f.run(f.update(43)));
      verify(f.f.rootTransaction,never()).commit();verify(f.f.runtime,never()).getComponentRegistry();
    }
  }
  @Test void failedOrMalformedPlansCannotBecomeNoModel()throws Exception {
    for(var plan:List.of(Dataflow.empty(),Dataflow.trivial()))try(var f=new Fixture()) {
      assertThrows(IllegalStateException.class,()->f.run(plan));verify(f.f.rootTransaction,never()).commit();
    }
    try(var f=new Fixture()) {
      f.f.resolution.completeExceptionally(new IllegalStateException("resolver failure"));
      assertThrows(CompletionException.class,()->f.f.runtime.characterizeConsequence(f.member,f.requested,f.admitted));
      verify(f.f.rootTransaction,never()).commit();
    }
  }
  @Test void deferredPlanRollsBackWithoutApplyingItsPartialWork()throws Exception {
    try(var f=new Fixture()) {
      var plan=(DataflowImpl)f.update(42);plan.setResolutionOutcome(Dataflow.ResolutionOutcome.DEFERRED);
      assertEquals(Dataflow.ResolutionOutcome.DEFERRED,f.run(plan));
      verify(f.f.rootTransaction).fail(null);verify(f.f.rootTransaction,never()).commit();
      verify(f.f.runtime,never()).executeCharacterization(any(),any(),any(),any());
    }
  }
  @Test void explicitEmptyDeferredRemainsDeferredButErroredDeferredFails()throws Exception {
    for(boolean errored:new boolean[]{false,true})try(var f=new Fixture()) {
      var plan=mock(Dataflow.class);when(plan.getResolutionOutcome()).thenReturn(Dataflow.ResolutionOutcome.DEFERRED);
      when(plan.isEmpty()).thenReturn(true);when(plan.getComputation()).thenReturn(List.of());
      when(plan.getNotifications()).thenReturn(errored?List.of(Notification.error("resolution failed")):List.of());
      if(errored)assertThrows(IllegalStateException.class,()->f.run(plan));
      else assertEquals(Dataflow.ResolutionOutcome.DEFERRED,f.run(plan));
      verify(f.f.rootTransaction,never()).commit();verify(f.f.runtime,never()).executeCharacterization(any(),any(),any(),any());
    }
  }
  @Test void cleanupFailureCannotMaskTheOriginalResolutionFailure()throws Exception {
    try(var f=new Fixture()) {
      var original=new IllegalStateException("resolver failed");
      f.f.resolution.completeExceptionally(original);
      doThrow(new IllegalArgumentException("child cleanup failed")).when(f.f.resolutionTransaction).fail(any());
      doThrow(new IllegalArgumentException("root cleanup failed")).when(f.f.rootTransaction).fail(any());
      var failure=assertThrows(CompletionException.class,()->f.f.runtime.characterizeConsequence(f.member,f.requested,f.admitted));
      assertSame(original,failure.getCause());assertEquals(2,failure.getSuppressed().length);
    }
  }
  @Test void inheritedTransactionAndDifferentFocusFailBeforeNativeWork()throws Exception {
    try(var f=new Fixture()) {
      assertThrows(IllegalStateException.class,()->f.f.runtime.characterizeConsequence(f.member,f.requested,f.admitted.withTransaction(f.f.rootTransaction)));
      var other=new ObservationImpl();other.setId(43);
      assertThrows(IllegalStateException.class,()->f.f.runtime.characterizeConsequence(f.member,f.requested,f.admitted.within(other)));
      verifyNoInteractions(f.authority,f.f.resolver);
    }
  }
  @Test void missingForeignOrWrongTargetAuthorityCannotReachResolver()throws Exception {
    try(var f=new Fixture()) {
      assertThrows(NullPointerException.class,()->f.f.runtime.characterizeConsequence(f.member,f.requested,f.admitted.withoutConsequenceSubmissionAdmission()));
      assertThrows(SecurityException.class,()->f.f.runtime.characterizeConsequence(f.member,f.requested,new ServiceContextScope(f.admitted).within(f.member).withResolutionCycleBinding(null)));
      doThrow(new IllegalStateException("wrong target")).when(f.authority).requireTarget(any(),any());
      assertThrows(IllegalStateException.class,()->f.f.runtime.characterizeConsequence(f.member,f.requested,f.admitted));
      verifyNoInteractions(f.f.resolver);verify(f.authority,never()).bind(any(),any());
    }
  }
  @Test void admissionOrCommitFailureNeverReturnsNoModel()throws Exception {
    try(var f=new Fixture()) {
      doThrow(new IllegalStateException("source drift")).when(f.authority).bind(any(),any());
      assertThrows(IllegalStateException.class,()->f.run(Dataflow.noModel(List.of())));verifyNoInteractions(f.f.resolver);
    }
    try(var f=new Fixture()) {
      when(f.f.rootTransaction.commit()).thenReturn(-1L);
      assertThrows(IllegalStateException.class,()->f.run(Dataflow.noModel(List.of())));
    }
  }
}
