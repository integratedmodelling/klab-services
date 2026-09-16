package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

/** Context-dependent adaptation of a raw observable to an unresolved observation. */
public final class ObservableSubmission {
  private ObservableSubmission() {}

  public static Observation.NaiveBuilder builder(Observable observable, ContextScope scope) {
    if (scope == null) throw new IllegalArgumentException("Select a digital twin before observing.");
    if (observable == null || observable.getSemantics() == null || observable.is(SemanticType.NOTHING))
      throw new IllegalArgumentException("A valid observable is required.");
    Geometry geometry;
    if (SemanticType.isDependent(observable.getSemantics().getType())) {
      var context = scope.getContextObservation();
      if (context == null || context.isEmpty())
        throw new IllegalArgumentException("A context observation is required to observe a quality or process.");
      geometry = context.getGeometry();
    } else if (SemanticType.isEnumerableSubstantial(observable.getSemantics().getType())
        || observable.getSemantics().isCollective()) {
      var observer = scope.getObserver();
      if (observer == null || observer.isEmpty())
        throw new IllegalArgumentException("Select an observer before observing a collective.");
      geometry = observer.geometry(Observation.GeometryRelationship.PERCEIVES);
      if (!observable.getSemantics().isCollective()) {
        observable = observable.builder(scope).collective(true).buildObservable();
        if (observable == null || observable.getSemantics() == null
            || observable.is(SemanticType.NOTHING) || !observable.getSemantics().isCollective())
          throw new IllegalArgumentException("The Reasoner could not make this observable collective.");
      }
    } else {
      throw new IllegalArgumentException("This observable type cannot yet be submitted directly.");
    }
    if (geometry == null || geometry.isEmpty())
      throw new IllegalArgumentException("The selected context or observer has no observation geometry.");
    var builder = new Observation.NaiveBuilder(observable, scope);
    builder.geometry(geometry);
    return builder;
  }
}
