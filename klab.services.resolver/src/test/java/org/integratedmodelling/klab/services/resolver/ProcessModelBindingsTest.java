package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class ProcessModelBindingsTest {
  @Test void descriptiveClosureRetainsDirectionWithoutAddingInputsEffectsOrCreation() {
    var f = new Fixture();
    var other = observable("slope", SemanticType.QUALITY);
    var third = observable("wet", SemanticType.QUALITY, SemanticType.PRESENCE);
    var firstLink = new SemanticInfluence(other.getSemantics(), f.elevation.getSemantics(),
        SemanticInfluence.Kind.INCREASES_WITH, "test:slope restriction");
    var secondLink = new SemanticInfluence(other.getSemantics(), third.getSemantics(),
        SemanticInfluence.Kind.MARKS, "test:wet restriction");
    when(f.reasoner.influences(f.elevation.getSemantics())).thenReturn(List.of(firstLink));
    when(f.reasoner.influences(other.getSemantics())).thenReturn(List.of(firstLink, secondLink));
    when(f.reasoner.influences(third.getSemantics())).thenReturn(List.of(secondLink));
    var plan = ProcessModelBindings.analyze(f.model, f.scope);
    assertEquals(2, plan.version());
    assertEquals(List.of(firstLink, secondLink), plan.descriptiveLinks());
    assertEquals(1, plan.bindings().size());
    assertTrue(plan.obligations().isEmpty());
    var restored = Utils.Json.parseObject(Utils.Json.asString(plan), ProcessPlan.class);
    assertEquals(plan, restored);
    assertThrows(IllegalArgumentException.class, () -> new ProcessPlan(1, 100, "legacy",
        List.of(new ProcessPlan.Binding("elevation", f.elevation, ProcessPlan.Effect.AFFECTED,
            true, false, List.of("MARKS"))), List.of()));
    assertDoesNotThrow(() -> new ProcessPlan(1, 100, "legacy-safe", plan.bindings(), plan.obligations()));
  }
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static ObservableImpl observable(String name, SemanticType... types) {
    var concept = new ConceptImpl();
    concept.setUrn("test:" + name); concept.setName(name); concept.setNamespace("test");
    concept.getType().addAll(List.of(types));
    var result = ObservableImpl.promote(concept, null);
    result.setStatedName(name);
    return result;
  }
  class Fixture {
    final ContextScope scope = mock(ContextScope.class);
    final Reasoner reasoner = mock(Reasoner.class);
    final ModelImpl model = new ModelImpl();
    final ObservableImpl process = observable("erosion", SemanticType.PROCESS);
    final ObservableImpl elevation = observable("elevation", SemanticType.QUALITY);
    final ObservationImpl region = new ObservationImpl();
    final Contextualizable expression = mock(Contextualizable.class);
    Fixture() {
      region.setId(100); region.setObservable(observable("region", SemanticType.SUBJECT));
      when(scope.getContextObservation()).thenReturn(region);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      model.setUrn("test:processModel"); model.getObservables().add(process);
      model.getDependencies().add(elevation);
      when(expression.getExpression()).thenReturn(org.integratedmodelling.klab.api.lang.ExpressionCode.of("elevation - 10", "groovy"));
      when(expression.getTargetId()).thenReturn("elevation");
      model.getComputation().add(expression);
      model.getAnnotations().add(Annotation.of("time", "step", QuantityImpl.parse("1.month")));
    }
  }

  @Test void namedDependencyBindsToBearerAndSurvivesPlanTransport() {
    var f = new Fixture();
    f.elevation.setOptional(true);
    var graph = ResolutionGraph.create(f.scope); graph.graph().addVertex(f.model);
    var actuator = new ActuatorImpl();
    new DataflowCompiler(null, graph, f.scope).compileModel(actuator, null, null, null, f.model, null);
    var plan = Utils.Json.parseObject(actuator.getData().get(ProcessPlan.DATA_KEY).toString(), ProcessPlan.class);
    assertEquals(100, plan.bearerId());
    assertTrue(plan.binding("elevation").observable().isOptional());
    assertEquals(ProcessPlan.Effect.AFFECTED, plan.binding("elevation").effect());
    assertEquals("elevation", actuator.getComputation().getFirst().getParameters().get("_targetId"));
    f.region.setId(200);
    assertEquals(200, ProcessModelBindings.analyze(f.model, f.scope).bearerId());
  }

  @Test void creationTakesPrecedenceOverMutationAndPreservesAnObligation() {
    var f = new Fixture();
    when(f.reasoner.createdBy(f.elevation, f.process)).thenReturn(true);
    when(f.reasoner.affectedBy(f.elevation, f.process)).thenReturn(true);
    when(f.reasoner.influences(f.process)).thenReturn(List.of(
        new SemanticInfluence(f.elevation.getSemantics(), SemanticInfluence.Kind.CREATES)));
    var plan = ProcessModelBindings.analyze(f.model, f.scope);
    assertEquals(ProcessPlan.Effect.CREATED, plan.binding("elevation").effect());
    assertEquals("CREATES", plan.obligations().getFirst().relation());
  }

  @Test void createdDependencyNeverQueriesOrAllocatesAnObservationDuringResolution() throws Exception {
    var f = new Fixture();
    when(f.reasoner.createdBy(f.elevation, f.process)).thenReturn(true);
    when(f.scope.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class)))
        .thenReturn(f.scope);
    var runtime = mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    when(runtime.resolveContextualizables(eq(f.model.getComputation()), eq(f.scope)))
        .thenReturn(new org.integratedmodelling.klab.api.services.resources.ResourceSet());
    f.model.setCoverage(org.integratedmodelling.klab.api.geometry.Geometry.UNIVERSAL);
    var scale = GeometryRepository.INSTANCE.scale(org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T0(1){tstart=1388534400000,tend=1420070400000,ttype=PHYSICAL}"));
    var method = ResolutionCompiler.class.getDeclaredMethod("resolve", Model.class,
        org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class,
        ResolutionGraph.class, ContextScope.class);
    method.setAccessible(true);
    var result = (ResolutionGraph) method.invoke(new ResolutionCompiler(mock(ResolverService.class)),
        f.model, scale, ResolutionGraph.create(f.scope), f.scope);
    assertFalse(result.isEmpty());
    assertEquals(ProcessPlan.Effect.CREATED, result.processPlan.binding("elevation").effect());
    verify(f.scope, never()).observation(any());
  }

  @Test void subjectEventAndFunctionalRelationshipAreHostsButStructuralRelationshipsAreNot() {
    var f = new Fixture();
    for (var host : List.of(observable("region", SemanticType.SUBJECT),
        observable("event", SemanticType.EVENT),
        observable("relationship", SemanticType.RELATIONSHIP, SemanticType.FUNCTIONAL))) {
      f.region.setObservable(host);
      assertDoesNotThrow(() -> ProcessModelBindings.analyze(f.model, f.scope));
    }
    f.region.setObservable(observable("relationship", SemanticType.RELATIONSHIP, SemanticType.STRUCTURAL));
    assertThrows(KlabValidationException.class, () -> ProcessModelBindings.analyze(f.model, f.scope));
    f.region.setObservable(f.process);
    assertThrows(KlabValidationException.class, () -> ProcessModelBindings.analyze(f.model, f.scope));
  }

  @Test void implicitUnknownAmbiguousNonqualityAndIncompatibleAssignmentsAreRejected() {
    var f = new Fixture();
    for (String target : new String[] {null, "", "erosion", "missing"}) {
      when(f.expression.getTargetId()).thenReturn(target);
      assertThrows(KlabValidationException.class, () -> ProcessModelBindings.analyze(f.model, f.scope));
    }
    when(f.expression.getTargetId()).thenReturn("elevation");
    f.model.getObservables().add(f.elevation);
    assertThrows(KlabValidationException.class, () -> ProcessModelBindings.analyze(f.model, f.scope));
    f.model.getObservables().removeLast();
    var otherBearer = observable("other", SemanticType.SUBJECT);
    when(f.reasoner.inherent(f.elevation)).thenReturn(otherBearer.getSemantics());
    assertThrows(KlabValidationException.class, () -> ProcessModelBindings.analyze(f.model, f.scope));
    when(f.reasoner.is(f.region.getObservable(), otherBearer.getSemantics())).thenReturn(true);
    assertDoesNotThrow(() -> ProcessModelBindings.analyze(f.model, f.scope));
    f.model.getDependencies().clear();
    f.model.getDependencies().add(observable("elevation", SemanticType.SUBJECT));
    assertThrows(KlabValidationException.class, () -> ProcessModelBindings.analyze(f.model, f.scope));
  }
}
