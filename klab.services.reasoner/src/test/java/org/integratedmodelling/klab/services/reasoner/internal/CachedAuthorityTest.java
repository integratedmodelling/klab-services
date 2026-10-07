package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachedAuthorityTest {
  @TempDir Path directory;

  private static java.net.URL documentationUrl() {
    try { return java.net.URI.create("https://example.org/taxon.md").toURL(); }
    catch (java.net.MalformedURLException e) { throw new AssertionError(e); }
  }

  private Authority.ConfigurationRequest request(String worldview, String name, String root, int release) {
    return new Authority.ConfigurationRequest(worldview, name, root,
        Map.of("urn", "test.authority", "release", release));
  }
  private Authority.ConfigurationRequest request() { return request("wv", "TAXA", "bio:Root", 123); }

  private static final class Time extends Clock {
    long millis = 1000;
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    @Override public long millis() { return millis; }
  }
  private static final class Provider implements Authority {
    int configurations, lookups, searches, matches, releases;
    boolean fail, diagnostics;
    String codelistConfiguration;
    CachePolicy policy = new CachePolicy("1", 10, 10, 10);
    ConfigurationRequest request;
    @Override public String getUrn() { return "test.authority"; }
    @Override public CachePolicy getCachePolicy() { return policy; }
    @Override public String configure(ConfigurationRequest request) { this.request=request; return "session-" + ++configurations; }
    @Override public void releaseConfiguration(String id) { releases++; }
    private Identity identity(String id) {
      if (fail) throw new IllegalStateException("HTTP 429");
      var result = new CachedAuthority.StoredIdentity(id.equals("alias") ? "A" : id, "Concept", request.name(),
          request.rootIdentity(), List.of("Parent"), List.of(), "Description", "Label", 1, request.name()+":"+id,
          Map.of("text/markdown", documentationUrl()));
      if (!diagnostics) return result;
      return new Identity() {
        @Override public String getId() { return result.getId(); }
        @Override public String getConceptName() { return result.getConceptName(); }
        @Override public String getAuthorityName() { return result.getAuthorityName(); }
        @Override public String getBaseIdentity() { return result.getBaseIdentity(); }
        @Override public List<String> getParentIds() { return result.getParentIds(); }
        @Override public List<String> getParentRelationship() { return result.getParentRelationship(); }
        @Override public String getDescription() { return result.getDescription(); }
        @Override public String getLabel() { return result.getLabel(); }
        @Override public float getScore() { return result.getScore(); }
        @Override public String getLocator() { return result.getLocator(); }
        @Override public List<Notification> getNotifications() { return List.of(Notification.error("Unavailable")); }
      };
    }
    @Override public Identity resolveIdentity(String config, String id) { lookups++; return identity(id); }
    @Override public List<Identity> search(String query, String sub, String config) { searches++; return List.of(identity("A")); }
    @Override public Identity reconcile(String config, Map<String,String> fields) { matches++; return identity("A"); }
    @Override public Capabilities getCapabilities() { return null; }
    @Override public Map<String,Codelist> getCodelists() { return Map.of(); }
    @Override public Map<String,Codelist> getCodelists(String id) { codelistConfiguration = id; return Map.of(); }
    @Override public Authority subAuthority(String catalog) { return this; }
  }

  @Test void codelistsReceiveProviderConfigurationRatherThanCacheKey() {
    var provider = new Provider();
    var cached = new CachedAuthority(provider, directory);
    String id = cached.configure(request());
    cached.getCodelists(id);
    assertEquals("session-1", provider.codelistConfiguration);
    assertThrows(IllegalArgumentException.class, () -> cached.getCodelists("unconfigured"));
  }

  @Test void persistsIdentitiesSearchAndReconciliationAcrossRestartWithStableBridgeId() {
    var clock = new Time();
    var original = new Provider();
    var first = new CachedAuthority(original, directory, clock);
    String id = first.configure(request());
    assertTrue(id.contains("wv:TAXA:"));
    var identity = first.resolveIdentity(id, "alias");
    first.search("name", "SPECIES", id);
    first.reconcile(id, Map.of("scientificName", "Name", "kingdom", "Plantae"));
    first.releaseConfiguration(id);
    assertThrows(IllegalArgumentException.class, () -> first.resolveIdentity(id, "A"));
    var restored = new Provider();
    var next = new CachedAuthority(restored, directory, clock);
    String restoredId = next.configure(request());
    assertEquals(id, restoredId);
    assertEquals(identity.getId(), next.resolveIdentity(restoredId, "alias").getId());
    assertEquals("A", next.resolveIdentity(restoredId, "A").getId());
    assertEquals(List.of("Parent"), next.resolveIdentity(restoredId, "A").getParentIds());
    assertEquals("bio:Root", next.resolveIdentity(restoredId, "A").getBaseIdentity());
    assertEquals(Map.of("text/markdown", documentationUrl()), next.resolveIdentity(restoredId, "A").getDocumentation());
    assertEquals(identity.getDocumentation(), next.search("name", "SPECIES", restoredId).getFirst().getDocumentation());
    assertEquals(identity.getDocumentation(), next.reconcile(restoredId, Map.of("scientificName", "Name", "kingdom", "Plantae")).getDocumentation());
    assertEquals(1, next.search("name", "SPECIES", restoredId).size());
    assertEquals("A", next.reconcile(restoredId, new LinkedHashMap<>(Map.of("kingdom","Plantae","scientificName","Name"))).getId());
    assertEquals(0, restored.lookups + restored.searches + restored.matches);
    assertEquals(1, restored.configurations);
    assertEquals(1, original.releases);
  }

  @Test void expirationAndOptOutRefetchInsteadOfReusingStaleData() {
    var clock = new Time();
    var provider = new Provider();
    var cached = new CachedAuthority(provider, directory, clock);
    String id = cached.configure(request());
    cached.resolveIdentity(id, "A");
    clock.millis += 10001;
    cached.resolveIdentity(id, "A");
    assertEquals(2, provider.lookups);
    provider.policy = new Authority.CachePolicy("disabled", 0, 0, 0);
    var disabled = new CachedAuthority(provider, directory, clock);
    String disabledId = disabled.configure(request());
    disabled.resolveIdentity(disabledId, "A");
    disabled.resolveIdentity(disabledId, "A");
    assertEquals(4, provider.lookups);
  }

  @Test void immutableIdentitiesDoNotOverflowExpiryAndConcurrentMissesCallProviderOnce() throws Exception {
    var time = new Time();
    var source = new Provider();
    source.policy = new Authority.CachePolicy("immutable",Long.MAX_VALUE,10,10);
    var cache = new CachedAuthority(source,directory,time);
    String id = cache.configure(request());
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<java.util.concurrent.Future<Authority.Identity>>();
      for (int i=0;i<12;i++) futures.add(threads.submit(() -> cache.resolveIdentity(id,"A")));
      for (var future : futures) assertEquals("A",future.get().getId());
    }
    assertEquals(1,source.lookups);
    time.millis += 365L * 86400 * 1000;
    var restarted = new Provider();
    restarted.policy = source.policy;
    var resumed = new CachedAuthority(restarted,directory,time);
    resumed.resolveIdentity(resumed.configure(request()),"A");
    assertEquals(0,restarted.lookups);
  }

  @Test void isolatesWorldviewsBindingsRootsParametersAndProviderPolicyRevisions() {
    var provider = new Provider();
    var initial = new CachedAuthority(provider, directory);
    String original = initial.configure(request());
    initial.resolveIdentity(original, "A");
    for (var request : List.of(request("different", "TAXA", "bio:Root",123),
        request("wv", "OTHER", "bio:Root",123), request("wv", "TAXA", "bio:Other",123),
        request("wv", "TAXA", "bio:Root",124))) {
      var other = new Provider();
      var cached = new CachedAuthority(other, directory);
      String id = cached.configure(request);
      assertNotEquals(original, id);
      cached.resolveIdentity(id, "A");
      assertEquals(1, other.lookups);
    }
    var upgraded = new Provider();
    upgraded.policy = new Authority.CachePolicy("2",10,10,10);
    var cached = new CachedAuthority(upgraded, directory);
    assertNotEquals(original, cached.configure(request()));
  }

  @Test void neverPersistsFailuresOrDiagnosticBearingResults() {
    var provider = new Provider();
    var cached = new CachedAuthority(provider, directory);
    String id = cached.configure(request());
    provider.fail = true;
    assertThrows(IllegalStateException.class, () -> cached.resolveIdentity(id,"A"));
    assertThrows(IllegalStateException.class, () -> cached.resolveIdentity(id,"A"));
    provider.fail = false;
    provider.diagnostics = true;
    cached.resolveIdentity(id,"A");
    cached.resolveIdentity(id,"A");
    assertEquals(4,provider.lookups);
    provider.diagnostics = false;
    cached.resolveIdentity(id,"A");
    cached.resolveIdentity(id,"A");
    assertEquals(5,provider.lookups);
  }

  @Test void corruptionAndUnavailableDiskFallBackToProviderAndMemory() throws Exception {
    var first = new CachedAuthority(new Provider(), directory);
    String id = first.configure(request());
    first.resolveIdentity(id, "A");
    try (var files = Files.walk(directory)) {
      for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) Files.writeString(file, "broken");
    }
    var source = new Provider();
    var restored = new CachedAuthority(source, directory);
    restored.resolveIdentity(restored.configure(request()), "A");
    assertEquals(1, source.lookups);
    var file = directory.resolve("not-a-directory");
    Files.writeString(file,"occupied");
    var unavailable = new Provider();
    var cached = new CachedAuthority(unavailable,file);
    String live = cached.configure(request());
    cached.resolveIdentity(live,"A");
    cached.resolveIdentity(live,"A");
    assertEquals(1,unavailable.lookups);
  }

  @Test void queryCandidatesDoNotBypassIdentityValidationAndRankViewsAreIsolated() {
    var provider = new Provider();
    var cached = new CachedAuthority(provider,directory);
    String id = cached.configure(request());
    cached.search("name",null,id);
    cached.resolveIdentity(id,"A");
    assertEquals(1,provider.lookups);
    cached.subAuthority("SPECIES").search("name",null,id);
    cached.subAuthority("GENUS").search("name",null,id);
    cached.subAuthority("SPECIES").search("name",null,id);
    assertEquals(3,provider.searches);
  }

  @Test void bindingReloadRetainsDataAndIdenticalDeclarationsReuseTheLiveHandle() {
    var source = new Provider();
    var first = new AuthorityBindings(directory);
    String id = first.configure(request(),source);
    assertEquals(id,first.configure(request(),source));
    assertEquals(1,source.configurations);
    first.get("TAXA").provider().resolveIdentity(id,"A");
    first.clear();
    var restored = new Provider();
    var second = new AuthorityBindings(directory);
    String other = second.configure(request(),restored);
    assertEquals(id,other);
    second.get("TAXA").provider().resolveIdentity(other,"A");
    assertEquals(0,restored.lookups);
  }
}
