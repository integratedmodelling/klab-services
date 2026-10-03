package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.JobManager;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SubmissionCancellationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void apiJobCancellationStopsPendingResolutionAndLeavesSiblingUsable() {
    var f = new Fixture();
    var submission = f.submit();
    var requestScope = f.resolutionScope.get();
    var jobs = new JobManager();
    // Same wrapping and cancellation action as RuntimeServerController.submit.
    var id = jobs.submit(submission.thenApply(Observation::forTransport), "test submission",
        () -> submission.cancel(true));
    assertTrue(jobs.cancel(id));
    assertEquals(Scope.Status.INTERRUPTED, jobs.status(id).getStatus());
    assertTrue(requestScope.isInterrupted());
    assertTrue(new ServiceContextScope(requestScope).isInterrupted());
    assertFalse(f.scope.isInterrupted());
    f.resolution.complete(f.dataflow);
    verify(f.rootTransaction).fail(any(Throwable.class));
    verify(f.rootTransaction, never()).commit();
    verify(f.scheduler, never()).submit(any(), any());

    f.resolution = new CompletableFuture<>();
    var sibling = f.submit();
    assertFalse(f.resolutionScope.get().isInterrupted());
    f.resolution.complete(f.dataflow);
    assertFalse(sibling.join().isEmpty());
    verify(f.scheduler).submit(any(), any());
  }

  @Test void cancellationDuringContextualizationPreventsSubmissionCommit() throws Exception {
    var f = new Fixture();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(f.scheduler.submit(any(), any())).thenAnswer(call -> {
      started.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return true; // Even a component that returns success after cancellation must not publish.
    });
    var submission = f.submit();
    try (var worker = Executors.newSingleThreadExecutor()) {
      var completion = worker.submit(() -> f.resolution.complete(f.dataflow));
      try {
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(submission.cancel(true));
      } finally { release.countDown(); }
      completion.get(5, TimeUnit.SECONDS);
    }
    verify(f.rootTransaction, never()).commit();
    verify(f.rootTransaction).fail(any(Throwable.class));
    assertFalse(f.scope.isInterrupted());
  }

  @Test void completedSubmissionCannotCancelItsExecutionScope() {
    var f = new Fixture();
    var submission = f.submit();
    f.resolution.complete(f.dataflow);
    assertFalse(submission.join().isEmpty());
    assertFalse(submission.cancel(true));
    assertFalse(f.resolutionScope.get().isInterrupted());
    verify(f.rootTransaction).commit();
  }

  @Test void cancellingCoalescedSubscriberDoesNotCancelSharedExecution() throws Exception {
    var f = new Fixture();
    // Mockito bypasses field initializers; supply the real in-flight registry for public submit.
    var registry = RuntimeService.class.getDeclaredField("inFlightSubmissions");
    registry.setAccessible(true);
    registry.set(f.runtime, new ConcurrentHashMap<>());
    var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    var status = f.resolver.status();
    when(reasoner.status()).thenReturn(status);
    var baseType = mock(Concept.class);
    when(baseType.getUrn()).thenReturn("test:subject");
    when(reasoner.baseSubstantialType(any(), any())).thenReturn(baseType);
    f.scope.addService(reasoner);
    var first = f.submit(SemanticType.SUBJECT, "test.subject");
    var second = f.submit(SemanticType.SUBJECT, "test.subject");
    assertTrue(first.cancel(true));
    assertFalse(f.resolutionScope.get().isInterrupted());
    f.resolution.complete(f.dataflow);
    assertFalse(second.join().isEmpty());
    verify(f.resolver).resolve(any(), any());
    verify(f.rootTransaction).commit();
  }

  static class Fixture {
    final RuntimeService runtime = mock(RuntimeService.class);
    final Resolver resolver = mock(Resolver.class);
    final DigitalTwin twin = mock(DigitalTwin.class);
    final Scheduler scheduler = mock(Scheduler.class);
    final DigitalTwinImpl.TransactionImpl rootTransaction = mock(DigitalTwinImpl.TransactionImpl.class);
    final DigitalTwinImpl.TransactionImpl resolutionTransaction = mock(DigitalTwinImpl.TransactionImpl.class);
    final Dataflow dataflow = mock(Dataflow.class);
    final ServiceContextScope scope;
    final AtomicReference<ServiceContextScope> resolutionScope = new AtomicReference<>();
    CompletableFuture<Dataflow> resolution = new CompletableFuture<>();

    Fixture() {
      var user = mock(UserIdentity.class);
      var session = new ServiceSessionScope(new ServiceUserScope(user, runtime));
      scope = new ServiceContextScope(session,
          DigitalTwin.Configuration.builder().id("cancellation-test").name("test").build(), user);
      scope.setDigitalTwin(twin);
      var status = mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);
      when(status.isOperational()).thenReturn(true);
      when(resolver.status()).thenReturn(status);
      scope.addService(resolver);
      when(twin.getKnowledgeGraph()).thenReturn(mock(KnowledgeGraph.class));
      when(twin.getScheduler()).thenReturn(scheduler);
      when(twin.transaction(any(), any(), any(Object[].class))).thenAnswer(call -> {
        when(rootTransaction.getActivity()).thenReturn(call.getArgument(0));
        return rootTransaction;
      });
      when(rootTransaction.getChild(any(), any(), any(Object[].class))).thenAnswer(call -> {
        when(resolutionTransaction.getActivity()).thenReturn(call.getArgument(0));
        return resolutionTransaction;
      });
      when(resolutionTransaction.commit()).thenReturn(0L);
      when(rootTransaction.commit()).thenReturn(1L);
      when(resolver.resolve(any(), any())).thenAnswer(call -> {
        resolutionScope.set(call.getArgument(1));
        return resolution;
      });
      when(dataflow.getResolutionOutcome()).thenReturn(Dataflow.ResolutionOutcome.RESOLVED);
      when(dataflow.getComputation()).thenReturn(List.of());
      when(scheduler.submit(any(), any())).thenReturn(true);
      when(runtime.register(any(), any())).thenAnswer(call -> call.getArgument(0));
      doCallRealMethod().when(runtime).submit(any(), any());
    }

    CompletableFuture<Observation> submit() {
      return submit(SemanticType.PROCESS, null);
    }

    CompletableFuture<Observation> submit(SemanticType type, String urn) {
      var observable = mock(Observable.class);
      var semantics = mock(Concept.class);
      when(observable.getSemantics()).thenReturn(semantics);
      when(semantics.getType()).thenReturn(EnumSet.of(type));
      when(observable.getUrn()).thenReturn("test:process");
      var observation = new ObservationImpl();
      observation.setObservable(observable);
      observation.setUrn(urn);
      observation.setGeometry(Geometry.create("1"));
      return runtime.submit(observation, scope);
    }
  }
}
