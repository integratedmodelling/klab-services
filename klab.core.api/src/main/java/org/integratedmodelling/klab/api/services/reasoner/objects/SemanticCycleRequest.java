package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.io.Serializable;

/** Ownership tuple is checked against the authenticated scope, never accepted as authorization. */
public record SemanticCycleRequest(String runtimeServiceId,String contextId,String cycleId,
    SemanticCycleBinding binding) implements Serializable {
  public SemanticCycleRequest {
    for(var value:new String[]{runtimeServiceId,contextId,cycleId})
      if(value==null || value.isBlank())throw new IllegalArgumentException("Incomplete cycle owner");
  }
}
