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
  @Test void realOntologyBoundsPreserveUnionAndAllInheritedRestrictions() throws Exception {
    var scope = (Scope) java.lang.reflect.Proxy.newProxyInstance(Scope.class.getClassLoader(),
        new Class<?>[]{Scope.class}, (self, method, args) -> null);
    var owl = new OWL(scope); owl.manager = OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("audit");
    var axioms = new ArrayList<Axiom>();
    for (String name : List.of("Parent", "Child", "Tree", "Oak", "Pine", "Rock"))
      axioms.add(Axiom.ClassAssertion(name, EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE)));
    axioms.add(Axiom.SubClass("Parent", "Child"));
    axioms.add(Axiom.SubClass("Tree", "Oak")); axioms.add(Axiom.SubClass("Tree", "Pine"));
    ontology.define(axioms);
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("isInherentTo")));
    var factory = owl.manager.getOWLDataFactory();
    var owner = ontology.getConcept("Child");
    var parent = owl.getOWLClass(ontology.getConcept("Parent"));
    var child = owl.getOWLClass(owner);
    var tree = owl.getOWLClass(ontology.getConcept("Tree"));
    var oak = owl.getOWLClass(ontology.getConcept("Oak"));
    var rock = owl.getOWLClass(ontology.getConcept("Rock"));
    var property = factory.getOWLObjectProperty(IRI.create(owl.getProperty("odo:isInherentTo").getURI()));
    owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLSubClassOfAxiom(parent,
        factory.getOWLObjectSomeValuesFrom(property, tree)));
    owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLEquivalentClassesAxiom(child,
        factory.getOWLObjectIntersectionOf(parent,
            factory.getOWLObjectSomeValuesFrom(property, factory.getOWLObjectUnionOf(oak, rock)))));
    var merged = owl.manager.createOntology(owl.manager.ontologies().flatMap(o -> o.axioms())
        .collect(java.util.stream.Collectors.toSet()));
    var hermit = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    var field = OWL.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(owl, hermit);
    try {
      var support = new OWLSemanticClauseSupport(owl);
      assertTrue(support.accepts(owner, SemanticRole.INHERENT, ontology.getConcept("Oak")));
      assertFalse(support.accepts(owner, SemanticRole.INHERENT, ontology.getConcept("Pine")));
      assertFalse(support.accepts(owner, SemanticRole.INHERENT, ontology.getConcept("Rock")));
      var clauses = support.clauses(owner);
      assertTrue(clauses.stream().anyMatch(c -> c.isInherited() && c.getFiller() != null
          && c.getFiller().getUrn().equals("audit:Tree")));
      assertTrue(clauses.stream().anyMatch(c -> !c.isInherited()
          && c.getCode().stream().anyMatch(t -> "or".equals(t.getValue()))));
      assertTrue(support.accepts(owner, SemanticRole.GOAL, ontology.getConcept("Rock")));
    } finally { hermit.dispose(); }
  }
}
