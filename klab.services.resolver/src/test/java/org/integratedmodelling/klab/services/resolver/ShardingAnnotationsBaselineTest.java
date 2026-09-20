package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ShardingAnnotationsBaselineTest {
  private ActuatorImpl compile(List<Annotation> annotations, List<Annotation> overrides) {
    var concept = new ConceptImpl();
    concept.setUrn("test:Elevation");
    concept.setName("Elevation");
    concept.setNamespace("test");
    concept.getType().add(SemanticType.QUALITY);
    var observable = ObservableImpl.promote(concept, null);
    var model = new ModelImpl();
    model.setUrn("test:model");
    model.getObservables().add(observable);
    model.getAnnotations().addAll(annotations);
    var observation = new ObservationImpl();
    observation.setObservable(observable);
    observation.mergeAnnotations(overrides, 2);
    var actuator = new ActuatorImpl();
    actuator.setObservation(Observation.forTransport(observation));
    var scope = mock(ContextScope.class);
    var graph = ResolutionGraph.create(scope);
    graph.graph().addVertex(model);
    new DataflowCompiler(observation, graph, scope)
        .compileModel(actuator, observation, null, null, model, null);
    return actuator;
  }

  @Test
  void namedValuesReachActuatorAndDefinitionOverridesModel() {
    var actuator = compile(List.of(
        Annotation.of("type", "value", "float"),
        Annotation.of("split", "value", 4),
        Annotation.of("maxsize", "value", 1000L),
        Annotation.of("minsplitsize", "value", 20L),
        Annotation.of("fillcurve", "value", "d2_yx")),
        List.of(Annotation.of("split", "value", 2)));
    var strategy = actuator.getShardingStrategy();
    assertEquals(Storage.Type.FLOAT, strategy.getDataType());
    assertEquals(2, strategy.getSuggestedSplits());
    assertEquals(1000, strategy.getMaxBufferSize());
    assertEquals(20, strategy.getMinSplitSize());
    assertEquals(Data.FillCurve.D2_YX, strategy.getCurve());
  }

  @Test
  void positionalArgumentShapeCompilesLikeNamedValue() {
    var annotation = Annotation.of("split");
    annotation.putUnnamed(4);
    assertNull(annotation.get(Annotation.VALUE_PARAMETER_KEY));
    assertEquals(4, compile(List.of(annotation), List.of()).getShardingStrategy().getSuggestedSplits());
  }

  @Test
  void mixedCaseEnumIsRecognizedAndFractionalSplitsFailValidation() {
    assertEquals(Data.FillCurve.D2_XInvY,
        compile(List.of(Annotation.of("fillcurve", "value", "d2_xinvy")), List.of()).getShardingStrategy().getCurve());
    assertThrows(IllegalArgumentException.class,
        () -> compile(List.of(Annotation.of("split", "value", 2.5)), List.of()));
  }

  @Test
  void negativeSizesZeroSplitsMissingAndAmbiguousValuesFailAtCompilation() {
    for (var annotation : List.of(Annotation.of("split", "value", 0),
        Annotation.of("maxsize", "value", -5), Annotation.of("split")))
      assertThrows(IllegalArgumentException.class, () -> compile(List.of(annotation), List.of()));
    var ambiguous = Annotation.of("split", "value", 4); ambiguous.putUnnamed(2);
    assertThrows(IllegalArgumentException.class, () -> compile(List.of(ambiguous), List.of()));
  }
}
