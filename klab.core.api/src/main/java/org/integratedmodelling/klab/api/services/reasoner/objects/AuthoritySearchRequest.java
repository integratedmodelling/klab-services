package org.integratedmodelling.klab.api.services.reasoner.objects;

/** Search a configured local binding. The optional filter is a configured codelist namespace or an advertised search-only subdivision.
 * Offset/limit page the provider-returned list, not an exhaustive remote catalog. Providers may
 * change ordering between requests. Query length is 1..256, limit 1..100, offset 0..10000. */
public record AuthoritySearchRequest(String authority, String query, String filter, int offset, int limit) {
  public AuthoritySearchRequest {
    AuthorityIdentitySyntax.validateAuthority(authority);
    if (query == null || query.isBlank() || query.length() > 256)
      throw new IllegalArgumentException("Authority search requires 1..256 query characters");
    if (offset < 0 || offset > 10000 || limit < 1 || limit > 100)
      throw new IllegalArgumentException("Authority search offset or limit is outside its bounds");
    if (filter != null && (filter.isBlank() || filter.length() > 128))
      throw new IllegalArgumentException("Invalid authority search filter");
  }
}
