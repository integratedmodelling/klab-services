package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Values;

class ObservationAnnotationsPersistenceTest {
  @Test
  void storedAnnotationsRetainPrecedenceWhenRehydrated() {
    var concept = new ConceptImpl();
    concept.setName("Thing");
    concept.setNamespace("test");
    concept.setUrn("test:Thing");
    concept.getType().add(SemanticType.SUBJECT);
    var observation = new ObservationImpl();
    observation.setObservable(ObservableImpl.promote(concept, null));
    observation.mergeAnnotations(List.of(Annotation.of("style", "value", "define")), 2);
    var graph = mock(AbstractKnowledgeGraph.class, CALLS_REAL_METHODS);
    var stored = graph.asParameters(observation);
    var restored = new ObservationImpl();
    KnowledgeGraphNeo4j.restoreObservationAnnotations(Values.value(stored), restored);
    restored.mergeAnnotations(List.of(Annotation.of("style", "value", "model")), 1);
    assertEquals("define", restored.getAnnotations().getFirst().get("value"));
    assertEquals(observation.getAnnotationPriorities(), restored.getAnnotationPriorities());
    assertTrue(restored.getMetadata().isEmpty());
  }
}
