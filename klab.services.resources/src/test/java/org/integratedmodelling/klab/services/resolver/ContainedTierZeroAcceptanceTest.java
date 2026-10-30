package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.services.reasoner.owl.ContextualRequestAdmission;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceDomainIngestionTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Real authored grammar, OWL admission, matching, transport and dataflow construction.
 * Catalog I/O and Runtime contextualizer admission are explicit service doubles: no execution claim. */
class ContainedTierZeroAcceptanceTest {
  @TempDir Path directory;
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private record Fixture(SemanticEnvironment state, KimOntology source) implements AutoCloseable {
    @Override public void close() throws Exception { state.close(); }
  }

  private Fixture fixture(boolean declared) throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("source")), Map.of("imod", """
        ontology imod in domain root version 1.0;
        abstract thing Subject is core odo:Subject;
        abstract quantity Ratio is core odo:Ratio;
        abstract length Length is core odo:Length;
        thing Region;
        thing Other;
        thing Waterway has children Reach, River, Stream%s;
        quantity StreambedGradient is imod:Ratio of imod:Stream;
        length Elevation is imod:Length of imod:Region;
        """.formatted(declared ? " contained into imod:Region" : "")));
    var source = manager.getOntologies(false).iterator().next();
    assertTrue(source.getNotifications().stream().noneMatch(n -> n.getLevel() == Notification.Level.Error),
        source.getNotifications().toString());
    var strategies = ObservationPipelineTest.document();
    strategies.getStatements().removeIf(s -> !s.getUrn().endsWith("dependent.direct"));
    assertEquals(1, strategies.getStatements().size());
    var world = new WorldviewImpl();
    world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(List.of(source)); world.setObservationStrategies(List.of(strategies));
    var core = Path.of(System.getProperty("klab.test.core.directory"), "odo.owl");
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), Map.of("odo.owl", Files.readAllBytes(core)));
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    var compiled = factory.build(captured, () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    return new Fixture(compiled.state(), source);
  }

  private static ObservationImpl observation(Observable observable, long id, Geometry geometry) {
    var result = new ObservationImpl(); result.setId(id); result.setUrn("fixture:" + id);
    result.setObservable(observable); result.setGeometry(geometry); return result;
  }

  @Test void authoredContainmentAdmitsUnitlessPrecomputedTierZeroWithoutRecursiveDiscovery() throws Exception {
    try (var fixture = fixture(true)) {
      var actor = fixture.state().actor();
      var target = actor.resolveObservable("imod:StreambedGradient");
      assertTrue(actor.is(target.asConcept(), actor.resolveConcept("imod:Ratio")),
          "Authored Ratio specialization, independent of enum projection details");
      assertNull(target.getUnit(), "This precomputed Ratio fixture requests no angular unit");
      assertEquals("imod:Stream", actor.inherent(target).getUrn());
      var authored = new KimModelImpl(); authored.setUrn("fixtures.precomputed.gradient");
      var outputSyntax = new KimConceptImpl(); outputSyntax.setName("imod:StreambedGradient");
      outputSyntax.setType(new HashSet<>(target.getSemantics().getType())); outputSyntax.finalizeDefinition();
      var authoredOutput = new KimObservableImpl(); authoredOutput.setSemantics(outputSyntax);
      authored.getObservables().add(authoredOutput);
      var constant = new ContextualizableImpl(); constant.setLiteral(0.12);
      authored.getContextualization().add(constant);
      var lexical = mock(org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context.class);
      doReturn(fixture.source()).when(lexical).getDocument();
      var validated = new DocumentSemanticValidator(actor, true).validateModel(authored, lexical);
      assertTrue(validated.isEmpty(), "Precomputed source must pass strict production validation: " + validated);
      var scale = GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
      var region = observation(actor.resolveObservable("imod:Region"), 21, scale);
      var request = observation(target, -1, scale);
      var scope = mock(ContextScope.class);
      var reasonerClient = mock(Reasoner.class);
      var receipt = mock(Reasoner.Capabilities.class);
      when(receipt.getServiceId()).thenReturn("fixture-reasoner");
      when(receipt.getKnowledgeRevision()).thenReturn(3L);
      when(reasonerClient.capabilities(scope)).thenReturn(receipt);
      when(scope.getService(Reasoner.class)).thenReturn(reasonerClient);
      when(scope.getContextObservation()).thenReturn(region);
      when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
      var data = Parameters.<String>create();
      data.put("__RESOLUTION_GRAPH__", ResolutionGraph.create(scope)); when(scope.getData()).thenReturn(data);
      // Admission and strategy matching run in the actual compiled semantic actor.
      var direct = actor.computeObservationStrategies(request, scope);
      assertEquals(1, direct.size()); assertEquals(0, direct.getFirst().getRank());
      var mapper = JacksonConfiguration.newObjectMapper();
      var listType = mapper.getTypeFactory().constructCollectionType(List.class, ObservationStrategy.class);
      List<ObservationStrategy> transported = mapper.readValue(mapper.writerFor(listType).writeValueAsBytes(direct), listType);
      var later = mock(ObservationStrategy.class);
      when(later.getRank()).thenReturn(1);
      when(later.getUrn()).thenReturn("fixture:discovery.must.not.run");
      when(later.getContextBinding()).thenThrow(new AssertionError("Tier-1 binding touched despite complete Tier-0"));
      // Deliberately supply the expensive candidate first: production Resolver must order tiers.
      when(reasonerClient.computeObservationStrategies(request, scope)).thenReturn(List.of(later, transported.getFirst()));
      when(scope.getService(Reasoner.class)).thenReturn(reasonerClient);
      var runtime = mock(RuntimeService.class); when(scope.getService(RuntimeService.class)).thenReturn(runtime);
      var computation = constant;
      var model = new ModelImpl(); model.setUrn("fixtures.precomputed.gradient");
      model.setNamespace("fixtures"); model.setProjectName("fixture.project"); model.setCoverage(scale);
      model.setObservables(List.of(target)); model.setComputation(List.of(computation));
      var requirements = new ResourceSet(); requirements.setEmpty(false);
      when(runtime.resolveContextualizables(model.getComputation(), scope)).thenReturn(requirements);
      var compiler = spy(new ResolutionCompiler(mock(ResolverService.class)));
      // The catalog/source-admission boundary is controlled in this planner fixture.
      // This receipt proves transport/wiring only; source validation is exercised above.
      doReturn(new org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt(
          model.getUrn(), model.getNamespace(), "fixture-version", "fixture-source",
          org.integratedmodelling.klab.runtime.language.ModelValidationGate.executableHash(model),
          "fixture-reasoner", 3L)).when(compiler).admissionReceipt(model);
      doReturn(new ResolutionCompiler.QueryMatch(null, null, scale, null, Coverage.create(scale, 0)))
          .when(compiler).query(target, scale, scope);
      doReturn(List.of(model)).when(compiler).queryModels(target, region.getObservable().asConcept(), scope, scale);
      var graph = compiler.resolve(request, scope);
      assertFalse(graph.isEmpty()); assertTrue(graph.getCoverage().isComplete());
      var flow = new DataflowCompiler(request, graph, scope).compile();
      assertEquals(1, flow.getComputation().size());
      var actuator = flow.getComputation().getFirst();
      assertNull(actuator.getContextBinding());
      assertEquals(target.getUrn(), actuator.getOperationObservable().getUrn());
      assertEquals("imod:Stream", actor.inherent(actuator.getOperationObservable()).getUrn());
      assertEquals(0.12, actuator.getComputation().getFirst().getParameters().get("value"));
      verify(later, never()).getContextBinding(); verify(later, never()).getOperations();
      verify(runtime, never()).getMembers(any(), any());
      verify(scope, never()).observation(any()); verify(scope, never()).within(any());
      verify(compiler, times(1)).queryModels(target, region.getObservable().asConcept(), scope, scale);
    }
  }

  @Test void undeclaredAndUnrelatedContainersDoNotAcquirePermission() throws Exception {
    try (var fixture = fixture(false)) {
      var actor = fixture.state().actor(); var gradient = actor.resolveConcept("imod:StreambedGradient");
      assertFalse(ContextualRequestAdmission.admits(actor.owl(), gradient, actor.resolveConcept("imod:Region")));
      assertTrue(ContextualRequestAdmission.admits(actor.owl(), gradient, actor.resolveConcept("imod:Stream")));
    }
    try (var fixture = fixture(true)) {
      var actor = fixture.state().actor();
      assertFalse(ContextualRequestAdmission.admits(actor.owl(), actor.resolveConcept("imod:StreambedGradient"),
          actor.resolveConcept("imod:Other")));
    }
  }

  @Test void containmentDoesNotLegalizeUnbridgedElevationComputation() throws Exception {
    try (var fixture = fixture(true)) {
      var actor = fixture.state().actor();
      var source = fixture.source();
      var model = new KimModelImpl();
      for (var name : List.of("StreambedGradient", "Elevation")) {
        var syntax = new KimConceptImpl(); syntax.setName("imod:" + name);
        syntax.setType(new HashSet<>(actor.resolveConcept("imod:" + name).getType())); syntax.finalizeDefinition();
        var observable = new KimObservableImpl(); observable.setSemantics(syntax);
        if (name.equals("Elevation")) model.getDependencies().add(observable);
        else model.getObservables().add(observable);
      }
      var lexical = mock(org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context.class);
      doReturn(source).when(lexical).getDocument();
      var validator = new DocumentSemanticValidator(actor, true);
      var rejected = validator.validateModel(model, lexical);
      assertTrue(rejected.stream().anyMatch(n -> n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED")
          || n.getMessage().contains("MODEL_DEPENDENCY_INCOMPATIBLE")), rejected.toString());
      assertEquals("imod:Stream", actor.inherent(actor.resolveConcept("imod:StreambedGradient")).getUrn());
      assertEquals("imod:Region", actor.inherent(actor.resolveConcept("imod:Elevation")).getUrn());
    }
  }
}
