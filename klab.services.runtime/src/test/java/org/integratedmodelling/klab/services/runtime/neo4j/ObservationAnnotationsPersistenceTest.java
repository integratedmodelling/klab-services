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
  void persistedTerrainColormapStillEvaluatesAfterRestoration() {
    var observation = new ObservationImpl();
    var concept = new ConceptImpl();
    concept.setName("Elevation");
    concept.setNamespace("test");
    concept.setUrn("test:Elevation");
    concept.getType().add(SemanticType.QUALITY);
    observation.setObservable(ObservableImpl.promote(concept, null));
    observation.mergeAnnotations(List.of(Annotation.of("colormap", "colors",
        List.of(List.of(0, 0, 128), "#e8e6b5", List.of(255, 255, 255)),
        "center", 0, "min", -8000, "max", 4000)), 2);
    var graph = mock(AbstractKnowledgeGraph.class, CALLS_REAL_METHODS);
    var restored = new ObservationImpl();
    KnowledgeGraphNeo4j.restoreObservationAnnotations(Values.value(graph.asParameters(observation)), restored);
    restored.mergeAnnotations(List.of(Annotation.of("colormap", "palette", "gray")), 1);
    var ramp = org.integratedmodelling.klab.api.view.modeler.visualization.ColorRamp.fromObservation(restored);
    assertEquals(0xff000080, ramp.argb(-8000, 0, 1));
    assertEquals(0xffe8e6b5, ramp.argb(0, 0, 1));
    assertEquals(0xffffffff, ramp.argb(4000, 0, 1));
  }
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
