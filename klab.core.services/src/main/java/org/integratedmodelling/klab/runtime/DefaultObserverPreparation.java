package org.integratedmodelling.klab.runtime;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.DefaultObserver;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Explicit preparation boundary; does not mutate a shared connection's observer or start behaviors. */
public final class DefaultObserverPreparation {
  private DefaultObserverPreparation() {}

  /**
   * Query the twin by the authenticated user's stable identity, submitting a missing agent through
   * normal resolution. The caller supplies the agent's geometry and a user-specific root context.
   * A null result means no default is configured. Failures never silently select another identity.
   */
  public static CompletableFuture<Observation> prepare(
      Worldview worldview, ContextScope scope, Geometry geometry) {
    try {
      if (worldview != null) worldview.getNotifications().forEach(scope::warn);
      String semantics = DefaultObserver.select(worldview == null ? null : worldview.getMetadata(),
          scope.getUser().getGroups(), message -> scope.warn(message));
      if (semantics == null) return CompletableFuture.completedFuture(null);
      if (scope.getContextObservation() != null || scope.getObserver() != null) {
        throw new IllegalArgumentException("Prepare the default observer in an unfocused context without an observer");
      }
      String identity = DefaultObserver.identity(scope.getUser());
      var observable = scope.getService(Reasoner.class).resolveObservable(semantics);
      if (observable == null || !observable.getSemantics().is(SemanticType.AGENT)
          || observable.getSemantics().is(SemanticType.ABSTRACT)
          || observable.getSemantics().isCollective()) {
        throw new IllegalArgumentException("Default observer must be a concrete singular agent: " + semantics);
      }
      var existing = scope.getDigitalTwin().getKnowledgeGraph().getAsset(
          ObservationImpl.catalogUrn(scope.getId(), identity), scope, Observation.class);
      if (existing != null) {
        if (existing.isEmpty() || existing.getObservable() == null
            || !Objects.equals(existing.getObservable().getUrn(), observable.getUrn())) {
          throw new IllegalStateException("Stored default observer conflicts with selected semantics for " + identity);
        }
        return CompletableFuture.completedFuture(existing);
      }
      if (geometry == null || geometry.isEmpty()) {
        throw new IllegalArgumentException("A valid geometry is required to instantiate the default observer");
      }
      return scope.observation(observable).identity(Urn.of(identity)).geometry(geometry)
          .metadata(DefaultObserver.AUTOMATIC, true).submit();
    } catch (RuntimeException e) {
      scope.warn("Cannot prepare default observer: " + e.getMessage());
      return CompletableFuture.failedFuture(e);
    }
  }
}
