package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.services.resources.persistence.ModelCatalog;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceDomainIngestionTest;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real parser/OWL admission reproducing the stale regional gradient that hid valid quality models. */
class StagingQualityAdmissionTest {
  @TempDir Path directory;
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private KimNamespace parse(WorkspaceManager manager, String models, long timestamp) throws Exception {
    var type = Class.forName(WorkspaceManager.class.getName() + "$KimParser");
    var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
    WorkspaceDomainIngestionTest.set(manager, "namespaceParser", constructor.newInstance());
    var read = WorkspaceManager.class.getDeclaredMethod("readNamespace", String.class,
        long.class, String.class, String.class);
    read.setAccessible(true);
    var document = (KimNamespace) read.invoke(manager,
        "namespace fixtures.quality version 1.0;\n" + models,
        timestamp, "fixture", "fixtures.quality");
    assertTrue(document.getNotifications().isEmpty(), document.getNotifications().toString());
    return document;
  }

  @Test void retiringAnInvalidReachModelRestoresQualityPublicationWithoutRelaxingAdmission()
      throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(
        Files.createDirectories(directory.resolve("sources")), Map.of("imod", """
            ontology imod in domain root version 1.0;
            abstract thing Subject is core odo:Subject;
            abstract quantity Ratio is core odo:Ratio;
            abstract length Length is core odo:Length;
            abstract angle Angle is core odo:Angle;
            thing Region;
            thing Stream;
            length Elevation is imod:Length of imod:Region;
            angle Slope is imod:Angle of imod:Region;
            quantity StreambedGradient is imod:Ratio of imod:Stream;
            """));
    var world = new WorldviewImpl();
    world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(new ArrayList<>(manager.getOntologies(false)));
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"),
        CoreOntologyCatalog.bundledCore());
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,
        captured.beanStatus(), captured.diagnostics().toString());
    var compiled = factory.build(captured, () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,
        compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var environment = compiled.state()) {
      var actor = environment.actor();
      String qualities = """
          private model imod:Elevation in m using klab.generators.geospatial.terrain();
          private model imod:Slope in degree_angle using klab.generators.geospatial.terrain();
          """;
      String obsolete = """
          private model imod:StreambedGradient in degree_angle
             observing imod:Elevation in m, imod:Slope in degree_angle
             set to [(elevation.max - elevation)/slope];
          """;
      var rejected = parse(manager, qualities + obsolete, 1L);
      var errors = new DocumentSemanticValidator(actor).validate(rejected).stream()
          .filter(n -> n.getLevel() == Notification.Level.Error).toList();
      assertFalse(errors.isEmpty());
      assertTrue(errors.stream().anyMatch(n -> n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED")
          && n.getMessage().contains("imod:Stream")), errors.toString());
      assertEquals("imod:Stream", actor.inherent(actor.resolveConcept("imod:StreambedGradient")).getUrn());

      var corrected = parse(manager, qualities + "// Retired until a valid stream-reach computation exists.\n"
          + obsolete.lines().map(line -> "// " + line).collect(java.util.stream.Collectors.joining("\n")), 2L);
      assertEquals(2, corrected.getStatements().size());
      assertTrue(new DocumentSemanticValidator(actor).validate(corrected).isEmpty());

      var scope = mock(ContextScope.class);
      var reasoner = mock(Reasoner.class);
      var capabilities = mock(Reasoner.Capabilities.class);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(reasoner.capabilities(scope)).thenReturn(capabilities);
      when(capabilities.getServiceId()).thenReturn("quality-admission-fixture");
      when(capabilities.getKnowledgeRevision()).thenReturn(7L);
      when(reasoner.validateDocument(any(), same(scope))).thenAnswer(invocation -> {
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest request =
            invocation.getArgument(0);
        var response = SemanticValidationResponse.forRequest(request);
        response.setStatus(SemanticValidationResponse.Status.COMPLETE);
        response.setKnowledgeRevision(7L);
        response.getNotifications().addAll(new DocumentSemanticValidator(actor).validate(request.getNamespace()));
        return response;
      });
      var catalog = mock(ModelCatalog.class);
      var index = new NamespaceModelIndex(0);
      index.reconcile(List.of(rejected), catalog, scope, 7L, 1L);
      verify(catalog).withholdNamespace("fixtures.quality", scope);
      verify(catalog, never()).replaceNamespace(any(), any());
      assertFalse(index.diagnostics().get("fixtures.quality").isEmpty());
      index.reconcile(List.of(corrected), catalog, scope, 7L, 2L);
      verify(catalog).replaceNamespace(corrected, scope);
      assertTrue(index.diagnostics().isEmpty());
    }
  }
}
