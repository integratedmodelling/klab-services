package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityRequest;

/** Exact saved source occurrence bound to one typed contextualizer input, not a scalar dependency. */
public record MemberQualityInputPlan(String modelUrn,String namespace,String sourceVersion,String sourceHash,
    int dependencyIndex,String port,String sourceObservable,MemberQualityRequest request)
    implements java.io.Serializable {
  public void validate() {
    if(modelUrn==null || modelUrn.isBlank() || namespace==null || namespace.isBlank()
        || sourceVersion==null || sourceHash==null || !sourceHash.matches("[0-9a-fA-F]{64}")
        || dependencyIndex<0 || port==null || port.isBlank() || sourceObservable==null || sourceObservable.isBlank()
        || request==null)
      throw new IllegalStateException("Member quality input lacks exact saved source provenance");
    request.validate();
  }
}
