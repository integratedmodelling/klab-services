package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

class BoundAxiomIndexTest {
  private static final Runnable NOTHING = () -> {};
  private static final Predicate<OWLAxiom> MARKED = axiom -> axiom.annotations().anyMatch(a ->
      a.getProperty().getIRI().equals(OWLSemanticClauseSupport.UNIVERSAL_BEARER)
          && a.getValue() instanceof OWLLiteral value && value.isBoolean() && value.parseBoolean());

  private static Map<BoundAxiomIndex.Edge, Long> multiset(List<BoundAxiomIndex.Edge> edges) {
    var result = new HashMap<BoundAxiomIndex.Edge, Long>();
    edges.forEach(edge -> result.merge(edge, 1L, Long::sum));
    return result;
  }

  /** Independent former per-name scan, including document duplicates and complete expressions. */
  private static List<BoundAxiomIndex.Edge> scanned(OWLOntologyManager manager, OWLClass named) {
    var result = new ArrayList<BoundAxiomIndex.Edge>();
    for (var ontology : manager.getOntologies()) {
      for (var axiom : ontology.getSubClassAxiomsForSubClass(named))
        result.add(new BoundAxiomIndex.Edge(axiom.getSuperClass(), MARKED.test(axiom)));
      for (var axiom : ontology.getEquivalentClassesAxioms(named))
        for (var equivalent : axiom.getClassExpressionsMinus(named))
          result.add(new BoundAxiomIndex.Edge(equivalent, MARKED.test(axiom)));
    }
    return result;
  }

  @Test void exactEdgesPreserveCyclesEquivalentOwnersAnnotationsUnionsAndDocumentDuplicates()
      throws Exception {
    try (var fixture = new TargetedBoundDiscoveryTest.Fixture()) {
      fixture.external(false); // Manager-only assertions must retain the original scope.
      fixture.add(fixture.f.getOWLSubClassOfAxiom(fixture.c("Marker"), fixture.c("Trait")));
      fixture.add(fixture.f.getOWLEquivalentClassesAxiom(fixture.c("Parent"),
          fixture.f.getOWLObjectIntersectionOf(fixture.c("Trait"), fixture.c("Marker"))));
      var duplicate = fixture.f.getOWLSubClassOfAxiom(fixture.c("Trait"), fixture.c("Parent"));
      fixture.owl.manager.addAxiom(fixture.root, duplicate);
      var index = BoundAxiomIndex.build(fixture.owl.manager, MARKED, NOTHING, NOTHING);
      var names = fixture.owl.manager.ontologies().flatMap(o -> o.classesInSignature()).distinct().toList();
      for (var named : names) assertEquals(multiset(scanned(fixture.owl.manager, named)),
          multiset(index.parents(named)), named.toString());
      assertEquals(2, index.parents(fixture.c("Trait")).stream()
          .filter(edge -> edge.parent().equals(fixture.c("Parent"))).count());
      assertTrue(index.parents(fixture.c("Parent")).stream().anyMatch(BoundAxiomIndex.Edge::semantic));
      assertTrue(index.parents(fixture.c("UnionOwner")).stream()
          .anyMatch(edge -> edge.parent() instanceof OWLObjectUnionOf));
      assertThrows(UnsupportedOperationException.class,
          () -> index.parents(fixture.c("Parent")).clear());
    }
  }

  @Test void repeatedOwnerLookupDoesNotRescanOntologies() throws Exception {
    var manager = OWLManager.createOWLOntologyManager();
    var ontology = manager.createOntology(IRI.create("urn:test:index"));
    var f = manager.getOWLDataFactory();
    var a = f.getOWLClass(IRI.create("urn:test:index#A"));
    var b = f.getOWLClass(IRI.create("urn:test:index#B"));
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(a, b));
    var counted = mock(OWLOntologyManager.class);
    when(counted.getOntologies()).thenReturn(Set.of(ontology));
    var index = BoundAxiomIndex.build(counted, MARKED, NOTHING, NOTHING);
    for (int i = 0; i < 1000; i++) assertEquals(List.of(new BoundAxiomIndex.Edge(b, false)), index.parents(a));
    verify(counted, times(1)).getOntologies();
  }

  @Test void freshIndexSeesAxiomChangesWithoutMutatingPreviousSnapshot() throws Exception {
    try (var fixture = new TargetedBoundDiscoveryTest.Fixture()) {
      var first = BoundAxiomIndex.build(fixture.owl.manager, MARKED, NOTHING, NOTHING);
      var before = first.parents(fixture.c("Marker"));
      fixture.add(fixture.f.getOWLSubClassOfAxiom(fixture.c("Marker"), fixture.c("Plant")));
      var next = BoundAxiomIndex.build(fixture.owl.manager, MARKED, NOTHING, NOTHING);
      assertEquals(before, first.parents(fixture.c("Marker")));
      assertNotEquals(before, next.parents(fixture.c("Marker")));
      assertEquals(multiset(scanned(fixture.owl.manager, fixture.c("Marker"))),
          multiset(next.parents(fixture.c("Marker"))));
    }
  }

  @SuppressWarnings("unchecked")
  private static ThreadLocal<BoundAxiomIndex> slot(OWLSemanticClauseSupport support) throws Exception {
    return (ThreadLocal<BoundAxiomIndex>) PropertyAncestorMemoTest.field(support, "assertedAxioms");
  }

  private void interrupted(String kind, boolean constructing) throws Exception {
    try (var fixture = new TargetedBoundDiscoveryTest.Fixture()) {
      if (kind.equals("import")) fixture.external(false);
      var support = new OWLSemanticClauseSupport(fixture.owl);
      var index = slot(support);
      var properties = PropertyAncestorMemoTest.slot(support);
      var once = new AtomicBoolean();
      Runnable checkpoint = () -> {
        if (properties.get() == null || (index.get() == null) != constructing
            || !once.compareAndSet(false, true)) return;
        assertNull(CompletableFuture.supplyAsync(index::get).join(), "Scratch is thread-confined");
        if (kind.equals("cancel")) throw new CancellationException("controlled cancellation");
        if (kind.equals("import")) fixture.owl.manager.applyChange(new AddImport(fixture.root,
            fixture.f.getOWLImportsDeclaration(IRI.create("urn:offline:external"))));
        else fixture.add(fixture.f.getOWLSubClassOfAxiom(fixture.c("Marker"), fixture.c("Plant")));
      };
      if (kind.equals("cancel")) assertThrows(CancellationException.class,
          () -> PropertyAncestorMemoTest.bounds(support, fixture.domain.getConcept("Trait"), checkpoint));
      else assertThrows(ConcurrentModificationException.class,
          () -> PropertyAncestorMemoTest.bounds(support, fixture.domain.getConcept("Trait"), checkpoint));
      assertTrue(once.get());
      assertNull(index.get());
      assertNull(properties.get());
      assertTrue(((Map<?, ?>) PropertyAncestorMemoTest.field(support, "cache")).isEmpty());
    }
  }

  @Test void mutationDuringConstructionCannotPublishIndexOrBounds() throws Exception { interrupted("mutation", true); }
  @Test void mutationDuringTraversalCannotPublishBounds() throws Exception { interrupted("mutation", false); }
  @Test void importDuringConstructionCannotPublishIndexOrBounds() throws Exception { interrupted("import", true); }
  @Test void importDuringTraversalCannotPublishBounds() throws Exception { interrupted("import", false); }
  @Test void cancellationDuringConstructionClearsScratch() throws Exception { interrupted("cancel", true); }
  @Test void cancellationDuringTraversalClearsScratch() throws Exception { interrupted("cancel", false); }
}
