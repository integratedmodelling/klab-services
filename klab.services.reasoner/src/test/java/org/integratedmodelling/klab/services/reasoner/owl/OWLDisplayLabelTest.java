package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class OWLDisplayLabelTest {
  @Test void directUnaryFactoriesUseReadableDisplayAnnotations() {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(Scope.class.getClassLoader(),
        new Class<?>[]{Scope.class}, (proxy, method, args) -> null);
    var owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
    owl.requireOntology("klab").define(List.of(
        Axiom.AnnotationPropertyAssertion("displayLabel"),
        Axiom.AnnotationPropertyAssertion("referenceName"),
        Axiom.AnnotationPropertyAssertion("baseDeclaration")));
    var ns = org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.DESCRIBES_OBSERVABLE_PROPERTY.split(":");
    owl.requireOntology(ns[0]).define(List.of(Axiom.ObjectPropertyAssertion(ns[1])));
    var ontology = owl.requireOntology("earth");
    ontology.define(List.of(Axiom.ClassAssertion("Region", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE))));
    assertEquals("Presence of region", owl.makePresence(ontology.getConcept("Region")).displayLabel());
    assertEquals("Number of regions", owl.makeCount(ontology.getConcept("Region")).displayLabel());
  }

  @Test void generatedSubclassPersistsReadableLabelAndReplacingItLeavesOneAnnotation() {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(Scope.class.getClassLoader(),
        new Class<?>[]{Scope.class}, (proxy, method, args) -> null);
    var owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
    owl.requireOntology("klab").define(List.of(
        Axiom.AnnotationPropertyAssertion("displayLabel"),
        Axiom.AnnotationPropertyAssertion("conceptDefinition"),
        Axiom.AnnotationPropertyAssertion("referenceName"),
        Axiom.AnnotationPropertyAssertion("baseDeclaration")));
    var ontology = owl.requireOntology("earth");
    ontology.define(List.of(Axiom.ClassAssertion("Region", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE))));
    var derived = owl.makeSubclass(ontology.getConcept("Region"), "earth:Terrestrial earth:Region");
    assertEquals("Terrestrial region", derived.displayLabel());
    String name = derived.getName();
    owl.setDisplayLabel(derived, "Land region");
    assertEquals("Land region", derived.displayLabel());
    var delegate = owl.getOntology(derived.getNamespace()).ontology;
    var metadata = new OWLMetadata(owl.getOWLClass(derived), delegate);
    assertEquals("Land region", metadata.get(Metadata.DISPLAY_LABEL));
    assertEquals(1, delegate.getAnnotationAssertionAxioms(owl.getOWLClass(derived).getIRI()).stream()
        .filter(ax -> Metadata.DISPLAY_LABEL.equals(OWLMetadata.translate(ax.getProperty().getIRI().toString()))).count());
    assertEquals(name, derived.getName());
    assertEquals("Land regions", derived.collective().displayLabel());
  }
}
