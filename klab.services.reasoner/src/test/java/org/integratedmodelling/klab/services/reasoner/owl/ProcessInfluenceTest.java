package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.SemanticInfluence;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class ProcessInfluenceTest {
  @Test void inheritedEffectsKeepCreationAndProportionalityDistinct() throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var owl = new OWL(null);
    owl.manager = OWLManager.createOWLOntologyManager();
    var field = ReasonerService.class.getDeclaredField("owl");
    field.setAccessible(true); field.set(reasoner, owl);
    var core = owl.requireOntology("odo");
    core.define(List.of(Axiom.ObjectPropertyAssertion("affects"), Axiom.ObjectPropertyAssertion("creates"),
        Axiom.ObjectPropertyAssertion("increasesWith"), Axiom.ObjectPropertyAssertion("decreasesWith"),
        Axiom.ObjectPropertyAssertion("marksQuality")));
    var ontology = owl.requireOntology("processfixture");
    ontology.define(List.of(
        Axiom.ClassAssertion("Parent", EnumSet.of(SemanticType.PROCESS)),
        Axiom.ClassAssertion("Child", EnumSet.of(SemanticType.PROCESS)),
        Axiom.ClassAssertion("Elevation", EnumSet.of(SemanticType.QUALITY)),
        Axiom.ClassAssertion("Rainfall", EnumSet.of(SemanticType.QUALITY)),
        Axiom.ClassAssertion("Wet", EnumSet.of(SemanticType.QUALITY, SemanticType.PRESENCE)),
        Axiom.SubClass("Parent", "Child")));
    var parent = ontology.getConcept("Parent");
    for (var property : List.of("affects", "increasesWith", "decreasesWith"))
      owl.restrictSome(parent, owl.getProperty("odo:" + property), ontology.getConcept("Elevation"), ontology);
    owl.restrictSome(parent, owl.getProperty("odo:creates"), ontology.getConcept("Rainfall"), ontology);
    owl.restrictSome(parent, owl.getProperty("odo:marksQuality"), ontology.getConcept("Wet"), ontology);
    var evidence = reasoner.influences(ontology.getConcept("Child"));
    assertEquals(5, evidence.size());
    assertTrue(evidence.contains(new SemanticInfluence(ontology.getConcept("Wet"), SemanticInfluence.Kind.MARKS)));
    assertTrue(evidence.contains(new SemanticInfluence(ontology.getConcept("Rainfall"), SemanticInfluence.Kind.CREATES)));
    assertEquals(2, evidence.stream().filter(SemanticInfluence::input).count());
    assertEquals(java.util.Set.of(ontology.getConcept("Elevation"), ontology.getConcept("Wet")),
        java.util.Set.copyOf(reasoner.affected(ontology.getConcept("Child"))));
    assertEquals(List.of(ontology.getConcept("Rainfall")), List.copyOf(reasoner.created(ontology.getConcept("Child"))));
  }
}
