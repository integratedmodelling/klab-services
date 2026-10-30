package org.integratedmodelling.common.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

/** Exact observer-neutral comparison of existing observations at one KG insertion point. */
public final class ObservationPerspectives {
  private ObservationPerspectives() {}

  /** Uses the ordinary visible-observation query; no resolution, ranking or observer grouping. */
  public static <T extends Observation> Collection<T> find(ContextScope scope, Observable observable) {
    Objects.requireNonNull(scope, "Perspective query requires a scope");
    var requested = key(scope, Objects.requireNonNull(observable, "Perspective query requires an observable"));
    var result = new ArrayList<T>();
    for (var observation : scope.getObservations()) {
      if (observation == null || observation.getObservable() == null) continue;
      var candidate = observation.getObservable();
      if (requested.equals(key(scope, candidate))) {
        // The API's type parameter is an existing observation convention, not a conversion.
        @SuppressWarnings("unchecked") T perspective = (T) observation;
        result.add(perspective);
      }
    }
    return result;
  }

  private record Key(String observable, String unit, String range, String currency,
      String contextualDimensions, org.integratedmodelling.klab.api.knowledge.Contextualization activity) {}

  private static Key key(ContextScope scope, Observable observable) {
    // Reuse portable mediator definitions/distribution flags rather than Java mediator identity.
    var portable = org.integratedmodelling.klab.api.data.StorageScan.semantics(observable);
    var result = observable.builder(scope).withObserverSemantics(null)
        .withUnit((org.integratedmodelling.klab.api.data.mediation.Unit) null)
        .withCurrency((org.integratedmodelling.klab.api.data.mediation.Currency) null)
        .buildObservable();
    if (result == null || result.getSemantics() == null || result.getUrn() == null
        || result.getObserverSemantics() != null || result.getUnit() != null || result.getCurrency() != null)
      throw new IllegalStateException("Cannot construct canonical observer-neutral observable");
    // Clear mediators only during canonical construction: the original portable constraints
    // remain in the key. This avoids UnitImpl Object.toString affecting otherwise equal URNs.
    // Activity is retained from the original because the existing builder does not preserve it.
    return new Key(result.getUrn(), portable.unit(), portable.range(), portable.currency(),
        portable.contextualDimensions(), observable.getContextualization());
  }
}
