package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/** Real OWLAPI graphs and real reconciliation, including the diagnostic-catching regions. */
class ReconciliationCancellationTest {
  private static boolean inside(Class<?> type,String method) {
    return StackWalker.getInstance().walk(frames -> frames.anyMatch(f ->
        f.getClassName().equals(type.getName()) && f.getMethodName().equals(method)));
  }

  private static void assertNoDiagnostics(Scope scope) {
    assertTrue(mockingDetails(scope).getInvocations().stream().noneMatch(i ->
        i.getMethod().getName().equals("error") || i.getMethod().getName().equals("warn")));
  }

  private static final class Fixture implements AutoCloseable {
    final Scope scope=mock(Scope.class);
    final OWL owl=new OWL(scope);
    final Ontology ontology;
    final List<WorldviewDescriptionStaging.Entry> entries;
    Fixture() {
      owl.manager=OWLManager.createOWLOntologyManager();
      ontology=owl.requireOntology("canceltest");
      owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("isInherentTo")));
      ontology.define(List.of(
          Axiom.ClassAssertion("Q",EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE)),
          Axiom.ClassAssertion("Tree",EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE))));
      var statement=new KimConceptStatementImpl();statement.setUrn("Q");statement.setNamespace("canceltest");
      statement.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
      var tree=new KimConceptImpl();tree.setName("canceltest:Tree");statement.setDeclaredInherent(tree);
      var source=new KimOntologyImpl();source.setUrn("canceltest");source.setProjectName("fixture");source.setSourceCode("quality Q within Tree");
      source.getStatements().add(statement);
      entries=List.of(new WorldviewDescriptionStaging.Entry(ontology,source,owl.getConcept("canceltest:Q"),
          statement,k -> owl.getConcept(k.getName()),scope));
      var f=owl.manager.getOWLDataFactory();
      // Many independent checks for one entry: cancellation cannot rely on the next declaration.
      for(int i=0;i<128;i++)owl.manager.addAxiom(ontology.getOWLOntology(),f.getOWLDisjointClassesAxiom(
          f.getOWLClass(IRI.create("urn:canceltest:left"+i)),f.getOWLClass(IRI.create("urn:canceltest:right"+i))));
    }
    Set<OWLAxiom> axioms() {return new HashSet<>(ontology.getOWLOntology().getAxioms());}
    public void close() {owl.reset();}
  }

  @Test void cancellationWithinOneEntryRecursiveCompatibilityPreservesIdentity() throws Exception {
    try(var fixture=new Fixture();var staged=fixture.owl.stageDeclarations()) {
      var visits=new AtomicInteger();var original=new CancellationException("same inner cancellation");
      assertSame(original,assertThrows(CancellationException.class,()->
          WorldviewDescriptionStaging.reconcile(fixture.owl,fixture.entries,()->{
            if(inside(OWLSemanticClauseSupport.class,"specializes") && visits.incrementAndGet()==17)throw original;
          })));
      assertEquals(17,visits.get());
      assertNoDiagnostics(fixture.scope);
    }
  }

  @Test void cancellationIsNotConvertedToDiagnosticsByEitherEntryCatch() throws Exception {
    for(var target:List.of(DescriptionBearerSupport.class,WorldviewDomainSupport.class)) {
      try(var fixture=new Fixture();var staged=fixture.owl.stageDeclarations()) {
        var original=new CancellationException("same cancellation in "+target.getSimpleName());
        assertSame(original,assertThrows(CancellationException.class,()->
            WorldviewDescriptionStaging.reconcile(fixture.owl,fixture.entries,()->{
              if(inside(target,target==DescriptionBearerSupport.class?"prepare":"interpretations"))throw original;
            })));
        assertNoDiagnostics(fixture.scope);
      }
    }
  }

  @Test void checkpointsDuringAncestryAndProvenanceTraversalPreserveIdentity() throws Exception {
    for(var method:List.of("visit","preserveProvenance","collectInterpretations")) {
      try(var fixture=new Fixture();var staged=fixture.owl.stageDeclarations()) {
        var original=new CancellationException("same recursive cancellation");
        var owner=method.equals("visit")?OWLSemanticClauseSupport.class:
            method.equals("preserveProvenance")?DescriptionBearerSupport.class:WorldviewDomainSupport.class;
        assertSame(original,assertThrows(CancellationException.class,()->
            WorldviewDescriptionStaging.reconcile(fixture.owl,fixture.entries,()->{
              if(inside(owner,method))throw original;
            })));
        assertNoDiagnostics(fixture.scope);
      }
    }
  }

  @Test void uncancelledOverloadKeepsAxiomsMetricsAndDiagnostics() throws Exception {
    try(var legacy=new Fixture();var observed=new Fixture();
        var first=legacy.owl.stageDeclarations();var second=observed.owl.stageDeclarations()) {
      var calls=new AtomicInteger();
      var expected=WorldviewDescriptionStaging.reconcile(legacy.owl,legacy.entries);
      var actual=WorldviewDescriptionStaging.reconcile(observed.owl,observed.entries,()->{
        calls.incrementAndGet();
        org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.checkpoint(()->false,
            org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Phase.DESCRIPTION_RECONCILE);
      });
      assertEquals(expected,actual);assertEquals(legacy.axioms(),observed.axioms());
      assertEquals(legacy.owl.compilationMetrics(),observed.owl.compilationMetrics());
      assertTrue(calls.get()>128,"Single-entry graph checks expose bounded work checkpoints");
      assertNoDiagnostics(legacy.scope);assertNoDiagnostics(observed.scope);
      System.out.println("RECONCILIATION_CHECKPOINTS singleEntryCalls="+calls.get());
    }
  }
}
