package org.integratedmodelling.klab.modeler;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.function.BiFunction;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;

class ObservableSubmissionTest {
  @Test void qualityUsesMandatoryContextGeometryAndRemainsUnresolved() {
    var geometry = geometry(); var context = new ObservationImpl(); context.setGeometry(geometry);
    var quality = observable(SemanticType.QUALITY, false);
    var observation = ObservableSubmission.builder(quality, scope(context, null)).make();
    assertSame(geometry, observation.getGeometry()); assertSame(quality, observation.getObservable());
    assertEquals(Observation.UNASSIGNED_ID, observation.getId()); assertFalse(observation.isEmpty());
    assertThrows(IllegalArgumentException.class, () -> ObservableSubmission.builder(quality, scope(null, null)));
  }

  @Test void collectiveUsesPerceivedGeometryWithoutFallingBackToObserverLocation() {
    var observer = new ObservationImpl(); observer.setGeometry(geometry());
    observer.setObservable(observable(SemanticType.AGENT, false));
    var collective = observable(SemanticType.SUBJECT, true);
    assertThrows(IllegalArgumentException.class, () -> ObservableSubmission.builder(collective, scope(null, observer)));
    var extent = geometry(); observer.setPerceivedGeometry(extent);
    assertSame(extent, ObservableSubmission.builder(collective, scope(null, observer)).make().getGeometry());
    assertThrows(IllegalArgumentException.class, () -> ObservableSubmission.builder(collective, scope(null, null)));
  }

  @Test void substantialIsConvertedWithoutMutatingTheSelectedObservable() {
    var collective = observable(SemanticType.SUBJECT, true);
    var original = new ObservableImpl() {
      @Override public Observable.Builder builder(Scope scope) {
        return proxy(Observable.Builder.class, (method, args) -> {
          if (method.equals("collective")) { assertEquals(true, args[0]); return null; }
          if (method.equals("buildObservable")) return collective;
          return null;
        });
      }
    };
    original.setSemantics(observable(SemanticType.SUBJECT, false).getSemantics());
    var observer = new ObservationImpl(); observer.setObservable(observable(SemanticType.AGENT, false));
    var extent = geometry(); observer.setPerceivedGeometry(extent);
    var result = ObservableSubmission.builder(original, scope(null, observer)).make();
    assertSame(collective, result.getObservable()); assertSame(extent, result.getGeometry());
    assertFalse(original.getSemantics().isCollective()); assertFalse(result.isEmpty());
  }

  @Test void predicatePromotesOnlyItsInherentAndRequiresObserverGeometry() {
    var tree = observable(SemanticType.SUBJECT, false).getSemantics();
    var promoted = observable(SemanticType.PREDICATE, false);
    var original = new ObservableImpl() {
      @Override public Observable.Builder builder(Scope scope) {
        return proxy(Observable.Builder.class, (method, args) -> {
          if (method.equals("of")) assertTrue(((org.integratedmodelling.klab.api.knowledge.Concept) args[0]).isCollective());
          if (method.equals("collective")) fail("The predicate itself must not be promoted");
          return method.equals("buildObservable") ? promoted : null;
        });
      }
    };
    original.setSemantics(observable(SemanticType.PREDICATE, false).getSemantics());
    var reasoner = proxy(org.integratedmodelling.klab.api.services.Reasoner.class, (method, args) ->
        method.equals("inherent") ? args[0] == promoted.getSemantics() ? tree.collective() : tree : null);
    var observer = new ObservationImpl(); observer.setObservable(observable(SemanticType.AGENT, false));
    var extent = geometry(); observer.setPerceivedGeometry(extent);
    var scope = proxy(ContextScope.class, (method, args) -> switch (method) {
      case "getObserver" -> observer; case "getService" -> reasoner; default -> null;
    });
    var result = ObservableSubmission.builder(original, scope).make();
    assertSame(promoted, result.getObservable()); assertSame(extent, result.getGeometry());
    assertFalse(tree.isCollective()); assertFalse(promoted.getSemantics().isCollective());
    assertEquals(Observation.UNASSIGNED_ID, result.getId());
    assertSame(promoted, ObservableSubmission.builder(promoted, scope).make().getObservable());
    var missing = proxy(ContextScope.class, (method, args) -> method.equals("getService") ? reasoner : null);
    assertThrows(IllegalArgumentException.class, () -> ObservableSubmission.builder(original, missing));
    var unqualified = proxy(ContextScope.class, (method, args) -> method.equals("getService")
        ? proxy(org.integratedmodelling.klab.api.services.Reasoner.class, (m, a) -> null) : observer);
    assertThrows(IllegalArgumentException.class, () -> ObservableSubmission.builder(original, unqualified));
  }

  @Test void predicateOverAQualityKeepsItsSemanticsAndUsesContextGeometry() {
    var predicate = observable(SemanticType.PREDICATE, false);
    var inherent = observable(SemanticType.QUALITY, false).getSemantics();
    var reasoner = proxy(org.integratedmodelling.klab.api.services.Reasoner.class,
        (method, args) -> method.equals("inherent") ? inherent : null);
    var context = new ObservationImpl(); var extent = geometry(); context.setGeometry(extent);
    var scope = proxy(ContextScope.class, (method, args) -> switch (method) {
      case "getService" -> reasoner; case "getContextObservation" -> context; default -> null;
    });
    var observation = ObservableSubmission.builder(predicate, scope).make();
    assertSame(predicate, observation.getObservable()); assertSame(extent, observation.getGeometry());
    var missing = proxy(ContextScope.class, (method, args) -> method.equals("getService") ? reasoner : null);
    assertThrows(IllegalArgumentException.class, () -> ObservableSubmission.builder(predicate, missing));
  }

  private static ObservableImpl observable(SemanticType type, boolean collective) {
    var concept = new ConceptImpl(); concept.setUrn("test:Concept");
    concept.setType(EnumSet.of(type, SemanticType.OBSERVABLE)); concept.setCollective(collective);
    var observable = new ObservableImpl(); observable.setSemantics(concept); observable.setUrn(concept.getUrn());
    return observable;
  }
  private static Geometry geometry() { return proxy(Geometry.class, (method, args) -> null); }
  private static ContextScope scope(Observation context, Observation observer) {
    return proxy(ContextScope.class, (method, args) -> switch(method) {
      case "getContextObservation" -> context; case "getObserver" -> observer; default -> null;
    });
  }
  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
      Object result = handler.apply(method.getName(), args);
      if (result != null) return result;
      if (method.getReturnType().isInstance(self)) return self;
      if (method.getReturnType() == boolean.class) return false;
      if (method.getReturnType() == long.class) return 0L;
      if (method.getReturnType() == int.class) return 0;
      return null;
    });
  }
}
