package org.integratedmodelling.klab.api.knowledge.observation.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.AnnotationCollector;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class ObservationAnnotationsTest {

  @Test
  void lateModelAndConceptContributorsCannotReplaceDefinitionAcrossTransport() {
    var submitted = new ObservationImpl();
    submitted.mergeAnnotations(List.of(annotation("style", "concept")), 0);
    submitted.mergeAnnotations(List.of(annotation("style", "define")), 2);
    var resolved = (ObservationImpl) Observation.forTransport(submitted);
    resolved.mergeAnnotations(List.of(annotation("style", "model"), annotation("units", "model")), 1);
    resolved.mergeAnnotations(List.of(annotation("units", "concept"), annotation("label", "concept")), 0);
    submitted.mergeAnnotations(Observation.forTransport(resolved));

    assertEquals("define", value(submitted, "style"));
    assertEquals("model", value(submitted, "units"));
    assertEquals("concept", value(submitted, "label"));
    assertEquals(3, submitted.getAnnotations().size());
    resolved.getAnnotations().getFirst().put("value", "changed");
    assertEquals("define", value(submitted, "style"));
  }

  @Test
  void interleavedPredicatesAreVisitedInReverseSourceOrderAfterMainObservable() {
    var main = syntax("one:Main", 40);
    var leftTrait = syntax("two:Left", 10);
    var role = syntax("three:Role", 20);
    var rightTrait = syntax("four:Right", 30);
    main.getTraits().addAll(List.of(leftTrait, rightTrait));
    main.getRoles().add(role);
    var concepts = Map.of(
        "one:Main", concept("main"), "two:Left", concept("left"),
        "three:Role", concept("role"), "four:Right", concept("right"));
    var result = AnnotationCollector.collect(main, concepts::get);
    assertEquals("left", result.getFirst().get("value"));
    main.getAnnotations().add(annotation("style", "syntax"));
    assertEquals("left", AnnotationCollector.collect(main, concepts::get).getFirst().get("value"));
    assertEquals("main", concepts.get("one:Main").getAnnotations().iterator().next().get("value"));
  }

  @Test
  void attributionCopiesAnnotationStateWithoutSharingMutableAnnotations() {
    var observation = new ObservationImpl();
    observation.mergeAnnotations(List.of(annotation("style", "define")), 2);
    var observable = (Observable) Proxy.newProxyInstance(Observable.class.getClassLoader(),
        new Class<?>[] {Observable.class}, (proxy, method, args) ->
            method.getName().equals("getAnnotations") ? List.of(annotation("label", "new")) : null);
    var copy = observation.copyForAttribution(observable);
    copy.getAnnotations().getFirst().put("value", "changed");
    assertEquals("define", value(observation, "style"));
    assertEquals(observation.getAnnotationPriorities().get("style"), copy.getAnnotationPriorities().get("style"));
    assertEquals("new", value(copy, "label"));
    assertEquals(1, observation.getAnnotations().size());
  }

  private static KimConceptImpl syntax(String name, int offset) {
    var result = new KimConceptImpl();
    result.setName(name);
    result.setOffsetInDocument(offset);
    return result;
  }

  private static Concept concept(String value) {
    return (Concept) Proxy.newProxyInstance(Concept.class.getClassLoader(),
        new Class<?>[] {Concept.class}, (proxy, method, args) ->
            method.getName().equals("getAnnotations") ? List.of(annotation("style", value)) : null);
  }

  private static Annotation annotation(String name, String value) {
    return Annotation.of(name, "value", value);
  }

  private static Object value(Observation observation, String name) {
    return observation.getAnnotations().stream().filter(a -> name.equals(a.getName()))
        .findFirst().orElseThrow().get("value");
  }
}
