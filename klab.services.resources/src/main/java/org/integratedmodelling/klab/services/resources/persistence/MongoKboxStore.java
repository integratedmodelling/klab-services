package org.integratedmodelling.klab.services.resources.persistence;

import static com.mongodb.client.model.Filters.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import java.util.*;
import org.bson.Document;

/** Shared production backend. IDs and per-model replacement are atomic across service processes. */
public final class MongoKboxStore implements KboxDocumentStore {
  private final MongoClient client;
  private final MongoCollection<Document> documents;
  public MongoKboxStore(String uri, String database) {
    client = MongoClients.create(uri);
    try {
      documents = client.getDatabase(database).getCollection("model_catalog_v1")
          .withWriteConcern(com.mongodb.WriteConcern.MAJORITY);
      for (String field : List.of("kind", "namespace", "name", "id", "core", "typeIds", "descriptorIds"))
        documents.createIndex(Indexes.ascending(field));
      insertIfAbsent("counter", Map.of("value", 0L));
    } catch (RuntimeException e) { client.close(); throw e; }
  }
  @Override public Map<String, Object> get(String key) { return documents.find(eq("_id", key)).first(); }
  @Override public Map<String, Object> insertIfAbsent(String key, Map<String, Object> value) {
    try {
      documents.updateOne(eq("_id", key), new Document("$setOnInsert", new Document(value)),
          new UpdateOptions().upsert(true));
    } catch (com.mongodb.MongoWriteException e) {
      if (e.getError().getCategory() != com.mongodb.ErrorCategory.DUPLICATE_KEY) throw e;
    }
    return get(key);
  }
  @Override public void put(String key, Map<String, Object> value) {
    documents.replaceOne(eq("_id", key), new Document(value).append("_id", key), new ReplaceOptions().upsert(true));
  }
  @Override public List<Map<String, Object>> find(String field, Collection<?> values) {
    if (values.isEmpty()) return List.of();
    var result = new ArrayList<Map<String, Object>>();
    try (var cursor = documents.find(in(field, values)).iterator()) {
      while (cursor.hasNext()) result.add(cursor.next());
    }
    return result;
  }
  @Override public void delete(String key) { documents.deleteOne(eq("_id", key)); }
  @Override public long nextId() {
    var result = documents.findOneAndUpdate(eq("_id", "counter"), Updates.inc("value", 1L),
        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    return ((Number) result.get("value")).longValue();
  }
  @Override public void close() { client.close(); }
}
