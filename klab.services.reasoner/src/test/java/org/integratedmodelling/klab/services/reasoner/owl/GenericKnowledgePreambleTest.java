package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

/** Category selection does not waive grounding for specialized domains or qualities. */
class GenericKnowledgePreambleTest {
  private OWL fixture() {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{Scope.class}, (self, method, args) -> null);
    var owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    owl.requireOntology("odo").define(List.of(
        Axiom.ClassAssertion("KnowledgeDomain", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ClassAssertion("PhysicalDomain", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ObjectPropertyAssertion("interpretsPhysicalDomain")));
    owl.requireOntology("audit").define(List.of(
        Axiom.ClassAssertion("Science", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ClassAssertion("ChildScience", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ClassAssertion("KnowledgeDomain", EnumSet.of(SemanticType.DOMAIN)),
        Axiom.ClassAssertion("Q", EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE)),
        Axiom.SubClass("odo:KnowledgeDomain", "Science"),
        Axiom.SubClass("Science", "ChildScience"),
        Axiom.SubClass("odo:KnowledgeDomain", "KnowledgeDomain")));
    return owl;
  }

  private void rejectsPreamble(OWL owl, Concept domain) {
    assertTrue(assertThrows(KlabValidationException.class,
        () -> DomainInterpretationSupport.requireEffectiveGrounding(owl, domain))
        .getMessage().startsWith("KNOWLEDGE_DOMAIN_INTERPRETATION_REQUIRED:"));
  }

  @Test void exactCanonicalCategoryAcceptedWithoutCreatingAuthoredGrounding() {
    var owl = fixture(); var category = owl.getConcept("odo:KnowledgeDomain");
    var before = owl.getOntology("odo").getOWLOntology().getAxioms();
    assertDoesNotThrow(() -> DomainInterpretationSupport.requireEffectiveGrounding(owl, category));
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, category).isEmpty());
    assertEquals(before, owl.getOntology("odo").getOWLOntology().getAxioms());
  }

  @Test void aliasResolvingToCanonicalEntityAccepted() {
    var owl = fixture(); var category = owl.getConcept("odo:KnowledgeDomain");
    owl.requireOntology("imod").addDelegateConcept("Knowledge", "imod", category);
    var alias = owl.getConcept("imod:Knowledge");
    assertNotNull(alias);
    assertEquals(owl.getOWLClass(category), owl.getOWLClass(alias));
    assertDoesNotThrow(() -> DomainInterpretationSupport.requireEffectiveGrounding(owl, alias));
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, alias).isEmpty());
  }

  @Test void ungroundedSpecializedDomainStillRejected() {
    var owl = fixture(); rejectsPreamble(owl, owl.getConcept("audit:Science"));
  }

  @Test void abstractSpecializedDeclarationAndItsPreambleStillRejected() {
    var owl = fixture(); var science = owl.getConcept("audit:Science");
    var statement = new KimConceptStatementImpl(); statement.setAbstract(true);
    assertTrue(assertThrows(KlabValidationException.class,
        () -> WorldviewDeclarationSupport.compile(owl, owl.getOntology("audit"), science,
            statement, ignored -> null)).getMessage()
        .startsWith("KNOWLEDGE_DOMAIN_INTERPRETATION_REQUIRED:"));
    rejectsPreamble(owl, science);
  }

  @Test void specializedDomainWithInheritedAuthoredGroundingAccepted() {
    var owl = fixture(); var ontology = owl.getOntology("audit");
    WorldviewDomainSupport.markInterpretation(owl, ontology, owl.getConcept("audit:Science"),
        owl.getConcept("odo:PhysicalDomain"));
    var child = owl.getConcept("audit:ChildScience");
    assertTrue(WorldviewDomainSupport.effectiveInterpretations(owl, child).stream()
        .anyMatch(WorldviewDomainSupport.InterpretationGrounding::inherited));
    assertDoesNotThrow(() -> DomainInterpretationSupport.requireEffectiveGrounding(owl, child));
    assertDoesNotThrow(() -> WorldviewDeclarationSupport.compile(owl, ontology, child,
        new KimConceptStatementImpl(), ignored -> null));
  }

  @Test void genericPhysicalCategoryStillAccepted() {
    var owl = fixture();
    assertDoesNotThrow(() -> DomainInterpretationSupport.requireEffectiveGrounding(owl,
        owl.getConcept("odo:PhysicalDomain")));
  }

  @Test void concreteQualityInGenericKnowledgeStillNeedsDescription() {
    var owl = fixture(); var statement = new KimConceptStatementImpl();
    statement.setNamespace("audit"); statement.setUrn("Q");
    var category = owl.getConcept("odo:KnowledgeDomain");
    assertDoesNotThrow(() -> DomainInterpretationSupport.requireEffectiveGrounding(owl, category));
    WorldviewDomainSupport.bind(owl, "audit", statement, category);
    assertTrue(assertThrows(KlabValidationException.class,
        () -> WorldviewDeclarationSupport.compile(owl, owl.getOntology("audit"),
            owl.getConcept("audit:Q"), statement, ignored -> null)).getMessage()
        .startsWith("KNOWLEDGE_QUALITY_GROUNDING_REQUIRED:"));
  }

  @Test void sameLocalNameInAnotherNamespaceDoesNotGainException() {
    var owl = fixture(); rejectsPreamble(owl, owl.getConcept("audit:KnowledgeDomain"));
  }

  @Test void distinctEquivalentClassDoesNotGainException() {
    var owl = fixture(); var science = owl.getConcept("audit:Science");
    var category = owl.getConcept("odo:KnowledgeDomain");
    var factory = owl.manager.getOWLDataFactory();
    owl.manager.addAxiom(owl.getOntology("audit").getOWLOntology(),
        factory.getOWLEquivalentClassesAxiom(owl.getOWLClass(science), owl.getOWLClass(category)));
    assertNotEquals(owl.getOWLClass(category), owl.getOWLClass(science));
    rejectsPreamble(owl, science);
  }
}
