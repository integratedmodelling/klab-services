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
      when(session.beginTransaction()).thenAnswer(config -> embeddedTransaction());
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
  @Test void semanticChildBookkeepingAddsOnlyTheOwnedDeltaWithoutReplacingOtherPayload()throws Exception {
    rows("MATCH (n:Observation {id:41}) SET n.childrenCount=7,n.foreignPayload='retained'",Map.of());
    var member=observation(41,"after");member.setChildrenCount(999);
    try(var tx=new Fixture("a").createTransaction(mock(ContextScope.class))){tx.updateSemantics(member,"before",3);}
    var actual=rows("MATCH (n:Observation {id:41}) RETURN n.childrenCount AS count,n.name AS name,n.parentId AS parent,n.foreignPayload AS payload",Map.of()).getFirst();
    assertEquals(10L,actual.get("count"));assertEquals("Preserved",actual.get("name"));assertEquals(77L,actual.get("parent"));assertEquals("retained",actual.get("payload"));
  }
  @Test void semanticChildBookkeepingRollsBackOnStaleTargetAndCancellation()throws Exception {
    rows("MATCH (n:Observation {id:41}) SET n.childrenCount=7",Map.of());
    try(var tx=new Fixture("a").createTransaction(mock(ContextScope.class))){tx.updateSemantics(observation(41,"tentative"),"before",3);tx.fail(new IllegalStateException("cancelled"));}
    assertEquals("before",observable(41));assertEquals(7L,rows("MATCH (n:Observation {id:41}) RETURN n.childrenCount AS n",Map.of()).getFirst().get("n"));
    try(var tx=new Fixture("a").createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.updateSemantics(observation(41,"stale"),"wrong",3));}
    assertEquals(7L,rows("MATCH (n:Observation {id:41}) RETURN n.childrenCount AS n",Map.of()).getFirst().get("n"));
  }
  @Test void negativeSemanticChildDeltaCannotWrite()throws Exception {
    try(var tx=new Fixture("a").createTransaction(mock(ContextScope.class))){assertThrows(IllegalArgumentException.class,()->tx.updateSemantics(observation(41,"invalid"),"before",-1));}
    assertEquals("before",observable(41));
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
  void cohort() {
    rows("MATCH (a:Context {id:'a'}),(n:Observation {id:41}) CREATE (a)-[:HAS_CHILD]->(c:Cohort {id:10, observable:'test:Region'}), (c)-[:HAS_MEMBER]->(n)",Map.of());
  }
  @Test void foreignMembershipWriteAndCaughtForeignValidationRollBackPriorWrites() throws Exception {
    cohort();var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    var cohort=new org.integratedmodelling.common.knowledge.CohortImpl();cohort.setId(10);
    try(var tx=new Fixture("b").createTransaction(mock(ContextScope.class))) {
      tx.updateSemantics(observation(42,"tentative"),"before");
      assertThrows(RuntimeException.class,()->tx.link(cohort,observation(42,"before"),GraphModel.Relationship.HAS_MEMBER));
    }
    assertEquals("before",observable(42));
    try(var tx=new Fixture("b").createTransaction(mock(ContextScope.class))) {
      tx.updateSemantics(observation(42,"tentative"),"before");
      assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));
    }
    assertEquals("before",observable(42));
    try(var tx=new Fixture("a").createTransaction(mock(ContextScope.class))) {
      tx.updateSemantics(observation(42,"tentative"),"before");
      assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(null));
    }
    assertEquals("before",observable(42));
  }
  @Test void globallyOrderedReadAndWriteUnionAllowsCompetingContextsToSettle() throws Exception {
    var first=new Fixture("a").createTransaction(mock(ContextScope.class));
    first.lockClassificationInputs(List.of(42L,41L));
    var started=new CountDownLatch(1);
    try(var executor=Executors.newSingleThreadExecutor()) {
      var second=executor.submit(()->{
        try(var tx=new Fixture("b").createTransaction(mock(ContextScope.class))) {
          started.countDown();tx.lockClassificationInputs(List.of(41L,42L));
          tx.updateSemantics(observation(42,"second"),"before");
        }
        return true;
      });
      assertTrue(started.await(10,TimeUnit.SECONDS));first.updateSemantics(observation(41,"first"),"before");first.close();
      assertTrue(second.get(10,TimeUnit.SECONDS));
      assertEquals("first",observable(41));assertEquals("second",observable(42));
    }
  }
  @Test void capturedNativePayloadMaterializesWithoutFetchingNewerPropertiesAndInvalidatesOnChange() throws Exception {
    cohort();var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    var metadata=GraphModel.Fields.METADATA;
    rows("MATCH (n:Observation {id:41}) SET n.urn='urn:test:41',n.childrenCount=0,n.parentId=77,n.eventTimestamps=[],n."+metadata+"=$metadata CREATE (n)-[:HAS_GEOMETRY]->(:Geometry {definition:'*'})",Map.of("metadata","{\"origin\":\"captured\"}"));
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    assertThrows(UnsupportedOperationException.class,()->read.members().getFirst().properties().put("name","mutated"));
    rows("MATCH (n:Observation {id:41}) SET n.name='Changed',n."+metadata+"=$metadata",Map.of("metadata","{\"origin\":\"newer\"}"));
    var scope=mock(ContextScope.class);var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    var meaning=observation(41,"before").getObservable();when(reasoner.resolveObservable("before")).thenReturn(meaning);
    var restored=graph.materializeClassificationMember(read.members().getFirst(),scope);
    assertEquals(41,restored.getId());assertEquals("Preserved",restored.getName());assertEquals("captured",restored.getMetadata().get("origin"));
    assertEquals("urn:test:41",restored.getUrn());assertEquals(77,restored.getParentId());assertEquals("*",restored.getGeometry().encode());
    try(var tx=graph.createTransaction(scope)){assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));}
  }
  @Test void cohortMeaningChangeInvalidatesTheNativeReadSet() throws Exception {
    cohort();var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    assertEquals("test:Region",read.cohortObservable());
    rows("MATCH (c:Cohort {id:10}) SET c.observable='test:Other'",Map.of());
    try(var tx=graph.createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));}
  }
  @Test void memberGeometryChangesInvalidateTheNativeReadSet() throws Exception {
    cohort();var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    rows("MATCH (n:Observation {id:41}) CREATE (n)-[:HAS_GEOMETRY]->(:Geometry {definition:'*'})",Map.of());
    try(var tx=graph.createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));}
  }
  @Test void rawCohortCaptureAndSameTransactionReadValidationProtectReusedInputs() throws Exception {
    cohort();var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    assertEquals(List.of(41L),read.members().stream().map(KnowledgeGraph.ClassificationMemberSnapshot::id).toList());
    assertEquals("before",read.members().getFirst().observable());
    try(var tx=graph.createTransaction(mock(ContextScope.class))){tx.validateClassificationMembers(read);}
    try(var tx=new Fixture("b").createTransaction(mock(ContextScope.class))){tx.updateSemantics(observation(41,"changed"),"before");}
    try(var tx=graph.createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));}
  }
  @Test void rawCohortCaptureRejectsAddedMembersAndForeignContexts() throws Exception {
    cohort();var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    rows("MATCH (c:Cohort {id:10, observable:'test:Region'}),(n:Observation {id:42}) CREATE (c)-[:HAS_MEMBER]->(n)",Map.of());
    try(var tx=graph.createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));}
    try(var tx=new Fixture("b").createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.captureClassificationMembers(10));}
  }
  @Test void currentMembershipValidationPreservesHistoricalEdgesWithoutCountingCeasedMembers() throws Exception {
    cohort();rows("MATCH(n:Observation{id:41}) SET n.klabCurrent=false",Map.of());var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    assertTrue(read.members().isEmpty());
    try(var tx=graph.createTransaction(mock(ContextScope.class))){tx.validateClassificationMembers(read);}
    assertEquals(1L,rows("MATCH(:Cohort{id:10})-[r:HAS_MEMBER]->(:Observation{id:41}) RETURN count(r) AS n",Map.of()).getFirst().get("n"));
  }
  @Test void reactivationInvalidatesAnEarlierCurrentMembershipReadSet() throws Exception {
    cohort();rows("MATCH(n:Observation{id:41}) SET n.klabCurrent=false",Map.of());var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    rows("MATCH(n:Observation{id:41}) SET n.klabCurrent=true",Map.of());
    try(var tx=graph.createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));}
  }
  @Test void cessationInvalidatesAnEarlierActiveMembershipReadSet() throws Exception {
    cohort();var graph=new Fixture("a");KnowledgeGraph.ClassificationReadSet read;
    try(var tx=graph.createTransaction(mock(ContextScope.class))){read=tx.captureClassificationMembers(10);}
    rows("MATCH(n:Observation{id:41}) SET n.klabCurrent=false",Map.of());
    try(var tx=graph.createTransaction(mock(ContextScope.class))){assertThrows(RuntimeException.class,()->tx.validateClassificationMembers(read));}
  }  @Test void emptyCapturedCohortIsAReadSetNotProducerCompletion() throws Exception {
    rows("MATCH (a:Context {id:'a'}) CREATE (a)-[:HAS_CHILD]->(:Cohort {id:10, observable:'test:Region'})",Map.of());
    try(var tx=new Fixture("a").createTransaction(mock(ContextScope.class))){assertTrue(tx.captureClassificationMembers(10).members().isEmpty());}
  }
  @Test void membershipWritesDoNotAdvanceSemanticGenerationAndCannotCertifyAClosure() throws Exception {
    cohort();var graph=new Fixture("a");long before=graph.getSemanticRevision();
    var cohort=new org.integratedmodelling.common.knowledge.CohortImpl();cohort.setId(10);
    try(var tx=graph.createTransaction(mock(ContextScope.class))){
      tx.link(cohort,observation(42,"before"),GraphModel.Relationship.HAS_MEMBER);
    }
    assertEquals(1L,rows("MATCH (:Cohort {id:10})-[r:HAS_MEMBER]->(:Observation {id:42}) RETURN count(r) AS n",Map.of()).getFirst().get("n"));
    assertEquals(before,graph.getSemanticRevision(),"This generation is not a membership snapshot/version");
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

  private static Value driverValue(Object value) {
    if(value instanceof org.neo4j.graphdb.Relationship original) {
      var properties=Map.copyOf(original.getAllProperties());
      var relationship=mock(org.neo4j.driver.types.Relationship.class);
      when(relationship.elementId()).thenReturn(original.getElementId());
      when(relationship.startNodeElementId()).thenReturn(original.getStartNode().getElementId());
      when(relationship.endNodeElementId()).thenReturn(original.getEndNode().getElementId());
      when(relationship.type()).thenReturn(original.getType().name());when(relationship.asMap()).thenReturn(properties);
      when(relationship.get(anyString())).thenAnswer(call->Values.value(properties.get(call.getArgument(0))));
      var wrapped=mock(Value.class);when(wrapped.asRelationship()).thenReturn(relationship);when(wrapped.asMap()).thenReturn(properties);
      when(wrapped.get(anyString())).thenAnswer(call->Values.value(properties.get(call.getArgument(0))));return wrapped;
    }
    if(value instanceof org.neo4j.graphdb.Node original) {
      var properties=Map.copyOf(original.getAllProperties());var labels=new ArrayList<String>();original.getLabels().forEach(label->labels.add(label.name()));
      var node=mock(org.neo4j.driver.types.Node.class);when(node.elementId()).thenReturn(original.getElementId());when(node.labels()).thenReturn(labels);when(node.asMap()).thenReturn(properties);
      when(node.get(anyString())).thenAnswer(call->Values.value(properties.get(call.getArgument(0))));
      var wrapped=mock(Value.class);when(wrapped.asNode()).thenReturn(node);when(wrapped.asMap()).thenReturn(properties);
      when(wrapped.get(anyString())).thenAnswer(call->Values.value(properties.get(call.getArgument(0))));return wrapped;
    }
    return Values.value(value);
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
          // A real Bolt Record is detached. Snapshot embedded Node values while its
          // transaction is open, including values() used by production adaptation.
          var detached=new LinkedHashMap<String,Value>();row.forEach((key,value)->detached.put(key,driverValue(value)));
          var record = mock(org.neo4j.driver.Record.class);
          // lockContext returns a node only to establish existence; no caller reads it.
          when(record.get(anyString())).thenAnswer(key -> detached.getOrDefault(key.getArgument(0),Values.NULL));
          when(record.get(anyInt())).thenAnswer(key -> new ArrayList<>(detached.values()).get(key.getArgument(0,Integer.class)));
          when(record.keys()).thenReturn(new ArrayList<>(detached.keySet()));
          when(record.values()).thenReturn(new ArrayList<>(detached.values()));
          records.add(record);
        }
      }
      var ret = mock(org.neo4j.driver.Result.class);
      when(ret.list()).thenReturn(records);
      var cursor=new java.util.concurrent.atomic.AtomicInteger();
      when(ret.hasNext()).thenAnswer(ignored->cursor.get()<records.size());
      when(ret.next()).thenAnswer(ignored->records.get(cursor.getAndIncrement()));
      when(ret.single()).thenAnswer(ignored->{if(records.size()!=1)throw new IllegalStateException("Expected one fixture row");return records.getFirst();});
      var keys=records.isEmpty()?List.<String>of():records.getFirst().keys();
      when(ret.keys()).thenReturn(keys);
      return ret;
    });
    doAnswer(ignored -> { embedded.commit(); open.set(false); return null; }).when(tx).commit();
    doAnswer(ignored -> { embedded.rollback(); open.set(false); return null; }).when(tx).rollback();
    doAnswer(ignored -> { if(open.compareAndSet(true,false))embedded.close();return null; }).when(tx).close();
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
