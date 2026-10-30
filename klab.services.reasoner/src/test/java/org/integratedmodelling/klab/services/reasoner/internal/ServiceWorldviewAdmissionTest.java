package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;

class ServiceWorldviewAdmissionTest {
  final ServiceScope service = mock(ServiceScope.class);
  final UserScope caller = mock(UserScope.class);
  final ResourcesService resources = mock(ResourcesService.class);
  final WorldviewImpl discovered = world("saved source");

  ServiceWorldviewAdmissionTest() throws Exception {
    var status = mock(KlabService.ServiceStatus.class);
    var capabilities = mock(ResourcesService.Capabilities.class);
    when(status.isAvailable()).thenReturn(true);
    when(capabilities.isWorldviewProvider()).thenReturn(true);
    when(resources.status()).thenReturn(status);
    when(resources.capabilities(service)).thenReturn(capabilities);
    when(resources.getUrl()).thenReturn(URI.create("http://127.0.0.1:8092/resources").toURL());
    when(resources.list(Worldview.class, null)).thenReturn(List.of(discovered));
    when(service.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    for (var entry : List.of(Map.entry(service, "service-owner"), Map.entry(caller, "request-owner"))) {
      var identity = mock(UserIdentity.class);
      when(identity.getId()).thenReturn(entry.getValue());
      when(identity.getUsername()).thenReturn(entry.getValue());
      when(identity.getGroups()).thenReturn(List.of());
      when(entry.getKey().getIdentity()).thenReturn(identity);
      if (entry.getKey() instanceof UserScope user) when(user.getUser()).thenReturn(identity);
    }
  }

  static WorldviewImpl world(String text) {
    var ontology = new KimOntologyImpl();
    ontology.setUrn("imod"); ontology.setProjectName("fixture"); ontology.setSourceCode(text);
    var result = new WorldviewImpl(); result.setUrn("world"); result.setWorldviewId("world");
    result.getOntologies().add(ontology);
    return result;
  }

  ReasonerService reasoner(AcceptedSemanticRuntime runtime) throws Exception {
    if (mockingDetails(runtime).isMock()) when(runtime.status()).thenReturn(
        new CandidatePublicationCoordinator.Status(2, 1, CandidatePublicationCoordinator.BeanStatus.VALID,
            CandidatePublicationCoordinator.LogicalStatus.PENDING, Map.of(), Map.of(), List.of(), List.of()));
    var result = mock(ReasonerService.class, CALLS_REAL_METHODS);
    doReturn(service).when(result).serviceScope();
    doNothing().when(result).setOperational(anyBoolean(), any(Notification[].class));
    var field = ReasonerService.class.getDeclaredField("candidateRuntime");
    field.setAccessible(true); field.set(result, runtime);
    return result;
  }

  @Test void requestUsesIndependentServiceBeansAndNeverPromotesCallerMetadata() {
    var requested = world("saved source");
    requested.getMetadata().put("caller-only", "private");
    var admitted = ServiceWorldviewAdmission.resolve(requested, service);
    assertNotSame(requested, admitted); assertNotSame(discovered, admitted);
    assertFalse(admitted.getMetadata().containsKey("caller-only"));
    verify(resources).list(Worldview.class, null);
    verify(resources, never()).retrieve(anyString(), any(), any());
  }

  @Test void differentSavedSourceCannotEnterGlobalRuntime() {
    assertThrows(IllegalArgumentException.class, () -> ServiceWorldviewAdmission.resolve(world("private source"), service));
  }

  @Test void additionalPrivateDocumentCannotEnterGlobalRuntime() {
    var requested = world("saved source"); requested.getOntologies().add(world("extra").getOntologies().getFirst());
    ((KimOntologyImpl) requested.getOntologies().getLast()).setUrn("private");
    assertThrows(IllegalArgumentException.class, () -> ServiceWorldviewAdmission.resolve(requested, service));
  }

  @Test void differentWorldviewIdCannotEnterGlobalRuntime() {
    var requested = world("saved source"); requested.setWorldviewId("other-world");
    assertThrows(IllegalArgumentException.class, () -> ServiceWorldviewAdmission.resolve(requested, service));
  }

  @Test void localAuthoringSelectionMatchesStartupPriority() throws Exception {
    var remote = mock(ResourcesService.class);
    when(remote.getUrl()).thenReturn(URI.create("https://example.org/resources").toURL());
    when(service.getServices(ResourcesService.class)).thenReturn(List.of(remote, resources));
    assertEquals(SemanticEnvironmentFactory.sources(discovered),
        SemanticEnvironmentFactory.sources(ServiceWorldviewAdmission.resolve(world("saved source"), service)));
    verify(remote, never()).list(Worldview.class, null);
  }

  @Test void dependencyOrderMustMatch() {
    var second = world("second").getOntologies().getFirst(); ((KimOntologyImpl) second).setUrn("second");
    discovered.getOntologies().add(second);
    var requested = world("saved source"); requested.getOntologies().addFirst(second);
    assertThrows(IllegalArgumentException.class, () -> ServiceWorldviewAdmission.resolve(requested, service));
  }

  @Test void unavailableDiscoveryNeverFallsBackToUserBeansOrInvalidatesGlobalRuntime() throws Exception {
    when(resources.list(Worldview.class, null)).thenReturn(List.of());
    var runtime = mock(AcceptedSemanticRuntime.class);
    var result = reasoner(runtime).loadKnowledge(world("saved source"), caller);
    assertTrue(result.getNotifications().stream().anyMatch(n -> n.getMessage().contains("discovery is unavailable")));
    verifyNoMoreInteractions(ignoreStubs(runtime));
  }

  @Test void mismatchedRequestDoesNotCancelAcceptedOrPendingGeneration() throws Exception {
    var runtime = mock(AcceptedSemanticRuntime.class);
    var result = reasoner(runtime).loadKnowledge(world("unpublished edit"), caller);
    assertTrue(result.getNotifications().stream().anyMatch(n -> n.getMessage().contains("differs from the service worldview")));
    verifyNoMoreInteractions(ignoreStubs(runtime));
  }

  @Test void requestedSemanticErrorsRemainVisibleAndDoNotTouchGlobalRuntime() throws Exception {
    var requested = world("saved source"); requested.getNotifications().add(Notification.error("specific source error"));
    var runtime = mock(AcceptedSemanticRuntime.class);
    var result = reasoner(runtime).loadKnowledge(requested, caller);
    assertTrue(result.getNotifications().stream().anyMatch(n -> n.getMessage().equals("specific source error")));
    verifyNoMoreInteractions(ignoreStubs(runtime)); verify(resources, never()).list(Worldview.class, null);
  }

  @Test void actualLoadBoundaryUsesServiceScopeForPreflight() throws Exception {
    var runtime = mock(AcceptedSemanticRuntime.class);
    when(runtime.beginPreflight(any(), same(service))).thenAnswer(i ->
        new AcceptedSemanticRuntime.PreflightAttempt(i.getArgument(0), "same-global-key", 2, false));
    reasoner(runtime).loadKnowledge(world("saved source"), caller);
    verify(runtime).beginPreflight(argThat(w -> w != discovered && SemanticEnvironmentFactory.sources(w)
        .equals(SemanticEnvironmentFactory.sources(discovered))), same(service));
    verifyNoMoreInteractions(ignoreStubs(runtime));
  }

  @Test void savedSourceRepairReturnsAdmissionDiagnosticToEditor() throws Exception {
    var requested = world("user-visible saved change");
    var runtime = mock(AcceptedSemanticRuntime.class);
    when(runtime.submittedSources()).thenReturn(Map.of());
    var root = reasoner(runtime); doReturn(1L).when(root).knowledgeRevision();
    var worldviewField = ReasonerService.class.getDeclaredField("worldview");
    worldviewField.setAccessible(true); worldviewField.set(root, discovered);
    when(caller.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.retrieve("imod", org.integratedmodelling.klab.api.lang.kim.KimOntology.class, caller))
        .thenReturn(requested.getOntologies().getFirst());
    when(resources.retrieve("world", Worldview.class, caller)).thenReturn(requested);
    var request = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(
        requested.getOntologies().getFirst(), "2");
    var response = root.validateDocument(request, caller);
    assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.UNAVAILABLE,
        response.getStatus());
    assertTrue(response.getNotifications().stream().anyMatch(n -> n.getMessage().contains("differs from the service worldview")));
    verify(runtime, never()).validate(any(), any());
    verify(runtime, never()).beginPreflight(any(), any());
  }

  @Test void repeatedUserRepairsAndPollingDoNotSupersedePendingServiceCompilation() throws Exception {
    var factory = mock(SemanticEnvironmentFactory.class);
    var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
    when(factory.dependencyRevision()).thenReturn("dependency");
    when(factory.capture(any())).thenAnswer(i -> {
      Worldview world = i.getArgument(0);
      return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
          SemanticEnvironmentFactory.sourceOrder(world), "dependency", SemanticEnvironmentFactory.CONFIGURATION_REVISION,
          world, CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    });
    when(factory.build(any(), any())).thenAnswer(i -> {
      entered.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS));
      return new CandidatePublicationCoordinator.Result<>(mock(SemanticEnvironment.class),
          CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of());
    });
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(discovered, service); assertTrue(entered.await(5, TimeUnit.SECONDS));
      long generation = runtime.status().inputGeneration();
      try {
        // Demonstrate the uncorrected entry path has a different key under the caller owner.
        assertTrue(runtime.shouldRetryPreflight(world("saved source"), caller));
        var root = reasoner(runtime);
        for (int i = 0; i < 5; i++) {
          root.loadKnowledge(world("saved source"), caller);
          assertFalse(runtime.shouldRetryPreflight(discovered, service));
          assertEquals(generation, runtime.status().inputGeneration());
        }
      } finally { finish.countDown(); }
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus());
      verify(factory, times(1)).build(any(), any());
      ((KimOntologyImpl) discovered.getOntologies().getFirst()).setSourceCode("next saved source");
      assertTrue(runtime.shouldRetryPreflight(discovered, service), "Real edits still invalidate admission");
    }
  }

  @Test void uncorrectedUserAdmissionCancelsIdenticalPendingServiceInput() throws Exception {
    var factory = mock(SemanticEnvironmentFactory.class);
    var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
    when(factory.dependencyRevision()).thenReturn("dependency");
    when(factory.capture(any())).thenAnswer(i -> {
      Worldview world = i.getArgument(0);
      return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
          SemanticEnvironmentFactory.sourceOrder(world), "dependency", SemanticEnvironmentFactory.CONFIGURATION_REVISION,
          world, CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    });
    when(factory.build(any(), any())).thenAnswer(i -> {
      CandidatePublicationCoordinator.Cancellation cancellation = i.getArgument(1);
      entered.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS));
      cancellation.checkpoint();
      throw new AssertionError("Superseded service compilation must observe cancellation");
    });
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(discovered, service); assertTrue(entered.await(5, TimeUnit.SECONDS));
      long generation = runtime.status().inputGeneration();
      try {
        var userAttempt = runtime.beginPreflight(world("saved source"), caller);
        assertTrue(userAttempt.retry());
        assertTrue(runtime.status().inputGeneration() > generation);
      } finally { finish.countDown(); }
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(0, runtime.status().acceptedGeneration());
      verify(factory, times(1)).build(any(), any());
    }
  }

  private org.integratedmodelling.klab.api.services.resources.ResourceSet failedBatch(ReasonerService root, String urn) {
    when(caller.getService(ResourcesService.class)).thenReturn(resources);
    var capabilities = mock(ResourcesService.Capabilities.class);
    when(capabilities.getServiceId()).thenReturn("request-resources");
    when(resources.capabilities(caller)).thenReturn(capabilities);
    var manager = mock(org.integratedmodelling.klab.services.scopes.ScopeManager.class);
    when(manager.collectMessagePayload(eq(caller), eq(Notification.class), any())).thenReturn(caller);
    doReturn(manager).when(root).getScopeManager();
    var changes = new org.integratedmodelling.klab.api.services.resources.ResourceSet();
    changes.getOntologies().add(new org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource(
        "request-resources", urn, "fixture", null,
        org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.ONTOLOGY, 0, false));
    return root.updateKnowledge(changes, caller);
  }

  private SemanticEnvironmentFactory pendingFactory(CountDownLatch entered, CountDownLatch finish) throws Exception {
    var factory = mock(SemanticEnvironmentFactory.class);
    when(factory.dependencyRevision()).thenReturn("dependency");
    when(factory.capture(any())).thenAnswer(i -> {
      Worldview world = i.getArgument(0);
      return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
          SemanticEnvironmentFactory.sourceOrder(world), "dependency", SemanticEnvironmentFactory.CONFIGURATION_REVISION,
          world, CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    });
    when(factory.build(any(), any())).thenAnswer(i -> {
      entered.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS));
      CandidatePublicationCoordinator.Cancellation cancellation = i.getArgument(1);
      cancellation.checkpoint();
      return new CandidatePublicationCoordinator.Result<>(mock(SemanticEnvironment.class),
          CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of());
    });
    return factory;
  }

  @Test void rejectedPrivateBatchLeavesActualPendingServiceCandidateAndDiagnosticsIntact() throws Exception {
    var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
    var factory = pendingFactory(entered, finish);
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(discovered, service); assertTrue(entered.await(5, TimeUnit.SECONDS));
      long generation = runtime.status().inputGeneration();
      try {
        var result = failedBatch(reasoner(runtime), "private-only");
        assertTrue(result.getNotifications().stream().anyMatch(n -> n.getMessage().contains("private-only")));
        assertEquals(generation, runtime.status().inputGeneration());
        assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, runtime.status().beanStatus());
        assertTrue(runtime.status().diagnostics().isEmpty());
        verify(resources).list(Worldview.class, null);
      } finally { finish.countDown(); }
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(1, runtime.status().acceptedGeneration());
      verify(factory, times(1)).build(any(), any());
    }
  }

  @Test void rejectedRequestStillRejectsIndependentlyChangedInvalidServiceSource() throws Exception {
    var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
    var factory = pendingFactory(entered, finish);
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(discovered, service); assertTrue(entered.await(5, TimeUnit.SECONDS));
      long generation = runtime.status().inputGeneration();
      try {
        ((KimOntologyImpl) discovered.getOntologies().getFirst()).setSourceCode("invalid public saved change");
        discovered.getNotifications().add(Notification.error("independently verified service source error"));
        var result = failedBatch(reasoner(runtime), "imod");
        assertTrue(result.getNotifications().stream().anyMatch(n -> n.getMessage().contains("Authoritative ontology unavailable: imod")));
        assertTrue(runtime.status().inputGeneration() > generation);
        assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID, runtime.status().beanStatus());
        assertTrue(runtime.status().diagnostics().contains("independently verified service source error"));
        assertFalse(runtime.status().diagnostics().stream().anyMatch(n -> n.contains("Authoritative ontology unavailable")));
      } finally { finish.countDown(); }
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(0, runtime.status().acceptedGeneration());
    }
  }

  @Test void failedBatchRetainsRequestErrorsWhenServiceDiscoveryIsUnavailable() throws Exception {
    when(resources.list(Worldview.class, null)).thenReturn(List.of());
    var runtime = mock(AcceptedSemanticRuntime.class);
    var result = failedBatch(reasoner(runtime), "private-only");
    assertTrue(result.getNotifications().stream().anyMatch(n -> n.getMessage().contains("private-only")));
    assertTrue(result.getNotifications().stream().anyMatch(n -> n.getMessage().contains("Service worldview refresh unavailable")));
    verifyNoMoreInteractions(ignoreStubs(runtime));
  }

  @Test void strategyReplacementRetainsAuthoritativePositionAndStrictOrderGate() throws Exception {
    var first = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl();
    first.setUrn("a"); first.setSourceCode("new saved a");
    var second = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl();
    second.setUrn("b"); second.setSourceCode("unchanged b");
    discovered.getObservationStrategies().addAll(List.of(first, second));
    var runtime = mock(AcceptedSemanticRuntime.class); var root = reasoner(runtime);
    var field = ReasonerService.class.getDeclaredField("worldview"); field.setAccessible(true); field.set(root, discovered);
    when(caller.getService(ResourcesService.class)).thenReturn(resources);
    var capabilities = mock(ResourcesService.Capabilities.class); when(capabilities.getServiceId()).thenReturn("resources");
    when(resources.capabilities(caller)).thenReturn(capabilities);
    var manager = mock(org.integratedmodelling.klab.services.scopes.ScopeManager.class);
    when(manager.collectMessagePayload(eq(caller), eq(Notification.class), any())).thenReturn(caller);
    doReturn(manager).when(root).getScopeManager();
    when(resources.retrieve("a", org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument.class, caller)).thenReturn(first);
    when(resources.retrieve("world", Worldview.class, caller)).thenReturn(discovered);
    when(runtime.beginPreflight(any(), same(service))).thenAnswer(i ->
        new AcceptedSemanticRuntime.PreflightAttempt(i.getArgument(0), "same-global-key", 2, false));
    var changes = new org.integratedmodelling.klab.api.services.resources.ResourceSet();
    changes.getObservationStrategies().add(new org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource(
        "resources", "a", "fixture", null,
        org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT, 0, false));
    var result = root.updateKnowledge(changes, caller);
    assertFalse(org.integratedmodelling.klab.api.utils.Utils.Resources.hasErrors(result));
    verify(runtime).beginPreflight(argThat(w -> w.getObservationStrategies().stream()
        .map(org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument::getUrn).toList().equals(List.of("a", "b"))), same(service));
  }

  @Test void successiveSavesStaleDiscoveryFailedEditsAndOverlappingPollsPreserveAcceptedState() throws Exception {
    var factory = mock(SemanticEnvironmentFactory.class);
    var firstSaveEntered = new CountDownLatch(1); var releaseFirstSave = new CountDownLatch(1);
    var builds = new java.util.concurrent.atomic.AtomicInteger();
    when(factory.dependencyRevision()).thenReturn("dependency");
    when(factory.capture(any())).thenAnswer(i -> {
      Worldview world = i.getArgument(0);
      return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
          SemanticEnvironmentFactory.sourceOrder(world), "dependency", SemanticEnvironmentFactory.CONFIGURATION_REVISION,
          world, CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    });
    when(factory.build(any(), any())).thenAnswer(i -> {
      CandidatePublicationCoordinator.Input<Worldview> input = i.getArgument(0);
      CandidatePublicationCoordinator.Cancellation cancellation = i.getArgument(1);
      builds.incrementAndGet();
      if (input.payload().getOntologies().getFirst().getSourceCode().equals("save one")) {
        firstSaveEntered.countDown(); assertTrue(releaseFirstSave.await(5, TimeUnit.SECONDS));
      }
      cancellation.checkpoint();
      return new CandidatePublicationCoordinator.Result<>(mock(SemanticEnvironment.class),
          CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of());
    });
    try (var runtime = new AcceptedSemanticRuntime(factory)) {
      runtime.submit(discovered, service); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(1, runtime.status().acceptedGeneration());
      var root = reasoner(runtime);
      try (var accepted = runtime.lease()) {
        var initialActor = accepted.state();
        long initialGeneration = runtime.status().inputGeneration();
        var privateFailure = failedBatch(root, "private-only");
        assertTrue(org.integratedmodelling.klab.api.utils.Utils.Resources.hasErrors(privateFailure));
        assertEquals(initialGeneration, runtime.status().inputGeneration());
        assertEquals(1, runtime.status().acceptedGeneration());
        try (var current = runtime.lease()) { assertSame(initialActor, current.state()); }

        // The request sees save one before public discovery; do not invalidate the valid state.
        assertTrue(org.integratedmodelling.klab.api.utils.Utils.Resources.hasErrors(root.loadKnowledge(world("save one"), caller)));
        assertEquals(initialGeneration, runtime.status().inputGeneration());
        ((KimOntologyImpl) discovered.getOntologies().getFirst()).setSourceCode("save one");
        root.loadKnowledge(world("save one"), caller);
        assertTrue(firstSaveEntered.await(5, TimeUnit.SECONDS));
        long pendingGeneration = runtime.status().inputGeneration();
        try {
          for (int i = 0; i < 3; i++) {
            root.loadKnowledge(world("save one"), caller);
            root.loadKnowledge(discovered, service);
            assertFalse(runtime.shouldRetryPreflight(discovered, service));
          }
          assertEquals(pendingGeneration, runtime.status().inputGeneration());
          assertEquals(1, runtime.status().acceptedGeneration());
          try (var current = runtime.lease()) { assertSame(initialActor, current.state()); }

          // A second save arrives while the first compiles, but service discovery is still old.
          assertTrue(org.integratedmodelling.klab.api.utils.Utils.Resources.hasErrors(root.loadKnowledge(world("save two"), caller)));
          assertEquals(pendingGeneration, runtime.status().inputGeneration());
          ((KimOntologyImpl) discovered.getOntologies().getFirst()).setSourceCode("save two");
          root.loadKnowledge(world("save two"), caller);
          long latestGeneration = runtime.status().inputGeneration();
          assertTrue(latestGeneration > pendingGeneration);
          root.loadKnowledge(discovered, service);
          root.loadKnowledge(world("save two"), caller);
          assertEquals(latestGeneration, runtime.status().inputGeneration());
        } finally { releaseFirstSave.countDown(); }
      }
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(2, runtime.status().acceptedGeneration());
      assertEquals(SemanticEnvironmentFactory.sources(world("save two")), runtime.status().acceptedSources());
      assertEquals(3, builds.get(), "Initial state, cancelled first save, latest save only");

      try (var accepted = runtime.lease()) {
        var validActor = accepted.state();
        long generation = runtime.status().inputGeneration();
        failedBatch(root, "private-only");
        assertEquals(generation, runtime.status().inputGeneration());
        assertEquals(2, runtime.status().acceptedGeneration());
        ((KimOntologyImpl) discovered.getOntologies().getFirst()).setSourceCode("invalid public save");
        discovered.getNotifications().add(Notification.error("public semantic error"));
        failedBatch(root, "imod");
        assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID, runtime.status().beanStatus());
        assertTrue(runtime.status().diagnostics().contains("public semantic error"));
        assertEquals(2, runtime.status().acceptedGeneration());
        assertEquals(SemanticEnvironmentFactory.sources(world("save two")), runtime.status().acceptedSources());
        try (var current = runtime.lease()) { assertSame(validActor, current.state()); }
      }
      discovered.getNotifications().clear();
      ((KimOntologyImpl) discovered.getOntologies().getFirst()).setSourceCode("repaired save three");
      root.loadKnowledge(world("repaired save three"), caller);
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(3, runtime.status().acceptedGeneration());
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus());
      assertEquals(SemanticEnvironmentFactory.sources(world("repaired save three")), runtime.status().acceptedSources());
      assertEquals(4, builds.get());
    } finally { releaseFirstSave.countDown(); }
  }
}
