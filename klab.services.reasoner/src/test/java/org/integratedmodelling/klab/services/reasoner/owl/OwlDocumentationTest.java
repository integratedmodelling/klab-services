package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;


import java.util.List;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;

class OwlDocumentationTest {
  @Test void separatesAssertionsAndInheritanceAndDoesNotInheritFromFillersOrUnionAlternatives() throws Exception {
    var manager = OWLManager.createOWLOntologyManager();
    var ontology = manager.createOntology(IRI.create("urn:audit"));
    var f = manager.getOWLDataFactory();
    var child = f.getOWLClass(IRI.create("urn:Child"));
    var parent = f.getOWLClass(IRI.create("urn:Parent"));
    var filler = f.getOWLClass(IRI.create("urn:Filler"));
    var alternative = f.getOWLClass(IRI.create("urn:Alternative"));
    var of = f.getOWLObjectProperty(IRI.create("urn:of"));
    var some = f.getOWLObjectSomeValuesFrom(of, filler);
    var all = f.getOWLObjectAllValuesFrom(of, filler);
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(child, parent));
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(parent, child)); // cyclic hierarchy
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(child, some));
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(parent, all));
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(filler, f.getOWLObjectMinCardinality(7, of)));
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(alternative, f.getOWLObjectMinCardinality(9, of)));
    manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(child, f.getOWLObjectUnionOf(filler, alternative)));
    manager.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(f.getRDFSLabel(), child.getIRI(), f.getOWLLiteral("Child label")));
    var markdown = OwlDocumentation.describe(child, List.of(ontology));
    var asserted = markdown.substring(markdown.indexOf("## Asserted"), markdown.indexOf("## Inherited"));
    var inherited = markdown.substring(markdown.indexOf("## Inherited"), markdown.indexOf("## All axioms"));
    assertTrue(asserted.contains("ObjectSomeValuesFrom"));
    assertFalse(asserted.contains("ObjectAllValuesFrom"));
    assertTrue(inherited.contains("ObjectAllValuesFrom"));
    assertTrue(inherited.contains("urn:Parent"));
    assertFalse(inherited.contains("ObjectMinCardinality"));
    assertTrue(markdown.contains("Child label"));
  }
}
