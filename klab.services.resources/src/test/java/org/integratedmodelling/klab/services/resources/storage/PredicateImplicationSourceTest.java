package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.*;

class PredicateImplicationSourceTest {
  @TempDir Path directory;
  private static final String ROOT = """
      ontology imod in domain root version 1.0;
      abstract thing Subject is core odo:Subject;
      abstract configuration Configuration is core odo:Configuration;
      abstract role Role is core odo:Role;
      abstract attribute Attribute is core odo:Attribute;
      abstract length Length is core odo:Length;
      abstract relationship Relationship is core odo:Relationship;
      abstract process Process is core odo:Process;
      thing Bearer is imod:Subject;
      length Height is imod:Length of imod:Bearer;
      length Width is imod:Length of imod:Bearer;
      role Marked is imod:Role;
      attribute Present is imod:Attribute;
      """;

  private WorldviewImpl source(String label, String clauses) throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(
        Files.createDirectories(directory.resolve(label)), Map.of("imod", ROOT + clauses));
    var result = new WorldviewImpl();
    result.setUrn("imod"); result.setWorldviewId("imod"); result.setEmpty(false);
    result.setOntologies(new ArrayList<>(manager.getOntologies(false)));
    return result;
  }

  private AcceptedSemanticRuntime runtime(String label) throws Exception {
    return new AcceptedSemanticRuntime(new SemanticEnvironmentFactory(directory.resolve(label),
        CoreOntologyCatalog.bundledCore()), "predicate-implication-source");
  }

  private void accept(AcceptedSemanticRuntime runtime, WorldviewImpl source) throws Exception {
    assertTrue(source.getOntologies().stream().flatMap(o -> o.getNotifications().stream())
        .noneMatch(n -> n.getLevel().severity >= Notification.Level.Error.severity));
    runtime.submit(source);
    assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,
        runtime.status().logicalStatus(), runtime.status().diagnostics().toString());
    assertTrue(runtime.status().acceptedGeneration() > 0);
  }

  private OWLClass owlClass(org.integratedmodelling.klab.services.reasoner.owl.OWL owl, Concept concept) {
    return owl.getOntology(concept.getNamespace()).getOWLOntology().classesInSignature()
        .filter(type -> type.getIRI().getRemainder().orElse("").equals(concept.getName())).findFirst().orElseThrow();
  }

  @Test void configurationRoleAndRolePredicateReachAcceptedSourceWithExactScopes() throws Exception {
    var world = source("legal", """
        role Trigger is imod:Role implies imod:Present for imod:Bearer within imod:Bearer;
        configuration Pair is imod:Configuration
            emerges from (imod:Height and imod:Width) within imod:Bearer
            implies imod:Marked for imod:Bearer;
        """);
    try (var runtime = runtime("accepted")) {
      accept(runtime, world);
      var trigger = runtime.invoke("resolveConcept", new Class<?>[]{String.class}, new Object[]{"imod:Trigger"});
      var pair = runtime.invoke("resolveConcept", new Class<?>[]{String.class}, new Object[]{"imod:Pair"});
      ConsequenceRuleResponse response = runtime.invoke("consequenceRules",
          new Class<?>[]{ConsequenceRuleQuery.class}, new Object[]{new ConsequenceRuleQuery(
              List.of((Concept) trigger, (Concept) pair), List.of(), Set.of(KimConsequenceRule.Kind.IMPLIES), null)});
      assertEquals(ConsequenceRuleResponse.Status.COMPLETE, response.status(), response.reason());
      assertEquals(2, response.rules().size());
      for (var entry : response.rules()) {
        assertEquals("imod:Bearer", entry.rule().secondaryInherency().getUrn());
        var code = world.getOntologies().getFirst().getSourceCode();
        var span = entry.rule().source();
        assertEquals(code.substring(span.offset(), span.offset() + span.length()), span.source());
        assertEquals(SemanticValidationRequest.sourceHash(code), entry.sourceHash());
      }
      var role = response.rules().stream().filter(e -> e.declarationUrn().equals("imod:Trigger")).findFirst().orElseThrow();
      assertEquals("imod:Present", role.rule().target().getUrn());
      assertEquals("imod:Bearer", role.rule().within().getUrn());
      assertEquals("imod:Marked", response.rules().stream().filter(e -> e.declarationUrn().equals("imod:Pair"))
          .findFirst().orElseThrow().rule().target().getUrn());
    }
  }

  @Test void owlTargetsKeepPredicateAndObservableTypesSeparateIncludingScopedQuality() throws Exception {
    var world = source("owl", """
        role PredicateTrigger is imod:Role implies imod:Present;
        role QualityTrigger is imod:Role implies imod:Height for imod:Bearer within imod:Bearer;
        configuration Pair is imod:Configuration emerges from (imod:Height and imod:Width) implies imod:Marked;
        """);
    var factory = new SemanticEnvironmentFactory(directory.resolve("owl-actor"), CoreOntologyCatalog.bundledCore());
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    var compiled = factory.build(captured, () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var state = compiled.state()) {
      var actor = state.actor(); var owl = actor.owl();
      var f = owl.getOntology("imod").getOWLOntology().getOWLOntologyManager().getOWLDataFactory();
      var predicates = owl.getProperty("odo:impliesPredicate").getOWLEntity().asOWLObjectProperty();
      var observables = owl.getProperty("odo:impliesObservable").getOWLEntity().asOWLObjectProperty();
      assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(owl, actor.resolveConcept("imod:Pair")),
          f.getOWLObjectSomeValuesFrom(predicates, owlClass(owl, actor.resolveConcept("imod:Marked"))))));
      assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(owl, actor.resolveConcept("imod:PredicateTrigger")),
          f.getOWLObjectSomeValuesFrom(predicates, owlClass(owl, actor.resolveConcept("imod:Present"))))));
      assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(owl, actor.resolveConcept("imod:QualityTrigger")),
          f.getOWLObjectSomeValuesFrom(observables, owlClass(owl, actor.resolveConcept("imod:Height"))))));
      var observable = f.getOWLClass(IRI.create("http://integratedmodelling.org/odo#Observable"));
      var role = owlClass(owl, actor.resolveConcept("imod:Role"));
      assertFalse(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(owl, actor.resolveConcept("imod:Marked")), observable)));
      assertFalse(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(owl, actor.resolveConcept("imod:Present")), observable)));
      assertFalse(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(owl, actor.resolveConcept("imod:Pair")), role)));
      assertTrue(owl.isConsistent());
    }
  }

  @Test void invalidTargetsSourcesAndDependentRoleRecipientsRemainRejected() throws Exception {
    int index = 0;
    for (var clause : List.of(
        "configuration Invalid is imod:Configuration emerges from imod:Height implies imod:Height;",
        "role Invalid is imod:Role implies imod:Bearer;",
        "thing Invalid is imod:Subject implies imod:Marked;",
        "configuration Invalid is imod:Configuration emerges from imod:Height implies imod:Marked for imod:Height;",
        "abstract relationship Invalid is imod:Relationship implies imod:Marked;")) {
      var world = source("invalid-source-" + index, clause);
      try (var runtime = runtime("invalid-runtime-" + index++)) {
        runtime.submit(world); assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
        assertEquals(0, runtime.status().acceptedGeneration(), clause);
        assertFalse(runtime.status().diagnostics().isEmpty(), clause);
      }
    }
  }

  @Test void otherObservableOnlyClausesDoNotGainPredicateAcceptance() throws Exception {
    var world = source("creates-role", "process Invalid is imod:Process creates imod:Marked;");
    try (var runtime = runtime("creates-role-runtime")) {
      runtime.submit(world); assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      assertEquals(0, runtime.status().acceptedGeneration());
      assertFalse(runtime.status().diagnostics().isEmpty());
    }
  }
}
