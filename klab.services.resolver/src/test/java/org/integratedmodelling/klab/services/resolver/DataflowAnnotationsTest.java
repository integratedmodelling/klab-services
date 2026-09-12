package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class DataflowAnnotationsTest {
  @Test
  void modelAnnotatesOnlyItsMainOutputAndHonorsDefinitionOverrides() {
    var scope = mock(ContextScope.class);
    var graph = ResolutionGraph.create(scope);
    var model = new ModelImpl();
    model.setUrn("test:model");
    var main = observable("Main");
    var secondary = observable("Secondary");
    model.getObservables().add(main);
    model.getObservables().add(secondary);
    model.getAnnotations().add(Annotation.of("style", "value", "model"));
    model.getAnnotations().add(Annotation.of("label", "value", "model"));
    graph.graph().addVertex(model);
    // Semantic-update operations compile models without allocating an observation.
    assertDoesNotThrow(() -> new DataflowCompiler(null, graph, scope)
        .compileModel(new ActuatorImpl(), null, null, null, model, null));

    for (var observable : model.getObservables()) {
      var observation = new ObservationImpl();
      observation.setObservable(observable);
      observation.mergeAnnotations(java.util.List.of(Annotation.of("style", "value", "define")), 2);
      var actuator = new ActuatorImpl();
      actuator.setObservation(Observation.forTransport(observation));
      new DataflowCompiler(observation, graph, scope)
          .compileModel(actuator, observation, null, null, model, null);
      var annotations = actuator.getObservation().getAnnotations();
      assertEquals("define", annotations.stream().filter(a -> a.getName().equals("style"))
          .findFirst().orElseThrow().get("value"));
      assertEquals(observable == main, annotations.stream().anyMatch(a -> a.getName().equals("label")));
      assertFalse(observation.getAnnotations().stream().anyMatch(a -> a.getName().equals("label")));
    }
  }

  private ObservableImpl observable(String name) {
    var concept = new ConceptImpl();
    concept.setUrn("test:" + name);
    concept.setName(name);
    concept.setNamespace("test");
    concept.getType().add(SemanticType.SUBJECT);
    concept.getAnnotations().add(Annotation.of("style", "value", "concept"));
    return ObservableImpl.promote(concept, null);
  }
}
