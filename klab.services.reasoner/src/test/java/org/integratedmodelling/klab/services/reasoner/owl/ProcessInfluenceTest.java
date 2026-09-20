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
  @Test void allDescriptiveClassesRetainDirectionThroughInheritedAndReverseDiscovery() throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var owl = new OWL(null); owl.manager = OWLManager.createOWLOntologyManager();
    // This fixture exercises restriction inheritance; semantic subsumption is tested separately.
    doReturn(false).when(reasoner).is(any(), any());
    var field = ReasonerService.class.getDeclaredField("owl"); field.setAccessible(true); field.set(reasoner, owl);
    var core = owl.requireOntology("odo");
    core.define(java.util.Arrays.stream(SemanticInfluence.Kind.values())
        .map(k -> Axiom.ObjectPropertyAssertion(k.property().substring(4))).toList());
    var ontology = owl.requireOntology("descriptions");
    ontology.define(List.of(
        Axiom.ClassAssertion("Value", EnumSet.of(SemanticType.QUALITY, SemanticType.QUANTIFIABLE)),
        Axiom.ClassAssertion("Wet", EnumSet.of(SemanticType.QUALITY, SemanticType.PRESENCE)),
        Axiom.ClassAssertion("Quality", EnumSet.of(SemanticType.QUALITY)),
        Axiom.ClassAssertion("Child", EnumSet.of(SemanticType.QUALITY)),
        Axiom.ClassAssertion("Order", EnumSet.of(SemanticType.PREDICATE, SemanticType.ORDERING)),
        Axiom.ClassAssertion("Predicate", EnumSet.of(SemanticType.PREDICATE)),
        Axiom.SubClass("Quality", "Child")));
    for (var kind : SemanticInfluence.Kind.values()) {
      if (!kind.descriptive()) continue;
      var source = ontology.getConcept(kind == SemanticInfluence.Kind.DISCRETIZES ? "Order"
          : kind == SemanticInfluence.Kind.CLASSIFIES ? "Predicate" : "Quality");
      var target = ontology.getConcept(kind == SemanticInfluence.Kind.MARKS ? "Wet" : "Value");
      owl.restrictSome(source, owl.getProperty(kind.property()), target, ontology);
      var outgoing = reasoner.influences(source).stream().filter(i -> i.kind() == kind && i.source().equals(source)).findFirst().orElseThrow();
      assertTrue(reasoner.influences(target).contains(outgoing));
      var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      assertEquals(outgoing, mapper.readValue(mapper.writeValueAsBytes(outgoing), SemanticInfluence.class));
      assertFalse(outgoing.input());
    }
    assertEquals(3, reasoner.influences(ontology.getConcept("Child")).stream()
        .filter(i -> i.source().equals(ontology.getConcept("Child"))).count());
    assertTrue(reasoner.influences(ontology.getConcept("Child")).stream()
        .filter(i -> i.source().equals(ontology.getConcept("Child")))
        .allMatch(i -> i.provenance().contains("#Quality") && i.provenance().contains("SubClassOf")));
  }
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
    for (var property : List.of("affects"))
      owl.restrictSome(parent, owl.getProperty("odo:" + property), ontology.getConcept("Elevation"), ontology);
    owl.restrictSome(parent, owl.getProperty("odo:creates"), ontology.getConcept("Rainfall"), ontology);
    owl.restrictSome(ontology.getConcept("Elevation"), owl.getProperty("odo:marksQuality"), ontology.getConcept("Wet"), ontology);
    var evidence = reasoner.influences(ontology.getConcept("Child"));
    assertEquals(2, evidence.size());
    assertTrue(evidence.stream().anyMatch(i -> i.kind() == SemanticInfluence.Kind.CREATES));
    assertTrue(evidence.stream().noneMatch(i -> i.kind().descriptive()));
    var forward = reasoner.influences(ontology.getConcept("Elevation"));
    var reverse = reasoner.influences(ontology.getConcept("Wet"));
    assertEquals(forward, reverse);
    assertEquals(1, forward.size());
    assertEquals(ontology.getConcept("Elevation"), forward.iterator().next().source());
    assertEquals(SemanticInfluence.Kind.MARKS, forward.iterator().next().kind());
    assertTrue(reasoner.affected(ontology.getConcept("Elevation")).isEmpty());
    assertEquals(java.util.Set.of(ontology.getConcept("Elevation")),
        java.util.Set.copyOf(reasoner.affected(ontology.getConcept("Child"))));
    assertEquals(List.of(ontology.getConcept("Rainfall")), List.copyOf(reasoner.created(ontology.getConcept("Child"))));
  }
}
