package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Reasoner-owned persistence around any provider. Provider configuration handles stay transient. */
public final class CachedAuthority implements Authority {
  private static final ObjectMapper JSON = new ObjectMapper()
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private final Authority source;
  private final Path root;
  private final Clock clock;
  private final String revision;
  private ConfigurationRequest request;
  private String stableId, providerId;
  private AuthorityCache cache;
  private CachePolicy policy;

  public CachedAuthority(Authority source, Path root) { this(source, root, Clock.systemUTC()); }
  CachedAuthority(Authority source, Path root, Clock clock) {
    this.source = Objects.requireNonNull(source);
    this.root = Objects.requireNonNull(root);
    this.clock = clock;
    this.revision = implementationRevision(source);
  }
  public boolean wraps(Authority provider) { return source == provider; }
  @Override public String getUrn() { return source.getUrn(); }
  @Override public Capabilities getCapabilities() { return source.getCapabilities(); }
  @Override public CachePolicy getCachePolicy() { return source.getCachePolicy(); }
  @Override public Map<String, Codelist> getCodelists() { return source.getCodelists(); }

  @Override public synchronized String configure(ConfigurationRequest request) {
    if (this.request != null) {
      if (this.request.equals(request)) return stableId;
      throw new IllegalArgumentException("A cached authority wrapper owns one bridge");
    }
    policy = Objects.requireNonNull(source.getCachePolicy(), "Authority cache policy is required");
    String fingerprint = digest(canonical(List.of("authority-cache-v1", request.worldview(), request.name(),
        request.rootIdentity(), request.parameters(), getUrn(), revision, policy)));
    String configured = source.configure(request);
    if (configured == null || configured.isBlank()) return configured;
    this.request = request;
    providerId = configured;
    stableId = "authority:" + request.worldview() + ":" + request.name() + ":" + fingerprint;
    cache = new AuthorityCache(root.resolve(segment("wv", request.worldview()))
        .resolve(segment("configuration", request.name())).resolve(fingerprint), clock);
    return stableId;
  }

  @Override public synchronized void releaseConfiguration(String id) {
    check(id);
    String transientId = providerId;
    providerId = null;
    try { source.releaseConfiguration(transientId); }
    finally {
      // Persisted data deliberately outlives the live bridge. configure() can rehydrate it later.
      request = null;
      stableId = null;
    }
  }

  @Override public synchronized Identity resolveIdentity(String id, String identity) {
    check(id);
    return resolve(source, "", identity);
  }

  @Override public synchronized List<Identity> search(String query, String subAuthority, String id) {
    check(id);
    return search(source, "", query, subAuthority);
  }

  @Override public synchronized Identity reconcile(String id, Map<String, String> fields) {
    check(id);
    return reconcile(source, "", fields);
  }

  @Override public Authority subAuthority(String catalog) {
    var delegate = source.subAuthority(catalog);
    return delegate == null ? null : new View(delegate, catalog);
  }

  private Identity resolve(Authority provider, String view, String identity) {
    String key = canonical(Arrays.asList("identity", view, identity));
    var cached = readIdentity(key, policy.identitySeconds());
    if (cached != null) return cached;
    var result = provider.resolveIdentity(providerId, identity);
    if (clean(result)) {
      cache.put(key, JSON.valueToTree(StoredIdentity.of(result)), policy.identitySeconds());
      // A canonical synonym ID must not force a second lookup after a restart.
      if (!Objects.equals(identity, result.getId())) cache.put(canonical(Arrays.asList("identity", view, result.getId())),
          JSON.valueToTree(StoredIdentity.of(result)), policy.identitySeconds());
    }
    return result;
  }

  private List<Identity> search(Authority provider, String view, String query, String subAuthority) {
    String key = canonical(Arrays.asList("search", view, query, subAuthority));
    if (policy.searchSeconds() > 0) {
      var hit = cache.get(key);
      if (hit != null && hit.isArray()) {
        try {
          var result = new ArrayList<Identity>();
          for (var node : hit) result.add(JSON.treeToValue(node, StoredIdentity.class));
          if (result.stream().allMatch(CachedAuthority::clean)) return List.copyOf(result);
        } catch (Exception ignored) { /* Corruption is a cache miss. */ }
      }
    }
    var result = provider.search(query, subAuthority, providerId);
    if (result != null && result.stream().allMatch(CachedAuthority::clean)) {
      cache.put(key, JSON.valueToTree(result.stream().map(StoredIdentity::of).toList()), policy.searchSeconds());
    }
    // Search candidates do not seed the identity cache: full lookup may validate more constraints.
    return result;
  }

  private Identity reconcile(Authority provider, String view, Map<String, String> fields) {
    String key = canonical(Arrays.asList("reconcile", view, fields));
    var cached = readIdentity(key, policy.reconciliationSeconds());
    if (cached != null) return cached;
    var result = provider.reconcile(providerId, fields);
    if (clean(result)) cache.put(key, JSON.valueToTree(StoredIdentity.of(result)), policy.reconciliationSeconds());
    return result;
  }

  private Identity readIdentity(String key, long seconds) {
    if (seconds == 0) return null;
    var hit = cache.get(key);
    if (hit != null) try {
      var result = JSON.treeToValue(hit, StoredIdentity.class);
      if (clean(result)) return result;
    } catch (Exception ignored) { /* Corruption is a cache miss. */ }
    return null;
  }

  private void check(String id) {
    if (providerId == null || !Objects.equals(id, stableId)) throw new IllegalArgumentException("Unknown or released authority bridge");
  }
  private static boolean clean(Identity identity) {
    return identity != null && identity.getId() != null && !identity.getId().isBlank()
        && identity.getConceptName() != null && !identity.getConceptName().isBlank()
        && (identity.getNotifications() == null || identity.getNotifications().isEmpty());
  }
  private static String canonical(Object value) {
    try { return JSON.writeValueAsString(value); }
    catch (Exception e) { throw new IllegalArgumentException("Authority cache parameters must be JSON serializable", e); }
  }
  static String digest(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  private static String segment(String prefix, String value) {
    String readable = value.replaceAll("[^A-Za-z0-9_.-]", "_");
    if (readable.length() > 48) readable = readable.substring(0, 48);
    return prefix + "_" + readable + "_" + digest(value).substring(0, 16);
  }
  private static String implementationRevision(Authority authority) {
    var type = authority.getClass();
    var annotation = type.getAnnotation(org.integratedmodelling.klab.api.services.reasoner.Authority.class);
    String version = annotation == null ? "unannotated" : annotation.version();
    try {
      var hash = MessageDigest.getInstance("SHA-256");
      var location = type.getProtectionDomain().getCodeSource();
      Path artifact = location != null && "file".equals(location.getLocation().getProtocol())
          ? Path.of(location.getLocation().toURI()) : null;
      try (InputStream input = artifact != null && Files.isRegularFile(artifact)
          ? Files.newInputStream(artifact) : type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
        if (input == null) throw new IllegalStateException("Cannot fingerprint authority implementation");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) hash.update(buffer, 0, read);
      }
      return type.getName() + ":" + version + ":" + HexFormat.of().formatHex(hash.digest());
    } catch (Exception e) { throw new IllegalStateException("Cannot fingerprint authority implementation", e); }
  }

  /** Cache DTO independent of plug-in classes and classloaders. Diagnostics are never persisted. */
  public record StoredIdentity(String id, String conceptName, String authorityName, String baseIdentity,
      List<String> parentIds, List<String> parentRelationship, String description, String label,
      float score, String locator) implements Identity {
    public StoredIdentity {
      parentIds = parentIds == null ? List.of() : List.copyOf(parentIds);
      parentRelationship = parentRelationship == null ? List.of() : List.copyOf(parentRelationship);
    }
    static StoredIdentity of(Identity i) { return new StoredIdentity(i.getId(), i.getConceptName(),
        i.getAuthorityName(), i.getBaseIdentity(), i.getParentIds(), i.getParentRelationship(),
        i.getDescription(), i.getLabel(), i.getScore(), i.getLocator()); }
    @Override public String getId() { return id; }
    @Override public String getConceptName() { return conceptName; }
    @Override public String getAuthorityName() { return authorityName; }
    @Override public String getBaseIdentity() { return baseIdentity; }
    @Override public List<String> getParentIds() { return parentIds; }
    @Override public List<String> getParentRelationship() { return parentRelationship; }
    @Override public String getDescription() { return description; }
    @Override public String getLabel() { return label; }
    @Override public float getScore() { return score; }
    @Override public String getLocator() { return locator; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override public List<Notification> getNotifications() { return List.of(); }
  }

  private final class View implements Authority {
    private final Authority delegate;
    private final String path;
    View(Authority delegate, String path) { this.delegate = delegate; this.path = path; }
    @Override public String getUrn() { return delegate.getUrn(); }
    @Override public Capabilities getCapabilities() { return delegate.getCapabilities(); }
    @Override public CachePolicy getCachePolicy() { return delegate.getCachePolicy(); }
    @Override public Map<String, Codelist> getCodelists() { return delegate.getCodelists(); }
    @Override public String configure(ConfigurationRequest request) { return CachedAuthority.this.configure(request); }
    @Override public void releaseConfiguration(String id) { CachedAuthority.this.releaseConfiguration(id); }
    @Override public Identity resolveIdentity(String id, String identity) {
      synchronized (CachedAuthority.this) { check(id); return resolve(delegate, path, identity); }
    }
    @Override public List<Identity> search(String query, String subAuthority, String id) {
      synchronized (CachedAuthority.this) { check(id); return CachedAuthority.this.search(delegate, path, query, subAuthority); }
    }
    @Override public Identity reconcile(String id, Map<String, String> fields) {
      synchronized (CachedAuthority.this) { check(id); return CachedAuthority.this.reconcile(delegate, path, fields); }
    }
    @Override public Authority subAuthority(String catalog) {
      var child = delegate.subAuthority(catalog);
      return child == null ? null : new View(child, path + "/" + catalog);
    }
  }
}
