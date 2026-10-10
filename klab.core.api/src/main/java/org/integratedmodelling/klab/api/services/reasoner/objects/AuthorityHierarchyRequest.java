package org.integratedmodelling.klab.api.services.reasoner.objects;

/** Explicit demand for complete ancestry of a canonical identity in an exact local binding. */
public record AuthorityHierarchyRequest(String authority, String identity) {
  public AuthorityHierarchyRequest { AuthorityIdentitySyntax.encode(authority, identity); }
}
