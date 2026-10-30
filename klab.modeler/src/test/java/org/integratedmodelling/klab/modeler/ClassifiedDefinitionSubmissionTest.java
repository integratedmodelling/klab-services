package org.integratedmodelling.klab.modeler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClassifiedDefinitionSubmissionTest {

  @Test
  void classifiedDefinitionsPreserveSubmissionOptionsAndSelectOnlyTheirIntendedMode() {
    for (String classification : new String[] {"query", "observation", "observer"}) {
      var scope = mock(ContextScope.class);
      var submissionScope = mock(ContextScope.class);
      var reasoner = mock(Reasoner.class);
      var runtime = mock(RuntimeService.class);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(scope.getService(RuntimeService.class)).thenReturn(runtime);
      when(scope.withResolutionConstraints(any(ResolutionConstraint[].class)))
          .thenReturn(submissionScope);
      when(submissionScope.getService(RuntimeService.class)).thenReturn(runtime);

      var semantics = new ConceptImpl();
      semantics.setType(EnumSet.of(SemanticType.SUBJECT));
      var observable = new ObservableImpl();
      observable.setSemantics(semantics);
      when(reasoner.resolveObservable("test:Subject")).thenReturn(observable);

      var definition = new KimSymbolDefinitionImpl();
      definition.setDefineClass(classification);
      definition.setNamespace("test.submissions");
      definition.setProjectName("test.project");
      definition.setName("request");
      definition.setUrn("test.submissions.request");
      definition.setValue(Map.of("semantics", "test:Subject", "time", Map.of("year", 2020),
          "test.option", "preserved"));
      definition.getAnnotations().add(Annotation.of("resolution", "value", "test"));

      var submitted = ArgumentCaptor.forClass(Observation.class);
      var pending = new CompletableFuture<Observation>();
      when(runtime.submit(submitted.capture(), same(submissionScope))).thenReturn(pending);
      var modeler = mock(ModelerImpl.class);
      doCallRealMethod().when(modeler).observe(scope, definition, false);

      assertSame(pending, modeler.observe(scope, definition, false));
      var observation = submitted.getValue();
      assertFalse(observation.isEmpty(), observation.getNotifications().toString());
      assertEquals(0.0, observation.getResolvedCoverage());
      assertEquals("query".equals(classification) ? Observation.QUERY_ID : Observation.UNASSIGNED_ID,
          observation.getId());
      assertSame(observable, observation.getObservable());
      assertNotNull(observation.getGeometry());
      assertEquals("test.submissions:request", observation.getUrn());
      assertEquals("preserved", observation.getMetadata().get("test.option"));
      assertEquals("observer".equals(classification),
          Boolean.TRUE.equals(observation.getMetadata().get(Metadata.IM_OBSERVER_TAG)));
      assertEquals("test", observation.getAnnotations().iterator().next().get("value"));

      // The runtime client transports observations before the runtime inspects their IDs.
      var transported = (ObservationImpl) Observation.forTransport(observation);
      assertEquals(observation.getId(), transported.getId());
      assertEquals("test", transported.getAnnotations().getFirst().get("value"));
      var constraints = ArgumentCaptor.forClass(ResolutionConstraint[].class);
      verify(scope).withResolutionConstraints(constraints.capture());
      assertEquals("observer".equals(classification) ? 3 : 2, constraints.getValue().length);
      assertEquals(ResolutionConstraint.Type.ResolutionNamespace, constraints.getValue()[0].getType());
      assertEquals(ResolutionConstraint.Type.ResolutionProject, constraints.getValue()[1].getType());
      if ("observer".equals(classification)) {
        assertEquals(ResolutionConstraint.Type.UseAsObserver, constraints.getValue()[2].getType());
      }
    }
  }
}
