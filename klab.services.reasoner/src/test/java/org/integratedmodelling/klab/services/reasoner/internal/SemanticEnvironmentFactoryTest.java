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
  private Map<String,byte[]> configurationCore() throws Exception {
    String xml=new String(core().get("odo.owl"),java.nio.charset.StandardCharsets.UTF_8);
    String additions="""
      <owl:ObjectProperty rdf:about="http://integratedmodelling.org/odo#describesConfiguration">
        <rdfs:domain rdf:resource="http://integratedmodelling.org/odo#Domain"/>
        <rdfs:range rdf:resource="http://integratedmodelling.org/odo#Configuration"/>
      </owl:ObjectProperty>
      <owl:Class rdf:about="http://integratedmodelling.org/odo#NoConfigurationDomain">
        <rdfs:subClassOf rdf:resource="http://integratedmodelling.org/odo#Domain"/>
        <rdfs:subClassOf><owl:Restriction>
          <owl:onProperty rdf:resource="http://integratedmodelling.org/odo#describesConfiguration"/>
          <owl:maxCardinality rdf:datatype="http://www.w3.org/2001/XMLSchema#nonNegativeInteger">0</owl:maxCardinality>
        </owl:Restriction></rdfs:subClassOf>
      </owl:Class>
      """;
    return Map.of("odo.owl",xml.replace("</rdf:RDF>",additions+"</rdf:RDF>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
  private Worldview configurationWorld(boolean conflict) {
    var world=world(conflict?"config-conflict":"config-independent",false);
    var ontology=(KimOntologyImpl)world.getOntologies().getFirst();
    var rootSystem=statement("SystemRoot",SemanticType.CONFIGURATION,11);rootSystem.setType(EnumSet.of(SemanticType.CONFIGURATION));rootSystem.setUpperConceptDefined("odo:Configuration");
    var system=statement("System",SemanticType.CONFIGURATION,12);system.setType(EnumSet.of(SemanticType.CONFIGURATION));system.setDeclaredParent(reference("w:SystemRoot"));
    system.setChildrenDisjoint(true);
    for(String name:List.of("Earth","Mars")){var child=statement(name,SemanticType.CONFIGURATION,13);child.setType(EnumSet.of(SemanticType.CONFIGURATION));system.getChildren().add(child);}
    var domain=statement("Perspective",SemanticType.DOMAIN,15);domain.setType(EnumSet.of(SemanticType.DOMAIN,SemanticType.PREDICATE));
    var rootDomain=statement("DomainRoot",SemanticType.DOMAIN,14);rootDomain.setType(EnumSet.of(SemanticType.DOMAIN,SemanticType.PREDICATE));
    rootDomain.setUpperConceptDefined(conflict?"odo:NoConfigurationDomain":"odo:Domain");domain.setDeclaredParent(reference("w:DomainRoot"));
    for(String name:List.of("Earth","Mars"))domain.getObservablesDescribed().add(new org.integratedmodelling.klab.api.collections.impl.PairImpl<>(reference("w:"+name),org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.DescriptionType.DESCRIBES));
    ontology.getStatements().addAll(List.of(rootSystem,system,rootDomain,domain));return world;
  }
  @Test void capturedParserRegistersCoreAliasTargetsFromTheAuthoritativeWorldview() throws Exception {
    assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.NOTHING,
        org.integratedmodelling.klab.runtime.language.WorldviewValidationScope.getMainType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.STRUCTURAL,SemanticType.EVENT)));
    assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.NOTHING,
        org.integratedmodelling.klab.runtime.language.WorldviewValidationScope.getMainType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.STRUCTURAL,SemanticType.FUNCTIONAL)));
    var worldview=world("core-parser",false);var ontology=(KimOntologyImpl)worldview.getOntologies().getFirst();
    var speed=statement("Speed",SemanticType.VELOCITY,19);speed.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE,SemanticType.VELOCITY,SemanticType.INTENSIVE,SemanticType.QUANTIFIABLE,SemanticType.ABSTRACT));speed.setUpperConceptDefined("odo:Speed");ontology.getStatements().add(speed);
    var relationship=statement("Relationship",SemanticType.RELATIONSHIP,20);relationship.setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.STRUCTURAL,SemanticType.OBSERVABLE,SemanticType.COUNTABLE,SemanticType.ABSTRACT));relationship.setUpperConceptDefined("odo:Relationship");ontology.getStatements().add(relationship);
    var parser=new org.integratedmodelling.klab.runtime.language.CapturedSemanticExpressionParser(worldview);
    var parsedRelationship=parser.concept("odo:Relationship");assertTrue(parsedRelationship.is(SemanticType.STRUCTURAL));assertFalse(parsedRelationship.is(SemanticType.FUNCTIONAL));
    var expression=parser.concept("change rate of odo:Speed");
    assertEquals("change rate of odo:Speed",expression.getUrn());
    assertFalse(expression.is(SemanticType.NOTHING));
    assertThrows(IllegalArgumentException.class,()->parser.concept("Nothing"));
  }
  @Test void wholeCandidateRejectsUnsatisfiableDescribingDomainAndKeepsIndependentWitnesses() throws Exception {
    var factory=new SemanticEnvironmentFactory(directory.resolve("config-actors"),configurationCore());
    var captured=factory.capture(configurationWorld(false));
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,captured.beanStatus(),captured.diagnostics().toString());
    var positive=factory.build(captured,()->false);
    try(var state=positive.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,positive.logicalStatus(),positive.diagnostics().toString());
      assertTrue(state.actor().owl().isConsistent());
      assertTrue(state.actor().satisfiable(state.actor().resolveConcept("w:Perspective")));

    }
    try(var runtime=new AcceptedSemanticRuntime(factory)) {
      runtime.submit(configurationWorld(false));assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));assertEquals(1,runtime.status().acceptedGeneration());
      runtime.submit(configurationWorld(true));assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.INVALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());
      assertEquals(1,runtime.status().acceptedGeneration());
    }
  }
  private CandidatePublicationCoordinator.Input<Worldview> input(SemanticEnvironmentFactory factory, Worldview world) {
    var sources = SemanticEnvironmentFactory.sources(world);
    return new CandidatePublicationCoordinator.Input<>(sources, SemanticEnvironmentFactory.sourceOrder(world),
        factory.dependencyRevision(), SemanticEnvironmentFactory.CONFIGURATION_REVISION, world,
        CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
  }
  private org.integratedmodelling.klab.services.reasoner.ReasonerService facade(SemanticEnvironmentFactory factory) throws Exception {
    var facade = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class,
        invocation -> {
          if (invocation.getMethod().getName().equals("getUrl")) return java.net.URI.create("http://127.0.0.1").toURL();
          if (invocation.getMethod().getName().equals("setOperational")) return null;
          return invocation.callRealMethod();
        });
    facade.enableCandidatePublication(factory); return facade;
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
    assertThrows(IllegalArgumentException.class, () -> source.service().declareConcept("within ("));
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
        org.integratedmodelling.klab.api.collections.Pair.of("odo", "http://integratedmodelling.org/odo/uncaptured"));
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

  @Test void queuedSnapshotCannotLoseDeclarationsBeforeTheWorkerBuilds() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var input = factory.capture(world("queued", false));
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    CandidatePublicationCoordinator.Builder<Worldview, SemanticEnvironment> delayed = (candidate, cancellation) -> {
      entered.countDown();
      if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
      return factory.build(candidate, cancellation);
    };
    try (var coordinator = new CandidatePublicationCoordinator<Worldview, SemanticEnvironment>(delayed, i -> true)) {
      coordinator.submit(input);
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
      input.payload().getOntologies().getFirst().getStatements().removeIf(s -> s.getUrn().equals("Q1"));
      release.countDown();
      assertTrue(coordinator.awaitIdle(Duration.ofSeconds(15)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, coordinator.status().logicalStatus(),
          coordinator.status().diagnostics().toString());
      try (var reader = AcceptedSemanticReadSession.open(coordinator)) {
        assertFalse(reader.resolveConcept("w:Q1").is(SemanticType.NOTHING));
      }
    } finally { release.countDown(); }
  }

  @Test void localParserUsesOnlyItsCapturedWorldview() {
    var original = world("parser", false);
    var resolver = new CapturedSemanticSourceResolver(original);
    original.getOntologies().getFirst().getStatements().clear();
    assertNotNull(resolver.service().declareConcept("w:Q1 of w:A"));
    assertNotNull(resolver.service().declareObservable("w:Q1"));
    assertThrows(IllegalArgumentException.class, () -> resolver.service().declareConcept("within ("));
  }

  @Test void productionLoadGateSchedulesAndApiReadsPreserveAcceptedActor() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var facade = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class,
        invocation -> {
          if (invocation.getMethod().getName().equals("getUrl")) return java.net.URI.create("http://127.0.0.1").toURL();
          if (invocation.getMethod().getName().equals("setOperational")) return null;
          return invocation.callRealMethod();
        });
    facade.enableCandidatePublication(factory);
    var source = new CapturedSemanticSourceResolver(world("sources", false));
    var scope = new SemanticEnvironmentScope(source.service());
    try (var runtime = facade.candidateRuntime()) {
      var pending = facade.loadKnowledge(world("accepted", false), scope);
      assertFalse(org.integratedmodelling.klab.api.utils.Utils.Resources.hasErrors(pending));
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, runtime.status().logicalStatus(), runtime.status().diagnostics().toString());
      var accepted = facade.resolveConcept("w:Q1");
      assertTrue(facade.satisfiable(accepted));
      facade.loadKnowledge(world("rejected", true), scope);
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.INVALID, runtime.status().logicalStatus(), runtime.status().diagnostics().toString());
      assertEquals(1, facade.knowledgeRevision());
      assertTrue(facade.satisfiable(facade.resolveConcept("w:Q1")));
      assertTrue(facade.is(facade.resolveConcept("w:A"), facade.resolveConcept("w:Thing")));
      accepted.getMetadata().put("outside", "client mutation");
      assertNull(facade.resolveConcept("w:Q1").getMetadata().get("outside"));
    } finally { scope.close(); }
  }

  @Test void controllerBuilderPinsOldActorThroughPublicationAndReleasesAtBuild() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var facade = facade(factory);
    var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("source", false)).service());
    try (var runtime = facade.candidateRuntime()) {
      facade.loadKnowledge(world("first", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      var builder = SemanticsBuilder.create(facade.resolveConcept("w:Q1"), facade, scope);
      facade.loadKnowledge(world("next", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      try (var files = Files.list(directory.resolve("actors"))) { assertEquals(2, files.count()); }
      assertNotNull(builder.buildObservable());
      try (var files = Files.list(directory.resolve("actors"))) { assertEquals(1, files.count()); }
      assertThrows(IllegalStateException.class, builder::buildConcept);
    } finally { scope.close(); }
  }

  @Test void tokenSearchPinsActorAcrossHttpCallsUntilCancellation() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var facade = facade(factory);
    var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("source", false)).service());
    try (var runtime = facade.candidateRuntime()) {
      facade.loadKnowledge(world("first", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      var request = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest();
      request.setQueryString(""); request.setRequestId(1);
      var response = facade.semanticSearch(request, scope);
      assertTrue(response.getSearchId() > 0, response.getErrors().toString());
      facade.loadKnowledge(world("next", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      try (var files = Files.list(directory.resolve("actors"))) { assertEquals(2, files.count()); }
      request.setSearchId(response.getSearchId()); request.setRequestId(2); request.setQueryString("Q");
      assertEquals(response.getSearchId(), facade.semanticSearch(request, scope).getSearchId());
      request.setCancelSearch(true); request.setRequestId(3);
      assertEquals(0, facade.semanticSearch(request, scope).getSearchId());
      try (var files = Files.list(directory.resolve("actors"))) { assertEquals(1, files.count()); }
    } finally { scope.close(); }
  }

  @Test void productionUpdateCompilesPrefetchedBeanAndRetainsAcceptedOnBeanFailure() throws Exception {
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core());
    var facade = facade(factory);
    var resources = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    var user = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    org.mockito.Mockito.when(user.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
    var manager = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.scopes.ScopeManager.class);
    org.mockito.Mockito.when(manager.collectMessagePayload(org.mockito.ArgumentMatchers.eq(user),
        org.mockito.ArgumentMatchers.eq(org.integratedmodelling.klab.api.services.runtime.Notification.class),
        org.mockito.ArgumentMatchers.any())).thenReturn(user);
    org.mockito.Mockito.doReturn(manager).when(facade).getScopeManager();
    var capabilities = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.services.ResourcesService.Capabilities.class);
    org.mockito.Mockito.when(capabilities.getServiceId()).thenReturn("resources");
    org.mockito.Mockito.when(resources.capabilities(user)).thenReturn(capabilities);
    var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("source", false)).service());
    try (var runtime = facade.candidateRuntime()) {
      facade.loadKnowledge(world("first", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      var saved = world("saved", false);
      org.mockito.Mockito.when(resources.retrieve("w", org.integratedmodelling.klab.api.lang.kim.KimOntology.class, user))
          .thenReturn(saved.getOntologies().getFirst());
      org.mockito.Mockito.when(resources.retrieve("w", Worldview.class, user)).thenReturn(saved);
      var changes = new org.integratedmodelling.klab.api.services.resources.ResourceSet();
      changes.getOntologies().add(new org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource(
          "resources", "w", "fixture", null, org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.ONTOLOGY, 0, false));
      facade.updateKnowledge(changes, user);
      saved.getOntologies().getFirst().getStatements().clear();
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      assertEquals(2, runtime.status().acceptedGeneration(), runtime.status().diagnostics().toString());
      assertFalse(facade.resolveConcept("w:Q1").is(SemanticType.NOTHING));
      org.mockito.Mockito.when(resources.retrieve("w", org.integratedmodelling.klab.api.lang.kim.KimOntology.class, user)).thenReturn(null);
      var rejected = facade.updateKnowledge(changes, user);
      assertTrue(org.integratedmodelling.klab.api.utils.Utils.Resources.hasErrors(rejected));
      assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID, runtime.status().beanStatus());
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.NOT_RUN, runtime.status().logicalStatus());
      assertEquals(2, runtime.status().acceptedGeneration());
      assertTrue(facade.satisfiable(facade.resolveConcept("w:Q1")));
    } finally { scope.close(); }
  }

  @Test void explicitDocumentLocatorUsesActualAliasUrlOrdering() throws Exception {
    String document = "https://example.test/captured/odo.owl";
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core(), Map.of("odo", document));
    var source = world("document", false);
    source.getOntologies().getFirst().getOwlImports().add(org.integratedmodelling.klab.api.collections.Pair.of("odo", document));
    var result = factory.build(factory.capture(source), () -> false);
    try (var state = result.state()) {
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, result.logicalStatus(), result.diagnostics().toString());
    }
    assertThrows(IllegalArgumentException.class, () -> new SemanticEnvironmentFactory(directory.resolve("other"), core(),
        Map.of("odo", "https://raw.githubusercontent.com/integratedmodelling/odo-im/master/releases/uncaptured/odo.owl")));
  }
  @Test void documentationAndCompoundInputsUseOneAcceptedActor() throws Exception {
    var facade = facade(new SemanticEnvironmentFactory(directory.resolve("actors"), core()));
    var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("source", false)).service());
    try (var runtime = facade.candidateRuntime()) {
      facade.loadKnowledge(world("first", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      String documentation = facade.info("w:Q1", org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.CONCEPT, String.class, null);
      assertTrue(documentation.contains("Q1"));
      assertThrows(IllegalStateException.class, facade::owl);
      var exportDirectory = Files.createDirectory(directory.resolve("export"));
      assertTrue(facade.exportNamespace("w", exportDirectory.toFile()));
      try (var exports = Files.list(exportDirectory)) { assertTrue(exports.findAny().isPresent()); }
      var strategy = new org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy(facade.resolveConcept("w:Q1"), scope);
      var before = strategy.getBaseConcept();
      facade.loadKnowledge(world("next", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      assertNotNull(facade.buildObservable(strategy, scope));
      assertSame(before, strategy.getBaseConcept());
      var a = facade.resolveConcept("w:A"); var thing = facade.resolveConcept("w:Thing");
      assertEquals(thing.getUrn(), facade.concretize(a, Map.of(a, thing)).getUrn());
      var context = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
      var observation = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
      var observable = facade.resolveObservable("w:Q1");
      org.mockito.Mockito.when(observation.getObservable()).thenReturn(observable);
      org.mockito.Mockito.when(context.getObservations()).thenReturn(List.of(observation));
      org.mockito.Mockito.when(context.getServices(org.integratedmodelling.klab.api.services.KlabService.class)).thenReturn(List.of(facade));
      try (var lease = runtime.lease()) {
        var bound = (org.integratedmodelling.klab.api.scope.ContextScope)runtime.scope(context, lease.state().actor());
        assertSame(lease.state().actor(), bound.getServices(org.integratedmodelling.klab.api.services.KlabService.class).iterator().next());
        assertNotSame(observation, bound.getObservations().getFirst());
        assertEquals("w:Q1", bound.getObservations().getFirst().getObservable().getSemantics().getUrn());
      }
    } finally { scope.close(); }
  }

  @Test void builderCloseWinsAgainstBlockedTerminalBuild() throws Exception {
    var facade = facade(new SemanticEnvironmentFactory(directory.resolve("actors"), core()));
    var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("source", false)).service());
    try (var runtime = facade.candidateRuntime()) {
      facade.loadKnowledge(world("first", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      var builder = SemanticsBuilder.create(facade.resolveConcept("w:Q1"), facade, scope);
      try (var lease = runtime.lease()) {
        var result = new java.util.concurrent.CompletableFuture<Throwable>();
        Thread worker;
        synchronized (lease.state()) {
          worker = Thread.ofPlatform().start(() -> {
            try { builder.buildConcept(); result.complete(null); } catch (Throwable failure) { result.complete(failure); }
          });
          long limit = System.nanoTime() + Duration.ofSeconds(3).toNanos();
          while (worker.getState() != Thread.State.BLOCKED && System.nanoTime() < limit) Thread.onSpinWait();
          assertEquals(Thread.State.BLOCKED, worker.getState());
          builder.close();
        }
        assertInstanceOf(IllegalStateException.class, result.get(5, java.util.concurrent.TimeUnit.SECONDS));
        worker.join(5000);
      }
    } finally { scope.close(); }
  }

  @Test void searchShutdownCannotRegisterLatePersistentLease() throws Exception {
    var facade = facade(new SemanticEnvironmentFactory(directory.resolve("actors"), core()));
    var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("source", false)).service());
    var runtime = facade.candidateRuntime();
    try {
      facade.loadKnowledge(world("first", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      var request = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest();
      request.setQueryString(""); request.setRequestId(1);
      var result = new java.util.concurrent.CompletableFuture<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse>();
      Thread worker;
      try (var lease = runtime.lease()) {
        synchronized (lease.state()) {
          worker = Thread.ofPlatform().start(() -> {
            try { result.complete(facade.semanticSearch(request, scope)); } catch (Throwable failure) { result.completeExceptionally(failure); }
          });
          long limit = System.nanoTime() + Duration.ofSeconds(3).toNanos();
          while (worker.getState() != Thread.State.BLOCKED && System.nanoTime() < limit) Thread.onSpinWait();
          assertEquals(Thread.State.BLOCKED, worker.getState());
          runtime.close();
        }
        assertEquals(0, result.get(5, java.util.concurrent.TimeUnit.SECONDS).getSearchId());
        worker.join(5000);
      }
      assertThrows(IllegalStateException.class, () -> facade.semanticSearch(request, scope));
      try (var files = Files.list(directory.resolve("actors"))) { assertEquals(0, files.count()); }
    } finally { runtime.close(); scope.close(); }
  }

  @Test void deploymentCapabilitiesRemainAvailableStableAndIndependentOfSemanticMonitor() throws Exception {
    var facade = facade(new SemanticEnvironmentFactory(directory.resolve("actors"), core()));
    var configuration = new org.integratedmodelling.klab.services.configuration.ReasonerConfiguration();
    configuration.setServiceId("deployment-id");
    var field = org.integratedmodelling.klab.services.reasoner.ReasonerService.class.getDeclaredField("configuration");
    field.setAccessible(true); field.set(facade, configuration);
    var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("source", false)).service());
    try (var runtime = facade.candidateRuntime()) {
      var initial = facade.capabilities(scope);
      assertEquals("deployment-id", initial.getServiceId()); assertEquals(facade.getUrl(), initial.getUrl());
      assertEquals(0, initial.getKnowledgeRevision()); assertFalse(initial.isConsistent());
      facade.loadKnowledge(world("first", false), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      var accepted = facade.capabilities(scope);
      assertEquals("deployment-id", accepted.getServiceId()); assertEquals(initial.getUrl(), accepted.getUrl());
      assertEquals(1, accepted.getKnowledgeRevision()); assertTrue(accepted.isConsistent());
      try (var lease = runtime.lease()) {
        synchronized (lease.state()) {
          var response = new java.util.concurrent.CompletableFuture<org.integratedmodelling.klab.api.services.Reasoner.Capabilities>();
          var thread = Thread.ofPlatform().start(() -> {
            try { response.complete(facade.capabilities(scope)); } catch (Throwable failure) { response.completeExceptionally(failure); }
          });
          assertEquals("deployment-id", response.get(2, java.util.concurrent.TimeUnit.SECONDS).getServiceId());
          thread.join(2000);
        }
      }
      facade.loadKnowledge(world("rejected", true), scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(15)));
      var retained = facade.capabilities(scope);
      assertEquals("deployment-id", retained.getServiceId()); assertEquals(initial.getUrl(), retained.getUrl());
      assertEquals(1, retained.getKnowledgeRevision()); assertTrue(retained.isConsistent());
    } finally { scope.close(); }
  }

  @Test void disposableCandidateCannotAcknowledgeDurableCodelistMutation() throws Exception {
    var facade=facade(new SemanticEnvironmentFactory(directory.resolve("actors"),core()));
    try(var runtime=facade.candidateRuntime()) {
      var request=new org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest(
          org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.Operation.SUBMIT,
          "TAXA","taxonomy.species","human","1",null,0,null,null,null,null);
      assertThrows(UnsupportedOperationException.class,()->facade.authorityCodelists(request,null));
      assertEquals(0,runtime.status().acceptedGeneration());
    }
  }

}
