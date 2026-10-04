package org.integratedmodelling.klab.services.resources.persistence;

import static org.dizitart.no2.filters.FluentFilter.where;
import java.nio.file.Path;
import java.util.*;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.index.IndexOptions;
import org.dizitart.no2.index.IndexType;
import org.dizitart.no2.rocksdb.RocksDBModule;

/** Embedded backend. A null path is intentionally ephemeral, primarily for contract tests. */
public final class NitriteKboxStore implements KboxDocumentStore {
  private final Nitrite db;
  private final NitriteCollection documents;
  public NitriteKboxStore(Path path) {
    var builder = Nitrite.builder();
    if (path != null) builder.loadModule(RocksDBModule.withConfig().filePath(path.toString()).build());
    db = builder.openOrCreate();
    documents = db.getCollection("model_catalog_v1");
    if (!documents.hasIndex("key")) documents.createIndex(IndexOptions.indexOptions(IndexType.UNIQUE), "key");
    for (String field : List.of("kind", "namespace", "name", "id", "core", "typeIds", "descriptorIds")) {
      if (!documents.hasIndex(field)) documents.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), field);
    }
  }
  private Map<String, Object> map(Document doc) {
    if (doc == null) return null;
    var ret = new HashMap<String, Object>();
    for (var pair : doc) if (!pair.getFirst().startsWith("_")) ret.put(pair.getFirst(), pair.getSecond());
    return ret;
  }
  @Override public synchronized Map<String, Object> get(String key) {
    return map(documents.find(where("key").eq(key)).firstOrNull());
  }
  @Override public synchronized Map<String, Object> insertIfAbsent(String key, Map<String, Object> value) {
    var existing = get(key);
    if (existing != null) return existing;
    put(key, value);
    return get(key);
  }
  @Override public synchronized void put(String key, Map<String, Object> value) {
    var data = new HashMap<>(value);
    data.put("key", key);
    documents.update(where("key").eq(key), Document.createDocument(data), UpdateOptions.updateOptions(true));
    db.commit();
  }
  @Override public synchronized List<Map<String, Object>> find(String field, Collection<?> values) {
    if (values.isEmpty()) return List.of();
    // Nitrite 4.3's IN filter performs a collection scan and does not match array elements.
    // Equality uses the multikey index; union the indexed lookups without duplicate documents.
    var ret = new LinkedHashMap<String, Map<String, Object>>();
    for (Object value : values) {
      for (var doc : documents.find(where(field).eq(value))) {
        var data = map(doc);
        ret.put((String) data.get("key"), data);
      }
    }
    return new ArrayList<>(ret.values());
  }
  @Override public synchronized void delete(String key) {
    documents.remove(where("key").eq(key));
    db.commit();
  }
  @Override public synchronized long nextId() {
    var counter = get("counter");
    long next = counter == null ? 1 : ((Number) counter.get("value")).longValue() + 1;
    put("counter", Map.of("value", next));
    return next;
  }
  @Override public synchronized void close() { if (!db.isClosed()) db.close(); }
}
