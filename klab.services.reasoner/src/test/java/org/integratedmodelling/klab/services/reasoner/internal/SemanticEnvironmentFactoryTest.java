package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticEnvironmentFactoryTest {
  @TempDir Path directory;
  private Map<String, byte[]> core() throws Exception {
    try (var stream = getClass().getClassLoader().getResourceAsStream("knowledge/odo.owl")) {
      assertNotNull(stream); return Map.of("odo.owl", stream.readAllBytes());
    }
  }
  private KimConceptStatementImpl statement(String name, SemanticType type, int position) {
    var statement = new KimConceptStatementImpl(); statement.setUrn(name); statement.setNamespace("w");
    statement.setOffsetInDocument(position);
    statement.setType(EnumSet.of(type, SemanticType.OBSERVABLE));
    if (type == SemanticType.SUBJECT) statement.getType().add(SemanticType.COUNTABLE);
    return statement;
  }
  private KimConceptImpl reference(String name) {
    var reference = new KimConceptImpl(); reference.setName(name); reference.setUrn(name); reference.setOffsetInDocument(999);
    return reference;
  }
  private Worldview world(String version, boolean conflict) {
    var ontology = new KimOntologyImpl(); ontology.setUrn("w"); ontology.setProjectName("fixture");
    ontology.setSourceCode(" ".repeat(1100) + version);
    var thing = statement("Thing", SemanticType.SUBJECT, 1); thing.setUpperConceptDefined("odo:Subject");
    var value = statement("Value", SemanticType.QUALITY, 2); value.setUpperConceptDefined("odo:Quality");
    var group = statement("Group", SemanticType.SUBJECT, 3); group.setDeclaredParent(reference("w:Thing"));
    group.setChildrenDisjoint(true);
    group.getChildren().add(statement("A", SemanticType.SUBJECT, 4));
    group.getChildren().add(statement("B", SemanticType.SUBJECT, 5));
    var first = statement("Q1", SemanticType.QUALITY, 6); first.setDeclaredParent(reference("w:Value"));
    first.setDeclaredInherent(reference("w:A"));
    ontology.getStatements().addAll(List.of(thing, value, group, first));
    if (conflict) {
      var second = statement("Q2", SemanticType.QUALITY, 7); second.setDeclaredParent(reference("w:Q1"));
      second.setDeclaredInherent(reference("w:B")); ontology.getStatements().add(second);
    }
    var worldview = new WorldviewImpl(); worldview.setUrn("w"); worldview.setWorldviewId("w");
    worldview.setEmpty(false); worldview.setOntologies(new ArrayList<>(List.of(ontology)));
    return worldview;
  }
  private CandidatePublicationCoordinator.Input<Worldview> input(SemanticEnvironmentFactory factory, Worldview world) {
    var sources = SemanticEnvironmentFactory.sources(world);
    return new CandidatePublicationCoordinator.Input<>(sources, SemanticEnvironmentFactory.sourceOrder(world),
        factory.dependencyRevision(), SemanticEnvironmentFactory.CONFIGURATION_REVISION, world,
        CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
  }

  @Test void realCompilerCreatesIndependentStateWithoutDeploymentSideEffects() throws Exception {
    var main = ServiceConfiguration.INSTANCE.getMainService(); var execution = Klab.INSTANCE.getExecutionContext();
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var left = factory.build(input(factory, world("left", false)), () -> false);
    var right = factory.build(input(factory, world("right", false)), () -> false);
    try (var first = left.state(); var second = right.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, left.logicalStatus(), left.diagnostics().toString());
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, right.logicalStatus(), right.diagnostics().toString());
      assertNotSame(first.actor(), second.actor()); assertNotSame(first.actor().owl(), second.actor().owl());
      assertNotSame(first.actor().owl().getOntology("w").getOWLOntology().getOWLOntologyManager(),
          second.actor().owl().getOntology("w").getOWLOntology().getOWLOntologyManager());
      assertNotSame(first.actor().resolveConcept("w:Q1"), second.actor().resolveConcept("w:Q1"));
      assertSame(first.actor(), first.actor().serviceScope().getService(org.integratedmodelling.klab.api.services.Reasoner.class));
      assertSame(main, ServiceConfiguration.INSTANCE.getMainService()); assertSame(execution, Klab.INSTANCE.getExecutionContext());
      try (var files = Files.walk(directory)) {
        assertTrue(files.noneMatch(p -> p.getFileName().toString().equals("reasoner.yaml")
            || p.getFileName().toString().contains("secret") || p.getFileName().toString().endsWith(".properties")));
      }
    }
    try (var files = Files.list(directory.resolve("actors"))) { assertEquals(0, files.count()); }
  }

  @Test void failedRealCompilerCandidatePreservesAcceptedEnvironmentAndCache() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    try (var coordinator = new CandidatePublicationCoordinator<Worldview, SemanticEnvironment>(factory, i -> true)) {
      coordinator.submit(input(factory, world("good", false)));
      assertTrue(coordinator.awaitIdle(Duration.ofSeconds(15)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, coordinator.status().logicalStatus(), coordinator.status().diagnostics().toString());
      try (var accepted = coordinator.accepted().orElseThrow()) {
        var concept = accepted.state().actor().resolveConcept("w:Q1");
        var graph = accepted.state().actor().owl().getOntology("w").getOWLOntology().getAxioms();
        coordinator.submit(input(factory, world("bad", true)));
        assertTrue(coordinator.awaitIdle(Duration.ofSeconds(15)));
        assertEquals(CandidatePublicationCoordinator.LogicalStatus.INVALID, coordinator.status().logicalStatus(), coordinator.status().diagnostics().toString());
        assertEquals(1, coordinator.status().acceptedGeneration());
        assertSame(concept, accepted.state().actor().resolveConcept("w:Q1"));
        assertEquals(graph, accepted.state().actor().owl().getOntology("w").getOWLOntology().getAxioms());
        assertTrue(accepted.state().actor().satisfiable(concept));
      }
    }
    try (var files = Files.list(directory.resolve("actors"))) { assertEquals(0, files.count()); }
  }

  @Test void manifestMismatchDoesNotConstructAnActor() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var normal = input(factory, world("original", false));
    var wrong = new CandidatePublicationCoordinator.Input<>(Map.of("wrong", "hash"), List.of("wrong"),
        factory.dependencyRevision(), "semantic-defaults", normal.payload(), CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
    assertThrows(IllegalArgumentException.class, () -> factory.build(wrong, () -> false));
    assertFalse(Files.exists(directory.resolve("actors")));
  }

  @Test void unavailableScopeAndParsingNeverUseLiveServices() throws Exception {
    var source = new CapturedSemanticSourceResolver(world("captured", false));
    var scope = new SemanticEnvironmentScope(source.service());
    assertThrows(UnsupportedOperationException.class, () -> scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class));
    assertThrows(UnsupportedOperationException.class, () -> source.service().declareConcept("uncaptured:X"));
    assertThrows(UnsupportedOperationException.class, () -> scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class));
    scope.close();
  }

  @Test void configurationAndOrderMustMatchTheCompilerSnapshot() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var normal = factory.capture(world("normal", false));
    var unsupported = new CandidatePublicationCoordinator.Input<>(normal.sourceHashes(), normal.sourceOrder(),
        normal.dependencyRevision(), "other-configuration", normal.payload(), normal.beanStatus(), List.of());
    assertThrows(IllegalArgumentException.class, () -> factory.build(unsupported, () -> false));
    var wrongOrder = new CandidatePublicationCoordinator.Input<>(normal.sourceHashes(), List.of(),
        normal.dependencyRevision(), normal.configurationRevision(), normal.payload(), normal.beanStatus(), List.of());
    assertThrows(IllegalArgumentException.class, () -> factory.build(wrongOrder, () -> false));
    assertFalse(Files.exists(directory.resolve("actors")));
  }

  @Test void submissionCaptureAndCompilerOwnTheirSourceTrees() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var original = world("normal", false);
    var captured = factory.capture(original);
    original.getOntologies().getFirst().getStatements().clear();
    var result = factory.build(captured, () -> false);
    try (var state = result.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, result.logicalStatus(), result.diagnostics().toString());
      assertNotNull(state.actor().resolveConcept("w:Q1"));
      captured.payload().getOntologies().getFirst().getStatements().clear();
      assertNotNull(state.actor().resolveConcept("w:Q1"));
    }
  }

  @Test void authoritativeBeanErrorsNeverConstructAnActor() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var rejected = world("rejected", false);
    rejected.getOntologies().getFirst().getNotifications().add(
        org.integratedmodelling.klab.api.services.runtime.Notification.error("source error"));
    var captured = factory.capture(rejected);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID, captured.beanStatus());
    assertThrows(IllegalArgumentException.class, () -> factory.build(captured, () -> false));
    assertFalse(Files.exists(directory.resolve("actors")));
  }

  @Test void strategyExpressionsReceiveOwlDependentValidation() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var built = factory.build(factory.capture(world("normal", false)), () -> false);
    try (var state = built.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, built.logicalStatus(), built.diagnostics().toString());
      var syntax = reference("w:Q1");
      syntax.setInherent(reference("w:B"));
      var observable = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();
      observable.setSemantics(syntax); observable.setUrn("w:Q1 of w:B");
      var alternative = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.MatchAlternativeImpl();
      alternative.setObservable(observable);
      var selection = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.StrategySelectionImpl();
      selection.getAlternatives().add(alternative);
      var strategy = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategyImpl();
      strategy.setUrn("s"); strategy.setNamespace("strategies"); strategy.setSelection(selection);
      var document = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl();
      document.setUrn("strategies"); document.getStatements().add(strategy);
      var errors = new DocumentSemanticValidator(state.actor()).validate(document);
      assertTrue(org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(errors), errors.toString());
    }
  }

  @Test void leasedReadersRetainGenerationAndReturnOwnedBeans() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    try (var coordinator = new CandidatePublicationCoordinator<Worldview, SemanticEnvironment>(factory, i -> true)) {
      coordinator.submit(factory.capture(world("first", false)));
      assertTrue(coordinator.awaitIdle(Duration.ofSeconds(15)));
      try (var old = AcceptedSemanticReadSession.open(coordinator)) {
        var concept = old.resolveConcept("w:Q1");
        var unknown = old.resolveConcept("w:Deleted");
        assertThrows(IllegalArgumentException.class, () -> old.satisfiable(unknown));
        assertThrows(IllegalArgumentException.class, () -> old.parents(unknown));
        assertThrows(IllegalArgumentException.class, () -> old.is(unknown, concept));
        concept.getMetadata().put("client-only", "mutation");
        assertNull(old.resolveConcept("w:Q1").getMetadata().get("client-only"));
        coordinator.submit(factory.capture(world("second", false)));
        assertTrue(coordinator.awaitIdle(Duration.ofSeconds(15)));
        try (var current = AcceptedSemanticReadSession.open(coordinator)) {
          assertEquals(1, old.generation()); assertEquals(2, current.generation());
          assertTrue(old.satisfiable(concept)); assertTrue(current.satisfiable(concept));
          try (var files = Files.list(directory.resolve("actors"))) { assertEquals(2, files.count()); }
        }
      }
      try (var files = Files.list(directory.resolve("actors"))) { assertEquals(1, files.count()); }
    }
    try (var files = Files.list(directory.resolve("actors"))) { assertEquals(0, files.count()); }
  }

  @Test void unavailableNumberedCoreImportCannotProduceValidCandidate() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var source = world("unsupported-core", false);
    source.getOntologies().getFirst().getOwlImports().add(
        org.integratedmodelling.klab.api.collections.Pair.of("http://integratedmodelling.org/odo/uncaptured", "odo"));
    var result = factory.build(factory.capture(source), () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED, result.logicalStatus());
    assertNull(result.state());
    assertFalse(Files.exists(directory.resolve("actors")));
  }

  @Test void ambiguousCoreDocumentsAreRejectedBeforeAnyActor() throws Exception {
    var bytes = core().get("odo.owl");
    assertThrows(IllegalArgumentException.class, () -> new SemanticEnvironmentFactory(
        directory.resolve("actors"), Map.of("odo.owl", bytes, "alias.owl", bytes)));
    assertFalse(Files.exists(directory.resolve("actors")));
  }
}
