package org.integratedmodelling.klab.authority.taxa;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.services.Authority;
@org.integratedmodelling.klab.api.services.reasoner.Authority(urn="klab.authority.taxa", version="1.0.0", embeddable=true, searchable=true, subAuthorities={"SPECIES"})
public class TaxaAuthority implements Authority {
  public String getUrn() { return "klab.authority.taxa"; }
  public CachePolicy getCachePolicy() { return new CachePolicy("fixture-1",Long.MAX_VALUE,86400,86400); }
  public Set<String> getSemanticBoundaries() { return Set.of("SPECIES"); }
  private String configured;
  public String configure(ConfigurationRequest request) { return configured = "live:" + request.name(); }
  public void releaseConfiguration(String id) { if (!Objects.equals(id, configured)) throw new IllegalStateException(); configured = null; }
  public Identity resolveIdentity(String configuration, String id) {
    if (configured == null || !configured.equals(configuration)) throw new IllegalStateException("Unconfigured fixture");
    return new org.integratedmodelling.klab.services.reasoner.internal.CachedAuthority.StoredIdentity(
        id, "taxon_" + id, "TAXA", null, List.of(), List.of(), "Live result", "Live " + id,
        1, "TAXA:" + id, Map.of(), Set.of("SPECIES"), HierarchyStatus.COMPLETE);
  }
  public List<Identity> search(String query, String filter, String id) { return List.of(resolveIdentity(id, query)); }
  public Authority subAuthority(String catalog) { return this; }
  public Capabilities getCapabilities() { return new FixtureCapabilities(); }
  public static class FixtureCapabilities implements Capabilities {
    public String getDescription() { return "Portable recorded public-provider fixture"; }
    public boolean isSearchable() { return true; } public boolean isFuzzy() { return false; }
    public boolean areSubAuthoritiesSearchFilters() { return true; }
    public List<Pair<String,String>> getSubAuthorities() { return List.of(Pair.of("SPECIES","species")); }
    public List<String> getDocumentationFormats() { return List.of(); } public String getWorldview() { return null; }
  }
}
