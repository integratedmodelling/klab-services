package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.List;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Configuration discovery entry point; the reasoner-backed matcher is intentionally deferred.
 *
 * <p>The design in docs/CONSEQUENCE_ENGINE.md requires a complete match of the authored
 * {@code emerges from} expression plus a valid {@code applies to} target, with ingredient identity,
 * support, placement and revision evidence. The legacy emergentResolvables implementation returns
 * an empty map without evaluating these conditions, so calling it would misreport unavailable
 * discovery as a successful no-match. Do not submit configurations until that evidence is available.
 */
public final class ConfigurationDiscoveryHandler implements ConsequenceEngine.Handler {
  @Override
  public ConsequenceEngine.Result evaluate(
      Consequence.Cause cause, KnowledgeGraph.Commit changes, ServiceContextScope scope) {
    return new ConsequenceEngine.Result(Consequence.Outcome.DEFERRED,
        "Configuration discovery requires reasoner support for emerges from matches and applies to targets",
        List.of());
  }
}
