package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.io.Serializable;

/** Opaque authenticated handle to one retained accepted semantic actor, not a graph capability. */
public record SemanticCycleBinding(String contractVersion, String reasonerServiceId,
    String reasonerSessionId, long knowledgeRevision, String semanticFingerprint,
    String leaseId) implements Serializable {
  public static final String VERSION="semantic-cycle-v1";
  public SemanticCycleBinding {
    if (!VERSION.equals(contractVersion) || knowledgeRevision<0)
      throw new IllegalArgumentException("Invalid semantic cycle version/revision");
    for(var value:new String[]{reasonerServiceId,reasonerSessionId,semanticFingerprint,leaseId})
      if(value==null || value.isBlank())throw new IllegalArgumentException("Incomplete semantic cycle binding");
  }
}
