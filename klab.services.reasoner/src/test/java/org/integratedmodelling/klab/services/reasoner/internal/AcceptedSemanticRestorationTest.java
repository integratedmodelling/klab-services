package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.junit.jupiter.api.Test;

/** Restoration is exact admission-provenance reuse, never semantic-content hash equivalence. */
class AcceptedSemanticRestorationTest {
  @org.junit.jupiter.api.BeforeAll static void initialize() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  private static final String SELECTION = org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA;
  private static Worldview source(String selection) {
    var world = AcceptedSemanticObserverScopeTest.world(); world.getMetadata().put(SELECTION, selection); return world;
  }
  @Test void exactAcceptedInputReturningAfterFailedCandidateRetainsItsGenerationAndActor() throws Exception {
    var first = mock(SemanticEnvironment.class); var rebuilt = mock(SemanticEnvironment.class);
    var current = new AtomicReference<>(first);
    var factory = AcceptedSemanticObserverScopeTest.factory(current);
    var selection = org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA;
    when(factory.build(any(), any())).thenAnswer(call -> {
      CandidatePublicationCoordinator.Input<Worldview> input = call.getArgument(0);
      return "B".equals(input.payload().getMetadata().get(selection))
          ? new CandidatePublicationCoordinator.Result<>(null, CandidatePublicationCoordinator.LogicalStatus.FAILED,
              true, List.of("controlled unavailable provider selection"))
          : new CandidatePublicationCoordinator.Result<>(current.get(), CandidatePublicationCoordinator.LogicalStatus.VALID,
              true, List.of("retained accepted compiler warning"));
    });
    var a = AcceptedSemanticObserverScopeTest.world(); a.getMetadata().put(selection, "A");
    var b = AcceptedSemanticObserverScopeTest.world(); b.getMetadata().put(selection, "B");
    var owner = mock(ServiceScope.class);
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      runtime.submit(b, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.FAILED, runtime.status().logicalStatus());
      current.set(rebuilt);
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(accepted, runtime.status().acceptedGeneration(), "Returning exact accepted A must not republish after failed B");
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus());
      assertEquals(List.of("retained accepted compiler warning"), runtime.status().diagnostics());
      try (var lease = runtime.lease()) { assertSame(first, lease.state()); }
      verify(factory, times(2)).build(any(), any());
      verify(first, never()).close();
    }
    verify(first, times(1)).close();
    verify(rebuilt, never()).close(); // It must never be constructed/published by the builder.
  }

  @Test void returnAfterBeanRejectionRestoresStatusWithoutLosingAcceptedDiagnostics() throws Exception {
    var state = mock(SemanticEnvironment.class);
    var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state));
    when(factory.build(any(), any())).thenReturn(new CandidatePublicationCoordinator.Result<>(state,
        CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of("accepted warning")));
    var owner = mock(ServiceScope.class); var a = source("A");
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source("B"), owner),
          List.of(org.integratedmodelling.klab.api.services.runtime.Notification.error("retained rejected B")), owner));
      assertTrue(runtime.shouldRetryPreflight(a, owner));
      assertFalse(runtime.beginPreflight(a, owner).retry());
      assertEquals(accepted, runtime.status().acceptedGeneration());
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus());
      assertEquals(List.of("accepted warning"), runtime.status().diagnostics());
      assertFalse(runtime.shouldRetryPreflight(a, owner));
      verify(factory, times(1)).build(any(), any());
    }
  }

  @Test void explicitInvalidationDiscardsRestorationProof() throws Exception {
    var first = mock(SemanticEnvironment.class); var next = mock(SemanticEnvironment.class);
    var current = new AtomicReference<>(first); var factory = AcceptedSemanticObserverScopeTest.factory(current);
    var owner = mock(ServiceScope.class); var a = source("A");
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      runtime.invalidate(); current.set(next);
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(accepted + 1, runtime.status().acceptedGeneration());
      verify(factory, times(2)).build(any(), any());
    }
  }

  @Test void changedAdmissionProvenanceDoesNotReuseTheAcceptedActor() throws Exception {
    for (String change : List.of("source", "dependency", "owner", "groups", "selection", "catalog", "order")) {
      var first = mock(SemanticEnvironment.class); var next = mock(SemanticEnvironment.class);
      var current = new AtomicReference<>(first); var factory = AcceptedSemanticObserverScopeTest.factory(current);
      var owner = mock(ServiceScope.class); var identity = mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);
      when(identity.getUsername()).thenReturn("owner"); when(identity.getGroups()).thenReturn(List.of());
      when(identity.getId()).thenReturn("fixture-credential");
      when(owner.getIdentity()).thenReturn(identity);
      var a = source("A");
      try (var runtime = new AcceptedSemanticRuntime(factory)) {
        runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        long accepted = runtime.status().acceptedGeneration();
        runtime.rejectPreflight(runtime.beginPreflight(source("B"), owner),
            List.of(org.integratedmodelling.klab.api.services.runtime.Notification.error("controlled rejected B")), owner);
        var restored = source("A"); current.set(next);
        switch (change) {
          case "source" -> ((org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl)
              restored.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // changed");
          case "dependency" -> when(factory.dependencyRevision()).thenReturn("changed dependency");
          case "owner" -> when(identity.getUsername()).thenReturn("other owner");
          case "groups" -> {
            var group = mock(org.integratedmodelling.klab.api.identities.Group.class);
            when(group.getName()).thenReturn("new group"); when(identity.getGroups()).thenReturn(List.of(group));
          }
          case "selection" -> restored.getMetadata().put(SELECTION, "C");
          case "catalog" -> restored.getMetadata().put(
              org.integratedmodelling.klab.components.AuthorityComponentResolver.REVISION_METADATA, "new catalog");
          case "order" -> {
            var extra = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();
            extra.setUrn("other"); extra.setSourceCode("ontology other in domain root;"); restored.getOntologies().add(extra);
          }
        }
        assertTrue(runtime.beginPreflight(restored, owner).retry(), change);
        verify(factory, times(1)).build(any(), any());
        assertEquals(accepted, runtime.status().acceptedGeneration());
      }
    }
  }

  @Test void publicationGuardIsRecheckedBeforeRestorationAndCannotBeBypassed() throws Exception {
    var state = mock(SemanticEnvironment.class); var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state));
    when(factory.authorityBootstrapEnabled()).thenReturn(true);
    when(factory.capture(any(), any())).thenAnswer(call -> factory.capture(call.getArgument(0)));
    when(factory.publicationCurrent(any())).thenReturn(true);
    var owner = mock(ServiceScope.class); var a = source("A");
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      runtime.rejectPreflight(runtime.beginPreflight(source("B"), owner),
          List.of(org.integratedmodelling.klab.api.services.runtime.Notification.error("controlled rejected B")), owner);
      when(factory.publicationCurrent(any())).thenReturn(false);
      assertTrue(runtime.beginPreflight(a, owner).retry(), "Stale publication proof must take the normal validation path");
      assertEquals(accepted, runtime.status().acceptedGeneration());
      assertNotEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus());
      verify(factory, times(1)).build(any(), any());
    }
  }

  @Test void provenanceChangingDuringGuardCannotRestoreOldAdmission() throws Exception {
    var state = mock(SemanticEnvironment.class); var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state));
    when(factory.authorityBootstrapEnabled()).thenReturn(true);
    when(factory.capture(any(), any())).thenAnswer(call -> factory.capture(call.getArgument(0)));
    when(factory.publicationCurrent(any())).thenReturn(true);
    var owner = mock(ServiceScope.class); var identity = mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);
    when(identity.getUsername()).thenReturn("owner"); when(identity.getId()).thenReturn("credential");
    when(identity.getGroups()).thenReturn(List.of()); when(owner.getIdentity()).thenReturn(identity);
    var a = source("A");
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      runtime.rejectPreflight(runtime.beginPreflight(source("B"), owner),
          List.of(org.integratedmodelling.klab.api.services.runtime.Notification.error("controlled B rejection")), owner);
      when(factory.publicationCurrent(any())).thenAnswer(call -> {
        when(identity.getUsername()).thenReturn("changed owner"); return true;
      });
      var attempt = runtime.beginPreflight(a, owner);
      assertTrue(attempt.retry());
      assertFalse(runtime.submit(attempt.snapshot(), attempt, owner), "Old-key attempt must also fail normal submit recheck");
      assertEquals(accepted, runtime.status().acceptedGeneration());
      assertNotEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus());
      verify(factory, times(1)).build(any(), any());
    }
  }

  @Test void restorationCancelsPendingReplacementWithoutClosingAcceptedActorOrLeakingDiscardedActor() throws Exception {
    var acceptedState = mock(SemanticEnvironment.class); var superseded = mock(SemanticEnvironment.class);
    var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(acceptedState));
    var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
    when(factory.build(any(), any())).thenAnswer(call -> {
      CandidatePublicationCoordinator.Input<Worldview> input = call.getArgument(0);
      if ("B".equals(input.payload().getMetadata().get(SELECTION))) {
        entered.countDown(); assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
        return new CandidatePublicationCoordinator.Result<>(superseded, CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of());
      }
      return new CandidatePublicationCoordinator.Result<>(acceptedState, CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of());
    });
    var owner = mock(ServiceScope.class); var a = source("A");
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      try (var held = runtime.lease()) {
        runtime.submit(source("B"), owner);
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        try {
          assertFalse(runtime.beginPreflight(source("B"), owner).retry(), "An identical pending candidate must remain suppressed");
          runtime.submit(a, owner);
          assertEquals(accepted, runtime.status().acceptedGeneration());
          assertSame(acceptedState, held.state());
          assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus());
        } finally { release.countDown(); }
        assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        assertEquals(accepted, runtime.status().acceptedGeneration());
        verify(superseded, times(1)).close();
        verify(acceptedState, never()).close();
      }
    } finally { release.countDown(); }
    verify(acceptedState, times(1)).close();
  }

  @Test void inFlightResolvingSurvivesExactRestorationWithoutReplay() throws Exception {
    var state = mock(SemanticEnvironment.class); var actor = mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class);
    when(state.actor()).thenReturn(actor);
    var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state));
    var owner = mock(ServiceScope.class); var a = source("A");
    var request = new org.integratedmodelling.common.knowledge.ConceptImpl(); request.setUrn("test:Elevation");
    when(actor.resolveConcept(request.getUrn())).thenReturn(request);
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      when(actor.resolving(request)).thenAnswer(call -> {
        runtime.rejectPreflight(runtime.beginPreflight(source("B"), owner),
            List.of(org.integratedmodelling.klab.api.services.runtime.Notification.error("controlled B rejection")), owner);
        runtime.submit(a, owner);
        assertEquals(accepted, runtime.status().acceptedGeneration());
        verify(state, never()).close();
        return List.of(request);
      });
      var returned = (java.util.Collection<?>)runtime.invoke("resolving",
          new Class<?>[]{org.integratedmodelling.klab.api.knowledge.Semantics.class}, new Object[]{request});
      assertEquals(1, returned.size());
      verify(actor, times(1)).resolving(request);
      verify(factory, times(1)).build(any(), any());
    }
  }

  @Test void unscopedAcceptedInputsDoNotAcquireProvenanceProof() throws Exception {
    var first = mock(SemanticEnvironment.class); var next = mock(SemanticEnvironment.class);
    var current = new AtomicReference<>(first); var factory = AcceptedSemanticObserverScopeTest.factory(current);
    var owner = mock(ServiceScope.class); var a = source("A");
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(a); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      runtime.rejectPreflight(runtime.beginPreflight(source("B"), owner),
          List.of(org.integratedmodelling.klab.api.services.runtime.Notification.error("controlled B rejection")), owner);
      current.set(next); runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(accepted + 1, runtime.status().acceptedGeneration());
      verify(factory, times(2)).build(any(), any());
    }
  }

  @Test void proofTrackingRemainsBoundedAcrossFailedSelectionsAndClearsOnClose() throws Exception {
    var state = mock(SemanticEnvironment.class); var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state));
    when(factory.build(any(), any())).thenAnswer(call -> {
      CandidatePublicationCoordinator.Input<Worldview> input = call.getArgument(0);
      return "A".equals(input.payload().getMetadata().get(SELECTION))
          ? new CandidatePublicationCoordinator.Result<>(state, CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of())
          : new CandidatePublicationCoordinator.Result<>(null, CandidatePublicationCoordinator.LogicalStatus.FAILED, true, List.of("controlled failed selection"));
    });
    var field = AcceptedSemanticRuntime.class.getDeclaredField("preflightKeys"); field.setAccessible(true);
    var owner = mock(ServiceScope.class); var a = source("A");
    var runtime = new AcceptedSemanticRuntime(factory);
    try {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted = runtime.status().acceptedGeneration();
      for (int i = 0; i < 8; i++) {
        runtime.submit(source("unavailable-" + i), owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
        assertTrue(((java.util.Map<?, ?>)field.get(runtime)).size() <= 2);
      }
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(accepted, runtime.status().acceptedGeneration());
      verify(factory, times(9)).build(any(), any());
    } finally { runtime.close(); }
    assertTrue(((java.util.Map<?, ?>)field.get(runtime)).isEmpty());
    assertFalse(runtime.beginPreflight(a, owner).retry());
    assertFalse(runtime.shouldRetryPreflight(a, owner));
    runtime.invalidate(); // Closed runtimes cannot restore or retain an admission proof.
  }

  @Test void closureDuringPreflightFingerprintingCannotRestoreOrReopenRuntime() throws Exception {
    var state = mock(SemanticEnvironment.class); var factory = AcceptedSemanticObserverScopeTest.factory(new AtomicReference<>(state));
    var owner = mock(ServiceScope.class); var a = source("A");
    var runtime = new AcceptedSemanticRuntime(factory);
    try {
      runtime.submit(a, owner); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      when(factory.dependencyRevision()).thenAnswer(call -> { runtime.close(); return "test"; });
      assertFalse(runtime.beginPreflight(a, owner).retry());
      verify(factory, times(1)).build(any(), any());
      verify(state, times(1)).close();
    } finally { runtime.close(); }
  }
}
