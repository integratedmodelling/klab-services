package org.integratedmodelling.klab.services.reasoner.internal;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.KimObservableVisitor;
import org.integratedmodelling.klab.runtime.language.KimOntologyVisitor;
import org.integratedmodelling.klab.runtime.language.KimObservationStrategyDocumentVisitor;

/** Resolves only the captured semantic trees; never falls back to live Resources or discovery. */
public final class CapturedSemanticSourceResolver {
  private final Map<String, KimConcept> concepts = new HashMap<>();
  private final Map<String, KimObservable> observables = new HashMap<>();

  public CapturedSemanticSourceResolver(Worldview input) {
    for (var ontology : input.getOntologies()) new KimOntologyVisitor(new KimOntologyVisitor.LenientValidator() {
      @Override public List<Notification> validateConcept(KimConcept concept, KimObservableVisitor.Context context) {
        collect(concept); return List.of();
      }
      @Override public List<Notification> validateObservable(KimObservable observable, KimObservableVisitor.Context context) {
        collect(observable); return List.of();
      }
    }, null).visit(ontology);
    for (var document : input.getObservationStrategies())
      new KimObservationStrategyDocumentVisitor(new KimObservationStrategyDocumentVisitor.LenientValidator() {
        @Override public List<Notification> validateConcept(KimConcept concept, KimObservableVisitor.Context context) {
          collect(concept); return List.of();
        }
        @Override public List<Notification> validateObservable(KimObservable observable, KimObservableVisitor.Context context) {
          collect(observable); return List.of();
        }
      }, null).visit(document);
  }
  private void collect(KimConcept concept) {
    if (concept.getUrn() != null) concepts.put(concept.getUrn(), concept);
    if (concept.getName() != null) concepts.put(concept.getName(), concept);
  }
  private void collect(KimObservable observable) {
    if (observable.getUrn() != null) observables.put(observable.getUrn(), observable);
  }
  public ResourcesService service() {
    return (ResourcesService)Proxy.newProxyInstance(ResourcesService.class.getClassLoader(),
        new Class<?>[]{ResourcesService.class}, (proxy, method, args) -> {
      if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
        case "toString" -> "Captured semantic source resolver";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> throw new IllegalStateException();
      };
      if (method.getName().equals("declareConcept") && args != null && args[0] instanceof String definition) {
        var value = concepts.get(definition);
        if (value != null) return JacksonConfiguration.newObjectMapper().convertValue(value, KimConcept.class);
      }
      if (method.getName().equals("declareObservable") && args != null && args[0] instanceof String definition) {
        var value = observables.get(definition);
        if (value != null) return JacksonConfiguration.newObjectMapper().convertValue(value, KimObservable.class);
      }
      throw new UnsupportedOperationException("CANDIDATE_ISOLATION_UNSUPPORTED: uncaptured source operation " + method.getName());
    });
  }
}
