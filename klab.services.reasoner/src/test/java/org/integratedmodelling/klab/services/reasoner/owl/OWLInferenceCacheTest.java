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
