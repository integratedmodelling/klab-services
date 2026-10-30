package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.*;

class AuthorityDependencyCacheTest {
  private AuthorityDependencySupport support(AuthorityDependencySupportTest.World w) {
    return new AuthorityDependencySupport(w.owl);
  }
  private long count(AuthorityDependencySupportTest.World w, String metric) {
    return w.owl.authorityDependencyCache().metrics().get(metric);
  }

  @Test void positiveAndProvedEmptyResultsAreReusedAcrossWrapperInstances() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var bindings = List.of(w.binding("TAXA", "AuthorityRoot"));
      var positive = support(w).closure(List.of("w:Code"), bindings, "recipe");
      var empty = support(w).closure(List.of("w:Tree"), bindings, "recipe");
      assertEquals(Set.of("TAXA"), positive.authorities());
      assertTrue(empty.known()); assertTrue(empty.authorities().isEmpty());
      assertEquals(positive, support(w).closure(List.of("w:Code"), bindings, "recipe"));
      assertEquals(empty, support(w).closure(List.of("w:Tree"), bindings, "recipe"));
      assertEquals(2, count(w, "computations")); assertEquals(2, count(w, "hits"));
      assertThrows(UnsupportedOperationException.class, () -> positive.authorities().add("OTHER"));
    }
  }

  @Test void unknownAndUnsupportedMeaningsAreNeverIndependentCacheHits() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var bindings = List.of(w.binding("TAXA", "AuthorityRoot"));
      for (int i = 0; i < 2; i++)
        assertFalse(support(w).closure(List.of("missing:Tree"), bindings, "recipe").known());
      assertEquals(0, count(w, "entries")); assertEquals(0, count(w, "hits"));
      assertEquals(2, count(w, "computations"));
      assertTrue(support(w).closure(List.of("w:Tree"), bindings, "recipe").known());
      assertFalse(support(w).closure(List.of(" w:Tree "), bindings, "recipe").known());
      assertFalse(support(w).closure(List.of("any w:Tree"), bindings, "recipe").known());
    }
  }

  @Test void axiomAddRemoveAndImportChangesInvalidateNegativeAndPositiveProofs() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var bindings = List.of(w.binding("TAXA", "AuthorityRoot"));
      assertTrue(support(w).closure(List.of("w:Tree"), bindings).authorities().isEmpty());
      var axiom = w.factory.getOWLSubClassOfAxiom(w.c("Tree"), w.c("AuthorityRoot"));
      w.owl.manager.addAxiom(w.ontology.getOWLOntology(), axiom);
      assertEquals(Set.of("TAXA"), support(w).closure(List.of("w:Tree"), bindings).authorities());
      w.owl.manager.removeAxiom(w.ontology.getOWLOntology(), axiom);
      assertTrue(support(w).closure(List.of("w:Tree"), bindings).authorities().isEmpty());
      var extra = w.owl.manager.createOntology(IRI.create("urn:extra"));
      w.owl.manager.addAxiom(extra, axiom);
      w.owl.manager.applyChange(new AddImport(w.ontology.getOWLOntology(),
          w.factory.getOWLImportsDeclaration(IRI.create("urn:extra"))));
      w.owl.flush();
      assertEquals(Set.of("TAXA"), support(w).closure(List.of("w:Tree"), bindings).authorities());
    }
  }

  @Test void sameIdOntologyReplacementAndRecipeOrProviderReplacementCannotReuseOldProofs() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var binding = w.binding("TAXA", "AuthorityRoot"); var bindings = List.of(binding);
      var extra = w.owl.manager.createOntology(IRI.create("urn:replace"));
      assertTrue(support(w).closure(List.of("w:Tree"), bindings, "one").known());
      long before = count(w, "computations");
      w.owl.manager.removeOntology(extra);
      w.owl.manager.createOntology(IRI.create("urn:replace"));
      assertTrue(support(w).closure(List.of("w:Tree"), bindings, "one").known());
      assertEquals(before + 1, count(w, "computations"));
      support(w).closure(List.of("w:Tree"), bindings, "two");
      var replacement = new AuthorityBindings.Binding(binding.request(), mock(Authority.class), binding.id());
      support(w).closure(List.of("w:Tree"), List.of(replacement), "two");
      assertEquals(before + 3, count(w, "computations"));
      var other = w.binding("TAXA", "OtherRoot");
      assertTrue(support(w).closure(List.of("w:Code"), List.of(other), "two").authorities().isEmpty());
    }
  }

  @Test void generatedCanonicalUrnRenameInvalidatesRegistryOnlyChanges() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var concept = (org.integratedmodelling.common.knowledge.ConceptImpl) w.ontology.getConcept("Tree");
      w.owl.setConceptUrn(concept, "w:Tree of w:Tree");
      var bindings = List.of(w.binding("TAXA", "AuthorityRoot"));
      assertTrue(support(w).closure(List.of("w:Tree of w:Tree"), bindings).known());
      w.owl.setConceptUrn(concept, "w:Tree of w:OtherRoot");
      assertFalse(support(w).closure(List.of("w:Tree of w:Tree"), bindings).known());
      assertTrue(support(w).closure(List.of("w:Tree of w:OtherRoot"), bindings).known());
    }
  }

  @Test void bufferingFlushRetiresProofComputedBetweenManagerEditAndFlush() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var buffering = new org.semanticweb.HermiT.Reasoner.ReasonerFactory()
          .createReasoner(w.ontology.getOWLOntology());
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(w.owl, buffering);
      try {
        var request = new Authority.ConfigurationRequest("w", "TAXA", "w:AuthorityRoot",
            Map.of("urn", "provider", "semanticBoundaries", Map.of("A", "w:BoundaryA", "B", "w:BoundaryB")));
        var bindings = List.of(new AuthorityBindings.Binding(request, mock(Authority.class), "bridge"));
        assertTrue(support(w).closure(List.of("w:Family"), bindings).known());
        w.owl.manager.addAxiom(w.ontology.getOWLOntology(), w.factory.getOWLSubClassOfAxiom(
            w.factory.getOWLObjectIntersectionOf(w.c("BoundaryA"), w.c("BoundaryB")), w.c("Family")));
        support(w).closure(List.of("w:Family"), bindings);
        long before = count(w, "computations");
        w.owl.flush();
        var after = support(w).closure(List.of("w:Family"), bindings);
        assertTrue(after.known()); assertEquals(Set.of("TAXA"), after.authorities());
        assertEquals(before + 1, count(w, "computations"));
      } finally { buffering.dispose(); }
    }
  }

  @Test void mutationDuringProofFailsClosedAndDoesNotPublishAnAnswer() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var spy = spy(w.owl);
      doAnswer(call -> {
        w.owl.manager.addAxiom(w.ontology.getOWLOntology(),
            w.factory.getOWLSubClassOfAxiom(w.c("Tree"), w.c("AuthorityRoot")));
        return call.callRealMethod();
      }).when(spy).getSubClasses(any(OWLClassExpression.class), eq(false));
      var result = new AuthorityDependencySupport(spy).closure(List.of("w:Tree"), List.of(w.binding("TAXA", "AuthorityRoot")));
      assertFalse(result.known()); assertTrue(result.reason().contains("changed"));
      assertEquals(0, count(w, "entries"));
    }
  }

  @Test void stagingQuarantineResetAndOtherActorsCannotReuseWarmProofs() throws Exception {
    try (var a = new AuthorityDependencySupportTest.World((world, axioms) -> {});
         var b = new AuthorityDependencySupportTest.World((world, axioms) ->
             axioms.add(world.factory.getOWLSubClassOfAxiom(world.c("Tree"), world.c("AuthorityRoot"))))) {
      var bindings = List.of(a.binding("TAXA", "AuthorityRoot"));
      assertTrue(support(a).closure(List.of("w:Tree"), bindings).authorities().isEmpty());
      assertEquals(Set.of("TAXA"), support(b).closure(List.of("w:Tree"), bindings).authorities());
      try (var stage = a.owl.stageDeclarations()) { assertFalse(support(a).closure(List.of("w:Tree"), bindings).known()); }
      a.owl.quarantineDerivedClosure(); assertFalse(support(a).closure(List.of("w:Tree"), bindings).known());
      a.owl.authorityDependencyCache().reset(); assertEquals(0, count(a, "entries"));
    }
  }

  @Test void cacheHasAHardEntryBoundWithoutExpiry() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      for (int i = 0; i <= AuthorityDependencySupport.Cache.CAPACITY; i++)
        assertTrue(support(w).closure(List.of("w:Tree"), List.of(), "recipe-" + i).known());
      assertEquals(AuthorityDependencySupport.Cache.CAPACITY, count(w, "entries"));
      long before = count(w, "computations");
      support(w).closure(List.of("w:Tree"), List.of(), "recipe-0");
      assertEquals(before + 1, count(w, "computations"));
    }
  }

  @Test void structuralHitsStillCheckRequestPermissionsAndCurrentAuthorityProof() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var actor = mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class, CALLS_REAL_METHODS);
      var catalog = mock(org.integratedmodelling.klab.services.reasoner.internal.CapturedAuthorityCatalog.class);
      var bindings = mock(AuthorityBindings.class);
      when(bindings.snapshot()).thenReturn(List.of(w.binding("TAXA", "AuthorityRoot")));
      var scope = mock(org.integratedmodelling.klab.api.scope.Scope.class);
      var denied = mock(org.integratedmodelling.klab.api.scope.Scope.class);
      var world = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
      var version = org.integratedmodelling.klab.api.data.Version.create("1.0.0");
      var provider = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor(
          "fixture.provider", version, true, true, List.of(), List.of());
      var definition = new org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding(
          "TAXA", "w:AuthorityRoot", "w", 0, 1, "hash", provider, "fixture.provider", version, null);
      world.getAuthorityBindings().add(definition);
      inject(actor, "owl", w.owl); inject(actor, "worldview", world);
      inject(actor, "semanticOnly", true); inject(actor, "authorityBindings", bindings);
      inject(actor, "capturedAuthorities", catalog);
      inject(actor, "semanticProofRestoration", new org.integratedmodelling.klab.services.reasoner.internal.SemanticProofRestoration());
      var caps = mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
      var recipe = "semantic-content-v2:" + "1".repeat(64);
      when(caps.getServiceId()).thenReturn("service"); when(caps.getKnowledgeRevision()).thenReturn(1L);
      when(caps.getSemanticContentFingerprint()).thenReturn(recipe);
      doReturn(caps).when(actor).capabilities(any(org.integratedmodelling.klab.api.scope.Scope.class));
      doReturn(w.ontology.getConcept("Code")).when(actor).resolveConcept("w:Code");
      when(catalog.authorized(definition, scope)).thenReturn(true);
      var snapshot = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.AuthorityUse(
          "TAXA", org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Kind.SNAPSHOT,
          "2".repeat(64), Long.MAX_VALUE);
      var mutable = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.AuthorityUse(
          "TAXA", org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Kind.UNVERSIONED, null, 0);
      when(catalog.dependencyProof(definition)).thenReturn(snapshot, mutable);
      var request = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest(
          "service", 1, recipe, List.of("w:Code"));
      assertTrue(actor.semanticDependencies(request, scope).completeAt(System.currentTimeMillis()));
      assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.REVALIDATION_REQUIRED,
          actor.semanticDependencies(request, scope).status());
      assertEquals(1, count(w, "computations")); assertEquals(1, count(w, "hits"));
      assertThrows(SecurityException.class, () -> actor.semanticDependencies(request, denied));
      verify(catalog, times(2)).authorized(definition, scope);
      verify(catalog).authorized(definition, denied);
      verify(catalog, times(2)).dependencyProof(definition);
    }
  }

  private static void inject(Object target, String name, Object value) throws Exception {
    var field = org.integratedmodelling.klab.services.reasoner.ReasonerService.class.getDeclaredField(name);
    field.setAccessible(true); field.set(target, value);
  }
}
