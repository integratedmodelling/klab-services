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

class ClassificationPersistenceTest {
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
    rows("MATCH (n) DETACH DELETE n", Map.of());
    rows("CREATE (:Context {id:'a'}), (:Context {id:'b'}), "
        + "(:Observation {id:41, observable:'before', semantics:'before', parentId:77, name:'Preserved'}), "
        + "(:Observation {id:42, observable:'before', semantics:'before'}), (:Activity {id:91})", Map.of());
  }
  ObservationImpl observation(long id, String urn) {
    var observable = mock(Observable.class); var concept = mock(Concept.class);
    when(observable.getUrn()).thenReturn(urn); when(observable.getSemantics()).thenReturn(concept);
    when(observable.getArtifactType()).thenReturn(Artifact.Type.OBJECT);
    when(concept.getUrn()).thenReturn(urn); when(concept.getType()).thenReturn(EnumSet.of(SemanticType.SUBJECT));
    var ret = new ObservationImpl(); ret.setId(id); ret.setObservable(observable); ret.setName("Stale copy name");
    return ret;
  }
  String observable(long id) {
    return (String) rows("MATCH (n:Observation {id:$id}) RETURN n.observable AS value", Map.of("id", id)).getFirst().get("value");
  }
  @Test void semanticIndexesAndClassifiedAuditCommitTogetherWithoutChangingOtherProperties() throws Exception {
    var graph = new Fixture("a"); var member = observation(41, "after");
    var activity = Activity.of(Activity.Type.CLASSIFICATION); activity.setId(91);
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      tx.updateSemantics(member, "before");
      tx.link(activity, member, GraphModel.Relationship.CLASSIFIED,
          "before", "before", "after", "after", "predicate", "test:Concrete", "support", "S2", "event", "init");
    }
    assertEquals("after", observable(41));
    assertTrue(graph.getSemanticRevision() > 0);
    var row = rows("MATCH (:Activity)-[r:CLASSIFIED]->(n:Observation {semantics:'after'}) "
        + "RETURN r.before AS before, r.after AS after, n.name AS name, n.parentId AS parent"
        , Map.of()).getFirst();
    assertEquals("before", row.get("before")); assertEquals("after", row.get("after"));
    assertEquals("Preserved", row.get("name")); assertEquals(77L, row.get("parent"));
  }
  @Test void laterConflictRollsBackEarlierSemanticsAndAudit() throws Exception {
    var graph = new Fixture("a"); var member = observation(41, "after");
    var activity = Activity.of(Activity.Type.CLASSIFICATION); activity.setId(91);
    try (var tx = graph.createTransaction(mock(ContextScope.class))) {
      tx.updateSemantics(member, "before");
      tx.link(activity, member, GraphModel.Relationship.CLASSIFIED, "before", "before", "after", "after");
      assertThrows(RuntimeException.class, () -> tx.updateSemantics(observation(42, "after"), "stale"));
    }
    assertEquals("before", observable(41));
    assertEquals(0L, rows("MATCH ()-[r:CLASSIFIED]->() RETURN count(r) AS n", Map.of()).getFirst().get("n"));
  }
  @Test void updatePlanAndAuditRemainPortableWithoutAnObservation() {
    var plan = new org.integratedmodelling.common.runtime.ActuatorImpl();
    plan.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.UPDATE);
    plan.setEffect(org.integratedmodelling.klab.api.services.runtime.Actuator.Effect.SEMANTIC_UPDATE);
    plan.setContextualization(Contextualization.CLASSIFICATION);
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setUrn("test:Predicate of each test:Region");
    plan.setOperationObservable(observable);
    var props = new Fixture("a").properties(plan);
    var restored = org.integratedmodelling.common.utils.Utils.Json.parseObject(
        (String) props.get("operationPlan"), org.integratedmodelling.klab.api.services.runtime.Actuator.class);
    assertNull(restored.getObservation());
    assertEquals(Contextualization.CLASSIFICATION, restored.getContextualization());
    assertEquals(observable.getUrn(), restored.getOperationObservable().getUrn());
    var activity = Activity.of(Activity.Type.CLASSIFICATION);
    activity.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_ATTRIBUTIONS,
        List.of(Map.of("before", "test:Region", "after", "test:Forest test:Region")));
    var stored = new Fixture("a").properties(activity);
    assertTrue(((String) stored.get(GraphModel.Fields.METADATA)).contains("test:Forest test:Region"));
  }

  @Test void competingContextsCannotOverwriteTheSameBaseline() throws Exception {
    var first = new Fixture("a").createTransaction(mock(ContextScope.class));
    first.updateSemantics(observation(41, "winner"), "before");
    var started = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var second = executor.submit(() -> {
        try (var tx = new Fixture("b").createTransaction(mock(ContextScope.class))) {
          started.countDown();
          assertThrows(RuntimeException.class, () -> tx.updateSemantics(observation(41, "loser"), "before"));
        }
        return true;
      });
      assertTrue(started.await(10, TimeUnit.SECONDS));
      first.close();
      assertTrue(second.get(10, TimeUnit.SECONDS));
      assertEquals("winner", observable(41));
    }
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
    Fixture(String id) { this.driver = ClassificationPersistenceTest.driver; this.rootContextId = id; }
    public KnowledgeGraph contextualize(DigitalTwin.Configuration c, UserScope s) { throw new UnsupportedOperationException(); }
    public KnowledgeGraph merge(URL url) { throw new UnsupportedOperationException(); }
    Map<String, Object> properties(Object asset) { return asParameters(asset); }
    public boolean isOnline() { return true; }
    public void shutdown() {}
  }
}
