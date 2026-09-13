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
