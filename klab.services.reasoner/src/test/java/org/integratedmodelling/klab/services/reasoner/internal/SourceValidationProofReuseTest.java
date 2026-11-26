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

  @Test void routingStampDoesNotChangeStaticNamespaceSyntax() {
    var ns = source();
    var request = SemanticValidationRequest.of(ns, "1");
    var staticKey = SourceValidationProofCache.key(request);
    ns.setServiceId("resources");
    assertEquals(staticKey, SourceValidationProofCache.key(request));
    request.setExecutableModels(true);
    var executableKey = SourceValidationProofCache.key(request);
    ns.setServiceId(null);
    assertNotEquals(executableKey, SourceValidationProofCache.key(request));
  }

  @Test void namespaceWithNoSemanticReadsReusesItsStaticProof() throws Exception {
    var current = new AtomicReference<>(environment("no-reads"));
    var ns = new KimNamespaceImpl();
    ns.setUrn("test.empty"); ns.setSourceCode("namespace test.empty;");
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current), "reasoner");
        var visitors = mockConstruction(DocumentSemanticValidator.class, (visitor, context) -> {
          when(visitor.validate(any())).thenReturn(List.of());
          when(visitor.isSourceSemanticsComplete()).thenReturn(true);
          when(visitor.getSemanticObligations()).thenReturn(List.of());
        })) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world());
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var request = SemanticValidationRequest.of(ns, "1");
      var first = validate(runtime, request);
      assertTrue(first.valid());
      assertFalse(first.isSemanticObligationsComplete());
      assertTrue(validate(runtime, request).valid());
      assertEquals(1, visitors.constructed().size());
      verify(current.get().actor(), never()).sourceValidationDependencies(any(), any());
    }
  }

  @Test void sourcePrincipalSurvivesLoginAndChangesWithRealmAccountOrMembership() {
    var user = new org.integratedmodelling.common.authentication.UserIdentityImpl();
    user.setUsername("alice"); user.setServerUrl("https://realm.example"); user.setId("first");
    var initial = SourceValidationRecords.principal(user);
    assertNotNull(initial);
    user.setId("renewed");
    assertEquals(initial, SourceValidationRecords.principal(user));
    user.setUsername("bob");
    assertNotEquals(initial, SourceValidationRecords.principal(user));
    user.setUsername("alice"); user.setServerUrl("https://another.example");
    assertNotEquals(initial, SourceValidationRecords.principal(user));
    user.setServerUrl("https://realm.example");
    var group = mock(org.integratedmodelling.klab.api.identities.Group.class);
    when(group.getId()).thenReturn("research");
    when(group.getName()).thenReturn("Research");
    user.setGroups(Set.of(group));
    var member = SourceValidationRecords.principal(user);
    assertNotNull(member);
    assertNotEquals(initial, member);
    user.setAnonymous(true);
    assertNull(SourceValidationRecords.principal(user));
  }

  @Test void durableRecordRechecksDependenciesAndExactInputsInAnotherInstance() throws Exception {
    var doc = new KimNamespaceImpl();
    doc.setUrn("test.ns");
    doc.setSourceCode("namespace test.ns;");
    var request = SemanticValidationRequest.of(doc, "1");
    var response = SemanticValidationResponse.forRequest(request);
    response.setStatus(SemanticValidationResponse.Status.COMPLETE);
    response.setSourceSemanticsComplete(true);
    response.setSemanticObligations(List.of("test:Region"));
    var deps = new SemanticDependencyRequest("reasoner", 1, BASE, response.getSemanticObligations());
    var proof = new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
        SemanticDependencyWitness.Status.COMPLETE, "reasoner", 1, BASE, deps.inputHash(), List.of(), null);
    var syntax = SourceValidationProofCache.key(request);
    var key = SourceValidationRecords.key("reasoner", BASE, "user", syntax);
    assertNotNull(key);
    new SourceValidationRecords(directory).write(key, response, proof);
    var restarted = new SourceValidationRecords(directory);
    var newGeneration = new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
        SemanticDependencyWitness.Status.COMPLETE, "reasoner", 2, BASE, deps.inputHash(), List.of(), null);
    var hit = restarted.read(key, response.getSourceHash(), terms -> {
      assertEquals(response.getSemanticObligations(), terms);
      return newGeneration;
    });
    assertNotNull(hit);
    assertEquals(2, hit.proof().knowledgeRevision());
    assertNull(restarted.read(key, response.getSourceHash(), terms -> SemanticDependencyWitness.unknown(deps, "changed")));
    assertNull(restarted.read(key, "different-file", terms -> proof));
    assertNull(restarted.read(SourceValidationRecords.key("reasoner", BASE, "other", syntax),
        response.getSourceHash(), terms -> proof));
    doc.setScenario(true);
    assertNotEquals(key, SourceValidationRecords.key("reasoner", BASE, "user", SourceValidationProofCache.key(request)));
    java.nio.file.Files.writeString(directory.resolve(key + ".json"), "partial");
    assertNull(restarted.read(key, response.getSourceHash(), terms -> proof));
  }

  @Test void ontologyReadHasADistinctSourceKeyAndExecutableOrDiagnosticRequestsCannotUseIt() {
    var ontology = new KimOntologyImpl();
    ontology.setUrn("test");
    ontology.setSourceCode("ontology test in domain root;");
    var req = SemanticValidationRequest.of(ontology, "1");
    var original = SourceValidationProofCache.key(req);
    assertNotNull(original);
    req.setExecutableModels(true);
    assertNull(SourceValidationProofCache.key(req));
    req.setExecutableModels(false);
    req.setDiagnosticsOnly(true);
    assertNull(SourceValidationProofCache.key(req));
    req.setDiagnosticsOnly(false);
    ontology.setSourceCode("ontology test in domain root; identity NewIdentity;");
    assertNotEquals(original, SourceValidationProofCache.key(req));
  }

  @Test void proofIdentityIgnoresSetOrderAndFileTimesButPreservesStatementOrder() {
    var doc = new KimNamespaceImpl();
    doc.setUrn("test.ns");
    doc.setSourceCode("namespace test.ns;");
    doc.setDisjointNamespaces(new LinkedHashSet<>(List.of("a", "b")));
    var first = new KimModelImpl(); first.setUrn("test.first");
    var second = new KimModelImpl(); second.setUrn("test.second");
    doc.getStatements().add(first);
    doc.getStatements().add(second);
    var request = SemanticValidationRequest.of(doc, "1");
    var initial = SourceValidationProofCache.key(request);
    doc.setCreationTimestamp(200);
    doc.setLastUpdateTimestamp(300);
    doc.setDisjointNamespaces(new LinkedHashSet<>(List.of("b", "a")));
    assertEquals(initial, SourceValidationProofCache.key(request));
    Collections.swap(doc.getStatements(), 0, 1);
    assertNotEquals(initial, SourceValidationProofCache.key(request));
  }

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
    var actor = current.get().actor();
    when(actor.semanticDependencies(any(), any())).thenAnswer(call ->
        actor.sourceValidationDependencies(call.getArgument(0), call.getArgument(1)));
    try (var runtime = new AcceptedSemanticRuntime(AcceptedSemanticObserverScopeTest.factory(current), "reasoner");
        var validators = mockConstruction(DocumentSemanticValidator.class, (validator, construction) -> {
          when(validator.validate(any())).thenReturn("test:WrongContext".equals(construction.arguments().get(3))
              ? List.of(Notification.error("Context does not satisfy model")) : List.of());
          when(validator.isSourceSemanticsComplete()).thenReturn(true);
          when(validator.isSemanticObligationsComplete()).thenReturn(true);
          when(validator.getSemanticObligations()).thenReturn(List.of("test:Subject"));
        })) {
      runtime.submit(AcceptedSemanticObserverScopeTest.world()); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var request = SemanticValidationRequest.of(source(), "1");
      assertTrue(validate(runtime, request).valid());
      request.setExecutableModels(true); request.setExecutionContext("test:RightContext");
      assertTrue(validate(runtime, request).valid());
      assertTrue(validate(runtime, request).valid());
      assertEquals(1L, runtime.sourceValidationMetrics().get("executableProofHits"));
      request.setExecutionContext("test:WrongContext"); assertFalse(validate(runtime, request).valid());
      request.setExecutionContext("test:RightContext");
      request.setClassifierPreparableDependencies(true);
      assertTrue(validate(runtime, request).valid());
      request.setClassifierPreparableDependencies(false);
      authority.set("c".repeat(64));
      assertTrue(validate(runtime, request).valid());
      assertEquals(5, validators.constructed().size());
      assertEquals(4L, runtime.sourceValidationMetrics().get("executableValidations"));
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
