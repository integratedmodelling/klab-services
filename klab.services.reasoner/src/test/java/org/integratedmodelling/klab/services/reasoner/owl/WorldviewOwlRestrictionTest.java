package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;

class WorldviewOwlRestrictionTest {
  @Test
  void shippedCoreContractsUseAlternativesAndTypedRequirements() throws Exception {
    var manager = OWLManager.createOWLOntologyManager();
    var ontology = manager.loadOntologyFromOntologyDocument(
        getClass().getResourceAsStream("/knowledge/odo.owl"));
    var f = manager.getOWLDataFactory();
    String ns = "http://integratedmodelling.org/odo#";
    for (String kind : List.of("Identity", "Realm", "Extent", "Attribute")) {
      var property = f.getOWLObjectProperty(IRI.create(ns + "requires" + kind));
      assertTrue(ontology.containsAxiom(f.getOWLSubObjectPropertyOfAxiom(property,
          f.getOWLObjectProperty(IRI.create(ns + "requires")))));
      assertTrue(ontology.containsAxiom(f.getOWLObjectPropertyRangeAxiom(property,
          f.getOWLClass(IRI.create(ns + kind)))));
    }
    for (String name : List.of("increasesWith", "decreasesWith")) {
      var property = f.getOWLObjectProperty(IRI.create(ns + name));
      assertTrue(ontology.containsAxiom(f.getOWLObjectPropertyRangeAxiom(property,
          f.getOWLObjectUnionOf(f.getOWLClass(IRI.create(ns + "Quality")),
              f.getOWLClass(IRI.create(ns + "Ordering"))))));
    }
    var reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(ontology);
    try {
      for (String kind : List.of("Identity", "Realm", "Extent", "Attribute"))
        assertTrue(reasoner.isSatisfiable(f.getOWLObjectIntersectionOf(
            f.getOWLClass(IRI.create(ns + "Observable")),
            f.getOWLObjectSomeValuesFrom(f.getOWLObjectProperty(IRI.create(ns + "requires" + kind)),
                f.getOWLClass(IRI.create(ns + kind))))), kind);
      assertTrue(reasoner.isSatisfiable(f.getOWLObjectIntersectionOf(
          f.getOWLClass(IRI.create(ns + "Process")),
          f.getOWLObjectSomeValuesFrom(f.getOWLObjectProperty(IRI.create(ns + "affects")),
              f.getOWLClass(IRI.create(ns + "Quality"))))));
    } finally { reasoner.dispose(); }
    assertEquals("odo:isSubjective", org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.IS_SUBJECTIVE);
    assertEquals("odo:orderingRank", org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.ORDER_PROPERTY);
  }

  @Test
  void subclassAndExistentialRestrictionHaveTheExpectedDirection() {
    var owl = new OWL(null);
    owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("audit");
    ontology.define(List.of(
        Axiom.ClassAssertion("Parent", EnumSet.of(SemanticType.SUBJECT)),
        Axiom.ClassAssertion("Child", EnumSet.of(SemanticType.SUBJECT)),
        Axiom.ClassAssertion("Filler", EnumSet.of(SemanticType.SUBJECT)),
        Axiom.ObjectPropertyAssertion("relation"),
        Axiom.SubClass("Parent", "Child")));
    owl.restrictSome(ontology.getConcept("Child"), owl.getProperty("audit:relation"),
        ontology.getConcept("Filler"), ontology);
    var factory = owl.manager.getOWLDataFactory();
    var prefix = ontology.getPrefix() + "#";
    var child = factory.getOWLClass(IRI.create(prefix + "Child"));
    var parent = factory.getOWLClass(IRI.create(prefix + "Parent"));
    var filler = factory.getOWLClass(IRI.create(prefix + "Filler"));
    var property = factory.getOWLObjectProperty(IRI.create(prefix + "relation"));
    assertTrue(ontology.getOWLOntology().containsAxiom(factory.getOWLSubClassOfAxiom(child, parent)));
    assertTrue(ontology.getOWLOntology().containsAxiom(factory.getOWLSubClassOfAxiom(child,
        factory.getOWLObjectSomeValuesFrom(property, filler))));
    assertFalse(ontology.getOWLOntology().containsAxiom(factory.getOWLSubClassOfAxiom(parent, child)));
  }
}
