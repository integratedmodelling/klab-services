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
    if (observable.is(SemanticType.PREDICATE)) {
      var reasoner = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
      if (reasoner == null) throw new IllegalArgumentException("A Reasoner is required to observe a predicate.");
      var inherent = reasoner.inherent(observable.getSemantics());
      if (inherent == null)
        throw new IllegalArgumentException("A predicate requires an inherent before observation.");
      if (inherent.is(SemanticType.QUALITY)) {
        var context = scope.getContextObservation();
        if (context == null || context.isEmpty())
          throw new IllegalArgumentException("A context observation is required for a predicate over a quality.");
        geometry = context.getGeometry();
      } else {
        if (!SemanticType.isEnumerableSubstantial(inherent.getType()))
          throw new IllegalArgumentException("A predicate requires a substantial or quality inherent before observation.");
        var observer = scope.getObserver();
        if (observer == null || observer.isEmpty())
          throw new IllegalArgumentException("Select an observer before observing a predicate over a collective.");
        geometry = observer.geometry(Observation.GeometryRelationship.PERCEIVES);
        if (!inherent.isCollective()) {
          observable = observable.builder(scope).of(inherent.collective()).buildObservable();
          var promoted = observable == null || observable.getSemantics() == null ? null
              : reasoner.inherent(observable.getSemantics());
          if (observable == null || observable.is(SemanticType.NOTHING)
              || !observable.is(SemanticType.PREDICATE) || promoted == null || !promoted.isCollective())
            throw new IllegalArgumentException("The Reasoner could not make the predicate's inherent collective.");
        }
      }
    } else if (SemanticType.isDependent(observable.getSemantics().getType())) {
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
