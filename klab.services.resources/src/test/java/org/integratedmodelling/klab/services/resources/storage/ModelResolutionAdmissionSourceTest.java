package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.runtime.language.SourceModelMapper;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Self-contained source admission diagnostics. No HTTP, expression evaluation or Jobs. */
class ModelResolutionAdmissionSourceTest {
  @TempDir Path directory;
  private static final String WORLDVIEW = """
      ontology imod in domain root version 1.0;
      abstract identity Identity is core odo:Identity;
      abstract thing Subject is core odo:Subject;
      abstract length Length is core odo:Length;
      abstract identity Areal;
      abstract identity Volumetric;
      thing Location is imod:Subject;
      thing Region is imod:Location inherits imod:Areal;
      abstract length Height is imod:Length requires identity (imod:Areal or imod:Volumetric);
      length Elevation is imod:Height of imod:Location;
      """;
  private static final String EXPRESSION = "set to [100 + 23.25];";
  private static final String ORIGINAL = "namespace admission; model imod:Elevation in m " + EXPRESSION;
  private static final String EXPLICIT = "namespace admission; model imod:Elevation of imod:Region in m " + EXPRESSION;

  @BeforeAll static void initialize() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }

  private record Fixture(WorkspaceManager manager, SemanticEnvironmentFactory factory,
      CandidatePublicationCoordinator.Input<org.integratedmodelling.klab.api.knowledge.Worldview> captured) {}

  private Fixture fixture() throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(
        Files.createDirectories(directory.resolve("sources")), Map.of("imod", WORLDVIEW));
    var ontologies = new ArrayList<>(manager.getOntologies(false));
    assertEquals(1, ontologies.size());
    assertTrue(ontologies.getFirst().getNotifications().stream().noneMatch(
        n -> n.getLevel().severity >= Notification.Level.Error.severity));
    WorkspaceDomainIngestionTest.set(manager, "namespaceParser", new WorkspaceManager.KimParser());
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod");
    world.setEmpty(false); world.setOntologies(ontologies);
    byte[] odo;
    try (var input = getClass().getResourceAsStream("/knowledge/odo.owl")) {
      assertNotNull(input); odo = input.readAllBytes();
    }
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), Map.of("odo.owl", odo));
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    return new Fixture(manager, factory, captured);
  }

  private KimNamespace parse(WorkspaceManager manager, String source) throws Exception {
    var read = WorkspaceManager.class.getDeclaredMethod("readNamespace", String.class, long.class, String.class, String.class);
    read.setAccessible(true);
    var namespace = (KimNamespace) read.invoke(manager, source, 1L, "fixture", "admission");
    assertTrue(namespace.getNotifications().isEmpty(), namespace.getNotifications().toString());
    assertEquals(source, namespace.getSourceCode());
    return namespace;
  }

  @Test void sourceAndExecutableAdmissionRejectIncompletePrimaryEvenWithObservedRegion() throws Exception {
    var fixture = fixture(); var source = parse(fixture.manager(), ORIGINAL);
    var compiled = fixture.factory().build(fixture.captured(), () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var state = compiled.state()) {
      var actor = state.actor(); assertTrue(actor.owl().isConsistent());
      assertFalse(actor.owl().getConcept("imod:Elevation").isAbstract());
      assertTrue(actor.resolveConcept("imod:Elevation").isAbstract());
      for (boolean executable : List.of(false, true)) {
        var validator = new DocumentSemanticValidator(actor, executable, false,
            executable ? "imod:Region" : null);
        var diagnostics = validator.validate(source);
        var error = diagnostics.stream().filter(n -> n.getLevel().severity >= Notification.Level.Error.severity
            && n.getMessage().contains("MODEL_OUTPUT_UNRESOLVED")).findFirst().orElseThrow();
        assertNotNull(error.getLexicalContext());
        assertEquals("admission", error.getLexicalContext().getDocumentUrn());
        assertEquals(ORIGINAL.indexOf("imod:Elevation"), error.getLexicalContext().getOffsetInDocument());
      }
      assertEquals(ORIGINAL, source.getSourceCode());
      assertFalse(actor.owl().getConcept("imod:Elevation").isAbstract());
    }
  }

  @Test void separateExplicitBearerSnapshotPassesWithoutExecutingOrChangingItsConstant() throws Exception {
    assertTrue(ORIGINAL.endsWith(EXPRESSION)); assertTrue(EXPLICIT.endsWith(EXPRESSION));
    var fixture = fixture(); var original = parse(fixture.manager(), ORIGINAL);
    var exploratory = parse(fixture.manager(), EXPLICIT);
    var compiled = fixture.factory().build(fixture.captured(), () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var state = compiled.state()) {
      var actor = state.actor(); assertTrue(actor.owl().isConsistent());
      for (boolean executable : List.of(false, true)) {
        var diagnostics = new DocumentSemanticValidator(actor, executable, false,
            executable ? "imod:Region" : null).validate(exploratory);
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
      }
      assertEquals(ORIGINAL, original.getSourceCode());
      assertEquals(EXPLICIT, exploratory.getSourceCode());
      assertFalse(actor.owl().getConcept("imod:Elevation").isAbstract());
      assertTrue(actor.resolveConcept("imod:Elevation").isAbstract(), "The original question must remain incomplete");
    }
  }

  @Test void publicationAndSelectedModelPreparationRequireTheActualSourceToBeAdmitted() throws Exception {
    var fixture = fixture(); var original = parse(fixture.manager(), ORIGINAL);
    var exploratory = parse(fixture.manager(), EXPLICIT);
    var compiled = fixture.factory().build(fixture.captured(), () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var state = compiled.state()) {
      var actor = state.actor();
      var scope = mock(ContextScope.class);
      when(scope.getService(Reasoner.class)).thenReturn(actor);
      when(scope.getService(ResourcesService.class)).thenReturn(actor.serviceScope().getService(ResourcesService.class));
      var region = new ObservationImpl(); region.setId(-2); region.setObservable(actor.resolveObservable("imod:Region"));
      when(scope.getContextObservation()).thenReturn(region);
      var failure = assertThrows(ModelValidationGate.AdmissionFailure.class,
          () -> ModelValidationGate.sourceAdmission(original, scope, actor.knowledgeRevision()));
      assertTrue(failure.diagnostics().stream().anyMatch(n -> n.getMessage().contains("MODEL_OUTPUT_UNRESOLVED")));
      var admitted = ModelValidationGate.sourceAdmission(exploratory, scope, actor.knowledgeRevision());
      assertTrue(admitted.valid()); assertTrue(admitted.isSourceSemanticsComplete());
      var requested = actor.resolveObservable("imod:Elevation of imod:Region in m");
      for (var document : List.of(original, exploratory)) {
        try (var publication = ModelValidationGate.openPublicationSession(document, scope)) {
          var model = SourceModelMapper.load((KimModel) document.getStatements().getFirst(), document, scope);
          var before = ModelValidationGate.executableHash(model);
          var receipt = ModelValidationGate.validatedSourcePreparation(model, scope, requested);
          assertEquals(document == exploratory, receipt != null,
              "A complete request must not hide invalid original source");
          assertEquals(before, ModelValidationGate.executableHash(model));
        }
      }
      assertEquals(ORIGINAL, original.getSourceCode());
      assertTrue(actor.resolveConcept("imod:Elevation").isAbstract());
    }
  }
}
