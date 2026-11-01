package org.integratedmodelling.klab.authority.taxa;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.services.Authority;
@org.integratedmodelling.klab.api.services.reasoner.Authority(urn="klab.authority.taxa", version="1.0.0", embeddable=true, searchable=true, subAuthorities={"SPECIES"})
public class TaxaAuthority implements Authority {
  public String getUrn() { return "klab.authority.taxa"; }
  public CachePolicy getCachePolicy() { return new CachePolicy("fixture-1",Long.MAX_VALUE,86400,86400); }
  public Set<String> getSemanticBoundaries() { return Set.of("SPECIES"); }
  public String configure(ConfigurationRequest request) { throw new AssertionError("Capture must not configure live providers"); }
  public Identity resolveIdentity(String configuration, String id) { throw new AssertionError("Capture must not fetch identities"); }
  public List<Identity> search(String query, String filter, String id) { throw new AssertionError("Capture must not search providers"); }
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
