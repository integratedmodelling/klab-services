package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

class OWLInferenceCacheTest {
  @Test void nonBufferingOntologyEditsAlsoInvalidateCachedAnswers() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var relation = w.factory.getOWLSubClassOfAxiom(w.c("Tree"), w.c("AuthorityRoot"));
      assertFalse(w.owl.isEntailed(relation));
      w.owl.manager.addAxiom(w.ontology.getOWLOntology(), relation);
      assertTrue(w.owl.isEntailed(relation));
      w.owl.manager.removeAxiom(w.ontology.getOWLOntology(), relation);
      assertFalse(w.owl.isEntailed(relation));
    }
  }
  @Test void targetedStrictSubsumptionMatchesTheClassifiedHierarchy() throws Exception {
    var owl = new OWL(mock(Scope.class));
    owl.manager = OWLManager.createOWLOntologyManager();
    var ont = owl.manager.createOntology();
    var f = owl.manager.getOWLDataFactory();
    var names = List.of("A", "B", "AliasA", "C", "Bottom").stream()
        .map(n -> f.getOWLClass(IRI.create("urn:strict:" + n))).toList();
    for (var c : names) owl.manager.addAxiom(ont, f.getOWLDeclarationAxiom(c));
    owl.manager.addAxiom(ont, f.getOWLSubClassOfAxiom(names.get(0), names.get(1)));
    owl.manager.addAxiom(ont, f.getOWLEquivalentClassesAxiom(names.get(0), names.get(2)));
    owl.manager.addAxiom(ont, f.getOWLEquivalentClassesAxiom(names.get(4), f.getOWLNothing()));
    var factory = new org.semanticweb.HermiT.Reasoner.ReasonerFactory();
    var delegate = factory.createReasoner(ont);
    var reference = factory.createReasoner(ont);
    var field = OWL.class.getDeclaredField("reasoner");
    field.setAccessible(true);
    field.set(owl, delegate);
    try {
      for (var parent : names) {
        var expected = reference.getSubClasses(parent, false);
        for (var child : names) {
          if (!child.equals(parent))
            assertEquals(expected.containsEntity(child), owl.properSubclass(child, parent), child + " < " + parent);
        }
      }
      for (var name : names) assertFalse(owl.properSubclass(name, name));
      var change = f.getOWLSubClassOfAxiom(names.get(3), names.get(1));
      owl.manager.addAxiom(ont, change);
      assertEquals(reference.getSubClasses(names.get(1), false).containsEntity(names.get(3)),
          owl.properSubclass(names.get(3), names.get(1)));
      owl.flushReasoner();
      assertTrue(owl.properSubclass(names.get(3), names.get(1)));
    } finally {
      delegate.dispose();
      reference.dispose();
    }
  }

  @Test void liveClauseBoundsReuseStopsOnManagerEditsAndFailedComputation() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var root = w.c("Tree");
      var loads = new java.util.concurrent.atomic.AtomicInteger();
      java.util.function.Supplier<java.util.List<OWLSemanticClauseSupport.Bound>> load = () -> {
        loads.incrementAndGet();
        return List.of();
      };
      w.owl.clauseBounds(root, load, () -> {});
      w.owl.clauseBounds(root, load, () -> {});
      var relation = w.factory.getOWLSubClassOfAxiom(root, w.c("AuthorityRoot"));
      w.owl.manager.addAxiom(w.ontology.getOWLOntology(), relation);
      assertThrows(IllegalStateException.class,
          () -> w.owl.clauseBounds(root, () -> { throw new IllegalStateException(); }, () -> {}));
      w.owl.clauseBounds(root, load, () -> {});
      w.owl.manager.addAxiom(w.ontology.getOWLOntology(), relation);
      w.owl.clauseBounds(root, load, () -> {});
      assertEquals(2, loads.get());
      var extra = w.owl.manager.createOntology();
      w.owl.clauseBounds(root, load, () -> {});
      w.owl.manager.removeOntology(extra);
      w.owl.clauseBounds(root, load, () -> {});
      assertEquals(4, loads.get());
    }
  }

  @Test void unchangedClosureReusesPositiveAndNegativeAnswersButFlushInvalidatesBoth() throws Exception {
    var owl = new OWL(mock(Scope.class));
    var manager = OWLManager.createOWLOntologyManager();
    var ontology = manager.createOntology();
    var factory = manager.getOWLDataFactory();
    var a = factory.getOWLClass(IRI.create("urn:test:A"));
    var b = factory.getOWLClass(IRI.create("urn:test:B"));
    var subclass = factory.getOWLSubClassOfAxiom(a, b);
    manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(a));
    manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(b));
    var delegate = spy(new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(ontology));
    var field = OWL.class.getDeclaredField("reasoner");
    field.setAccessible(true); field.set(owl, delegate);
    try {
      for (int i=0; i<20; i++) {
        assertFalse(owl.isEntailed(subclass)); assertTrue(owl.isSatisfiable(a));
        owl.flushReasoner(); owl.flush(); // No changes: preserve both caches.
      }
      verify(delegate, times(1)).isEntailed(subclass);
      verify(delegate, times(1)).isSatisfiable(a);
      verify(delegate, never()).flush();
      manager.addAxiom(ontology, subclass);
      var impossible = factory.getOWLSubClassOfAxiom(a, factory.getOWLNothing());
      manager.addAxiom(ontology, impossible);
      owl.flushReasoner();
      assertTrue(owl.isEntailed(subclass)); assertFalse(owl.isSatisfiable(a));
      manager.removeAxiom(ontology, subclass); manager.removeAxiom(ontology, impossible);
      owl.flush();
      assertFalse(owl.isEntailed(subclass)); assertTrue(owl.isSatisfiable(a));
      verify(delegate, times(3)).isEntailed(subclass);
      verify(delegate, times(3)).isSatisfiable(a);
      owl.quarantineDerivedClosure();
      assertThrows(RuntimeException.class, () -> owl.isEntailed(subclass));
      assertThrows(RuntimeException.class, () -> owl.isSatisfiable(a));
    } finally { delegate.dispose(); }
  }

  @Test void failedInferenceIsNeverCached() throws Exception {
    var owl = new OWL(mock(Scope.class));
    var delegate = mock(OWLReasoner.class);
    var field = OWL.class.getDeclaredField("reasoner");
    field.setAccessible(true); field.set(owl, delegate);
    var axiom = OWLManager.getOWLDataFactory().getOWLSubClassOfAxiom(
        OWLManager.getOWLDataFactory().getOWLThing(), OWLManager.getOWLDataFactory().getOWLNothing());
    when(delegate.isEntailed(axiom)).thenThrow(new IllegalStateException("unavailable")).thenReturn(false);
    assertThrows(IllegalStateException.class, () -> owl.isEntailed(axiom));
    assertFalse(owl.isEntailed(axiom)); assertFalse(owl.isEntailed(axiom));
    verify(delegate, times(2)).isEntailed(axiom);
  }
}
