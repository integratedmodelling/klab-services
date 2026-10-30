package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

class BearerIdentityRequirementTest {
  private IdentityRequirementSupportTest.Fixture fixture() {
    var t = new IdentityRequirementSupportTest.Fixture();
    t.owl.requireOntology("odo", "http://integratedmodelling.org").define(List.of(
        Axiom.ObjectPropertyAssertion("isInherentTo"), Axiom.ObjectPropertyAssertion("appliesTo")));
    return t;
  }

  @Test void disjointImportedBearerWitnessesRemainIndependentAndEachMustQualify() throws Exception {
    for (boolean secondQualifies : List.of(true, false)) {
      try (var t = fixture()) {
        t.declare("OtherBearer", SemanticType.SUBJECT, SemanticType.OBSERVABLE);
        t.require("QUALITY", t.type("RootA"));
        t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
        t.restriction("QUALITY", "isInherentTo", t.type("OtherBearer"));
        t.add(t.f.getOWLDisjointClassesAxiom(t.type("SUBJECT"), t.type("OtherBearer")));
        t.restriction("SUBJECT", "hasIdentity", t.type("RootA"));
        if (secondQualifies) t.restriction("OtherBearer", "hasIdentity", t.type("RootA"));
        t.ready();
        assertTrue(t.owl.isSatisfiable(t.type("QUALITY")),
            "Separate imported existential witnesses remain logically consistent");
        assertEquals(!secondQualifies, t.project("QUALITY").isAbstract(),
            "One imported witness cannot lend its identity to another; both qualifying witnesses suffice");
      }
    }
  }

  @Test void specializedIdentityPropertiesCountButUnrelatedPropertiesDoNot() throws Exception {
    for (boolean identitySubproperty : List.of(true, false)) {
      try (var t = fixture()) {
        t.require("QUALITY", t.type("RootA"));
        t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
        var family = t.f.getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create(
            "http://integratedmodelling.org/odo#familyPredicate"));
        var identity = t.f.getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create(
            "http://integratedmodelling.org/odo#hasIdentity"));
        if (identitySubproperty) t.add(t.f.getOWLSubObjectPropertyOfAxiom(family, identity));
        t.restriction("SUBJECT", "familyPredicate", t.type("RootA"));
        t.ready();
        assertEquals(identitySubproperty, t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("SUBJECT"),
            t.f.getOWLObjectSomeValuesFrom(identity, t.type("RootA")))));
        assertEquals(!identitySubproperty, t.project("QUALITY").isAbstract(),
            "Only a proved identity-family property supplies bearer identity evidence");
      }
    }
  }

  @Test void missingBearerAndWrongTargetIdentityNeverDischargeDependentRequirement() throws Exception {
    try (var t = fixture()) {
      t.require("QUALITY", t.type("RootA"));
      t.restriction("QUALITY", "hasIdentity", t.type("A"));
      t.ready();
      assertTrue(t.project("QUALITY", "A").isAbstract());
      var syntax = t.syntax("QUALITY", "A"); syntax.setInherent(t.leaf("SUBJECT"));
      assertTrue(new IdentityRequirementSupport(t.owl).project(t.concept("QUALITY"), syntax).isAbstract());
      syntax.setInherent(t.syntax("SUBJECT", "RootA"));
      assertFalse(new IdentityRequirementSupport(t.owl).project(t.concept("QUALITY"), syntax).isAbstract());
    }
  }

  @Test void universalBearerTypeCanProveCategoryWithoutAssertingBearerExistence() throws Exception {
    for (boolean qualifyingType : List.of(true, false)) {
      try (var t = fixture()) {
        t.require("QUALITY", t.type("RootA"));
        var inherent = t.f.getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create(
            "http://integratedmodelling.org/odo#isInherentTo"));
        t.add(t.f.getOWLSubClassOfAxiom(t.type("QUALITY"),
            t.f.getOWLObjectAllValuesFrom(inherent, t.type("SUBJECT"))));
        if (qualifyingType) t.restriction("SUBJECT", "hasIdentity", t.type("RootA"));
        t.ready();
        int axioms = t.ontology.getOWLOntology().getAxiomCount();
        assertEquals(!qualifyingType, t.project("QUALITY").isAbstract(),
            "A necessary bearer type must prove the identity category; a bare universal cannot waive it");
        assertFalse(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("QUALITY"),
            t.f.getOWLObjectSomeValuesFrom(inherent, t.type("SUBJECT")))),
            "Category eligibility must not invent an existential bearer witness");
        assertEquals(axioms, t.ontology.getOWLOntology().getAxiomCount());
      }
    }
  }

  @Test void identityAlternativesNeedEntailmentRatherThanOverlap() throws Exception {
    try (var t = fixture()) {
      t.require("QUALITY", t.f.getOWLObjectUnionOf(t.type("RootA"), t.type("RootB")));
      t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.f.getOWLObjectUnionOf(t.type("A"), t.type("Wrong")));
      t.ready();
      assertTrue(t.project("QUALITY").isAbstract(), "One qualifying alternative is not sufficient evidence");
    }
    try (var t = fixture()) {
      t.require("QUALITY", t.f.getOWLObjectUnionOf(t.type("RootA"), t.type("RootB")));
      t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.f.getOWLObjectUnionOf(t.type("RootA"), t.type("RootB")));
      t.ready(); assertFalse(t.project("QUALITY").isAbstract());
    }
  }

  @Test void inheritedClausesAreConjunctiveAndEachIntersectionNeedsOneWitness() throws Exception {
    try (var t = fixture()) {
      t.require("QUALITY", t.type("RootA"));
      t.declare("Child", SemanticType.QUALITY, SemanticType.OBSERVABLE); t.sub("Child", "QUALITY");
      t.require("Child", t.type("RootB"));
      t.restriction("Child", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.type("A"));
      t.ready(); assertTrue(t.project("Child").isAbstract());
    }
    try (var t = fixture()) {
      t.require("QUALITY", t.f.getOWLObjectIntersectionOf(t.type("RootA"), t.type("RootB")));
      t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.type("A"));
      t.restriction("SUBJECT", "hasIdentity", t.type("B"));
      t.ready(); assertTrue(t.project("QUALITY").isAbstract());
    }
    try (var t = fixture()) {
      t.require("QUALITY", t.f.getOWLObjectIntersectionOf(t.type("RootA"), t.type("RootB")));
      t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.type("Both"));
      t.ready(); assertFalse(t.project("QUALITY").isAbstract());
    }
  }

  @Test void processNativeIdentityStaysOnProcessAndDoesNotSatisfyBearerObligation() throws Exception {
    try (var t = fixture()) {
      t.require("PROCESS", t.type("RootA"));
      t.restriction("PROCESS", "hasIdentity", t.type("A"));
      t.restriction("PROCESS", "isInherentTo", t.type("SUBJECT"));
      t.ready(); assertTrue(t.project("PROCESS").isAbstract());
      var syntax = t.syntax("PROCESS"); syntax.setInherent(t.syntax("SUBJECT", "RootA"));
      int axioms = t.ontology.getOWLOntology().getAxiomCount();
      assertFalse(new IdentityRequirementSupport(t.owl).project(t.concept("PROCESS"), syntax).isAbstract());
      assertEquals(axioms, t.ontology.getOWLOntology().getAxiomCount(), "Eligibility creates no traits or individuals");
      assertTrue(t.owl.isEntailed(t.f.getOWLSubClassOfAxiom(t.type("PROCESS"), t.f.getOWLObjectSomeValuesFrom(
          t.f.getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/odo#hasIdentity")), t.type("A")))));
    }
  }

  @Test void inconsistentBearerDoesNotGiveVacuousProofAndApplicabilityAloneIsNotEvidence() throws Exception {
    try (var t = fixture()) {
      t.require("QUALITY", t.type("RootA"));
      t.restriction("QUALITY", "appliesTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.type("RootA"));
      t.ready(); assertTrue(t.project("QUALITY").isAbstract());
    }
    try (var t = fixture()) {
      t.require("QUALITY", t.type("RootA"));
      t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.type("RootA"));
      t.add(t.f.getOWLSubClassOfAxiom(t.type("SUBJECT"), t.f.getOWLNothing()));
      t.ready(); assertTrue(t.project("QUALITY").isAbstract());
    }
  }

  @Test void abstractAuthorityCategoryDoesNotBypassAdmission() throws Exception {
    try (var t = fixture()) {
      t.concept("RootA").getType().add(SemanticType.AUTHORITY_IDENTITY);
      t.require("QUALITY", t.type("RootA"));
      t.restriction("QUALITY", "isInherentTo", t.type("SUBJECT"));
      t.restriction("SUBJECT", "hasIdentity", t.type("RootA"));
      t.ready(); assertTrue(t.project("QUALITY").isAbstract());
    }
  }
}
