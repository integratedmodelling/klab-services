package org.integratedmodelling.klab.services.reasoner.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.services.ResourcesService;

/** Reasoner-owned parsing cache. Serialized snapshots keep mutable builder syntax private. */
public final class ConceptSyntaxCache {
  private record Key(ResourcesService resources, String definition) {}
  private volatile Cache<Key, byte[]> syntax = newCache();
  private final com.fasterxml.jackson.databind.ObjectMapper mapper = JacksonConfiguration.newObjectMapper();

  public KimConcept get(ResourcesService resources, String definition) {
    var key = new Key(resources, definition);
    var uncached = new java.util.concurrent.atomic.AtomicReference<KimConcept>();
    var bytes = syntax.get(key, ignored -> {
      var parsed = resources.declareConcept(definition);
      uncached.set(parsed);
      if (parsed == null || parsed.is(SemanticType.NOTHING)
          || parsed.getNotifications() != null && !parsed.getNotifications().isEmpty()) return null;
      try { return mapper.writeValueAsBytes(parsed); }
      catch (java.io.IOException e) { throw new IllegalStateException("Cannot cache concept syntax", e); }
    });
    if (bytes == null) return uncached.get();
    try { return mapper.readValue(bytes, KimConcept.class); }
    catch (java.io.IOException e) { throw new IllegalStateException("Cannot restore concept syntax", e); }
  }

  private static Cache<Key, byte[]> newCache() {
    return Caffeine.newBuilder().maximumSize(5000).build();
  }

  public void clear() {
    // Do not wait for network calls holding old cache entries, or let their results
    // populate the new knowledge revision after invalidation.
    syntax = newCache();
  }
}
