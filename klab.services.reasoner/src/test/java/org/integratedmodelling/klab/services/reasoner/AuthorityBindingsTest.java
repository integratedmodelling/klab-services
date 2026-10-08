package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.junit.jupiter.api.Test;

class AuthorityBindingsTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

  private static final class StrictProvider implements Authority {
    int configurations, releases;
    @Override public String getUrn() { return "test.authority"; }
    @Override public Capabilities getCapabilities() { return null; }
    @Override public String configure(ConfigurationRequest supplied) {
      if (!supplied.parameters().keySet().equals(java.util.Set.of("urn", "datasetKey")))
        throw new IllegalArgumentException("Unknown provider parameter");
      assertEquals(312578, supplied.parameters().get("datasetKey"));
      assertEquals("worldview", supplied.worldview());
      assertEquals("TAXA", supplied.name());
      assertEquals("life:TaxonomicIdentity", supplied.rootIdentity());
      configurations++;
      return "provider-session";
    }
    @Override public void releaseConfiguration(String id) {
      assertEquals("provider-session", id); releases++;
    }
    @Override public Map<String, org.integratedmodelling.klab.api.knowledge.Codelist> getCodelists(String id) {
      assertEquals("provider-session", id);
      return Map.of("species", new org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl());
    }
    @Override public Identity resolveIdentity(String configuration, String code) { return null; }
    @Override public Authority subAuthority(String catalog) { return this; }
    @Override public java.util.List<Identity> search(String query, String filter, String configuration) {
      return java.util.List.of();
    }
  }

  @Test void reasonerCodelistMappingsNeverReachStrictProviders() {
    for (boolean cached : new boolean[] {false, true}) {
      var provider = new StrictProvider();
      var bindings = cached ? new AuthorityBindings(directory) : new AuthorityBindings();
      var request = new Authority.ConfigurationRequest("worldview", "TAXA", "life:TaxonomicIdentity",
          Map.of("urn", "test.authority", "datasetKey", 312578,
              "codelists", Map.of("species", "taxonomy.species")));
      var id = bindings.configure(request, provider);
      assertEquals(request, bindings.get("TAXA").request());
      assertEquals(java.util.Set.of("taxonomy.species"), bindings.codelists("TAXA").namespaces());
      assertEquals(id, bindings.configure(request, provider));
      assertEquals(1, provider.configurations);
      bindings.clear();
      assertEquals(1, provider.releases);
    }
  }

  @Test
  void loadedInstanceIdsNormalizeToTheSamePersistentWorldviewName() {
    var worldview = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    worldview.setUrn("imod");
    worldview.setWorldviewId("first-instance");
    var parameters = Map.<String, Object>of("urn", "test.authority");
    var first = AuthorityBindings.forWorldview(
        new Authority.ConfigurationRequest("first-instance", "TAXA", "biology:Identity", parameters),
        worldview);
    worldview.setWorldviewId("second-instance");
    var second = AuthorityBindings.forWorldview(
        new Authority.ConfigurationRequest("second-instance", "TAXA", "biology:Identity", parameters),
        worldview);
    assertEquals(first, second);
    assertEquals("imod", first.worldview());
    assertEquals(first, AuthorityBindings.forWorldview(first, worldview));
    assertThrows(KlabValidationException.class, () -> AuthorityBindings.forWorldview(
        new Authority.ConfigurationRequest("another-worldview", "TAXA", "biology:Identity", parameters),
        worldview));
  }

  private Authority.ConfigurationRequest request(String name, String root) {
    return new Authority.ConfigurationRequest("worldview", name, root,
        Map.of("urn", "test.authority", "catalog", name));
  }

  @Test void documentationResolvesAliasesAndHonorsExactBindingsAndDiagnostics() throws Exception {
    var provider = mock(Authority.class);
    var capabilities = mock(Authority.Capabilities.class);
    when(provider.getCapabilities()).thenReturn(capabilities);
    when(capabilities.areSubAuthoritiesSearchFilters()).thenReturn(true);
    when(capabilities.getSubAuthorities()).thenReturn(java.util.List.of(
        org.integratedmodelling.klab.api.collections.Pair.of("SPECIES", "Species")));
    var bindings = new AuthorityBindings();
    var request = request("TAXA", "taxa:Identity");
    when(provider.configure(request)).thenReturn("base");
    bindings.configure(request, provider);
    var identity = mock(Authority.Identity.class);
    var url = java.net.URI.create("https://example.org/taxon.md").toURL();
    when(identity.getDocumentation()).thenReturn(Map.of("text/markdown", url));
    when(provider.resolveIdentity("base", "alias")).thenReturn(identity);
    assertEquals(url.toExternalForm(), bindings.documentation("TAXA.SPECIES", "alias")
        .get("text/markdown").toExternalForm());
    assertThrows(java.util.NoSuchElementException.class, () -> bindings.documentation("TAXA.PHLYUM", "alias"));
    assertThrows(IllegalArgumentException.class, () -> bindings.documentation("TAXA", " "));
    var dotted = request("TAXA.SPECIES", "taxa:Species");
    when(provider.configure(dotted)).thenReturn("exact");
    when(provider.resolveIdentity("exact", "alias")).thenReturn(identity);
    bindings.configure(dotted, provider);
    bindings.documentation("TAXA.SPECIES", "alias");
    verify(provider).resolveIdentity("exact", "alias");
    when(identity.getNotifications()).thenReturn(java.util.List.of(
        org.integratedmodelling.klab.api.services.runtime.Notification.error("Unknown taxon")));
    assertThrows(java.util.NoSuchElementException.class, () -> bindings.documentation("TAXA", "alias"));
    when(identity.getNotifications()).thenReturn(java.util.List.of());
    when(identity.getDocumentation()).thenReturn(null);
    assertTrue(bindings.documentation("TAXA", "alias").isEmpty());
    bindings.releaseNamespace("taxa");
    assertThrows(java.util.NoSuchElementException.class, () -> bindings.documentation("TAXA", "alias"));
  }

  @Test
  void oneProviderHasIndependentBridgesWithWorldviewAnchors() {
    var provider = mock(Authority.class);
    var first = request("SPECIES", "taxa:Species");
    var second = request("HABITATS", "habitats:Habitat");
    when(provider.configure(first)).thenReturn("species-id");
    when(provider.configure(second)).thenReturn("habitats-id");
    var bindings = new AuthorityBindings();
    assertEquals("species-id", bindings.configure(first, provider));
    assertEquals("habitats-id", bindings.configure(second, provider));
    assertEquals("species-id", bindings.configure(first, provider));
    verify(provider, times(1)).configure(first);
    assertEquals("taxa:Species", bindings.get("SPECIES").request().rootIdentity());
    assertThrows(KlabValidationException.class,
        () -> bindings.configure(request("SPECIES", "taxa:Other"), provider));
    bindings.releaseNamespace("taxa");
    verify(provider).releaseConfiguration("species-id");
    assertNull(bindings.get("SPECIES"));
    assertNotNull(bindings.get("HABITATS"));
    bindings.clear();
    verify(provider).releaseConfiguration("habitats-id");
  }

  @Test
  void failedOrIncompatibleConfigurationsAreNotPublished() {
    var provider = mock(Authority.class);
    var bindings = new AuthorityBindings();
    var request = request("SPECIES", "taxa:Species");
    when(provider.configure(request)).thenReturn(" ");
    assertThrows(KlabValidationException.class, () -> bindings.configure(request, provider));
    assertNull(bindings.get("SPECIES"));
    var capabilities = mock(Authority.Capabilities.class);
    when(provider.getCapabilities()).thenReturn(capabilities);
    when(capabilities.getWorldview()).thenReturn("another-worldview");
    clearInvocations(provider);
    assertThrows(KlabValidationException.class, () -> bindings.configure(request, provider));
    verify(provider, never()).configure(any());
  }
}
