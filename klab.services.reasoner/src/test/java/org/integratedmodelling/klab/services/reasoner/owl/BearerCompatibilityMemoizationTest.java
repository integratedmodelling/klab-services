package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

class BearerCompatibilityMemoizationTest {
  static class Fixture implements AutoCloseable {
    final OWL owl;
    final Ontology ontology;
    final OWLDataFactory f;
    final OWLReasoner dl;
    final OWLSemanticClauseSupport support;
    Fixture(boolean buffering) throws Exception {
      var scope = (Scope)java.lang.reflect.Proxy.newProxyInstance(Scope.class.getClassLoader(),
          new Class<?>[]{Scope.class}, (self, method, args) -> null);
      owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
      f = owl.manager.getOWLDataFactory(); ontology = owl.requireOntology("memo");
      for (var name : List.of("A", "B", "C", "D", "Other1", "Other2"))
        ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE))));
      owl.manager.addAxioms(ontology.getOWLOntology(), Set.of(
          f.getOWLSubClassOfAxiom(type("A"), type("B")),
          f.getOWLSubClassOfAxiom(type("A"), type("C")),
          f.getOWLSubClassOfAxiom(type("B"), type("D")),
          f.getOWLSubClassOfAxiom(type("C"), type("D")),
          f.getOWLDisjointClassesAxiom(type("Other1"), type("Other2"))));
      var factory = new org.semanticweb.HermiT.Reasoner.ReasonerFactory();
      dl = spy(buffering ? factory.createReasoner(ontology.getOWLOntology())
          : factory.createNonBufferingReasoner(ontology.getOWLOntology()));
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(owl, dl);
      support = new OWLSemanticClauseSupport(owl);
    }
    OWLClass type(String name) { return owl.getOWLClass(ontology.getConcept(name)); }
    Map<Object, Long> queries() {
      return mockingDetails(dl).getInvocations().stream()
          .filter(i -> i.getMethod().getName().equals("isEntailed"))
          .collect(Collectors.groupingBy(i -> i.getArgument(0), Collectors.counting()));
    }
    public void close() { dl.dispose(); }
  }

  @Test void duplicateQueriesAreReusedOnlyWithinOneBufferingCheck() throws Exception {
    try (var fixture = new Fixture(true)) {
      assertEquals(OWLSemanticClauseSupport.Compatibility.COMPATIBLE,
          fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
      var first = fixture.queries(); assertFalse(first.isEmpty());
      assertTrue(first.values().stream().allMatch(count -> count == 1));
      verify(fixture.dl).isSatisfiable(any(OWLClassExpression.class));
      clearInvocations(fixture.dl);
      assertEquals(OWLSemanticClauseSupport.Compatibility.COMPATIBLE,
          fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
      assertEquals(first, fixture.queries()); // No result survives into the next call.
    }
  }

  @Test void nonbufferingReasonerDoesNotReuseAnswers() throws Exception {
    try (var fixture = new Fixture(false)) {
      assertEquals(OWLSemanticClauseSupport.Compatibility.COMPATIBLE,
          fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
      assertTrue(fixture.queries().values().stream().anyMatch(count -> count > 1));
    }
  }

  @Test void subsequentLogicalChangesAndFailuresCannotReuseOldAnswers() throws Exception {
    try (var fixture = new Fixture(true)) {
      var failed = new AtomicBoolean();
      doAnswer(call -> {
        assertTrue(Thread.holdsLock(fixture.owl));
        if (!failed.getAndSet(true)) throw new IllegalStateException("injected entailment failure");
        return call.callRealMethod();
      }).when(fixture.dl).isEntailed(any(OWLAxiom.class));
      assertThrows(IllegalStateException.class,
          () -> fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
      assertEquals(OWLSemanticClauseSupport.Compatibility.COMPATIBLE,
          fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
      fixture.owl.manager.addAxiom(fixture.ontology.getOWLOntology(),
          fixture.f.getOWLDisjointClassesAxiom(fixture.type("A"), fixture.type("D")));
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE,
          fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
    }
  }

  @Test void quantifiersCardinalitiesAndEntailedPropertiesRemainInScope() throws Exception {
    try (var fixture = new Fixture(true)) {
      var f = fixture.f;
      var p = f.getOWLObjectProperty(IRI.create("urn:test:p"));
      var q = f.getOWLObjectProperty(IRI.create("urn:test:q"));
      fixture.owl.manager.addAxiom(fixture.ontology.getOWLOntology(), f.getOWLEquivalentObjectPropertiesAxiom(p, q));
      fixture.owl.flushReasoner();
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE, fixture.support.bearerCompatibility(List.of(
          f.getOWLObjectSomeValuesFrom(p, fixture.type("B")), f.getOWLObjectMaxCardinality(0, q, f.getOWLThing()))));
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE, fixture.support.bearerCompatibility(List.of(
          f.getOWLObjectMinCardinality(2, p, f.getOWLThing()), f.getOWLObjectMaxCardinality(1, p, f.getOWLThing()))));
      assertEquals(OWLSemanticClauseSupport.Compatibility.COMPATIBLE, fixture.support.bearerCompatibility(List.of(
          f.getOWLObjectSomeValuesFrom(p, fixture.type("Other1")), f.getOWLObjectSomeValuesFrom(p, fixture.type("Other2")))));
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE, fixture.support.bearerCompatibility(List.of(
          f.getOWLObjectSomeValuesFrom(p, fixture.type("Other1")), f.getOWLObjectAllValuesFrom(p, fixture.type("Other2")))));
    }
  }

  @Test void bootstrapUnknownIsNotCompatibilityOrIncompatibility() throws Exception {
    try (var fixture = new Fixture(true)) {
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(fixture.owl, null);
      assertEquals(OWLSemanticClauseSupport.Compatibility.UNRESOLVED,
          fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE,
          fixture.support.bearerCompatibility(List.of(fixture.type("Other1"), fixture.type("Other2"))));
    }
  }

  @Test void anotherThreadCannotFlushDuringMemoizedEntailmentTraversal() throws Exception {
    try (var fixture = new Fixture(true); var executor = Executors.newFixedThreadPool(2)) {
      var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
      var attempted = new CountDownLatch(1); var first = new AtomicBoolean(true);
      doAnswer(call -> {
        if (first.getAndSet(false)) {
          assertTrue(Thread.holdsLock(fixture.owl)); entered.countDown();
          assertTrue(release.await(5, TimeUnit.SECONDS));
        }
        return call.callRealMethod();
      }).when(fixture.dl).isEntailed(any(OWLAxiom.class));
      var check = executor.submit(() -> fixture.support.bearerCompatibility(List.of(fixture.type("A"))));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var flush = executor.submit(() -> { attempted.countDown(); fixture.owl.flush(); });
        assertTrue(attempted.await(5, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> flush.get(100, TimeUnit.MILLISECONDS));
        release.countDown();
        assertEquals(OWLSemanticClauseSupport.Compatibility.COMPATIBLE, check.get(5, TimeUnit.SECONDS));
        flush.get(5, TimeUnit.SECONDS);
      } finally { release.countDown(); }
    }
  }
}
