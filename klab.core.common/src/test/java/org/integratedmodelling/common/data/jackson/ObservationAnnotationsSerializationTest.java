package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.junit.jupiter.api.Test;

class ObservationAnnotationsSerializationTest {
  @Test
  void definitionAndDependencyAnnotationsAreAvailableBeforeSubmission() {
    var concept = new ConceptImpl();
    concept.setUrn("test:Temperature");
    concept.setName("Temperature");
    concept.setNamespace("test");
    concept.getType().add(SemanticType.QUALITY);
    concept.getAnnotations().add(Annotation.of("style", "value", "concept"));
    var observable = ObservableImpl.promote(concept, null);
    var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    when(reasoner.resolveObservable(anyString())).thenReturn(observable);
    when(reasoner.resolveConcept(anyString())).thenReturn(concept);
    var scope = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    var syntax = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();
    syntax.setUrn("test:Temperature");
    var semantics = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
    semantics.setName("test:Temperature");
    syntax.setSemantics(semantics);
    syntax.getAnnotations().add(Annotation.of("style", "value", "dependency"));
    var dependency = new Observation.NaiveBuilder(syntax, scope).build();
    assertEquals("dependency", dependency.getAnnotations().getFirst().get("value"));
    var define = new org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl();
    define.setDefineClass("observation");
    define.setNamespace("test");
    define.setName("temperature");
    define.setUrn("test.temperature");
    define.setValue(java.util.Map.of("semantics", syntax));
    define.getAnnotations().add(Annotation.of("style", "value", "define"));
    var built = new Observation.NaiveBuilder(define, scope).build();
    assertFalse(built.isEmpty());
    assertEquals("define", built.getAnnotations().getFirst().get("value"));
    built.mergeAnnotations(List.of(Annotation.of("style", "value", "model")), 1);
    assertEquals("define", built.getAnnotations().getFirst().get("value"));
    assertEquals("concept", concept.getAnnotations().getFirst().get("value"));
  }

  @Test
  void serviceRoundTripRetainsPrecedenceAndParameters() throws Exception {
    var concept = new ConceptImpl();
    concept.setUrn("test:Temperature");
    concept.setName("Temperature");
    concept.setNamespace("test");
    concept.getType().add(SemanticType.QUALITY);
    concept.getAnnotations().add(Annotation.of("style", "value", "concept"));
    var observable = ObservableImpl.promote(concept, null);
    var submitted = new ObservationImpl();
    submitted.setObservable(observable);
    submitted.mergeAnnotations(List.of(Annotation.of("style", "value", "define")), 2);
    var mapper = JacksonConfiguration.newObjectMapper();
    var remote = (ObservationImpl) mapper.readValue(
        mapper.writeValueAsString(Observation.forTransport(submitted)), Observation.class);
    remote.mergeAnnotations(List.of(Annotation.of("style", "value", "model"),
        Annotation.of("label", "value", "new")), 1);
    var completed = mapper.readValue(mapper.writeValueAsString(remote), Observation.class);
    assertEquals("define", completed.getAnnotations().stream()
        .filter(a -> a.getName().equals("style")).findFirst().orElseThrow().get("value"));
    assertEquals(2, completed.getAnnotations().size());
    assertEquals(2, ((ObservationImpl) completed).getAnnotationPriorities().get("style"));
  }
}
