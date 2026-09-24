package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

class OWLSemanticClauseSupportTest {
  @Test
  void importedProvenanceRestrictionsDoNotRequireRegisteredNamespaces() throws Exception {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(
        Scope.class.getClassLoader(), new Class<?>[] {Scope.class},
        (self, method, args) -> null);
    var owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("domain");
    ontology.define(List.of(
        Axiom.ClassAssertion("Quality", EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Tree", EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Rock", EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE))));
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("isInherentTo")));
    var factory = owl.manager.getOWLDataFactory();
    var owner = ontology.getConcept("Quality");
    var quality = owl.getOWLClass(owner);
    var tree = ontology.getConcept("Tree");
    var rock = ontology.getConcept("Rock");
    var provenance = factory.getOWLObjectProperty(IRI.create("http://www.w3.org/ns/prov#wasGeneratedBy"));
    owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLSubClassOfAxiom(
        quality, factory.getOWLObjectSomeValuesFrom(provenance, factory.getOWLThing())));
    var support = new OWLSemanticClauseSupport(owl);
    assertTrue(support.applicableTo(owner, tree));
    assertTrue(support.clauses(owner).isEmpty());

    // Even an unregistered external subproperty must retain its semantic restriction.
    var external = factory.getOWLObjectProperty(IRI.create("https://example.org/external#bearer"));
    var inherent = factory.getOWLObjectProperty(IRI.create(owl.getProperty("odo:isInherentTo").getURI()));
    owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLSubObjectPropertyOfAxiom(external, inherent));
    owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLSubClassOfAxiom(
        quality, factory.getOWLObjectSomeValuesFrom(external, owl.getOWLClass(tree))));
    support = new OWLSemanticClauseSupport(owl);
    assertTrue(support.accepts(owner, SemanticRole.INHERENT, tree));
    assertFalse(support.accepts(owner, SemanticRole.INHERENT, rock));
    assertEquals(1, support.clauses(owner).size());
  }

  @Test
  void applicabilityConstrainsPredicatesAndAddsToDependentInherency() throws Exception {
    var scope =
        (Scope)
            java.lang.reflect.Proxy.newProxyInstance(
                Scope.class.getClassLoader(),
                new Class<?>[] {Scope.class},
                (self, method, args) -> null);
    var owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("domain");
    for (String name : List.of("Tree", "Oak", "Pine", "Rock"))
      ontology.define(
          List.of(
              Axiom.ClassAssertion(
                  name,
                  EnumSet.of(
                      SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE))));
    for (String name : List.of("Predicate", "NarrowPredicate"))
      ontology.define(
          List.of(
              Axiom.ClassAssertion(
                  name, EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE))));
    ontology.define(
        List.of(
            Axiom.ClassAssertion(
                "Quality", EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE)),
            Axiom.ClassAssertion(
                "Process", EnumSet.of(SemanticType.PROCESS, SemanticType.OBSERVABLE)),
            Axiom.SubClass("Tree", "Oak"),
            Axiom.SubClass("Tree", "Pine"),
            Axiom.SubClass("Predicate", "NarrowPredicate")));
    owl.requireOntology("odo")
        .define(
            List.of(
                Axiom.ObjectPropertyAssertion("appliesTo"),
                Axiom.ObjectPropertyAssertion("isInherentTo")));
    owl.setApplicableObservables(
        ontology.getConcept("Predicate"),
        List.of(ontology.getConcept("Tree"), ontology.getConcept("Rock")),
        ontology);
    owl.setApplicableObservables(
        ontology.getConcept("NarrowPredicate"), List.of(ontology.getConcept("Oak")), ontology);
    for (String name : List.of("Quality", "Process")) {
      owl.setApplicableObservables(
          ontology.getConcept(name),
          List.of(ontology.getConcept("Oak"), ontology.getConcept("Rock")),
          ontology);
      owl.restrictSome(
          ontology.getConcept(name),
          owl.getProperty("odo:isInherentTo"),
          ontology.getConcept("Tree"),
          ontology);
    }
    var merged =
        owl.manager.createOntology(
            owl.manager
                .ontologies()
                .flatMap(o -> o.axioms())
                .collect(java.util.stream.Collectors.toSet()));
    var hermit = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    try {
      for (boolean strict : List.of(false, true)) {
        var field = OWL.class.getDeclaredField("reasoner");
        field.setAccessible(true);
        field.set(owl, strict ? hermit : null);
        var support = new OWLSemanticClauseSupport(owl);
        assertTrue(
            support.applicableTo(ontology.getConcept("Predicate"), ontology.getConcept("Rock")));
        assertTrue(
            support.applicableTo(
                ontology.getConcept("NarrowPredicate"), ontology.getConcept("Oak").collective()));
        assertFalse(
            support.applicableTo(
                ontology.getConcept("NarrowPredicate"), ontology.getConcept("Pine")));
        assertFalse(
            support.applicableTo(
                ontology.getConcept("NarrowPredicate"), ontology.getConcept("Rock")));
        assertFalse(support.hasBounds(ontology.getConcept("Predicate"), SemanticRole.INHERENT));
        for (String name : List.of("Quality", "Process")) {
          var dependent = ontology.getConcept(name);
          assertTrue(support.hasBounds(dependent, SemanticRole.INHERENT));
          assertTrue(
              support.accepts(
                  dependent, SemanticRole.INHERENT, ontology.getConcept("Oak").collective()));
          assertFalse(
              support.accepts(dependent, SemanticRole.INHERENT, ontology.getConcept("Pine")));
          assertFalse(
              support.accepts(dependent, SemanticRole.INHERENT, ontology.getConcept("Rock")));
          assertTrue(
              support.clauses(dependent).stream()
                  .anyMatch(
                      clause ->
                          clause.getCode().stream()
                              .anyMatch(token -> "applies to".equals(token.getValue()))));
        }
      }
    } finally {
      hermit.dispose();
    }
  }

  @Test
  void realOntologyBoundsPreserveUnionAndAllInheritedRestrictions() throws Exception {
    var scope =
        (Scope)
            java.lang.reflect.Proxy.newProxyInstance(
                Scope.class.getClassLoader(),
                new Class<?>[] {Scope.class},
                (self, method, args) -> null);
    var owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("audit");
    var axioms = new ArrayList<Axiom>();
    for (String name : List.of("Parent", "Child", "Tree", "Oak", "Pine", "Rock"))
      axioms.add(
          Axiom.ClassAssertion(name, EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE)));
    axioms.add(Axiom.SubClass("Parent", "Child"));
    axioms.add(Axiom.SubClass("Tree", "Oak"));
    axioms.add(Axiom.SubClass("Tree", "Pine"));
    ontology.define(axioms);
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("isInherentTo")));
    var factory = owl.manager.getOWLDataFactory();
    var owner = ontology.getConcept("Child");
    var parent = owl.getOWLClass(ontology.getConcept("Parent"));
    var child = owl.getOWLClass(owner);
    var tree = owl.getOWLClass(ontology.getConcept("Tree"));
    var oak = owl.getOWLClass(ontology.getConcept("Oak"));
    var rock = owl.getOWLClass(ontology.getConcept("Rock"));
    var property =
        factory.getOWLObjectProperty(IRI.create(owl.getProperty("odo:isInherentTo").getURI()));
    owl.manager.addAxiom(
        ontology.getOWLOntology(),
        factory.getOWLSubClassOfAxiom(parent, factory.getOWLObjectSomeValuesFrom(property, tree)));
    owl.manager.addAxiom(
        ontology.getOWLOntology(),
        factory.getOWLEquivalentClassesAxiom(
            child,
            factory.getOWLObjectIntersectionOf(
                parent,
                factory.getOWLObjectSomeValuesFrom(
                    property, factory.getOWLObjectUnionOf(oak, rock)))));
    var merged =
        owl.manager.createOntology(
            owl.manager
                .ontologies()
                .flatMap(o -> o.axioms())
                .collect(java.util.stream.Collectors.toSet()));
    var hermit = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    var field = OWL.class.getDeclaredField("reasoner");
    field.setAccessible(true);
    field.set(owl, hermit);
    try {
      var support = new OWLSemanticClauseSupport(owl);
      assertTrue(support.accepts(owner, SemanticRole.INHERENT, ontology.getConcept("Oak")));
      assertFalse(support.accepts(owner, SemanticRole.INHERENT, ontology.getConcept("Pine")));
      assertFalse(support.accepts(owner, SemanticRole.INHERENT, ontology.getConcept("Rock")));
      var clauses = support.clauses(owner);
      assertTrue(
          clauses.stream()
              .anyMatch(
                  c ->
                      c.isInherited()
                          && c.getFiller() != null
                          && c.getFiller().getUrn().equals("audit:Tree")));
      assertTrue(
          clauses.stream()
              .anyMatch(
                  c ->
                      !c.isInherited()
                          && c.getCode().stream().anyMatch(t -> "or".equals(t.getValue()))));
      assertTrue(support.accepts(owner, SemanticRole.GOAL, ontology.getConcept("Rock")));
    } finally {
      hermit.dispose();
    }
  }
}
