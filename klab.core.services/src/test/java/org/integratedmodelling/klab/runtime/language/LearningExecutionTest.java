package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.LearningContextualizer;
import org.integratedmodelling.klab.resources.LearningResourceCommitter;
import org.junit.jupiter.api.Test;

class LearningExecutionTest {
  final LearningPlanTest fixture = new LearningPlanTest();
  final ContextScope scope = mock(ContextScope.class);
  final Map<String, Resource> store = new ConcurrentHashMap<>();
  final LearningResourceCommitter commits = new LearningResourceCommitter(store::get,
      resource -> {store.put(resource.getUrn(), resource); return true;});
  final AtomicInteger fits = new AtomicInteger();
  final LearningContextualizer learner = (predictors, targets, builder, context) -> {
    fits.incrementAndGet();
    assertEquals(1, predictors.size());
    assertEquals(1, targets.size());
    builder.withAdapterType("test.inference");
  };

  LearningExecutionTest() {
    fixture.model.setNamespace("training");
    fixture.model.setUrn("training.model");
    fixture.model.setLearnedResourceId("fitted.runoff");
  }
  LearningPlan plan() {
    return LearningPlan.bind(fixture.model, Map.of(), Map.of(),
        o -> List.of(LearningPlanTest.observation(o)), true);
  }

  @Test void previewFailurePreservesCommitAndCanBeRetriedWithoutFitting() {
    var result = LearningExecution.train("resolver", "attempt", fixture.model, plan(), learner, scope,
        submission -> commits.commit("resolver", "id", "context", "actor", submission),
        resource -> assertFalse(store.isEmpty()), resource -> {throw new IllegalStateException("preview");});
    assertEquals(LearningExecution.PreviewStatus.FAILED, result.previewStatus());
    assertTrue(result.commitActivityRecorded());
    assertEquals(1, store.size());
    assertEquals(1, fits.get());
    var retried = LearningExecution.complete(result.resource(), resource -> {}, resource -> List.of());
    assertEquals(LearningExecution.PreviewStatus.COMPLETE, retried.previewStatus());
    assertEquals(1, fits.get());
    assertEquals("0.0.1", retried.resource().getVersion().toString());
  }

  @Test void graphRecordingFailurePreservesResourceAndDefersPreview() {
    var result = LearningExecution.train("resolver", "attempt", fixture.model, plan(), learner, scope,
        submission -> commits.commit("resolver", "id", "context", "actor", submission),
        resource -> {throw new IllegalStateException("graph");}, resource -> fail("Must reconcile commit first"));
    assertFalse(result.commitActivityRecorded());
    assertEquals(LearningExecution.PreviewStatus.FAILED, result.previewStatus());
    assertEquals(1, store.size());
  }

  @Test void fittingFailureCancellationAndIdentityOverrideNeverCommit() {
    LearningContextualizer failed = (predictors, targets, builder, context) -> {throw new IllegalStateException("fit");};
    assertThrows(IllegalStateException.class, () -> LearningExecution.train("resolver", "attempt",
        fixture.model, plan(), failed, scope, submission -> fail("Must not commit"), r -> {}, null));
    LearningContextualizer redirected = (predictors, targets, builder, context) -> builder.withUrn("elsewhere");
    assertThrows(IllegalArgumentException.class, () -> LearningExecution.train("resolver", "attempt",
        fixture.model, plan(), redirected, scope, submission -> fail("Must not commit"), r -> {}, null));
    when(scope.isInterrupted()).thenReturn(true);
    assertThrows(java.util.concurrent.CancellationException.class, () -> LearningExecution.train("resolver", "attempt",
        fixture.model, plan(), learner, scope, submission -> fail("Must not commit"), r -> {}, null));
    assertEquals(0, fits.get());
  }

  @Test void ordinaryAdmissionRejectsLearnersAndHashesIncludeLearningIdentity() {
    var original = ModelValidationGate.executableHash(fixture.model);
    assertFalse(ModelValidationGate.admits(fixture.model, null, null));
    fixture.model.setLearnedResourceId("other");
    assertNotEquals(original, ModelValidationGate.executableHash(fixture.model));
    var renamed = ModelValidationGate.executableHash(fixture.model);
    fixture.model.setLearningModel(false);
    assertNotEquals(renamed, ModelValidationGate.executableHash(fixture.model));
  }
}
