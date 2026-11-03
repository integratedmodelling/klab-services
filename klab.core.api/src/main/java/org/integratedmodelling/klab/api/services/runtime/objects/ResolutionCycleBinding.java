package org.integratedmodelling.klab.api.services.runtime.objects;

import java.io.Serializable;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;

/**
 * Portable identity of a frozen resolution/contextualization cycle. This envelope grants no
 * authority or ownership: Runtime and Reasoner must validate it against their retained registries.
 * A null semantic binding explicitly means no semantic view was captured; it must never authorize
 * a fallback to whatever generation happens to be current later in this cycle.
 */
public record ResolutionCycleBinding(int version, String cycleId, String contextId,
    String runtimeServiceId, boolean consequencesEnabled, SemanticCycleBinding semanticBinding)
    implements Serializable {
  public ResolutionCycleBinding {
    if(version!=1 || cycleId==null || cycleId.isBlank() || contextId==null || contextId.isBlank()
        || runtimeServiceId==null || runtimeServiceId.isBlank())
      throw new IllegalArgumentException("Invalid resolution cycle binding");
  }
}
