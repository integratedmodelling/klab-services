package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

class AssertedHierarchyIndexTest {
  static class Fixture implements AutoCloseable {
    final OWL owl;
    final OWLOntology ontology;
    final OWLDataFactory f;
    final AssertedHierarchyIndex index;
    final OWLSemanticClauseSupport support;
    Fixture() throws Exception {
      var scope = (Scope)Proxy.newProxyInstance(Scope.class.getClassLoader(),
          new Class<?>[]{Scope.class}, (self, method, args) -> null);
      owl = new OWL(scope);
      owl.manager = OWLManager.createOWLOntologyManager();
      ontology = owl.manager.createOntology(IRI.create("urn:index:test"));
      f = owl.manager.getOWLDataFactory();
      index = new AssertedHierarchyIndex(owl);
      support = new OWLSemanticClauseSupport(owl,index);
    }
    OWLClass c(String name) { return f.getOWLClass(IRI.create("urn:index:"+name)); }
    void add(OWLAxiom axiom) { owl.manager.addAxiom(ontology,axiom); }
    boolean query(OWLClassExpression candidate, OWLClassExpression bound) {
      return query(candidate,bound,OWLSemanticClauseSupport.NO_CHECKPOINT);
    }
    boolean query(OWLClassExpression candidate, OWLClassExpression bound,Runnable checkpoint) {
      try {
        var method = OWLSemanticClauseSupport.class.getDeclaredMethod("specializes",
            OWLClassExpression.class,OWLClassExpression.class,Set.class,Runnable.class);
        method.setAccessible(true);
        return (boolean)method.invoke(support,candidate,bound,new HashSet<>(),checkpoint);
      } catch (InvocationTargetException e) {
        if(e.getCause() instanceof RuntimeException cause) throw cause;
        throw new AssertionError(e.getCause());
      } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void close() { index.close(); }
  }

  // Frozen pre-optimization asserted traversal: deliberately uses EntitySearcher,
  // fresh branch-local visited sets and the original compound-expression ordering.
  static boolean reference(OWL owl,OWLClassExpression candidate,OWLClassExpression bound,
      Set<OWLClassExpression> visited) {
    if(candidate.equals(bound)||bound.isOWLThing())return true;
    if(bound instanceof OWLObjectUnionOf union)
      return union.operands().anyMatch(v->reference(owl,candidate,v,new HashSet<>(visited)));
    if(bound instanceof OWLObjectIntersectionOf intersection)
      return intersection.operands().allMatch(v->reference(owl,candidate,v,new HashSet<>(visited)));
    if(!visited.add(candidate))return false;
    if(candidate instanceof OWLClass named)
      return java.util.stream.Stream.concat(
          EntitySearcher.getSuperClasses(named,owl.manager.ontologies()),
          EntitySearcher.getEquivalentClasses(named,owl.manager.ontologies()))
          .anyMatch(v->reference(owl,v,bound,new HashSet<>(visited)));
    if(candidate instanceof OWLObjectIntersectionOf intersection)
      return intersection.operands().anyMatch(v->reference(owl,v,bound,new HashSet<>(visited)));
    if(candidate instanceof OWLObjectUnionOf union)
      return union.operands().allMatch(v->reference(owl,v,bound,new HashSet<>(visited)));
    return false;
  }

  @Test void cyclicRootsNeverReuseVisitedSensitivePartialAnswers() throws Exception {
    try(var t=new Fixture()) {
      var a=t.c("A");var b=t.c("B");var target=t.c("T");
      t.add(t.f.getOWLSubClassOfAxiom(a,b));
      t.add(t.f.getOWLSubClassOfAxiom(b,a));
      t.add(t.f.getOWLSubClassOfAxiom(a,target));
      assertTrue(t.query(a,target));assertTrue(t.query(b,target));
      assertFalse(t.query(a,t.c("Missing")));assertFalse(t.query(b,t.c("Missing")));
      assertTrue(t.query(b,target));
    }
  }

  @Test void randomizedCompoundHierarchyMatchesFrozenTraversalInBothWarmOrders() throws Exception {
    try(var t=new Fixture()) {
      var random=new Random(0x519ac77);
      var named=new ArrayList<OWLClass>();
      for(int i=0;i<12;i++)named.add(t.c("N"+i));
      for(int i=0;i<25;i++)t.add(t.f.getOWLSubClassOfAxiom(
          named.get(random.nextInt(named.size())),named.get(random.nextInt(named.size()))));
      t.add(t.f.getOWLEquivalentClassesAxiom(named.get(2),named.get(3),
          t.f.getOWLObjectIntersectionOf(named.get(4),named.get(5))));
      t.add(t.f.getOWLSubClassOfAxiom(named.get(6),t.f.getOWLObjectUnionOf(named.get(7),named.get(8))));
      var expressions=new ArrayList<OWLClassExpression>(named);
      expressions.add(t.f.getOWLThing());expressions.add(t.f.getOWLNothing());
      for(int i=0;i<8;i++) {
        var a=named.get(random.nextInt(named.size()));var b=named.get(random.nextInt(named.size()));
        expressions.add(t.f.getOWLObjectUnionOf(a,b));
        expressions.add(t.f.getOWLObjectIntersectionOf(a,b));
      }
      expressions.add(t.f.getOWLObjectComplementOf(named.get(0)));
      for(int pass=0;pass<2;pass++) {
        for(var candidate:expressions)for(var bound:expressions)
          assertEquals(reference(t.owl,candidate,bound,new HashSet<>()),t.query(candidate,bound),
              ()->candidate+" specializes "+bound);
        Collections.reverse(expressions);
      }
    }
  }

  @Test void axiomAndOntologyMembershipChangesInvalidatePositiveAndNegativeRoots() throws Exception {
    try(var t=new Fixture()) {
      var a=t.c("A");var b=t.c("B");
      assertFalse(t.query(a,b));
      var edge=t.f.getOWLSubClassOfAxiom(a,b);t.add(edge);
      assertTrue(t.query(a,b));
      t.owl.manager.removeAxiom(t.ontology,edge);assertFalse(t.query(a,b));
      var imported=t.owl.manager.createOntology(IRI.create("urn:extra"));
      t.owl.manager.addAxiom(imported,t.f.getOWLEquivalentClassesAxiom(a,b,t.c("C")));
      assertTrue(t.query(a,b));assertTrue(t.query(t.c("C"),a));
      t.owl.manager.removeOntology(imported);assertFalse(t.query(a,b));
      var replacement=t.owl.manager.createOntology(IRI.create("urn:extra"));
      t.owl.manager.addAxiom(replacement,edge);assertTrue(t.query(a,b));
    }
  }

  @Test void disjointnessAndMultipleBoundsRemainConjunctiveAcrossInvalidation() throws Exception {
    try(var t=new Fixture()) {
      var a=t.c("A");var b=t.c("B");var c=t.c("C");
      var axiom=t.f.getOWLDisjointClassesAxiom(a,b,c);t.add(axiom);
      var old=new OWLSemanticClauseSupport(t.owl);
      for(var constraints:List.<List<OWLClassExpression>>of(List.of(a,b),List.of(a),
          List.of(t.f.getOWLObjectUnionOf(a,b)),
          List.of(t.f.getOWLObjectUnionOf(a,b),c),
          List.of(t.f.getOWLObjectIntersectionOf(a,b))))
        assertEquals(old.bearerCompatibility(constraints),t.support.bearerCompatibility(constraints));
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE,t.support.bearerCompatibility(List.of(a,b)));
      t.owl.manager.removeAxiom(t.ontology,axiom);
      assertEquals(OWLSemanticClauseSupport.Compatibility.UNRESOLVED,t.support.bearerCompatibility(List.of(a,b)));
      var imported=t.owl.manager.createOntology(IRI.create("urn:disjoint"));
      t.owl.manager.addAxiom(imported,axiom);
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE,t.support.bearerCompatibility(List.of(a,b)));
      t.owl.manager.removeOntology(imported);
      assertEquals(OWLSemanticClauseSupport.Compatibility.UNRESOLVED,t.support.bearerCompatibility(List.of(a,b)));
    }
  }

  @Test void cancellationDuringBuildOrWarmLookupIsNeverConvertedIntoAnAnswer() throws Exception {
    try(var t=new Fixture()) {
      var a=t.c("A");var b=t.c("B");t.add(t.f.getOWLSubClassOfAxiom(a,b));
      var count=new AtomicInteger();
      assertThrows(CancellationException.class,()->t.query(a,b,()->{
        if(count.incrementAndGet()==4)throw new CancellationException("build");
      }));
      assertTrue(t.query(a,b));
      assertThrows(CancellationException.class,()->t.query(a,b,()->{throw new CancellationException("warm");}));
      assertTrue(t.query(a,b));
      t.index.close();assertFalse(t.index.usable(t.owl));assertTrue(t.query(a,b));
    }
  }

  @Test void closedIndexAndDifferentOwnerCannotReusePrivateAnswers() throws Exception {
    try(var t=new Fixture();var other=new Fixture()) {
      assertFalse(t.index.usable(other.owl));
      t.index.close();assertFalse(t.index.usable(t.owl));
    }
  }

  @Test void replacingOntologyWithSameIdCannotRetainOldAncestryOrDisjointness() throws Exception {
    try(var t=new Fixture()) {
      var a=t.c("A");var b=t.c("B");
      var original=t.owl.manager.createOntology(IRI.create("urn:replace"));
      t.owl.manager.addAxioms(original,Set.of(t.f.getOWLSubClassOfAxiom(a,b),
          t.f.getOWLDisjointClassesAxiom(a,b)));
      assertTrue(t.query(a,b));
      assertEquals(OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE,t.support.bearerCompatibility(List.of(a,b)));
      t.owl.manager.removeOntology(original);
      t.owl.manager.createOntology(IRI.create("urn:replace"));
      // No intervening lookup and no addAxiom event on the empty replacement.
      assertFalse(t.query(a,b));
      assertEquals(OWLSemanticClauseSupport.Compatibility.UNRESOLVED,t.support.bearerCompatibility(List.of(a,b)));
    }
  }

  @Test void reasonerPresenceBypassesAssertedAnswersAndDoesNotPolluteTheirLifetime() throws Exception {
    try(var t=new Fixture()) {
      var a=t.c("A");var b=t.c("B");assertFalse(t.query(a,b));
      var dl=mock(org.semanticweb.owlapi.reasoner.OWLReasoner.class);
      when(dl.isEntailed(t.f.getOWLSubClassOfAxiom(a,b))).thenReturn(true);
      var field=OWL.class.getDeclaredField("reasoner");field.setAccessible(true);field.set(t.owl,dl);
      assertFalse(t.index.usable(t.owl));assertTrue(t.query(a,b));
      verify(dl).isEntailed(t.f.getOWLSubClassOfAxiom(a,b));
      field.set(t.owl,null);assertTrue(t.index.usable(t.owl));assertFalse(t.query(a,b));
    }
  }
}
