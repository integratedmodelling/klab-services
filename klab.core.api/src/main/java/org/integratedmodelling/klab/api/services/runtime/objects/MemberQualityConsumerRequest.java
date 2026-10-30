package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.lang.ServiceCall;

/** Read-only capability inquiry about an installed, exactly named typed input. */
public record MemberQualityConsumerRequest(ServiceCall call,String port) implements java.io.Serializable {
  public void validate() {
    if(call==null || call.getUrn()==null || port==null || port.isBlank())
      throw new IllegalArgumentException("A contextualizer call and named collection input are required");
  }
}
