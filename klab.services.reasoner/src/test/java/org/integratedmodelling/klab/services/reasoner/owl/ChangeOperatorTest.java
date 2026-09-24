package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.AddImport;

class ChangeOperatorTest {
  @TempDir Path directory;

  @Test
  void shippedOntologyBuildsSatisfiableChangesForEveryQualityBearer() throws Exception {
    var scope = mock(Scope.class);
    var service = mock(ReasonerService.class);
    var owl = new OWL(scope);
    owl.manager = OWLManager.createOWLOntologyManager();
    for (String name : List.of("odo", "klab")) {
      try (var input = getClass().getResourceAsStream("/knowledge/" + name + ".owl")) {
        Files.copy(input, directory.resolve(name + ".owl"));
      }
    }
    owl.load(directory.toFile());
    when(scope.getService(Reasoner.class)).thenReturn(service);
    when(service.owl()).thenReturn(owl);
    when(service.resolveConcept(anyString())).thenAnswer(call -> owl.getConcept(call.getArgument(0)));
    when(service.inherent(any())).thenAnswer(call -> {
      var owner = ((Semantics) call.getArgument(0)).asConcept();
      var bearers = owl.getRestrictedClasses(owner, owl.getProperty("odo:isInherentTo"));
      return bearers.isEmpty() ? null : bearers.iterator().next();
    });

    var domain = owl.requireOntology("geography");
    domain.define(List.of(
        Axiom.ClassAssertion("Location", EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Activity", EnumSet.of(SemanticType.PROCESS, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Storm", EnumSet.of(SemanticType.EVENT, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("OtherQuality", EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE)),
        Axiom.ClassAssertion("Unrelated", EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE)),
        Axiom.SubClass("odo:Subject", "Location"),
        Axiom.SubClass("odo:Process", "Activity"),
        Axiom.SubClass("odo:Event", "Storm"),
        Axiom.SubClass("odo:Quality", "OtherQuality")));
    // Unsatisfiable changes previously inherited this unrelated restriction among all superclasses.
    owl.setApplicableObservables(domain.getConcept("OtherQuality"),
        List.of(domain.getConcept("Unrelated")), domain);
    var qualityNames = List.of("Elevation", "Intensity", "Severity", "Category", "Unbound");
    var bearerNames = List.of("Location", "Activity", "Storm", "OtherQuality");
    for (int i = 0; i < qualityNames.size(); i++) {
      var name = qualityNames.get(i);
      var types = EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE);
      if (i == 0) types.addAll(List.of(SemanticType.LENGTH, SemanticType.QUANTIFIABLE));
      if (i == 3) types.add(SemanticType.CLASS);
      domain.define(List.of(
          Axiom.ClassAssertion(name, types),
          Axiom.SubClass("odo:Quality", name)));
      if (i < bearerNames.size()) owl.restrictSome(domain.getConcept(name),
          owl.getProperty("odo:isInherentTo"), domain.getConcept(bearerNames.get(i)), domain);
    }
    var merged = owl.manager.createOntology();
    var factory = owl.manager.getOWLDataFactory();
    for (var ontology : owl.manager.getOntologies()) {
      if (!ontology.equals(merged)) owl.manager.applyChange(new AddImport(merged,
          factory.getOWLImportsDeclaration(ontology.getOntologyID().getOntologyIRI().orElseThrow())));
    }
    var hermit = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(merged);
    var field = OWL.class.getDeclaredField("reasoner");
    field.setAccessible(true);
    field.set(owl, hermit);
    try {
      assertNotNull(owl.getConcept("odo:Change"));
      for (String name : qualityNames) {
        var quality = domain.getConcept(name);
        var syntax = new KimConceptImpl();
        syntax.setName(quality.getUrn());
        syntax.setUrn(quality.getUrn());
        syntax.setType(EnumSet.copyOf(quality.getType()));
        var change = SemanticsBuilder.create(syntax, service, scope)
            .as(UnarySemanticOperator.CHANGE).buildObservable();
        assertTrue(change.is(SemanticType.CHANGE), name);
        assertTrue(change.is(SemanticType.PROCESS), name);
        assertEquals("change in geography:" + name, change.getUrn());
        owl.flushReasoner();
        assertTrue(hermit.isSatisfiable(owl.getOWLClass(change.getSemantics())), name);
        assertEquals(service.inherent(quality), service.inherent(change));
      }
      // Reproduce the original ontology defect: two domains mean Event AND Process.
      var changes = factory.getOWLObjectProperty(IRI.create(owl.getProperty("odo:changes").getURI()));
      var eventDomain = factory.getOWLObjectPropertyDomainAxiom(changes, owl.getOWLClass(owl.getConcept("odo:Event")));
      owl.manager.addAxiom(merged, eventDomain);
      hermit.flush();
      assertFalse(hermit.isSatisfiable(owl.getOWLClass(owl.makeChange(domain.getConcept("Elevation")))));
      owl.manager.removeAxiom(merged, eventDomain);
      hermit.flush();
      assertTrue(hermit.isSatisfiable(owl.getOWLClass(owl.makeChange(domain.getConcept("Elevation")))));
    } finally {
      hermit.dispose();
    }
  }
}
