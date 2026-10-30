package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.knowledge.SingularContextBinding;

/** Explicit native-member result request. This does not request a container-wide quality. */
public record MemberQualityRequest(SingularContextBinding container, String members, String quality,
    String agentName, java.util.List<org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint> resolutionConstraints)
    implements java.io.Serializable {
  public MemberQualityRequest(SingularContextBinding container,String members,String quality) {
    this(container,members,quality,null,java.util.List.of());
  }
  public MemberQualityRequest {
    resolutionConstraints=resolutionConstraints==null?java.util.List.of():java.util.List.copyOf(resolutionConstraints);
  }
  public void validate() {
    if (container == null || members == null || members.isBlank() || quality == null || quality.isBlank()
        || !quality.equals(container.childObservable()))
      throw new IllegalArgumentException("A member-quality request requires a container witness and closed semantics");
  }
}
