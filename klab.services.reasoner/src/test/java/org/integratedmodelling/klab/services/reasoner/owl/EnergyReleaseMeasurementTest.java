package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.integratedmodelling.klab.data.mediation.UnitServiceImpl;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.HermiT.Reasoner;

class EnergyReleaseMeasurementTest {
  @Test void languageEnergyCategoryMatchesTheCorrectedRelease() {
    assertTrue(org.integratedmodelling.languages.api.SemanticSyntax.Type.ENERGY.is(
        org.integratedmodelling.languages.api.SemanticSyntax.TypeCategory.EXTENSIVE));
    assertFalse(org.integratedmodelling.languages.api.SemanticSyntax.Type.ENERGY.is(
        org.integratedmodelling.languages.api.SemanticSyntax.TypeCategory.INTENSIVE));
  }
  byte[] core() throws Exception {
    var directory=System.getProperty("klab.test.core.directory");
    if(directory!=null)return Files.readAllBytes(Path.of(directory,"odo.owl"));
    try(var in=getClass().getResourceAsStream("/knowledge/odo.owl")){assertNotNull(in);return in.readAllBytes();}
  }
  @Test void energyIsExtensiveAndRetainsJouleMeasurement() throws Exception {
    var manager=OWLManager.createOWLOntologyManager();
    var ontology=manager.loadOntologyFromOntologyDocument(new java.io.ByteArrayInputStream(core()));
    var f=manager.getOWLDataFactory();String ns="http://integratedmodelling.org/odo#";
    var energy=f.getOWLClass(IRI.create(ns+"Energy"));
    var reasoner=new Reasoner.ReasonerFactory().createReasoner(ontology);
    try {
      assertTrue(reasoner.isConsistent());assertTrue(reasoner.isSatisfiable(energy));
      assertTrue(reasoner.isEntailed(f.getOWLSubClassOfAxiom(energy,f.getOWLClass(IRI.create(ns+"ExtensivePhysicalProperty")))));
      assertFalse(reasoner.isEntailed(f.getOWLSubClassOfAxiom(energy,f.getOWLClass(IRI.create(ns+"IntensivePhysicalProperty")))));
    } finally {reasoner.dispose();}
    var definition=ontology.annotationAssertionAxioms(energy.getIRI())
        .filter(a->a.getProperty().getIRI().toString().equals(ns+"unit"))
        .map(a->a.getValue().asLiteral().orElseThrow().getLiteral()).findFirst().orElseThrow();
    assertEquals("J",definition);
    var units=new UnitServiceImpl();var joule=units.getUnit(definition);var kilojoule=units.getUnit("kJ");
    assertTrue(units.isCompatible(joule,units.getUnit("kg*m^2/s^2")));
    assertEquals(1000,joule.convert(1,kilojoule).doubleValue(),1e-9);
    assertEquals(1,kilojoule.convert(1000,joule).doubleValue(),1e-12);
    assertFalse(units.isCompatible(joule,units.getUnit("W")));
    assertFalse(units.isCompatible(joule,units.getUnit("J/kg")));
  }
}
