package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Codelist;

/** Published lists contain approved mappings only. Review history is separate and immutable. */
public record AuthorityCodelistResponse(long revision, Map<String, Codelist> codelists,
    List<Proposal> proposals) {
  public enum Status { PENDING, ACCEPTED, REDIRECTED, REJECTED, DELETED, MANAGED }
  public record Proposal(String id, String namespace, String alias, String identity,
      Status status, String approvedAlias, String message, String submittedBy, String reviewedBy,
      long revision, List<AuthorityCodelistRequest.Source> sources) {}
}
