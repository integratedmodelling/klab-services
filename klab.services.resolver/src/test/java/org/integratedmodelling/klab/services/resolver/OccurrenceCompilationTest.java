package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;

class OccurrenceCompilationTest {
  private ModelImpl model(SemanticType type, boolean collective) {
    var concept = new ConceptImpl();
    concept.setUrn("test:Occurrence");
    concept.setName("Occurrence");
    concept.setNamespace("test");
    concept.getType().add(type);
    concept.setCollective(collective);
    var model = new ModelImpl();
    model.setUrn("test:occurrenceModel");
    model.getObservables().add(ObservableImpl.promote(concept, null));
    var computation = mock(Contextualizable.class);
    when(computation.getServiceCall()).thenReturn(new ServiceCallImpl("test.compute"));
    model.getComputation().add(computation);
    return model;
  }

  private Actuator compile(ModelImpl model) {
    var scope = mock(ContextScope.class);
    var graph = ResolutionGraph.create(scope);
    graph.graph().addVertex(model);
    var actuator = new ActuatorImpl();
    new DataflowCompiler(null, graph, scope).compileModel(actuator, null, null, null, model, null);
    return actuator;
  }

  @Test
  void processAndCollectiveEventReceiveScheduleButIndividualEventsAndRelationshipsDoNot() {
    for (var type : List.of(SemanticType.PROCESS, SemanticType.EVENT)) {
      var model = model(type, type == SemanticType.EVENT);
      model.getAnnotations().add(Annotation.of("time", "step", QuantityImpl.parse("1.month")));
      var actuator = compile(model);
      assertEquals(type == SemanticType.PROCESS ? Actuator.ExecutionRole.PROCESS
          : Actuator.ExecutionRole.EVENT_INSTANTIATOR, actuator.getExecutionRole());
      assertEquals(Time.Resolution.Type.MONTH, actuator.getOccurrenceSchedules().get(0).unit());
    }
    for (var type : List.of(SemanticType.EVENT, SemanticType.RELATIONSHIP, SemanticType.SUBJECT)) {
      var actuator = compile(model(type, false));
      assertEquals(Actuator.ExecutionRole.INITIALIZATION, actuator.getExecutionRole());
      assertTrue(actuator.getOccurrenceSchedules().isEmpty());
    }
  }

  @Test
  void missingScheduleReportsTheModelAndDoesNotCompileAsStatic() {
    var error = assertThrows(KlabValidationException.class, () -> compile(model(SemanticType.PROCESS, false)));
    assertTrue(error.getMessage().contains("test:occurrenceModel"));
    assertTrue(error.getMessage().contains("@time"));
  }
}
