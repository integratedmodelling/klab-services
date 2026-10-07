package org.integratedmodelling.klab.api.services.reasoner.objects;

/** Commands use worldview-local authority and namespace names; revisions protect review decisions. */
public record AuthorityCodelistRequest(
    Operation operation, String authority, String namespace, String alias, String identity,
    String proposalId, long expectedRevision, Decision decision, String approvedAlias,
    String message, Source source) {
  public enum Operation { LIST, SUBMIT, REVIEW, CREATE, UPDATE, DELETE }
  public enum Decision { ACCEPT, REJECT }
  /** Hash binds a marker to the parsed source; offsets alone are not stable identities. */
  public record Source(String document, String sourceHash, int offset, int length) {}
  public AuthorityCodelistRequest {
    if (operation == null || authority == null || authority.isBlank())
      throw new IllegalArgumentException("Operation and authority are required");
  }
}
