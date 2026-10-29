package org.integratedmodelling.klab.runtime;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Observer compatibility shared by knowledge reuse and model resolution. Instance IDs are not
 * semantic compatibility constraints; the selected instance supplies its Agent meaning only. */
public final class ObserverResolution {
  private ObserverResolution() {}

  /** Negative means incompatible; zero specific (or no preference); one absolute fallback. */
  public static int preference(Observable candidate, Observable requested, ContextScope scope) {
    if (candidate == null || requested == null) return -1;
    var stated = requested.getObserverSemantics();
    var provided = candidate.getObserverSemantics();
    if (stated != null) {
      if (provided == null) return -1;
      var reasoner = scope.getService(Reasoner.class);
      var receipt = reasoner == null ? null : reasoner.capabilities(scope);
      return receipt != null && reasoner.semanticDistance(candidate, requested, null, receipt.getKnowledgeRevision()) >= 0 ? 0 : -1;
    }
    var observer = scope.getObserver();
    if (observer == null) return 0;
    if (provided == null) return 1;
    if (observer.getObservable() == null || !provided.is(SemanticType.AGENT)
        || !observer.getObservable().is(SemanticType.AGENT)) return -1;
    ObserverSemanticUse.semanticComparison(scope,provided);
    var reasoner = scope.getService(Reasoner.class);
    var receipt = reasoner == null ? null : reasoner.capabilities(scope);
    return receipt != null
        && reasoner.semanticDistance(candidate, requested, null, receipt.getKnowledgeRevision()) >= 0
        && reasoner.semanticDistance(provided, observer.getObservable().asConcept(), null, receipt.getKnowledgeRevision()) >= 0 ? 0 : -1;
  }

  /** An implicit request may try specific models before using this committed absolute result. */
  public static boolean deferred(Observation existing, Observable requested, ContextScope scope) {
    return existing != null && existing.getId() > 0 && requested.getObserverSemantics() == null
        && (requested.is(SemanticType.QUALITY) || requested.is(SemanticType.PROCESS))
        && scope.getObserver() != null && existing.getObservable().getObserverSemantics() == null;
  }
}
