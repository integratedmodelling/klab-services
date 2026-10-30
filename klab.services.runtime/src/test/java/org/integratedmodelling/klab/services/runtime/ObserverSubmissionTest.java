package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.DefaultObserverPreparation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObserverSubmissionTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test
  void rootSubmissionHonorsOptOutBeforePreparingDefaultObserver() {
    for (boolean disabled : new boolean[] {false, true}) {
      var f = new SubmissionCancellationTest.Fixture();
      var scope = disabled ? f.scope.withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.DoNotCreateDefaultObserver, true)) : f.scope;
      var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
      concept.setUrn("test:Agent");
      concept.setType(java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT));
      var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
      observable.setSemantics(concept);
      observable.setUrn("test:Agent");
      var observer = new ObservationImpl();
      observer.setId(12);
      observer.setObservable(observable);
      @SuppressWarnings("unchecked")
      var query = (org.integratedmodelling.klab.api.data.KnowledgeGraph.Query<Observation>)
          mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class, RETURNS_SELF);
      when(f.twin.getKnowledgeGraph().query(eq(Observation.class), any())).thenReturn(query);
      when(query.run(any())).thenReturn(java.util.List.of(observer));
      var observation = new ObservationImpl();
      observation.setObservable(mock(org.integratedmodelling.klab.api.knowledge.Observable.class));
      when(observation.getObservable().getSemantics()).thenReturn(
          mock(org.integratedmodelling.klab.api.knowledge.Concept.class));
      when(observation.getObservable().getSemantics().getType()).thenReturn(
          java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.PROCESS));
      observation.setGeometry(Geometry.create("1"));

      try (var defaults = mockStatic(DefaultObserverPreparation.class, CALLS_REAL_METHODS)) {
        defaults.when(() -> DefaultObserverPreparation.prepare(
            nullable(Worldview.class), any(ContextScope.class), any(Geometry.class)))
            .thenReturn(CompletableFuture.completedFuture(observer));
        var submission = f.runtime.submit(observation, scope);
        f.resolution.complete(f.dataflow);
        assertFalse(submission.join().isEmpty());
        assertEquals(disabled ? null : observer, f.resolutionScope.get().getObserver());
        defaults.verify(() -> DefaultObserverPreparation.prepare(
            nullable(Worldview.class), any(ContextScope.class), any(Geometry.class)),
            times(disabled ? 0 : 1));
        assertNull(f.scope.getObserver());
      }
    }
  }
}
