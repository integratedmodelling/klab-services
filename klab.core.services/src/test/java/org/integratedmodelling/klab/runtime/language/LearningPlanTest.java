package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.extension.Learning;
import org.junit.jupiter.api.Test;

class LearningPlanTest {
  final ModelImpl model = new ModelImpl();
  final Observable predictor = observable("rain");
  final Observable target = observable("runoff");
  LearningPlanTest() {
    model.setLearningModel(true);
    model.getDependencies().add(predictor);
    model.getObservables().add(target);
    when(predictor.getAnnotations()).thenReturn(List.of(Annotation.of("predictor", "weight", 0.25)));
  }

  static Observable observable(String name) {
    var result = mock(Observable.class);
    when(result.getStatedName()).thenReturn(name);
    when(result.getArtifactType()).thenReturn(Artifact.Type.NUMBER);
    when(result.getUrn()).thenReturn("test:" + name);
    return result;
  }
  static Observation observation(Observable observable) {
    var result = mock(Observation.class);
    var name = observable.getStatedName();
    when(result.getObservable()).thenReturn(observable);
    when(result.getId()).thenReturn(10L);
    when(result.getUrn()).thenReturn("observation:" + name);
    when(result.getEventTimestamps()).thenReturn(List.of(0L));
    when(result.getMetadata()).thenReturn(org.integratedmodelling.klab.api.data.Metadata.create());
    return result;
  }

  @Test void explicitAndPreparedTargetsHavePrecedenceAndPredictorOptionsSurvive() {
    var rain = observation(predictor);
    var explicit = observation(target);
    var prepared = observation(target);
    var plan = LearningPlan.bind(model, Map.of("runoff", explicit), Map.of("runoff", prepared),
        o -> { assertSame(predictor, o); return List.of(rain); }, true);
    assertSame(explicit, plan.targets().getFirst().observation());
    assertEquals(0.25, plan.predictors().getFirst().parameters().get("weight"));
    plan = LearningPlan.bind(model, Map.of(), Map.of("runoff", prepared),
        o -> { assertSame(predictor, o); return List.of(rain); }, true);
    assertSame(prepared, plan.targets().getFirst().observation());
  }

  @Test void invalidExplicitOptionalInputNeverFallsBack() {
    when(predictor.isOptional()).thenReturn(true);
    var unresolved = observation(predictor);
    when(unresolved.getEventTimestamps()).thenReturn(List.of());
    assertThrows(IllegalArgumentException.class, () -> LearningPlan.bind(model,
        Map.of("rain", unresolved), Map.of(), o -> fail("Must not fall back"), true));
  }

  @Test void unresolvedRequiredAndAmbiguousInputsFail() {
    assertThrows(IllegalArgumentException.class,
        () -> LearningPlan.bind(model, Map.of(), Map.of(), o -> List.of(), true));
    assertThrows(IllegalArgumentException.class, () -> LearningPlan.bind(model, Map.of(), Map.of(),
        o -> List.of(observation(o), observation(o)), true));
  }

  @Test void optionalInputsAndUnsupervisedTargetsCanBeAbsent() {
    when(predictor.isOptional()).thenReturn(true);
    var calls = new AtomicInteger();
    var plan = LearningPlan.bind(model, Map.of(), Map.of(), o -> {
      calls.incrementAndGet(); assertSame(predictor, o); return List.of();
    }, false);
    assertTrue(plan.targets().isEmpty());
    assertTrue(plan.predictors().isEmpty());
    assertEquals(1, calls.get());
  }

  @Test void previewsAreExcludedAutomaticallyButExplicitBindingCanUseThem() {
    var preview = observation(target);
    preview.getMetadata().put(Learning.PREVIEW, true);
    var evidence = observation(target);
    var plan = LearningPlan.bind(model, Map.of("rain", observation(predictor)), Map.of(),
        o -> List.of(preview, evidence), true);
    assertSame(evidence, plan.targets().getFirst().observation());
    plan = LearningPlan.bind(model, Map.of("rain", observation(predictor), "runoff", preview),
        Map.of(), o -> fail("Explicit bindings must win"), true);
    assertSame(preview, plan.targets().getFirst().observation());
  }

  @Test void duplicatesUnknownNamesAndTypesFail() {
    when(target.getStatedName()).thenReturn("rain");
    assertThrows(IllegalArgumentException.class,
        () -> LearningPlan.bind(model, Map.of(), Map.of(), o -> List.of(), true));
    when(target.getStatedName()).thenReturn("runoff");
    assertThrows(IllegalArgumentException.class,
        () -> LearningPlan.bind(model, Map.of("unknown", observation(target)), Map.of(), o -> List.of(), true));
    var text = observable("text");
    when(text.getArtifactType()).thenReturn(Artifact.Type.TEXT);
    assertThrows(IllegalArgumentException.class, () -> LearningPlan.bind(model,
        Map.of("rain", observation(text)), Map.of(), o -> List.of(), true));
  }

  @Test void observerOverrideAndDefaultAreValidatedBySemanticOwner() {
    var agent = mock(Concept.class);
    when(agent.getUrn()).thenReturn("test:LearningObserver");
    when(agent.is(SemanticType.AGENT)).thenReturn(true);
    assertSame(agent, LearningPlan.observer(model, agent, urn -> agent));
    model.getAnnotations().add(Annotation.of("learning", "observer", "test:OtherObserver"));
    assertSame(agent, LearningPlan.observer(model, null, urn -> {
      assertEquals("test:OtherObserver", urn); return agent;
    }));
    assertThrows(IllegalArgumentException.class, () -> LearningPlan.observer(model, agent, urn -> null));
  }
}
