package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.impl.*;

class HierarchyQueryCacheTest {
  @Test void hierarchySnapshotsRetainEquivalenceGroupsAndAreInvalidatedByEdits() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {
      axioms.add(world.factory.getOWLSubClassOfAxiom(world.c("Tree"), world.c("Special")));
      axioms.add(world.factory.getOWLEquivalentClassesAxiom(world.c("Special"), world.c("Combined")));
    })) {
      var parents = w.owl.getSuperClasses(w.c("Tree"), true);
      assertTrue(parents.nodes().anyMatch(n -> n.contains(w.c("Special")) && n.contains(w.c("Combined"))));
      ((OWLClassNodeSet) parents).addEntity(w.c("OtherRoot"));
      assertFalse(w.owl.getSuperClasses(w.c("Tree"), true).containsEntity(w.c("OtherRoot")));
      var equivalents = w.owl.getEquivalentClasses(w.c("Special"));
      ((OWLClassNode) equivalents).add(w.c("OtherRoot"));
      assertFalse(w.owl.getEquivalentClasses(w.c("Special")).contains(w.c("OtherRoot")));
      assertTrue(w.owl.getSubClasses(w.c("Special"), false).containsEntity(w.c("Tree")));
      assertTrue(w.owl.specializationParents(w.c("Tree")).contains(w.c("Special")));
      assertThrows(UnsupportedOperationException.class,
          () -> w.owl.specializationParents(w.c("Tree")).add(w.c("OtherRoot")));
      var addition = w.factory.getOWLSubClassOfAxiom(w.c("Tree"), w.c("OtherRoot"));
      w.owl.manager.addAxiom(w.ontology.getOWLOntology(), addition);
      assertTrue(w.owl.getSuperClasses(w.c("Tree"), false).containsEntity(w.c("OtherRoot")));
      assertTrue(w.owl.specializationParents(w.c("Tree")).contains(w.c("OtherRoot")));
      assertTrue(w.owl.getSubClasses(w.c("OtherRoot"), false).containsEntity(w.c("Tree")));
      w.owl.manager.removeAxiom(w.ontology.getOWLOntology(), addition);
      assertFalse(w.owl.getSuperClasses(w.c("Tree"), false).containsEntity(w.c("OtherRoot")));
      assertFalse(w.owl.specializationParents(w.c("Tree")).contains(w.c("OtherRoot")));
      assertFalse(w.owl.getSubClasses(w.c("OtherRoot"), false).containsEntity(w.c("Tree")));
      assertTrue(w.owl.inferenceCacheMetrics().get("hierarchy").get("hits") > 0);
      w.owl.quarantineDerivedClosure();
      assertThrows(RuntimeException.class, () -> w.owl.getEquivalentClasses(w.c("Special")));
    }
  }

  @Test void specializationCachesCompleteRootsOnlyAndRetiresNegativeAnswers() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {})) {
      var calls = new AtomicInteger();
      for (int i=0; i<10; i++) assertFalse(w.owl.cachedSpecialization(w.c("Tree"), w.c("Special"),
          () -> { calls.incrementAndGet(); return false; }, () -> {}));
      assertEquals(1, calls.get());
      w.owl.manager.addAxiom(w.ontology.getOWLOntology(), w.factory.getOWLSubClassOfAxiom(w.c("Tree"), w.c("Special")));
      assertTrue(w.owl.cachedSpecialization(w.c("Tree"), w.c("Special"),
          () -> { calls.incrementAndGet(); return true; }, () -> {}));
      assertEquals(2, calls.get());
      assertThrows(IllegalStateException.class, () -> w.owl.cachedSpecialization(w.c("Tree"), w.c("OtherRoot"),
          () -> { throw new IllegalStateException("cancelled"); }, () -> {}));
      assertTrue(w.owl.cachedSpecialization(w.c("Tree"), w.c("OtherRoot"), () -> true, () -> {}));
    }
  }

  @Test void recursiveVisitedSetsCannotConsumeOrPoisonRootMemo() throws Exception {
    try (var w = new AuthorityDependencySupportTest.World((world, axioms) -> {
      axioms.add(world.factory.getOWLSubClassOfAxiom(world.c("Tree"), world.c("Special")));
    })) {
      var backend = mock(org.semanticweb.owlapi.reasoner.OWLReasoner.class);
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(w.owl, backend);
      var support = new OWLSemanticClauseSupport(w.owl);
      var method = OWLSemanticClauseSupport.class.getDeclaredMethod("specializes", OWLClassExpression.class, OWLClassExpression.class, Set.class);
      method.setAccessible(true);
      assertEquals(false, method.invoke(support, w.c("Tree"), w.c("Special"), new HashSet<>(Set.of(w.c("Tree")))));
      assertEquals(true, method.invoke(support, w.c("Tree"), w.c("Special"), new HashSet<>()));
      var outside = w.owl.manager.createOntology(IRI.create("urn:test:asserted-outside"));
      w.owl.manager.addAxiom(outside, w.factory.getOWLSubClassOfAxiom(w.c("Tree"), w.c("OtherRoot")));
      assertEquals(true, method.invoke(support, w.c("Tree"), w.c("OtherRoot"), new HashSet<>()));
      w.owl.manager.removeOntology(outside);
      w.owl.manager.createOntology(IRI.create("urn:test:asserted-outside"));
      assertEquals(false, method.invoke(support, w.c("Tree"), w.c("OtherRoot"), new HashSet<>()),
          "Replacing an ontology with the same ID must retire its asserted specialization");
      assertEquals(false, method.invoke(support, w.c("Tree"), w.c("Special"), new HashSet<>(Set.of(w.c("Tree")))));
      assertEquals(true, method.invoke(support, w.c("Tree"), w.c("Special"), new HashSet<>()));
    }
  }
}
