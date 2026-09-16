package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.*;
import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.neo4j.harness.*;

class PerceivedGeometryPersistenceTest {
  static Neo4j database;
  static Driver driver;
  @BeforeAll static void start() {
    database = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled, false).build();
    // Thin driver adapter: execute the production transaction logic against real embedded Cypher,
    // without the application's conflicting Netty/Bolt runtime dependencies.
    driver = mock(Driver.class);
    when(driver.session()).thenAnswer(ignored -> {
      var session = mock(org.neo4j.driver.Session.class);
      when(session.beginTransaction(any(TransactionConfig.class))).thenAnswer(config -> embeddedTransaction());
      return session;
    });
  }
  @AfterAll static void stop() { if (driver != null) driver.close(); if (database != null) database.close(); }
  @BeforeEach void seed() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    rows("MATCH (n) DETACH DELETE n", Map.of());
    rows("CREATE (:Context {id:'a'}), (:Context {id:'b'}), "
        + "(o:Observation {id:41}), (p:Observation {id:42}), "
        + "(g:Geometry {definition:'1', size:1}) "
        + "CREATE (o)-[:HAS_GEOMETRY]->(g), (p)-[:HAS_GEOMETRY]->(g)", Map.of());
    rows("MATCH (c:Context {id:'a'}), (o:Observation {id:41}) CREATE (c)-[:HAS_CHILD]->(:Cohort)-[:HAS_MEMBER]->(o)", Map.of());
  }
  ObservationImpl observer() {
    var observable = mock(Observable.class);
    when(observable.is(SemanticType.AGENT)).thenReturn(true);
    var observer = new ObservationImpl(); observer.setId(41); observer.setObservable(observable);
    return observer;
  }
  org.integratedmodelling.klab.api.geometry.Geometry geometry(long start, long end) {
    return org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T0(1){tend=" + end + ",tstart=" + start + ",ttype=PHYSICAL}");
  }
  org.integratedmodelling.klab.api.geometry.Geometry perceived() {
    var values = rows("MATCH (:Observation {id:41})-[:PERCEIVES_GEOMETRY]->(g) RETURN g.definition AS value", Map.of());
    return values.isEmpty() ? null : org.integratedmodelling.klab.api.geometry.Geometry.create((String) values.getFirst().get("value"));
  }
  @Test void unionsPerceptionWithoutChangingOccupiedOrSharedGeometry() throws Exception {
    var graph = new Fixture("a");
    try (var tx = graph.createTransaction(mock(ContextScope.class))) { tx.perceive(observer(), geometry(0, 10)); }
    try (var tx = graph.createTransaction(mock(ContextScope.class))) { tx.perceive(observer(), geometry(20, 30)); }
    var scale = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(perceived());
    assertEquals(0, scale.getTime().getStart().getMilliseconds());
    assertEquals(30, scale.getTime().getEnd().getMilliseconds());
    assertEquals(1L, rows("MATCH (:Observation {id:41})-[r:PERCEIVES_GEOMETRY]->() RETURN count(r) AS n", Map.of()).getFirst().get("n"));
    assertEquals(2L, rows("MATCH ()-[r:HAS_GEOMETRY]->(g {definition:'1'}) RETURN count(r) AS n", Map.of()).getFirst().get("n"));
  }
  @Test void explicitSubmissionPreservesExistingAgentMetadata() throws Exception {
    rows("MATCH (n:Observation {id:41}) SET n.metadata=$metadata",
        Map.of("metadata", "{\"retained\":\"value\",\"klab.observer.automatic\":true}"));
    try (var tx = new Fixture("a").createTransaction(mock(ContextScope.class))) {
      tx.markExplicitAgent(observer());
    }
    var json = (String) rows("MATCH (n:Observation {id:41}) RETURN n.metadata AS metadata", Map.of())
        .getFirst().get("metadata");
    var metadata = org.integratedmodelling.common.utils.Utils.Json.parseObject(json, Map.class);
    assertEquals("value", metadata.get("retained"));
    assertEquals(true, metadata.get(DefaultObserver.AUTOMATIC));
    assertEquals(true, metadata.get(DefaultObserver.EXPLICIT));
  }

  @Test void spatialEditPreservesTimeAndRejectsStaleBaseline() throws Exception {
    var graph = new Fixture("a");
    try (var tx = graph.createTransaction(mock(ContextScope.class))) { tx.perceive(observer(), geometry(10, 30)); }
    var baseline = perceived().encode();
    var rectangle = org.integratedmodelling.klab.api.geometry.Geometry.create(
        org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
            "EPSG:4326 POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))").encode());
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      tx.replacePerceivedSpace(observer(), baseline, rectangle);
    }
    var edited = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(perceived());
    assertEquals(10, edited.getTime().getStart().getMilliseconds());
    assertEquals(30, edited.getTime().getEnd().getMilliseconds());
    assertTrue(edited.getSpace().getGeometricShape().contains(new double[] {0.5, 0.5}));
    var saved = perceived().encode();
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      assertThrows(ConcurrentModificationException.class, () -> tx.replacePerceivedSpace(observer(), baseline, rectangle));
    }
    assertEquals(saved, perceived().encode());
    try (var tx = new Fixture("b").createTransaction(mock(ContextScope.class))) {
      assertThrows(IllegalArgumentException.class, () -> tx.replacePerceivedSpace(observer(), saved, rectangle));
    }
    assertEquals(saved, perceived().encode());
    assertEquals(2L, rows("MATCH ()-[r:HAS_GEOMETRY]->() RETURN count(r) AS n", Map.of()).getFirst().get("n"));
    // An automatic observation arriving after the audit also makes the manual edit stale.
    try (var tx = graph.createTransaction(mock(ContextScope.class))) { tx.perceive(observer(), geometry(40, 50)); }
    var expanded = perceived().encode();
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      assertThrows(ConcurrentModificationException.class, () -> tx.replacePerceivedSpace(observer(), saved, rectangle));
    }
    assertEquals(expanded, perceived().encode());
  }

  @Test void laterFailureRollsBackPerception() throws Exception {
    var graph = new Fixture("a");
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      tx.perceive(observer(), geometry(0, 10));
      tx.fail(new IllegalStateException("rollback"));
    }
    assertNull(perceived());
    assertEquals(2L, rows("MATCH ()-[r:HAS_GEOMETRY]->() RETURN count(r) AS n", Map.of()).getFirst().get("n"));
  }
  @Test void disjointSpatialPerceptionIsPersistedAsAUnion() throws Exception {
    var first = org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
        "EPSG:4326 POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
    var second = org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
        "EPSG:4326 POLYGON ((3 3, 4 3, 4 4, 3 4, 3 3))");
    var graph = new Fixture("a");
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      tx.perceive(observer(), org.integratedmodelling.klab.api.geometry.Geometry.create(first.encode()));
    }
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      tx.perceive(observer(), org.integratedmodelling.klab.api.geometry.Geometry.create(second.encode()));
    }
    var shape = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(perceived()).getSpace().getGeometricShape();
    assertTrue(shape.contains(new double[] {0.5, 0.5}), () -> "Persisted union: " + perceived() + " shape: " + shape);
    assertTrue(shape.contains(new double[] {3.5, 3.5}));
    assertFalse(shape.contains(new double[] {2, 2}));
  }
  @Test void concurrentWritersUnionTheStoredBaselineAcrossContexts() throws Exception {
    var first = new Fixture("a").createTransaction(mock(ContextScope.class));
    first.perceive(observer(), geometry(0, 10));
    try (var executor = Executors.newSingleThreadExecutor()) {
      var started = new CountDownLatch(1);
      var second = executor.submit(() -> {
        started.countDown();
        try (var tx = new Fixture("b").createTransaction(mock(ContextScope.class))) {
          tx.perceive(observer(), geometry(20, 30));
        }
        return true;
      });
      assertTrue(started.await(10, TimeUnit.SECONDS));
      first.close();
      assertTrue(second.get(20, TimeUnit.SECONDS));
    } finally { first.close(); }
    var scale = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(perceived());
    assertEquals(0, scale.getTime().getStart().getMilliseconds());
    assertEquals(30, scale.getTime().getEnd().getMilliseconds());
  }
  static List<Map<String, Object>> rows(String cypher, Map<String, Object> parameters) {
    try (var tx = database.defaultDatabaseService().beginTx()) {
      var ret = new ArrayList<Map<String, Object>>();
      try (var result = tx.execute(cypher, parameters)) { result.forEachRemaining(ret::add); }
      tx.commit();
      return ret;
    }
  }

  static org.neo4j.driver.Transaction embeddedTransaction() {
    var embedded = database.defaultDatabaseService().beginTx();
    var tx = mock(org.neo4j.driver.Transaction.class);
    var open = new java.util.concurrent.atomic.AtomicBoolean(true);
    when(tx.isOpen()).thenAnswer(ignored -> open.get());
    when(tx.run(anyString(), anyMap())).thenAnswer(call -> {
      var records = new ArrayList<org.neo4j.driver.Record>();
      try (var result = embedded.execute(call.getArgument(0), call.getArgument(1))) {
        while (result.hasNext()) {
          var row = result.next();
          var record = mock(org.neo4j.driver.Record.class);
          // lockContext returns a node only to establish existence; no caller reads it.
          when(record.get(anyString())).thenAnswer(key -> Values.value(row.get(key.getArgument(0))));
          records.add(record);
        }
      }
      var ret = mock(org.neo4j.driver.Result.class);
      when(ret.list()).thenReturn(records);
      when(ret.hasNext()).thenReturn(!records.isEmpty());
      return ret;
    });
    doAnswer(ignored -> { embedded.commit(); open.set(false); return null; }).when(tx).commit();
    doAnswer(ignored -> { embedded.rollback(); open.set(false); return null; }).when(tx).rollback();
    return tx;
  }

  static class Fixture extends KnowledgeGraphNeo4j {
    Fixture(String id) { this.driver = PerceivedGeometryPersistenceTest.driver; this.rootContextId = id; }
    public KnowledgeGraph contextualize(DigitalTwin.Configuration c, UserScope s) { throw new UnsupportedOperationException(); }
    public KnowledgeGraph merge(URL url) { throw new UnsupportedOperationException(); }
    Map<String, Object> properties(Object asset) { return asParameters(asset); }
    public boolean isOnline() { return true; }
    public void shutdown() {}
  }
}
