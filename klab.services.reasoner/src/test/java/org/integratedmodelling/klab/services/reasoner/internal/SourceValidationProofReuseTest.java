package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

class SourceValidationProofReuseTest {
  @TempDir Path directory;
  private static final String BASE = "semantic-content-v2:" + "a".repeat(64);
  private final AtomicReference<String> authority = new AtomicReference<>("b".repeat(64));

  @BeforeAll static void configure() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  private SemanticEnvironment environment(String name) {
    var actor = mock(ReasonerService.class);
    when(actor.semanticContentFingerprint()).thenReturn(BASE);
    when(actor.portableCapabilities(anyString(), anyLong(), anyString())).thenReturn(() -> {});
    when(actor.sourceValidationDependencies(any(), any())).thenAnswer(call -> {
      SemanticDependencyRequest r = call.getArgument(0);
      return authority.get() == null ? SemanticDependencyWitness.unknown(r, "access denied")
          : new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
              SemanticDependencyWitness.Status.COMPLETE, r.expectedServiceId(), r.expectedKnowledgeRevision(),
              r.expectedBaseFingerprint(), r.inputHash(), List.of(new SemanticDependencyWitness.AuthorityUse(
                  "TEST", SemanticDependencyWitness.Kind.SNAPSHOT, authority.get(), Long.MAX_VALUE)), null);
    });
    return new SemanticEnvironment(actor, directory.resolve(name));
  }
  private KimNamespaceImpl source() {
    var ns = new KimNamespaceImpl(); ns.setUrn("staging.test"); ns.setProjectName("test");
    ns.setSourceCode("namespace staging.test; model test:Subject;"); ns.setLastUpdateTimestamp(1);
    var model = new KimModelImpl(); model.setUrn("staging.test.model"); model.setNamespace(ns.getUrn());
    ns.getStatements().add(model);
    return ns;
  }
  private MockedConstruction<DocumentSemanticValidator> validators() {
    return mockConstruction(DocumentSemanticValidator.class, (v, context) -> {
      when(v.validate(any())).thenReturn("test:WrongContext".equals(context.arguments().get(3))
          ? List.of(Notification.error("Context does not satisfy model")) : List.of());
      when(v.isSourceSemanticsComplete()).thenReturn(true);
      // A known static source footprint does not claim complete evaluator semantics.
      when(v.isSemanticObligationsComplete()).thenReturn(false);
      when(v.getSemanticObligations()).thenReturn(List.of("test:Subject"));
    });
  }
  private SemanticValidationResponse validate(AcceptedSemanticRuntime runtime, SemanticValidationRequest request) {
    return runtime.validate(request, SemanticValidationResponse.forRequest(request));
  }
  @Test void indexingProofIsReusedAcrossResolutionCyclesWithoutImportingProviderGrants() throws Exception {
    var current = new AtomicReference<>(environment("first"));
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current), "reasoner");
        var validators = validators()) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world()); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var source = source();
      source.getMetadata().put(ResourcesService.NAMESPACE_ADMISSION_METADATA, "untrusted provider receipt");
      var indexed = validate(runtime, SemanticValidationRequest.of(source, "1"));
      assertTrue(indexed.valid()); assertFalse(indexed.isSemanticObligationsComplete());
      indexed.getNotifications().add(Notification.error("caller mutation"));
      source.getMetadata().remove(ResourcesService.NAMESPACE_ADMISSION_METADATA);
      source.setLastUpdateTimestamp(2);
      var scope = AcceptedSemanticCycleTest.scope("alice", "context", "runtime");
      for (var cycle : List.of("first-resolution", "second-resolution")) {
        var binding = runtime.beginCycle(AcceptedSemanticCycleTest.request(null, cycle), scope);
        try {
          var reasoner = AcceptedSemanticCycleTest.facade(runtime, binding, cycle, scope);
          var response = reasoner.validateDocument(SemanticValidationRequest.of(source, "2"), scope);
          assertTrue(response.valid()); assertEquals("2", response.getDocumentVersion());
          assertFalse(response.isSemanticObligationsComplete());
          assertEquals(SemanticDependencyWitness.Status.UNKNOWN, response.getSemanticWitness().status());
        } finally { runtime.releaseCycle(AcceptedSemanticCycleTest.request(binding, cycle), scope); }
      }
      assertEquals(1, validators.constructed().size(), "Unchanged source must be validated only once across cycles");
      assertEquals(2L, runtime.sourceValidationMetrics().get("hits"));
      verify(current.get().actor(), times(3)).sourceValidationDependencies(any(), any());
    }
  }
  @Test void changedSyntaxTextDependenciesAndGenerationCannotReuseOldProof() throws Exception {
    var current = new AtomicReference<>(environment("first"));
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current), "reasoner");
        var validators = validators()) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world()); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var source = source(); var request = SemanticValidationRequest.of(source, "1");
      assertTrue(validate(runtime, request).valid());
      ((KimModelImpl) source.getStatements().getFirst()).setUrn("staging.test.changed");
      assertTrue(validate(runtime, request).valid()); // Same text, different parsed source.
      source.setSourceCode(source.getSourceCode() + " "); assertTrue(validate(runtime, request).valid());
      authority.set("c".repeat(64)); assertTrue(validate(runtime, request).valid());
      authority.set(null); assertTrue(validate(runtime, request).valid());
      assertTrue(validate(runtime, request).valid()); // Unknown proof cannot be cached.
      assertEquals(6, validators.constructed().size());
      authority.set("c".repeat(64));
      current.set(environment("second"));
      var world = AcceptedSemanticObserverScopeTest.world();
      ((KimOntologyImpl) world.getOntologies().getFirst()).setSourceCode("ontology w in domain root; // revised");
      runtime.submit(world); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertTrue(validate(runtime, request).valid()); assertEquals(7, validators.constructed().size());
    }
  }
  @Test void executableContextsRemainCheckedAndCannotUseStaticProofs() throws Exception {
    var current = new AtomicReference<>(environment("first"));
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current), "reasoner");
        var validators = validators()) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world()); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var request = SemanticValidationRequest.of(source(), "1");
      assertTrue(validate(runtime, request).valid());
      request.setExecutableModels(true); request.setExecutionContext("test:RightContext");
      assertTrue(validate(runtime, request).valid());
      request.setExecutionContext("test:WrongContext"); assertFalse(validate(runtime, request).valid());
      assertEquals(3, validators.constructed().size());
      assertEquals(2L, runtime.sourceValidationMetrics().get("executableValidations"));
    }
  }

  @Test void localSourceReuseNeverUpgradesAnUnversionedPortableWitness() throws Exception {
    var current = new AtomicReference<>(environment("mutable"));
    when(current.get().actor().semanticDependencies(any(), any())).thenAnswer(call -> {
      SemanticDependencyRequest r = call.getArgument(0);
      return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
          SemanticDependencyWitness.Status.REVALIDATION_REQUIRED, r.expectedServiceId(), r.expectedKnowledgeRevision(),
          r.expectedBaseFingerprint(), r.inputHash(), List.of(new SemanticDependencyWitness.AuthorityUse(
              "TEST", SemanticDependencyWitness.Kind.UNVERSIONED, null, 0)), "fresh provider proof required");
    });
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current), "reasoner");
        var validators = mockConstruction(DocumentSemanticValidator.class, (v, context) -> {
          when(v.validate(any())).thenReturn(List.of());
          when(v.isSourceSemanticsComplete()).thenReturn(true);
          when(v.isSemanticObligationsComplete()).thenReturn(true);
          when(v.getSemanticObligations()).thenReturn(List.of("test:Subject"));
        })) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world()); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var request = SemanticValidationRequest.of(source(), "1");
      assertEquals(SemanticDependencyWitness.Status.REVALIDATION_REQUIRED, validate(runtime, request).getSemanticWitness().status());
      assertEquals(SemanticDependencyWitness.Status.REVALIDATION_REQUIRED, validate(runtime, request).getSemanticWitness().status());
      assertEquals(1, validators.constructed().size());
      assertEquals(1L, runtime.sourceValidationMetrics().get("hits"));
    }
  }

  @Test void expiredAndIncompleteProofsCannotAuthorizeReuse() {
    var cache = new SourceValidationProofCache();
    var request = SemanticValidationRequest.of(source(), "1");
    var response = SemanticValidationResponse.forRequest(request);
    response.setStatus(SemanticValidationResponse.Status.COMPLETE);
    response.setSourceSemanticsComplete(true);
    var fresh = new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
        SemanticDependencyWitness.Status.COMPLETE, "reasoner", 1, BASE, "a".repeat(64),
        List.of(new SemanticDependencyWitness.AuthorityUse("TEST", SemanticDependencyWitness.Kind.CAPTURED,
            "b".repeat(64), System.currentTimeMillis() + 60000)), null);
    var expired = new SemanticDependencyWitness(fresh.contractVersion(), fresh.status(), fresh.serviceId(),
        fresh.knowledgeRevision(), fresh.baseFingerprint(), fresh.inputHash(),
        List.of(new SemanticDependencyWitness.AuthorityUse("TEST", SemanticDependencyWitness.Kind.CAPTURED,
            "b".repeat(64), 1)), null);
    var key = SourceValidationProofCache.key(request);
    cache.put(key, response, fresh);
    assertNull(cache.get(key, terms -> expired));
    cache.put(key, response, expired);
    assertNull(cache.get(key, terms -> fresh));
    response.setSourceSemanticsComplete(false); cache.put(key, response, fresh);
    assertNull(cache.get(key, terms -> fresh));
    response.setSourceSemanticsComplete(true); response.getNotifications().add(Notification.error("invalid source"));
    cache.put(key, response, fresh); assertNull(cache.get(key, terms -> fresh));
  }

  @Test void actorLocalProofTracksClosureAndExpiryWithoutChangingPortableProof() throws Exception {
    var actor = mock(ReasonerService.class);
    var owl = mock(org.integratedmodelling.klab.services.reasoner.owl.OWL.class);
    when(owl.sourceValidationFingerprint(anyList())).thenReturn("first module");
    var catalog = mock(CapturedAuthorityCatalog.class);
    var owlField = ReasonerService.class.getDeclaredField("owl"); owlField.setAccessible(true); owlField.set(actor, owl);
    var catalogField = ReasonerService.class.getDeclaredField("capturedAuthorities"); catalogField.setAccessible(true); catalogField.set(actor, catalog);
    when(catalog.revision()).thenReturn("owned corpus");
    when(catalog.sourceCaptureExpiresAt()).thenReturn(Long.MAX_VALUE);
    var request = new SemanticDependencyRequest("reasoner", 1, BASE, List.of("test:Subject"));
    var portable = new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
        SemanticDependencyWitness.Status.REVALIDATION_REQUIRED, "reasoner", 1, BASE, request.inputHash(),
        List.of(new SemanticDependencyWitness.AuthorityUse("TEST", SemanticDependencyWitness.Kind.UNVERSIONED,null,0)), "mutable");
    when(actor.semanticDependencies(eq(request), any())).thenReturn(portable);
    when(actor.sourceValidationDependencies(eq(request), any())).thenCallRealMethod();
    var first = actor.sourceValidationDependencies(request, null);
    assertTrue(first.completeAt(System.currentTimeMillis()));
    assertTrue(SemanticDependencyWitness.revalidates(first, actor.sourceValidationDependencies(request, null)));
    when(owl.sourceValidationFingerprint(anyList())).thenReturn("changed module");
    assertFalse(SemanticDependencyWitness.revalidates(first, actor.sourceValidationDependencies(request, null)));
    when(catalog.sourceCaptureExpiresAt()).thenReturn(1L);
    assertSame(portable, actor.sourceValidationDependencies(request, null));
    assertSame(portable, actor.semanticDependencies(request, null));
    when(actor.semanticDependencies(eq(request), any())).thenThrow(new SecurityException("denied"));
    assertThrows(SecurityException.class, () -> actor.sourceValidationDependencies(request, null));
  }
}
