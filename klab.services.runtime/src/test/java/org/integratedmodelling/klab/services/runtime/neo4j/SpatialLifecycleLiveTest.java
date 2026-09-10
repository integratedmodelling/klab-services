package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabStorageException;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;

/** Opt-in tests against a local Neo4j with the spatial plugin. Only unique test contexts are touched. */
@EnabledIfSystemProperty(named = "klab.test.neo4j", matches = ".+")
class SpatialLifecycleLiveTest {
  private Driver driver;
  private String id;
  private String layer;

  @BeforeEach void connect() {
    driver = GraphDatabase.driver(System.getProperty("klab.test.neo4j"), AuthTokens.none(), Config.builder().withConnectionTimeout(5, TimeUnit.SECONDS)
        .withConnectionAcquisitionTimeout(5, TimeUnit.SECONDS)
        .withMaxTransactionRetryTime(0, TimeUnit.SECONDS).build());
    id = "lifecycle-test." + UUID.randomUUID().toString().replace("-", "");
    layer = "shape_" + id.substring(id.indexOf('.') + 1);
  }

  @AfterEach void cleanup() {
    try (var connection = driver) {
      if (layerExists()) {
        driver.executableQuery("CALL spatial.removeLayer($layer)")
            .withParameters(Map.of("layer", layer)).execute();
      }
      driver.executableQuery("MATCH (n {testOwner:$id}) DETACH DELETE n")
          .withParameters(Map.of("id", id)).execute();
    }
  }

  private void createContext(Transaction tx) {
    tx.run("CREATE (c:Context {id:$id, testOwner:$id})-[:HAS_PROVENANCE]->"
        + "(:Provenance {testOwner:$id})", Map.of("id", id)).consume();
  }

  private boolean layerExists() {
    return driver.executableQuery("CALL spatial.layers() YIELD name WHERE name=$name RETURN name")
        .withParameters(Map.of("name", layer)).execute().records().size() == 1;
  }

  private long contextCount() {
    return driver.executableQuery("MATCH (c:Context {id:$id}) RETURN count(c) AS n")
        .withParameters(Map.of("id", id)).execute().records().getFirst().get("n").asLong();
  }

  private void initialize() {
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      createContext(tx);
      KnowledgeGraphNeo4j.ensureSpatialLayer(tx, id, true);
      tx.commit();
    }
  }

  @Test void initializationRollbackRemovesBothContextAndLayer() {
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      createContext(tx);
      KnowledgeGraphNeo4j.ensureSpatialLayer(tx, id, true);
      tx.rollback();
    }
    assertEquals(0, contextCount());
    assertFalse(layerExists());
  }

  @Test void newContextCannotReuseExistingLayer() {
    initialize();
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      assertThrows(KlabStorageException.class,
          () -> KnowledgeGraphNeo4j.ensureSpatialLayer(tx, id, true));
    }
    assertTrue(layerExists());
    assertEquals(1, contextCount());
  }

  @Test void existingLayerCanBeReopened() {
    initialize();
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      KnowledgeGraphNeo4j.lockContext(tx, id);
      KnowledgeGraphNeo4j.ensureSpatialLayer(tx, id, false);
      tx.commit();
    }
    assertTrue(layerExists());
    assertEquals(1, contextCount());
  }

  @Test void missingExistingLayerFailsWithoutCreatingEmptyIndex() {
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      createContext(tx);
      tx.commit();
    }
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      assertThrows(KlabStorageException.class,
          () -> KnowledgeGraphNeo4j.ensureSpatialLayer(tx, id, false));
    }
    assertFalse(layerExists());
    assertEquals(1, contextCount());
  }

  @Test void failedDeletionRollsBackLayerRemoval() {
    initialize();
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      KnowledgeGraphNeo4j.lockContext(tx, id);
      tx.run("CALL spatial.removeLayer($layer)", Map.of("layer", layer)).consume();
      assertThrows(org.neo4j.driver.exceptions.ClientException.class,
          () -> tx.run("CALL nonexistent.lifecycleFailure()").consume());
    }
    assertTrue(layerExists());
    assertEquals(1, contextCount());
  }

  @Test void deletionWaitsForWriterAndSubsequentWriterFails() throws Exception {
    initialize();
    try (var pool = Executors.newSingleThreadExecutor();
        var session = driver.session(); var tx = session.beginTransaction()) {
      KnowledgeGraphNeo4j.lockContext(tx, id);
      var started = new CountDownLatch(1);
      var deletion = pool.submit(() -> {
        started.countDown();
        new Fixture(driver, id).deleteContext();
      });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      try {
        assertThrows(TimeoutException.class, () -> deletion.get(300, TimeUnit.MILLISECONDS));
      } finally {
        tx.commit();
      }
      deletion.get(10, TimeUnit.SECONDS);
    }
    assertFalse(layerExists());
    assertEquals(0, contextCount());
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      assertThrows(KlabStorageException.class, () -> KnowledgeGraphNeo4j.lockContext(tx, id));
    }
  }

  @Test void deletionPreservesForeignAssetsAndSharedAgents() {
    initialize();
    driver.executableQuery("MATCH (c:Context {id:$id}) "
        + "CREATE (other:Context {id:$foreign, testOwner:$id})-[:HAS_CHILD]->"
        + "(o:Observation {testOwner:$id}), "
        + "(c)-[:AFFECTS]->(o), "
        + "(c)-[:HAS_CHILD]->(shared:Observation {testOwner:$id}), "
        + "(other)-[:HAS_CHILD]->(shared), "
        + "(c)-[:HAS_AGENT]->(a:Agent {testOwner:$id}), (other)-[:HAS_AGENT]->(a)")
        .withParameters(Map.of("id", id, "foreign", id + ".foreign")).execute();
    new Fixture(driver, id).deleteContext();
    var counts = driver.executableQuery("MATCH (n {testOwner:$id}) RETURN count(n) AS n")
        .withParameters(Map.of("id", id)).execute();
    assertEquals(4, counts.records().getFirst().get("n").asLong());
    assertEquals(0, contextCount());
  }

  private static class Fixture extends KnowledgeGraphNeo4j {
    Fixture(Driver driver, String id) { this.driver = driver; this.rootContextId = id; }
    public KnowledgeGraph contextualize(DigitalTwin.Configuration c, UserScope s) {
      throw new UnsupportedOperationException();
    }
    public KnowledgeGraph merge(URL url) { throw new UnsupportedOperationException(); }
    public boolean isOnline() { return true; }
    public void shutdown() {}
  }
}
