package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class CharacterizationLifecycleTest {
  @Test void rejectsPlanBoundToAnotherMemberBeforeCompilation() {
    var runtime = mock(RuntimeService.class);
    doCallRealMethod().when(runtime).executeCharacterization(any(), any(), any(), any());
    var member = new ObservationImpl(); member.setId(42);
    var wrongMember = new ObservationImpl(); wrongMember.setId(43);
    var node = new ActuatorImpl(); node.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.UPDATE);
    node.setContextualization(Contextualization.CHARACTERIZATION);
    var binding = new ActuatorImpl.TargetBindingImpl();
    binding.setKind(org.integratedmodelling.klab.api.services.runtime.Actuator.TargetBinding.Kind.OBSERVATION);
    binding.setTarget(wrongMember); node.getTargetBindings().add(binding);
    var plan = new DataflowImpl(); plan.getComputation().add(node);
    assertThrows(IllegalArgumentException.class, () -> runtime.executeCharacterization(plan, member,
        mock(Scheduler.Event.class), mock(ServiceContextScope.class)));
    verify(runtime, never()).getComponentRegistry();
  }
  @Test void distinguishesAbsenceFromFailure() {
    assertDoesNotThrow(() -> CharacterizationLifecycle.validate(Dataflow.noModel(List.of())));
    assertThrows(IllegalStateException.class, () -> CharacterizationLifecycle.validate(Dataflow.empty()));
    assertThrows(IllegalStateException.class, () -> CharacterizationLifecycle.validate(Dataflow.trivial()));
    assertThrows(IllegalStateException.class, () -> CharacterizationLifecycle.validate(null));
    var inconsistent = Dataflow.noModel(List.of());
    inconsistent.getComputation().add(new ActuatorImpl());
    assertThrows(IllegalStateException.class, () -> CharacterizationLifecycle.validate(inconsistent));
  }

  @Test void resolvesEachStagedMemberOnceAndAwaitsWorkBeforeCommit() {
    try (var promotion = mockStatic(Observable.class)) {
      var f = new Fixture(promotion);
      var first = f.member(42);
      var second = f.member(43);
      var plan = new DataflowImpl(); plan.getComputation().add(new ActuatorImpl());
      when(f.resolver.resolve(any(), eq(first.resolution))).thenReturn(CompletableFuture.completedFuture(plan));
      when(f.resolver.resolve(any(), eq(second.resolution))).thenReturn(CompletableFuture.completedFuture(Dataflow.noModel(List.of())));
      CharacterizationLifecycle.run(List.of(first.pending, first.pending, second.pending), f.scope, f.runtime);
      var order = inOrder(f.runtime, first.resolution, f.resolver, second.resolution);
      order.verify(f.resolver).resolve(any(), eq(first.resolution));
      order.verify(f.runtime).executeCharacterization(plan, first.staged, f.event, first.resolution);
      order.verify(first.resolution).commit();
      order.verify(f.resolver).resolve(any(), eq(second.resolution));
      order.verify(second.resolution).commit();
      verify(f.runtime, times(1)).executeCharacterization(any(), any(), any(), any());
      verify(f.scope, never()).commit();
      verify(f.resolver).resolve(argThat(request -> request.getObservable() == f.concrete
          && request.getId() == Observation.UNASSIGNED_ID), eq(first.resolution));
      assertEquals("NO_MODEL", second.activity.getMetadata().get("resolutionOutcome"));
    }
  }

  @Test void executionFailureFailsChildAndStopsLaterMembers() {
    try (var promotion = mockStatic(Observable.class)) {
      var f = new Fixture(promotion);
      var first = f.member(42); var second = f.member(43);
      var plan = new DataflowImpl(); plan.getComputation().add(new ActuatorImpl());
      when(f.resolver.resolve(any(), eq(first.resolution))).thenReturn(CompletableFuture.completedFuture(plan));
      doThrow(new IllegalStateException("contextualizer failed")).when(f.runtime)
          .executeCharacterization(any(), any(), any(), any());
      assertThrows(IllegalStateException.class, () -> CharacterizationLifecycle.run(
          List.of(first.pending, second.pending), f.scope, f.runtime));
      verify(first.resolution).fail(any(Throwable.class));
      verify(first.resolution, never()).commit();
      verify(f.resolver, never()).resolve(any(), eq(second.resolution));
    }
  }

  @Test void failedResolutionIsNotOptional() {
    try (var promotion = mockStatic(Observable.class)) {
      var f = new Fixture(promotion); var first = f.member(42);
      when(f.resolver.resolve(any(), eq(first.resolution)))
          .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("service unavailable")));
      assertThrows(IllegalStateException.class, () -> CharacterizationLifecycle.run(List.of(first.pending), f.scope, f.runtime));
      verify(first.resolution).fail(any(Throwable.class));
      verify(first.resolution, never()).commit();
      verifyNoInteractions(f.runtime);
    }
  }

  record Member(MemberClassifierExecutor.PendingAttribution pending, Observation staged,
      ServiceContextScope resolution, Activity activity) {}
  static class Fixture {
    final ServiceContextScope scope = mock(ServiceContextScope.class);
    final RuntimeService runtime = mock(RuntimeService.class);
    final Resolver resolver = mock(Resolver.class);
    final Scheduler.Event event = mock(Scheduler.Event.class);
    final Concept predicate = mock(Concept.class);
    final Concept subject = mock(Concept.class);
    final Observable concrete = mock(Observable.class);
    final Observable original = mock(Observable.class);
    Fixture(org.mockito.MockedStatic<Observable> promotion) {
      when(predicate.getUrn()).thenReturn("test:Concrete");
      when(original.getSemantics()).thenReturn(subject); when(subject.singular()).thenReturn(subject);
      var promoted = mock(Observable.class);
      var builder = mock(Observable.Builder.class);
      promotion.when(() -> Observable.promote(predicate)).thenReturn(promoted);
      when(promoted.builder(any())).thenReturn(builder);
      when(builder.of(subject)).thenReturn(builder); when(builder.buildObservable()).thenReturn(concrete);
      when(concrete.getContextualization()).thenReturn(Contextualization.CHARACTERIZATION);
      when(concrete.getSemantics()).thenReturn(predicate);
    }
    Member member(long id) {
      var before = new ObservationImpl(); before.setId(id); before.setObservable(original);
      var staged = new ObservationImpl(); staged.setId(id); staged.setGeometry(Geometry.UNIVERSAL);
      staged.setObservable(concrete);
      when(scope.getObservation(id)).thenReturn(staged);
      var focused = mock(ServiceContextScope.class);
      var resolution = mock(ServiceContextScope.class);
      when(scope.within(staged)).thenReturn(focused);
      var activity = Activity.of(Activity.Type.RESOLUTION);
      when(focused.executing(any(Activity.class), any(Object[].class))).thenAnswer(call -> {
        Activity actual = call.getArgument(0);
        // Retain the activity metadata for the no-model audit assertion.
        actual.getMetadata().putAll(activity.getMetadata());
        when(resolution.getActivity()).thenReturn(actual);
        return resolution;
      });
      when(resolution.getService(Resolver.class)).thenReturn(resolver);
      when(resolution.commit()).thenAnswer(call -> {
        activity.getMetadata().putAll(resolution.getActivity().getMetadata()); return 0L;
      });
      return new Member(new MemberClassifierExecutor.PendingAttribution(before, original,
          mock(Concept.class), predicate, Geometry.UNIVERSAL, event), staged, resolution, activity);
    }
  }
}
