package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

class ObservationalContainmentSupportTest {
  private OWL owl() {
    var scope = (org.integratedmodelling.klab.api.scope.Scope) java.lang.reflect.Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[]{org.integratedmodelling.klab.api.scope.Scope.class},
        (self, method, args) -> null);
    var owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("observationallyContains")));
    return owl;
  }
  private KimConcept syntax(String name) {
    var value = new KimConceptImpl(); value.setName("audit:" + name); return value;
  }
  private KimConceptStatementImpl statement(String container, String... members) {
    var value = new KimConceptStatementImpl();
    for (var member : members) value.getObservationalContainments().add(
        new KimConceptStatement.ObservationalContainment(syntax(container), syntax(member)));
    return value;
  }
  private void declare(Ontology graph, String name, SemanticType type) {
    graph.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(type, SemanticType.OBSERVABLE))));
  }

  @Test void directionsDuplicatesAndTransportUseOneContainerOwnedAxiom() throws Exception {
    var owl = owl(); var graph = owl.requireOntology("audit");
    declare(graph,"Region",SemanticType.SUBJECT); declare(graph,"Stream",SemanticType.SUBJECT);
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var statement = mapper.readValue(mapper.writeValueAsBytes(statement("Region","Stream","Stream")),
        KimConceptStatement.class);
    assertEquals(2,statement.getObservationalContainments().size());
    var forward = ObservationalContainmentSupport.prepare(owl,graph.getConcept("Region"),statement,
        value -> owl.getConcept(value.getName()));
    var reverse = ObservationalContainmentSupport.prepare(owl,graph.getConcept("Stream"),statement,
        value -> owl.getConcept(value.getName()));
    assertEquals(forward,reverse); assertEquals(1,forward.size());
    var axiom = (OWLSubClassOfAxiom)forward.iterator().next();
    assertEquals(owl.getOWLClass(graph.getConcept("Region")),axiom.getSubClass());
    assertInstanceOf(OWLObjectMinCardinality.class,axiom.getSuperClass());
    assertEquals(0,((OWLObjectMinCardinality)axiom.getSuperClass()).getCardinality());
  }

  @Test void disjointMemberTypesEmptyAndUnrelatedFillersRemainConsistentAndRoundTrip() throws Exception {
    var owl = owl(); var graph = owl.requireOntology("audit");
    for(var name:List.of("Region","Stream","Basin","Other")) declare(graph,name,SemanticType.SUBJECT);
    var axioms = ObservationalContainmentSupport.prepare(owl,graph.getConcept("Region"),
        statement("Region","Stream","Basin"),value -> owl.getConcept(value.getName()));
    owl.manager.addAxioms(graph.getOWLOntology(),axioms);
    var f=owl.manager.getOWLDataFactory(); var region=owl.getOWLClass(graph.getConcept("Region"));
    var stream=owl.getOWLClass(graph.getConcept("Stream")); var basin=owl.getOWLClass(graph.getConcept("Basin"));
    var property=owl.getProperty(ObservationalContainmentSupport.PROPERTY)
        .getOWLEntity().asOWLObjectProperty();
    owl.manager.addAxiom(graph.getOWLOntology(),f.getOWLDisjointClassesAxiom(stream,basin));
    var empty=f.getOWLNamedIndividual(IRI.create("urn:test:empty"));
    owl.manager.addAxiom(graph.getOWLOntology(),f.getOWLClassAssertionAxiom(region,empty));
    owl.manager.addAxiom(graph.getOWLOntology(),f.getOWLClassAssertionAxiom(
        f.getOWLObjectMaxCardinality(0,property),empty));
    var unrelated=f.getOWLNamedIndividual(IRI.create("urn:test:unrelated"));
    owl.manager.addAxiom(graph.getOWLOntology(),f.getOWLClassAssertionAxiom(region,unrelated));
    owl.manager.addAxiom(graph.getOWLOntology(),f.getOWLClassAssertionAxiom(
        f.getOWLObjectSomeValuesFrom(property,owl.getOWLClass(graph.getConcept("Other"))),unrelated));
    var reasoner=new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(graph.getOWLOntology());
    try {
      assertTrue(reasoner.isConsistent());
      assertFalse(reasoner.isEntailed(f.getOWLSubClassOfAxiom(region,f.getOWLObjectSomeValuesFrom(property,stream))));
      assertFalse(reasoner.isEntailed(f.getOWLSubClassOfAxiom(region,stream)));
      // This is why declarations must be read structurally, never by entailment.
      assertTrue(reasoner.isEntailed(f.getOWLSubClassOfAxiom(region,
          f.getOWLObjectMinCardinality(0,property,owl.getOWLClass(graph.getConcept("Other"))))));
      assertEquals(2,ObservationalContainmentSupport.declarations(owl,graph.getOWLOntology()).size());
    } finally { reasoner.dispose(); }
    var bytes=new java.io.ByteArrayOutputStream();owl.manager.saveOntology(graph.getOWLOntology(),bytes);
    var manager=OWLManager.createOWLOntologyManager();
    manager.createOntology(owl.getOntology("odo").getOWLOntology().getAxioms(),
        owl.getOntology("odo").getOWLOntology().getOntologyID().getOntologyIRI().orElseThrow());
    var reloaded=manager.loadOntologyFromOntologyDocument(new java.io.ByteArrayInputStream(bytes.toByteArray()));
    assertEquals(ObservationalContainmentSupport.declarations(owl,graph.getOWLOntology()),
        ObservationalContainmentSupport.declarations(owl,reloaded));
  }

  @Test void unknownAndMixedFamiliesRejectBeforeAnyAxiomIsWritten() {
    var owl=owl();var graph=owl.requireOntology("audit");
    declare(graph,"Region",SemanticType.SUBJECT);declare(graph,"Event",SemanticType.EVENT);
    declare(graph,"Quality",SemanticType.QUALITY);declare(graph,"Process",SemanticType.PROCESS);
    long before=graph.getOWLOntology().getAxiomCount();
    for(var member:List.of("Missing","Quality","Event")) assertThrows(KlabValidationException.class,
        () -> ObservationalContainmentSupport.prepare(owl,graph.getConcept("Region"),
            statement("Region",member),value -> owl.getConcept(value.getName())));
    assertEquals(before,graph.getOWLOntology().getAxiomCount());
    assertEquals(1,ObservationalContainmentSupport.prepare(owl,graph.getConcept("Event"),
        statement("Event","Process"),value -> owl.getConcept(value.getName())).size());
  }

  @Test void bundledPropertyAddsNoInverseParthoodInherencyOrTransitivity() throws Exception {
    var manager=OWLManager.createOWLOntologyManager();
    var graph=manager.loadOntologyFromOntologyDocument(getClass().getResourceAsStream("/knowledge/odo.owl"));
    var property=manager.getOWLDataFactory().getOWLObjectProperty(
        IRI.create("http://integratedmodelling.org/odo#observationallyContains"));
    assertTrue(graph.containsObjectPropertyInSignature(property.getIRI()));
    assertTrue(graph.objectSubPropertyAxiomsForSubProperty(property).findAny().isEmpty());
    assertTrue(graph.inverseObjectPropertyAxioms(property).findAny().isEmpty());
    assertTrue(graph.transitiveObjectPropertyAxioms(property).findAny().isEmpty());
    assertTrue(graph.annotationAssertionAxioms(property.getIRI()).anyMatch(a ->
        a.getProperty().isComment() && a.getValue().toString().contains("non-exhaustive")));
    var bytes=new java.io.ByteArrayOutputStream();manager.saveOntology(graph,bytes);
    var reloaded=OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
        new java.io.ByteArrayInputStream(bytes.toByteArray()));
    assertEquals(graph.annotationAssertionAxioms(property.getIRI()).collect(java.util.stream.Collectors.toSet()),
        reloaded.annotationAssertionAxioms(property.getIRI()).collect(java.util.stream.Collectors.toSet()));
    var f=manager.getOWLDataFactory();
    var a=f.getOWLNamedIndividual(IRI.create("urn:test:a"));
    var b=f.getOWLNamedIndividual(IRI.create("urn:test:b"));
    var c=f.getOWLNamedIndividual(IRI.create("urn:test:c"));
    manager.addAxiom(graph,f.getOWLObjectPropertyAssertionAxiom(property,a,b));
    manager.addAxiom(graph,f.getOWLObjectPropertyAssertionAxiom(property,b,c));
    var reasoner=new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(graph);
    try {
      assertTrue(reasoner.isConsistent());
      assertFalse(reasoner.isEntailed(f.getOWLObjectPropertyAssertionAxiom(property,a,c)));
      for(var name:List.of("isPartOf","containsPart","isInherentTo","hasContext"))
        assertFalse(reasoner.isEntailed(f.getOWLObjectPropertyAssertionAxiom(
            f.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#"+name)),a,b)),name);
    } finally {reasoner.dispose();}
  }

  @Test void compilerRejectsLaterInvalidPairWithoutPublishingEarlierContainmentAxiom() {
    var owl=owl();var graph=owl.requireOntology("audit");
    declare(graph,"Region",SemanticType.SUBJECT);declare(graph,"Stream",SemanticType.SUBJECT);
    var statement=statement("Region","Stream","Missing");
    assertThrows(KlabValidationException.class,()->WorldviewDeclarationSupport.compile(owl,graph,
        graph.getConcept("Region"),statement,value->owl.getConcept(value.getName())));
    assertTrue(ObservationalContainmentSupport.declarations(owl,graph.getOWLOntology()).isEmpty());
  }

  @Test void inheritedLookupTraversesAuthoredParentsWithoutInventingUndeclaredContracts() {
    var owl=owl();var graph=owl.requireOntology("audit");
    for(var name:List.of("Region","Subregion","Stream","Basin","Other")) declare(graph,name,SemanticType.SUBJECT);
    var f=owl.manager.getOWLDataFactory();
    owl.manager.addAxiom(graph.getOWLOntology(),f.getOWLSubClassOfAxiom(
        owl.getOWLClass(graph.getConcept("Subregion")),owl.getOWLClass(graph.getConcept("Region"))));
    var inherited=ObservationalContainmentSupport.prepare(owl,graph.getConcept("Region"),
        statement("Region","Stream"),value->owl.getConcept(value.getName()));
    var own=ObservationalContainmentSupport.prepare(owl,graph.getConcept("Subregion"),
        statement("Subregion","Basin"),value->owl.getConcept(value.getName()));
    owl.manager.addAxioms(graph.getOWLOntology(),inherited);owl.manager.addAxioms(graph.getOWLOntology(),own);
    assertEquals(2,ObservationalContainmentSupport.declarationsForContainer(owl,graph.getConcept("Subregion")).size());
    assertEquals(inherited,ObservationalContainmentSupport.declarationsForContainer(owl,graph.getConcept("Region")));
    assertTrue(ObservationalContainmentSupport.declarationsForContainer(owl,graph.getConcept("Other")).isEmpty());
  }
}
