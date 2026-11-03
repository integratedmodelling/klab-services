package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

class PropertyAncestorMemoTest {
  static class Graph {
    final OWL owl = new OWL(mock(Scope.class));
    final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    final OWLDataFactory f = manager.getOWLDataFactory();
    final OWLOntology ontology;

    Graph() throws Exception {
      owl.manager = manager;
      ontology = manager.createOntology(IRI.create("urn:test:property-graph"));
    }

    OWLObjectProperty p(String name) {
      return f.getOWLObjectProperty(IRI.create("urn:test:property-graph#" + name));
    }

    void sub(String child, String parent) {
      manager.addAxiom(ontology, f.getOWLSubObjectPropertyOfAxiom(p(child), p(parent)));
    }
  }

  static Object field(Object object, String name) throws Exception {
    var field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  static int entries(OWLSemanticClauseSupport.BoundPropertyMemo memo) throws Exception {
    return ((Map<?, ?>) field(memo, "values")).size();
  }

  static Set<OWLObjectPropertyExpression> collect(
      OWLSemanticClauseSupport.BoundPropertyMemo memo, OWLObjectPropertyExpression property) {
    var result = new HashSet<OWLObjectPropertyExpression>();
    memo.collect(property, result, () -> {});
    return result;
  }

  @Test void completeCyclicClosuresAreReusedWithoutPartialAncestorAnswers() throws Exception {
    var graph = new Graph();
    graph.sub("p", "q"); graph.sub("q", "p"); graph.sub("q", "r");
    var manager = mock(OWLOntologyManager.class);
    when(manager.getOntologies()).thenReturn(Set.of(graph.ontology));
    when(manager.ontologies()).thenAnswer(invocation -> Set.of(graph.ontology).stream());
    graph.owl.manager = manager;
    try (var memo = new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl)) {
      var expected = Set.of(graph.p("p"), graph.p("q"), graph.p("r"));
      assertEquals(expected, collect(memo, graph.p("p")));
      long reads = mockingDetails(manager).getInvocations().stream()
          .filter(invocation -> invocation.getMethod().getName().equals("ontologies")).count();
      assertTrue(reads > 0);
      assertEquals(expected, collect(memo, graph.p("p")));
      assertEquals(reads, mockingDetails(manager).getInvocations().stream()
          .filter(invocation -> invocation.getMethod().getName().equals("ontologies")).count());
      assertEquals(expected, collect(memo, graph.p("q")), "A cycle must not retain a partial q closure");
    }
  }

  @Test void scratchRetentionIsBoundedAndLargeClosuresAreNotRetained() throws Exception {
    var graph = new Graph();
    try (var memo = new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl)) {
      for (int i = 0; i < 600; i++) collect(memo, graph.p("leaf" + i));
      assertEquals(512, entries(memo));
    }
    for (int i = 0; i < 4100; i++) graph.sub("wide", "parent" + i);
    try (var memo = new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl)) {
      assertEquals(4101, collect(memo, graph.p("wide")).size());
      assertEquals(0, entries(memo));
    }
  }

  static List<String> bounds(OWLSemanticClauseSupport support, Concept concept, Runnable checkpoint)
      throws Exception {
    var method = OWLSemanticClauseSupport.class.getDeclaredMethod("bounds", Concept.class, Runnable.class);
    method.setAccessible(true);
    try {
      return ((List<?>) method.invoke(support, concept, checkpoint)).stream()
          .map(Object::toString).sorted().toList();
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException cause) throw cause;
      throw failure;
    }
  }

  @SuppressWarnings("unchecked")
  static ThreadLocal<OWLSemanticClauseSupport.BoundPropertyMemo> slot(OWLSemanticClauseSupport support)
      throws Exception {
    return (ThreadLocal<OWLSemanticClauseSupport.BoundPropertyMemo>) field(support, "propertyMemos");
  }

  @Test void freshComputationsPreserveCompleteBoundsAndSeePropertyChanges() throws Exception {
    try (var old = new TargetedBoundDiscoveryTest.Fixture();
        var next = new TargetedBoundDiscoveryTest.Fixture()) {
      for (var fixture : List.of(old, next)) {
        var alias = fixture.f.getOWLObjectProperty(IRI.create("urn:test:alias"));
        for (var name : List.of("Trait", "Marker"))
          fixture.add(fixture.f.getOWLSubClassOfAxiom(fixture.c(name),
              fixture.f.getOWLObjectSomeValuesFrom(alias, fixture.c("Tree"))));
      }
      var reference = new OWLSemanticClauseSupport(old.owl);
      var candidate = new OWLSemanticClauseSupport(next.owl);
      assertEquals(TargetedBoundDiscoveryTest.normalize(TargetedBoundDiscoveryTest.legacyBounds(
          old.owl, reference, old.domain.getConcept("Trait"))),
          bounds(candidate, next.domain.getConcept("Trait"), () -> {}));
      assertNull(slot(candidate).get());
      for (var fixture : List.of(old, next))
        fixture.add(fixture.f.getOWLSubObjectPropertyOfAxiom(
            fixture.f.getOWLObjectProperty(IRI.create("urn:test:alias")), fixture.p("appliesTo")));
      var actual = bounds(candidate, next.domain.getConcept("Marker"), () -> {});
      assertEquals(TargetedBoundDiscoveryTest.normalize(TargetedBoundDiscoveryTest.legacyBounds(
          old.owl, reference, old.domain.getConcept("Marker"))), actual);
      assertTrue(actual.stream().anyMatch(bound -> bound.contains("property=<urn:test:alias>")));
      assertNull(slot(candidate).get());
    }
  }

  private void failedComputation(boolean imported) throws Exception {
    try (var fixture = new TargetedBoundDiscoveryTest.Fixture()) {
      if (imported) fixture.external(false);
      var support = new OWLSemanticClauseSupport(fixture.owl);
      var local = slot(support);
      var once = new AtomicBoolean();
      Runnable change = () -> {
        var memo = local.get();
        try {
          if (memo == null || entries(memo) == 0 || !once.compareAndSet(false, true)) return;
          if (imported) fixture.owl.manager.applyChange(new AddImport(fixture.root,
              fixture.f.getOWLImportsDeclaration(IRI.create("urn:offline:external"))));
          else fixture.add(fixture.f.getOWLSubObjectPropertyOfAxiom(
              fixture.p("hasPurpose"), fixture.p("appliesTo")));
        } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
        catch (Exception failure) { throw new RuntimeException(failure); }
      };
      assertThrows(ConcurrentModificationException.class,
          () -> bounds(support, fixture.domain.getConcept("Trait"), change));
      assertTrue(once.get());
      assertNull(local.get());
      assertTrue(((Map<?, ?>) field(support, "cache")).isEmpty());
    }
  }

  @Test void inFlightPropertyMutationCannotPublishBounds() throws Exception { failedComputation(false); }
  @Test void inFlightImportMutationCannotPublishBounds() throws Exception { failedComputation(true); }

  @Test void cancellationClearsThreadLocalAndCannotExposeScratchToAnotherThread() throws Exception {
    try (var fixture = new TargetedBoundDiscoveryTest.Fixture()) {
      var support = new OWLSemanticClauseSupport(fixture.owl);
      var local = slot(support);
      var once = new AtomicBoolean();
      assertThrows(CancellationException.class, () -> bounds(support, fixture.domain.getConcept("Trait"), () -> {
        if (local.get() != null && once.compareAndSet(false, true)) {
          assertNull(CompletableFuture.supplyAsync(local::get).join());
          throw new CancellationException("controlled cancellation");
        }
      }));
      assertTrue(once.get());
      assertNull(local.get());
      assertTrue(((Map<?, ?>) field(support, "cache")).isEmpty());
    }
  }

  @Test void memoHitsStillCheckCancellation() throws Exception {
    var graph = new Graph();
    try (var memo = new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl)) {
      collect(memo, graph.p("p"));
      assertThrows(CancellationException.class, () -> memo.collect(graph.p("p"), new HashSet<>(),
          () -> { throw new CancellationException("cancel cached lookup"); }));
    }
  }

  @Test void sameIdOntologyReplacementAndManagerReplacementRejectTheScope() throws Exception {
    var graph = new Graph();
    var memo = new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl);
    graph.manager.removeOntology(graph.ontology);
    graph.manager.createOntology(IRI.create("urn:test:property-graph"));
    assertThrows(ConcurrentModificationException.class, memo::close);
    assertEquals(0, entries(memo));
    memo = new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl);
    graph.owl.manager = OWLManager.createOWLOntologyManager();
    assertThrows(ConcurrentModificationException.class, memo::close);
  }

  @Test void reasonerReplacementRejectsTheScope() throws Exception {
    var graph = new Graph();
    var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true);
    var first = mock(OWLReasoner.class); when(first.getRootOntology()).thenReturn(graph.ontology);
    field.set(graph.owl, first);
    var memo = new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl);
    var second = mock(OWLReasoner.class); when(second.getRootOntology()).thenReturn(graph.ontology);
    field.set(graph.owl, second);
    assertThrows(ConcurrentModificationException.class, memo::close);
  }

  @Test void constructorFailureRemovesItsMutationListener() throws Exception {
    var graph = new Graph();
    var manager = mock(OWLOntologyManager.class);
    when(manager.getOntologies()).thenThrow(new IllegalStateException("snapshot failed"));
    graph.owl.manager = manager;
    assertThrows(IllegalStateException.class, () -> new OWLSemanticClauseSupport.BoundPropertyMemo(graph.owl));
    verify(manager).addOntologyChangeListener(any(OWLOntologyChangeListener.class));
    verify(manager).removeOntologyChangeListener(any(OWLOntologyChangeListener.class));
  }
}
