package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.*;
import java.util.concurrent.*;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;

/** Opt-in integration test; uses and drops only a newly allocated test database. */
class MongoKboxStoreTest {
  @Test void concurrentClientsShareIdsAndAtomicModelReplacements() throws Exception {
    String uri = System.getenv("KLAB_TEST_MONGO_URI");
    assumeTrue(uri != null && !uri.isBlank(), "Set KLAB_TEST_MONGO_URI to run Mongo integration tests");
    String database = "klab_catalog_test_" + UUID.randomUUID().toString().replace("-", "");
    try (var first = new MongoKboxStore(uri, database);
         var second = new MongoKboxStore(uri, database)) {
      // Initialize the counter before contention; also exercise cross-client visibility.
      first.nextId();
      try (var pool = Executors.newFixedThreadPool(4)) {
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
          var store = i % 2 == 0 ? first : second;
          tasks.add(store::nextId);
        }
        Set<Long> ids = new HashSet<>();
        for (var result : pool.invokeAll(tasks)) assertTrue(ids.add(result.get()));
      }
      var winner = first.insertIfAbsent("concept:x", Map.of("id", first.nextId(), "kind", "concept"));
      assertEquals(winner, second.insertIfAbsent("concept:x", Map.of("id", second.nextId(), "kind", "concept")));
      first.put("model:x", Map.of("kind", "model", "typeIds", List.of(10L, 20L), "payloads", List.of("one", "two")));
      assertEquals(1, second.find("typeIds", List.of(20L)).size());
      second.put("model:x", Map.of("kind", "model", "typeIds", List.of(30L), "payloads", List.of("three")));
      assertTrue(first.find("typeIds", List.of(20L)).isEmpty());
      assertEquals(List.of("three"), first.get("model:x").get("payloads"));
    } finally {
      try (var client = MongoClients.create(uri)) { client.getDatabase(database).drop(); }
    }
  }
}
