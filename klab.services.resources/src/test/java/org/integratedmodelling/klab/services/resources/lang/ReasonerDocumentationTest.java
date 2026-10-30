package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class ReasonerDocumentationTest {
  @Test void inferredClosureKeepsBottomOutAndReportsRootEquivalentNameBoundary() throws Exception {
    var owl=new OWL(null);
    var manager=OWLManager.createOWLOntologyManager();
    var managerField=OWL.class.getDeclaredField("manager");managerField.setAccessible(true);managerField.set(owl,manager);
    var ontology=owl.requireOntology("inferredclosure");
    for(var name:List.of("Parent","Child","Alias","Impossible"))
      ontology.define(List.of(Axiom.ClassAssertion(name,EnumSet.of(SemanticType.ATTRIBUTE))));
    var lookup=OWL.class.getDeclaredMethod("getOWLClass",org.integratedmodelling.klab.api.knowledge.Concept.class);lookup.setAccessible(true);
    var parent=(org.semanticweb.owlapi.model.OWLClass)lookup.invoke(owl,ontology.getConcept("Parent"));
    var child=(org.semanticweb.owlapi.model.OWLClass)lookup.invoke(owl,ontology.getConcept("Child"));
    var alias=(org.semanticweb.owlapi.model.OWLClass)lookup.invoke(owl,ontology.getConcept("Alias"));
    var impossible=(org.semanticweb.owlapi.model.OWLClass)lookup.invoke(owl,ontology.getConcept("Impossible"));
    var f=manager.getOWLDataFactory();
    manager.addAxioms(ontology.getOWLOntology(),java.util.Set.of(f.getOWLSubClassOfAxiom(child,parent),
        f.getOWLEquivalentClassesAxiom(child,alias),f.getOWLSubClassOfAxiom(impossible,parent),
        f.getOWLSubClassOfAxiom(impossible,f.getOWLNothing())));
    var delegate=new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(ontology.getOWLOntology());
    var field=OWL.class.getDeclaredField("reasoner");field.setAccessible(true);field.set(owl,delegate);
    try {
      assertEquals(java.util.Set.of(ontology.getConcept("Parent"),ontology.getConcept("Child"),ontology.getConcept("Alias")),new java.util.HashSet<>(owl.getSemanticClosure(ontology.getConcept("Parent"))));
      assertTrue(owl.getSemanticClosure(ontology.getConcept("Impossible")).isEmpty());
      // Existing API returns the requested name and strict subclass nodes, not
      // the other names in its own equivalence node. Do not widen that policy here.
      assertEquals(java.util.Set.of(ontology.getConcept("Child")),new java.util.HashSet<>(owl.getSemanticClosure(ontology.getConcept("Child"))));
      assertEquals(java.util.Set.of(ontology.getConcept("Alias")),new java.util.HashSet<>(owl.getSemanticClosure(ontology.getConcept("Alias"))));
    } finally {delegate.dispose();}
  }
  @Test void semanticClosureExcludesEveryMemberOfTheBottomEquivalenceNode() throws Exception {
    var owl = new OWL(null);
    var managerField = OWL.class.getDeclaredField("manager");
    managerField.setAccessible(true);
    var manager = OWLManager.createOWLOntologyManager();
    managerField.set(owl, manager);
    var ontology = owl.requireOntology("closureaudit");
    for (String name : List.of("Parent", "Valid", "Alias", "Impossible", "OtherImpossible")) {
      ontology.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.ATTRIBUTE))));
    }
    var lookup = OWL.class.getDeclaredMethod("getOWLClass",
        org.integratedmodelling.klab.api.knowledge.Concept.class);
    lookup.setAccessible(true);
    var parent = (org.semanticweb.owlapi.model.OWLClass) lookup.invoke(owl, ontology.getConcept("Parent"));
    var valid = (org.semanticweb.owlapi.model.OWLClass) lookup.invoke(owl, ontology.getConcept("Valid"));
    var alias = (org.semanticweb.owlapi.model.OWLClass) lookup.invoke(owl, ontology.getConcept("Alias"));
    var impossible = (org.semanticweb.owlapi.model.OWLClass) lookup.invoke(owl, ontology.getConcept("Impossible"));
    var otherImpossible = (org.semanticweb.owlapi.model.OWLClass) lookup.invoke(owl, ontology.getConcept("OtherImpossible"));
    var descendants = new org.semanticweb.owlapi.reasoner.impl.OWLClassNodeSet();
    descendants.addNode(new org.semanticweb.owlapi.reasoner.impl.OWLClassNode(java.util.Set.of(valid, alias)));
    descendants.addNode(new org.semanticweb.owlapi.reasoner.impl.OWLClassNode(java.util.Set.of(
        manager.getOWLDataFactory().getOWLNothing(), impossible, otherImpossible)));
    var delegate = mock(org.semanticweb.owlapi.reasoner.OWLReasoner.class);
    when(delegate.getSubClasses(parent, false)).thenReturn(descendants);
    when(delegate.isSatisfiable(parent)).thenReturn(true);
    var reasonerField = OWL.class.getDeclaredField("reasoner");
    reasonerField.setAccessible(true);
    reasonerField.set(owl, delegate);
    assertEquals(java.util.Set.of(ontology.getConcept("Parent"), ontology.getConcept("Valid"), ontology.getConcept("Alias")),
        new java.util.HashSet<>(owl.getSemanticClosure(ontology.getConcept("Parent"))));
  }

  @Test void collectiveViewsUseTheirMemberOwlClassForParentTraversal() throws Exception {
    var owl = new OWL(null);
    var managerField = OWL.class.getDeclaredField("manager");
    managerField.setAccessible(true);
    managerField.set(owl, OWLManager.createOWLOntologyManager());
    var ontology = owl.requireOntology("collectiveaudit");
    ontology.define(List.of(
        Axiom.ClassAssertion("Region", EnumSet.of(SemanticType.SUBJECT)),
        Axiom.ClassAssertion("Basin", EnumSet.of(SemanticType.SUBJECT)),
        Axiom.SubClass("Region", "Basin")));
    var basin = ontology.getConcept("Basin");
    var region = ontology.getConcept("Region");
    assertTrue(owl.getParents(basin.collective()).contains(region));
    assertEquals(owl.getParents(basin), owl.getParents(basin.collective()));
    assertTrue(basin.collective().isCollective());
    assertFalse(basin.isCollective());
  }

  @Test void reasonerInfoDocumentsConceptAndObservableCounterparts() throws Exception {
    var owl = new OWL(null);
    var managerField = OWL.class.getDeclaredField("manager");
    managerField.setAccessible(true);
    managerField.set(owl, OWLManager.createOWLOntologyManager());
    var ontology = owl.requireOntology("audit");
    ontology.define(List.of(Axiom.ClassAssertion("Tree", EnumSet.of(SemanticType.SUBJECT))));
    var concept = ontology.getConcept("Tree");
    concept.getMetadata().put("custom", "Tree metadata");
    var observable = ObservableImpl.promote(concept, null);
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var owlField = ReasonerService.class.getDeclaredField("owl");
    owlField.setAccessible(true);
    owlField.set(reasoner, owl);
    doReturn(concept).when(reasoner).resolveConcept("audit:Tree");
    doReturn(observable).when(reasoner).resolveObservable("audit:Tree");
    for (var cls : List.of(KnowledgeClass.CONCEPT, KnowledgeClass.OBSERVABLE)) {
      String markdown = reasoner.info("audit:Tree", cls, String.class, null);
      assertTrue(markdown.contains("Semantic documentation"));
      assertTrue(markdown.contains("Tree metadata"));
      assertTrue(markdown.contains("SUBJECT"));
      assertTrue(markdown.contains("## OWL identity"));
    }
  }
}
