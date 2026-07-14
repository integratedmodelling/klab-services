package org.integratedmodelling.tests.services.reasoner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

class OwlApi5IntegrationTest {

  @Test
  void hermitClassifiesAnOwlApi5Ontology() throws Exception {
    var manager = OWLManager.createOWLOntologyManager();
    var ontology = manager.createOntology(IRI.create("urn:klab:test:owlapi5"));
    var dataFactory = manager.getOWLDataFactory();
    var animal = dataFactory.getOWLClass(IRI.create("urn:klab:test:Animal"));
    var mammal = dataFactory.getOWLClass(IRI.create("urn:klab:test:Mammal"));
    var dog = dataFactory.getOWLClass(IRI.create("urn:klab:test:Dog"));

    manager.addAxioms(
        ontology,
        Set.of(
            dataFactory.getOWLSubClassOfAxiom(mammal, animal),
            dataFactory.getOWLSubClassOfAxiom(dog, mammal)));

    OWLReasoner reasoner = new Reasoner.ReasonerFactory().createReasoner(ontology);
    try {
      reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

      assertTrue(reasoner.isConsistent());
      assertTrue(reasoner.getSuperClasses(dog, false).containsEntity(animal));
      assertTrue(reasoner.isEntailed(dataFactory.getOWLSubClassOfAxiom(dog, animal)));
    } finally {
      reasoner.dispose();
    }
  }
}
