package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.*;

/** Real public declaration and HermiT proof: independent role families are conjunctive. */
class RoleFamilyConjunctionTest {
  static class Fixture extends ClassifierNativeAdmissionTest.Fixture {
    Fixture() throws Exception {
      ClassifierNativeAdmissionTest.field(owl,OWL.class,"reasonerActive",true);
      for (var name : List.of("RoleFamilyA","RoleFamilyB","RoleA","RoleADeep","AlternateA","RoleB","RoleChoice")) {
        declare(name,SemanticType.ROLE,SemanticType.PREDICATE);register(leaf(name));
      }
      for (var name : List.of("RoleFamilyA","RoleFamilyB")) {
        concept(name).setAbstract(true);concept(name).getType().add(SemanticType.ABSTRACT);
        concept(name).getMetadata().put(CoreOntology.NS.BASE_DECLARATION,"true");register(leaf(name));
      }
      sub("RoleA","RoleFamilyA");sub("RoleADeep","RoleA");sub("AlternateA","RoleFamilyA");
      sub("RoleB","RoleFamilyB");sub("RoleChoice","RoleFamilyA");
      add(f.getOWLEquivalentClassesAxiom(type("RoleChoice"),f.getOWLObjectUnionOf(type("RoleA"),type("AlternateA"))));
    }
    Concept build(String... roles) {
      var subject=leaf("Region");for (var role:roles) subject.getRoles().add(leaf(role));
      return reasoner.declareConcept(register(subject));
    }
    OWLClassExpression some(String role) {
      return f.getOWLObjectSomeValuesFrom(owl.getProperty(CoreOntology.NS.HAS_ROLE_PROPERTY)._owl.asOWLObjectProperty(),type(role));
    }
    void flush() {
      for(var o:owl.getOntologies(true))if(o!=ontology)owl.registerWithReasoner(o);
      dl.flush();
    }
    boolean entails(Concept subject, OWLClassExpression required) {
      return owl.isEntailed(f.getOWLSubClassOfAxiom(owl.getOWLClass(subject),required));
    }
    boolean asserted(Concept subject,OWLClassExpression required) {
      var axiom=f.getOWLSubClassOfAxiom(owl.getOWLClass(subject),required);
      return owl.manager.ontologies().anyMatch(o->o.containsAxiom(axiom));
    }
  }

  @Test void distinctRoleFamiliesHaveIndependentExistentialsAndSubtypeChecks() throws Exception {
    try (var t=new Fixture()) {
      var both=t.build("RoleADeep","RoleB");var first=t.build("RoleADeep");var second=t.build("RoleB");
      var mixedSyntax=t.leaf("Region");mixedSyntax.getTraits().add(t.leaf("Concrete"));
      mixedSyntax.getRoles().add(t.leaf("RoleADeep"));mixedSyntax.getRoles().add(t.leaf("RoleB"));
      var mixed=t.reasoner.declareConcept(t.register(mixedSyntax));t.flush();
      assertTrue(t.asserted(both,t.some("RoleADeep")),"Each asserted role must have its own existential");
      assertTrue(t.asserted(both,t.some("RoleB")));
      assertTrue(t.entails(both,t.some("RoleA")));assertTrue(t.entails(both,t.some("RoleB")));
      assertFalse(t.entails(first,t.some("RoleB")));assertFalse(t.entails(second,t.some("RoleA")));
      assertTrue(t.reasoner.is(t.concept("RoleADeep"),t.concept("RoleA")));
      assertFalse(t.reasoner.is(t.concept("RoleB"),t.concept("RoleA")));
      assertTrue(t.reasoner.hasRole(both,t.concept("RoleFamilyA")));
      assertTrue(t.reasoner.hasRole(both,t.concept("RoleFamilyB")));
      assertFalse(t.reasoner.hasRole(first,t.concept("RoleFamilyB")));
      assertTrue(t.reasoner.hasTrait(mixed,t.concept("Category")));
      assertFalse(t.reasoner.hasTrait(both,t.concept("Category")));
      assertTrue(t.reasoner.hasRole(mixed,t.concept("RoleFamilyA")));
      assertTrue(t.reasoner.hasRole(mixed,t.concept("RoleFamilyB")));
      assertFalse(t.reasoner.hasTrait(mixed,t.concept("RoleFamilyA")),"Role attribution must not leak into the trait channel");
      assertEquals(Set.of("audit:RoleADeep","audit:RoleB"),t.reasoner.directRoles(both).stream().map(Concept::getUrn).collect(java.util.stream.Collectors.toSet()));
    }
  }

  @Test void alternativesInsideOneAuthoredRoleRemainAlternatives() throws Exception {
    try (var t=new Fixture()) {
      var subject=t.build("RoleChoice","RoleB");t.flush();
      assertTrue(t.asserted(subject,t.some("RoleChoice")));assertTrue(t.asserted(subject,t.some("RoleB")));
      var roleProperty=t.owl.getProperty(CoreOntology.NS.HAS_ROLE_PROPERTY)._owl.asOWLObjectProperty();
      assertTrue(t.entails(subject,t.f.getOWLObjectSomeValuesFrom(roleProperty,t.f.getOWLObjectUnionOf(t.type("RoleA"),t.type("AlternateA")))));
      assertFalse(t.entails(subject,t.some("RoleA")));assertFalse(t.entails(subject,t.some("AlternateA")));
      assertTrue(t.entails(subject,t.some("RoleB")));
    }
  }

  @Test void competingRolesInOneLexicalFamilyStillFailValidation() throws Exception {
    try (var t=new Fixture()) {
      var invalid=t.build("RoleA","AlternateA");
      assertTrue(invalid.is(SemanticType.NOTHING) || org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(invalid.getNotifications()));
    }
  }
}
