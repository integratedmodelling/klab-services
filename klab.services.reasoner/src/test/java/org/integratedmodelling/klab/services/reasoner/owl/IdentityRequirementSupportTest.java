package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

class IdentityRequirementSupportTest {
  @Test void dependentRequirementTargetsInheritedBearerCategoryRatherThanDependentIdentity() throws Exception {
    try (var t = new Fixture()) {
      t.owl.requireOntology("odo", "http://integratedmodelling.org").define(List.of(
          Axiom.ObjectPropertyAssertion("isInherentTo"), Axiom.ObjectPropertyAssertion("appliesTo")));
      t.declare("Height", SemanticType.QUALITY, SemanticType.OBSERVABLE);
      t.concept("Height").setAbstract(true);
      t.require("Height", t.f.getOWLObjectUnionOf(t.type("RootA"), t.type("RootB")));
      t.declare("Elevation", SemanticType.QUALITY, SemanticType.OBSERVABLE);
      t.sub("Elevation", "Height");
      t.restriction("Elevation", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.type("RootA"));
      t.ready();
      assertFalse(t.project("Elevation").isAbstract(), "Abstract identity category on the bearer satisfies the bound");
      assertTrue(t.project("Height").isAbstract(), "Authored abstractness survives");
      assertFalse(t.concept("Elevation").isAbstract());
    }
  }
  static class Fixture implements AutoCloseable {
    final OWL owl = new OWL(mock(Scope.class));
    final Ontology ontology;
    final OWLDataFactory f;
    OWLReasoner dl;
    Fixture() {
      owl.manager = OWLManager.createOWLOntologyManager(); f = owl.manager.getOWLDataFactory();
      ontology = owl.requireOntology("audit");
      for (var kind : List.of(SemanticType.SUBJECT, SemanticType.QUALITY, SemanticType.PROCESS))
        declare(kind.name(), kind, SemanticType.OBSERVABLE);
      // Self-identification tests use substantial heads; dependent targeting has its own suite.
      declare("SecondSubject", SemanticType.SUBJECT, SemanticType.OBSERVABLE);
      declare("ThirdSubject", SemanticType.SUBJECT, SemanticType.OBSERVABLE);
      for (var name : List.of("RootA", "RootB", "A", "B", "Both", "Wrong"))
        declare(name, SemanticType.IDENTITY, SemanticType.PREDICATE);
      concept("RootA").setAbstract(true); concept("RootB").setAbstract(true);
      sub("A", "RootA"); sub("B", "RootB"); sub("Both", "RootA"); sub("Both", "RootB");
    }
    ConceptImpl concept(String name) { return (ConceptImpl) ontology.getConcept(name); }
    OWLClass type(String name) { return owl.getOWLClass(concept(name)); }
    void declare(String name, SemanticType... flags) {
      ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.copyOf(List.of(flags)))));
    }
    void sub(String child, String parent) { add(f.getOWLSubClassOfAxiom(type(child), type(parent))); }
    void add(OWLAxiom axiom) { owl.manager.addAxiom(ontology.getOWLOntology(), axiom); }
    void require(String owner, OWLClassExpression filler) { restriction(owner, "requiresIdentity", filler); }
    void restriction(String owner, String property, OWLClassExpression filler) {
      add(f.getOWLSubClassOfAxiom(type(owner), f.getOWLObjectSomeValuesFrom(
          f.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#" + property)), filler)));
    }
    void ready() throws Exception {
      dl = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createNonBufferingReasoner(ontology.getOWLOntology());
      var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(owl, dl);
    }
    KimConceptImpl syntax(String head, String... identities) {
      var syntax = leaf(head); for (var identity : identities) syntax.getTraits().add(leaf(identity)); return syntax;
    }
    KimConceptImpl leaf(String name) {
      var syntax = new KimConceptImpl(); syntax.setName("audit:" + name);
      syntax.setType(EnumSet.copyOf(concept(name).getType())); return syntax;
    }
    ConceptImpl project(String head, String... identities) {
      return new IdentityRequirementSupport(owl).project(concept(head), syntax(head, identities));
    }
    public void close() { if (dl != null) dl.dispose(); }
  }

  @Test void requiredIdentityInducesAndDischargesStatusForSubstantialHeads() throws Exception {
    try (var t = new Fixture()) {
      for (var kind : List.of("SUBJECT", "ThirdSubject", "SecondSubject")) t.require(kind, t.type("RootA"));
      t.ready();
      for (var kind : List.of("SUBJECT", "ThirdSubject", "SecondSubject")) {
        assertTrue(t.project(kind).isAbstract(), kind);
        assertFalse(t.project(kind, "A").isAbstract(), kind);
        assertTrue(t.project(kind, "Wrong").isAbstract(), kind);
        assertTrue(t.project(kind, "RootA").isAbstract(), kind);
        assertFalse(t.concept(kind).isAbstract(), "Canonical flag is independent: " + kind);
        assertTrue(t.project(kind).isAbstract(), "Removing the identity restores the requirement");
      }
      assertEquals(0, t.ontology.getOWLOntology().getIndividualsInSignature().size());
    }
  }

  @Test void inheritedAndMultipleRequirementsRemainConjunctive() throws Exception {
    try (var t = new Fixture()) {
      t.require("SUBJECT", t.type("RootA"));
      t.declare("Child", SemanticType.SUBJECT, SemanticType.OBSERVABLE); t.sub("Child", "SUBJECT");
      t.require("Child", t.type("RootB")); t.ready();
      assertTrue(t.project("Child", "A").isAbstract());
      assertTrue(t.project("Child", "B").isAbstract());
      assertFalse(t.project("Child", "A", "B").isAbstract());
      assertFalse(t.project("Child", "Both").isAbstract());
      assertTrue(t.project("Child").isAbstract());
    }
  }

  @Test void inferredNecessarySuperclassRequirementsCannotBeMissedWhenAssertedPathsDoNotNameIt() throws Exception {
    try (var t = new Fixture()) {
      for (var name : List.of("ConditionOne", "ConditionTwo", "RequiredRule", "Derived"))
        t.declare(name, SemanticType.SUBJECT, SemanticType.OBSERVABLE);
      t.add(t.f.getOWLEquivalentClassesAxiom(t.type("RequiredRule"),
          t.f.getOWLObjectIntersectionOf(t.type("ConditionOne"), t.type("ConditionTwo"))));
      t.require("RequiredRule", t.type("RootA"));
      t.concept("RequiredRule").setAbstract(true); // Independent parent flag is not blindly inherited.
      t.sub("Derived", "ConditionOne"); t.sub("Derived", "ConditionTwo");
      assertTrue(t.project("Derived").isAbstract(), "Unavailable classification cannot certify no requirements");
      assertTrue(t.project("Derived", "A").isAbstract(), "Even supplied identity needs complete requirement closure");
      t.ready();
      assertTrue(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("Derived"), t.type("RequiredRule"))));
      assertTrue(t.project("Derived").isAbstract());
      assertFalse(t.project("Derived", "A").isAbstract());
      assertTrue(t.project("RequiredRule", "A").isAbstract());
      assertFalse(t.concept("Derived").isAbstract());
    }
  }

  @Test void namedConcreteIdentityProvedBelowAUnionDischargesTheCompleteFiller() throws Exception {
    try (var t = new Fixture()) {
      t.declare("UnionIdentity", SemanticType.IDENTITY, SemanticType.PREDICATE);
      var union = t.f.getOWLObjectUnionOf(t.type("RootA"), t.type("RootB"));
      t.add(t.f.getOWLSubClassOfAxiom(t.type("UnionIdentity"), union));
      t.require("SecondSubject", union); t.ready();
      assertTrue(t.owl.isSatisfiable(t.type("UnionIdentity")));
      assertTrue(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("UnionIdentity"), union)));
      assertFalse(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("UnionIdentity"), t.type("RootA"))));
      assertFalse(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("UnionIdentity"), t.type("RootB"))));
      assertFalse(t.project("SecondSubject", "UnionIdentity").isAbstract());
      assertTrue(t.project("SecondSubject", "Wrong").isAbstract());
      var restricted = new IdentityRequirementSupport(t.owl, (root, identity) -> !root.equals(t.concept("RootA")));
      assertTrue(restricted.project(t.concept("SecondSubject"), t.syntax("SecondSubject", "UnionIdentity")).isAbstract(),
          "An ambiguous union cannot bypass an ineligible authority branch");
      assertFalse(restricted.project(t.concept("SecondSubject"), t.syntax("SecondSubject", "B")).isAbstract(),
          "A proved eligible branch remains sufficient");
    }
  }

  @Test void publicOperandAndRestrictionTraversalsMatchAtomicEligibilityWithoutChangingDeclarations() throws Exception {
    try (var t = new Fixture()) {
      t.declare("RequiredRule", SemanticType.SUBJECT, SemanticType.OBSERVABLE);
      t.declare("UnionHead", SemanticType.SUBJECT, SemanticType.OBSERVABLE, SemanticType.UNION);
      t.declare("Container", SemanticType.PROCESS, SemanticType.OBSERVABLE);
      t.require("RequiredRule", t.type("RootA"));
      t.add(t.f.getOWLSubClassOfAxiom(t.type("UnionHead"),
          t.f.getOWLObjectUnionOf(t.type("RequiredRule"), t.type("SUBJECT"))));
      t.owl.requireOntology("odo", "http://integratedmodelling.org").define(List.of(Axiom.ObjectPropertyAssertion("isInherentTo"),
          Axiom.ObjectPropertyAssertion("appliesTo")));
      t.restriction("Container", "isInherentTo", t.type("RequiredRule"));
      t.restriction("Container", "appliesTo", t.type("RequiredRule"));
      t.ready();
      var service = mock(ReasonerService.class, CALLS_REAL_METHODS);
      var field = ReasonerService.class.getDeclaredField("owl"); field.setAccessible(true); field.set(service, t.owl);
      var atomic = service.resolveConceptInternal("audit:RequiredRule"); assertTrue(atomic.isAbstract());
      assertTrue(service.operands(t.concept("UnionHead")).stream()
          .filter(c -> c.equals(t.concept("RequiredRule"))).findFirst().orElseThrow().isAbstract());
      assertTrue(service.operands(t.concept("RequiredRule")).iterator().next().isAbstract());
      assertTrue(service.directInherent(t.concept("Container")).isAbstract());
      assertTrue(service.inherent(t.concept("Container")).isAbstract());
      assertTrue(service.applicableObservables(t.concept("Container")).iterator().next().isAbstract());
      assertTrue(service.coreObservable(t.concept("RequiredRule")).isAbstract());
      assertTrue(service.baseObservable(t.concept("RequiredRule")).isAbstract());
      assertTrue(service.rawObservable(t.concept("RequiredRule")).isAbstract());
      assertFalse(t.concept("RequiredRule").isAbstract());
    }
  }

  @Test void aCompositeFillerPreservesItsWitnessAndUnionSemantics() throws Exception {
    try (var t = new Fixture()) {
      t.require("SUBJECT", t.f.getOWLObjectUnionOf(t.type("RootA"), t.type("RootB")));
      t.require("SecondSubject", t.f.getOWLObjectIntersectionOf(t.type("RootA"), t.type("RootB")));
      t.ready();
      assertFalse(t.project("SUBJECT", "A").isAbstract());
      assertFalse(t.project("SUBJECT", "B").isAbstract());
      assertTrue(t.project("SecondSubject", "A", "B").isAbstract(), "Separate identities do not fill one conjunction");
      assertFalse(t.project("SecondSubject", "Both").isAbstract());
    }
  }

  @Test void explicitAbstractFamiliesAndAbstractAttributesSurviveIdentityQualification() throws Exception {
    try (var t = new Fixture()) {
      for (var family : List.of("ArtifactProduction", "ArtifactModification")) {
        t.declare(family, SemanticType.PROCESS, SemanticType.OBSERVABLE);
        t.concept(family).setAbstract(true); t.require(family, t.type("RootA"));
      }
      t.declare("Unresolved", SemanticType.ATTRIBUTE, SemanticType.PREDICATE);
      t.concept("Unresolved").setAbstract(true); t.require("ThirdSubject", t.type("RootA")); t.ready();
      for (var family : List.of("ArtifactProduction", "ArtifactModification"))
        assertTrue(t.project(family, "A").isAbstract());
      assertTrue(t.project("ThirdSubject", "A", "Unresolved").isAbstract());
      assertFalse(t.project("ThirdSubject", "A").isAbstract());
    }
  }

  @Test void concreteInheritedIdentityCountsButAnonymousAlternativesDoNot() throws Exception {
    try (var t = new Fixture()) {
      t.require("SUBJECT", t.type("RootA")); t.restriction("SUBJECT", "hasIdentity", t.type("A"));
      t.require("SecondSubject", t.type("RootA"));
      t.restriction("SecondSubject", "hasIdentity", t.f.getOWLObjectUnionOf(t.type("A"), t.type("Wrong")));
      t.ready(); assertFalse(t.project("SUBJECT").isAbstract()); assertTrue(t.project("SecondSubject").isAbstract());
    }
  }

  @Test void inconsistentAndUnavailableHierarchyCannotSupplyVacuousProof() throws Exception {
    try (var t = new Fixture()) {
      t.require("SecondSubject", t.type("RootA")); t.add(t.f.getOWLSubClassOfAxiom(t.type("A"), t.f.getOWLNothing()));
      assertTrue(t.project("SecondSubject", "Both").isAbstract(), "No backend cannot prove compatibility");
      t.ready(); assertTrue(t.project("SecondSubject", "A").isAbstract());
    }
  }

  @Test void qualifiersStayWithTheirOwnHeadAndUnaryBoundariesRemainIntact() throws Exception {
    try (var t = new Fixture()) {
      t.require("SecondSubject", t.type("RootA")); t.require("SUBJECT", t.type("RootB")); t.ready();
      var expression = t.syntax("SecondSubject"); expression.setInherent(t.syntax("SUBJECT", "A"));
      assertTrue(new IdentityRequirementSupport(t.owl).project(t.concept("SecondSubject"), expression).isAbstract());
      var wrapper = new KimConceptImpl(); wrapper.setObservable(t.syntax("SecondSubject"));
      wrapper.getTraits().add(t.leaf("A"));
      assertFalse(new IdentityRequirementSupport(t.owl).project(t.concept("SecondSubject"), wrapper).isAbstract());
      wrapper.setSemanticModifier(UnarySemanticOperator.COUNT);
      assertFalse(new IdentityRequirementSupport(t.owl).project(t.concept("SecondSubject"), wrapper).isAbstract());
    }
  }

  @Test void authorityIdentityRequiresAdmissionEvenWhenItsOwlHierarchyMatches() throws Exception {
    try (var t = new Fixture()) {
      t.concept("A").getType().add(SemanticType.AUTHORITY_IDENTITY);
      t.require("SecondSubject", t.type("RootA")); t.ready();
      assertTrue(t.project("SecondSubject", "A").isAbstract());
      var trusted = new IdentityRequirementSupport(t.owl, (root, identity) -> identity == t.concept("A"));
      assertFalse(trusted.project(t.concept("SecondSubject"), t.syntax("SecondSubject", "A")).isAbstract());
      assertTrue(new IdentityRequirementSupport(t.owl, (root, identity) -> false)
          .project(t.concept("SecondSubject"), t.syntax("SecondSubject", "A")).isAbstract());
    }
  }

  @Test void selectorsAndTransformedPredicatesAreNotConcreteIdentityEvidence() throws Exception {
    try (var t = new Fixture()) {
      t.require("SecondSubject", t.type("RootA")); t.ready();
      var syntax = t.syntax("SecondSubject", "A");
      syntax.getTraits().getFirst().getType().add(SemanticType.ANY);
      assertTrue(new IdentityRequirementSupport(t.owl).project(t.concept("SecondSubject"), syntax).isAbstract());
      syntax.getTraits().clear();
      var transformed = new KimConceptImpl(); transformed.setObservable(t.leaf("A"));
      transformed.setSemanticModifier(UnarySemanticOperator.TYPE); syntax.getTraits().add(transformed);
      assertTrue(new IdentityRequirementSupport(t.owl).project(t.concept("SecondSubject"), syntax).isAbstract());
    }
  }

  @Test void atomicServiceResolutionAndObservablePromotionRetainEffectiveStatusWithoutMutation() throws Exception {
    try (var t = new Fixture()) {
      t.require("SecondSubject", t.type("RootA")); t.ready();
      var service = mock(ReasonerService.class, CALLS_REAL_METHODS);
      var field = ReasonerService.class.getDeclaredField("owl"); field.setAccessible(true); field.set(service, t.owl);
      assertTrue(service.resolveConceptInternal("audit:SecondSubject").isAbstract());
      var result = service.projectExpressionStatus(t.concept("SecondSubject"), t.syntax("SecondSubject", "A"));
      assertFalse(ObservableImpl.promote(result, null).isAbstract());
      assertNotSame(result, t.concept("SecondSubject")); assertFalse(t.concept("SecondSubject").isAbstract());
      assertTrue(service.projectExpressionStatus(t.concept("SecondSubject"), t.syntax("SecondSubject")).isAbstract());
    }
  }
}
