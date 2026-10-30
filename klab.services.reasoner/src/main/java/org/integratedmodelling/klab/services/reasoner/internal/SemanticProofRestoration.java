package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.List;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

/** Restore canonical expression registry entries without enabling unseen interactive authority answers. */
public final class SemanticProofRestoration {
  private final ThreadLocal<Boolean> restoring = ThreadLocal.withInitial(() -> false);

  public boolean interactiveAllowed() { return !restoring.get(); }

  public String restore(List<String> obligations, Function<String, Concept> compiler) {
    boolean previous = restoring.get();
    restoring.set(true);
    try {
      for (var term : obligations) {
        var concept = compiler.apply(term);
        if (concept == null || concept.is(SemanticType.NOTHING))
          return "Semantic obligation cannot be restored from accepted content: " + term;
        if (!term.equals(concept.getUrn()))
          return "Semantic obligation is not a canonical Concept expression: " + term;
      }
      return null;
    } catch (RuntimeException unavailable) {
      // Captured-provider misses and unavailable compilation remain UNKNOWN, never independence.
      return "Semantic obligation restoration unavailable: " + unavailable.getClass().getSimpleName();
    } finally {
      if (previous) restoring.set(true); else restoring.remove();
    }
  }
}
