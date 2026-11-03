package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Deterministic read-only generation controls, not a reconstruction of a live publication trigger. */
class ModelResolutionAdmissionGenerationTest {
  @BeforeAll static void initialize() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }

  @Test void resolvingReturnsStableGenerationWithoutReexecution() throws Exception {
    var state = mock(SemanticEnvironment.class);
    var actor = mock(ReasonerService.class);
    when(state.actor()).thenReturn(actor);
    var request = new ConceptImpl(); request.setUrn("test:Elevation");
    when(actor.resolveConcept(request.getUrn())).thenReturn(request);
    when(actor.resolving(request)).thenReturn(List.of(request));
    try (var runtime = new AcceptedSemanticRuntime(
        AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state)))) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      var result = (java.util.Collection<?>) runtime.invoke("resolving",
          new Class<?>[]{Semantics.class}, new Object[]{request});
      assertEquals(1, result.size());
      assertEquals(accepted, runtime.status().acceptedGeneration());
      verify(actor, times(1)).resolving(request);
    }
  }

  @Test void publicationDuringResolvingRejectsOldResultWithoutAutomaticReplay() throws Exception {
    var oldState = mock(SemanticEnvironment.class); var nextState = mock(SemanticEnvironment.class);
    var oldActor = mock(ReasonerService.class); var nextActor = mock(ReasonerService.class);
    when(oldState.actor()).thenReturn(oldActor); when(nextState.actor()).thenReturn(nextActor);
    var request = new ConceptImpl(); request.setUrn("test:Elevation");
    when(oldActor.resolveConcept(request.getUrn())).thenReturn(request);
    var current = new AtomicReference<>(oldState);
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current))) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      when(oldActor.resolving(request)).thenAnswer(call -> {
        current.set(nextState);
        var changed = AcceptedSemanticObserverScopeTest.world();
        ((KimOntologyImpl)changed.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // new generation");
        runtime.submit(changed);
        assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        assertTrue(runtime.status().acceptedGeneration() > accepted);
        verify(oldState, never()).close(); // Its invocation still owns a lease.
        return List.of(request);
      });
      var failure = assertThrows(IllegalStateException.class, () -> runtime.invoke("resolving",
          new Class<?>[]{Semantics.class}, new Object[]{request}));
      assertEquals("Accepted semantic generation changed during evaluation; replan the request", failure.getMessage());
      verify(oldActor, times(1)).resolving(request);
      verify(nextActor, never()).resolving(any());
      verify(oldState, times(1)).close();
    }
  }

  @Test void rejectedCandidateAloneDoesNotInvalidateAnAcceptedResolvingResult() throws Exception {
    var state = mock(SemanticEnvironment.class); var actor = mock(ReasonerService.class);
    when(state.actor()).thenReturn(actor);
    var request = new ConceptImpl(); request.setUrn("test:Elevation");
    when(actor.resolveConcept(request.getUrn())).thenReturn(request);
    var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state));
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      when(actor.resolving(request)).thenAnswer(call -> {
        when(factory.build(any(), any())).thenReturn(new CandidatePublicationCoordinator.Result<>(
            null, CandidatePublicationCoordinator.LogicalStatus.FAILED, true, List.of("controlled rejected candidate")));
        var changed = AcceptedSemanticObserverScopeTest.world();
        ((KimOntologyImpl)changed.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // rejected candidate");
        runtime.submit(changed);
        assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        assertEquals(CandidatePublicationCoordinator.LogicalStatus.FAILED, runtime.status().logicalStatus());
        return List.of(request);
      });
      var result = (java.util.Collection<?>) runtime.invoke("resolving",
          new Class<?>[]{Semantics.class}, new Object[]{request});
      assertEquals(1, result.size());
      assertEquals(accepted, runtime.status().acceptedGeneration());
      verify(actor, times(1)).resolving(request);
    }
  }
}
