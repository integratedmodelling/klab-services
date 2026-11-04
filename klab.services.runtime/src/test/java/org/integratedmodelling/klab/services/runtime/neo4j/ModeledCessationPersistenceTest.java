package org.integratedmodelling.klab.services.runtime.neo4j;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.*;
import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
/** Native storage protocol only: these receipts do not claim a modeled execution issuer. */
class ModeledCessationPersistenceTest {
 @BeforeAll static void start(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();ClassificationPersistenceTest.start();}
 @AfterAll static void stop(){ClassificationPersistenceTest.stop();}
 @BeforeEach void seed(){rows("MATCH(n) DETACH DELETE n");rows("CREATE(a:Context{id:'a'}),(b:Context{id:'b'}),(c:Cohort{id:10,ownerContextId:'a',observable:'test:Subject',geometry:'1',childrenCount:2}),(n:Observation{id:1,ownerContextId:'a',observable:'test:Subject',urn:'a.original',foreignPayload:'immutable',metadata:'{\"original\":true}'}),(m:Observation{id:2,ownerContextId:'a',observable:'test:Subject'}),(model:Observation{id:3,ownerContextId:'a'}),(p:Provenance{id:'a.PROVENANCE'}),(activity:Activity{id:100,outcome:'SUCCESS',metadata:'original-scientific-proof'}),(g:Geometry{definition:'original-support'}) CREATE(a)-[:HAS_CHILD]->(c),(c)-[:HAS_MEMBER{rank:1}]->(n),(c)-[:HAS_MEMBER{rank:2}]->(m),(n)-[:HAS_GEOMETRY]->(g),(a)-[:HAS_PROVENANCE]->(p),(p)-[:HAS_CHILD]->(activity),(activity)-[:INSTANTIATED]->(n),(n)-[:AFFECTS{role:'configuration-input'}]->(model),(c)-[:AFFECTS{occurrenceRole:'PREREQUISITE'}]->(model)");}
 static List<Map<String,Object>> rows(String query){return ClassificationPersistenceTest.rows(query,Map.of());}
 static ContextScope scope(String id){var scope=mock(ContextScope.class);when(scope.getId()).thenReturn(id);return scope;}
 static class Graph extends ClassificationPersistenceTest.Fixture {
  private long next=500;
  Graph(String id){super(id);}
  @Override public long nextKey(){return next++;}
  @Override protected synchronized EagerResult query(String cypher,Map<String,Object> parameters,Scope scope){
   try(var session=driver.session();var tx=session.beginTransaction(TransactionConfig.empty())) {
    var result=tx.run(cypher,parameters);var records=result.list();var keys=result.keys();tx.commit();
    return new EagerResult(){public List<String> keys(){return keys;}public List<org.neo4j.driver.Record> records(){return records;}public org.neo4j.driver.summary.ResultSummary summary(){return null;}};
   }
  }
 }
 static ConsequenceReadSet before(long target)throws Exception {
  var selections=new ArrayList<ConsequenceEdgeSelection>();
  for(var type:List.of(GraphModel.Relationship.HAS_MEMBER,GraphModel.Relationship.HAS_CHILD,GraphModel.Relationship.AFFECTS))selections.add(new ConsequenceEdgeSelection(target,type,GraphModel.Relationship.Direction.INCOMING));
  selections.add(new ConsequenceEdgeSelection(target,GraphModel.Relationship.AFFECTS,GraphModel.Relationship.Direction.OUTGOING));
  for(var type:List.of(GraphModel.Relationship.HAS_MEMBER,GraphModel.Relationship.AFFECTS))selections.add(new ConsequenceEdgeSelection(10,type,GraphModel.Relationship.Direction.OUTGOING));
  try(var tx=new Graph("a").createTransaction(scope("a"))){return tx.captureConsequenceInputs(List.of(1L,2L,3L,10L,100L),selections);}
 }
 static ObservationCessation cease(long id,ConsequenceReadSet before){return new ObservationCessation(id,500,100,"1","native-storage-test:source-admission-is-external",before);}
 static int currentCount(){var q=new org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery<Observation>(org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.AssetType.OBSERVATION);q.source(new org.integratedmodelling.common.knowledge.CohortImpl(){ {setId(10);} }).along(GraphModel.Relationship.HAS_MEMBER);var statement=Neo4jQueryCompiler.compile(q,"a");return ClassificationPersistenceTest.rows(statement.cypher(),statement.parameters()).size();}
 @Test void currentLookupAndMembershipChangeWhileNativePastAndProvenanceArePreserved()throws Exception {
  var original=before(1);long edges=(Long)rows("MATCH()-[r]->() RETURN count(r) AS n").getFirst().get("n");assertEquals(2,currentCount());
  List<CohortMembershipChange> changes;try(var tx=new Graph("a").createTransaction(scope("a"))){changes=tx.ceaseObservations(List.of(cease(1,original)));}
  assertEquals(1,currentCount());
  try(var read=new Graph("a").createTransaction(scope("a"))){assertEquals(List.of(2L),read.captureClassificationMembers(10).members().stream().map(ClassificationMemberSnapshot::id).toList());}
  assertTrue(new Graph("a").getLinks(new ClassificationPersistenceTest().observation(1,"test:Subject"),GraphModel.Relationship.Direction.OUTGOING,scope("a"),GraphModel.Relationship.AFFECTS).isEmpty());
  assertNull(new Graph("a").getAsset(1,scope("a"),Observation.class));assertNull(new Graph("a").getAsset("a.original",scope("a"),Observation.class));
  assertEquals(List.of(1L,2L),changes.getFirst().beforeMembers());assertEquals(List.of(2L),changes.getFirst().afterMembers());assertEquals(1,changes.getFirst().afterRevision());
  assertEquals(edges,rows("MATCH()-[r]->() RETURN count(r) AS n").getFirst().get("n"));assertEquals("original-support",rows("MATCH(:Observation{id:1})-[:HAS_GEOMETRY]->(g) RETURN g.definition AS value").getFirst().get("value"));
  var history=new Graph("a").getObservationHistory(1,scope("a"));assertEquals(List.of(cease(1,original)),history);assertEquals("original-scientific-proof",rows("MATCH(a:Activity{id:100}) RETURN a.metadata AS proof").getFirst().get("proof"));
  assertFalse(history.getFirst().before().assets().stream().filter(a->a.id()==1).findFirst().orElseThrow().properties().containsKey(KnowledgeGraph.CURRENT_PROPERTY));
 }
 @Test void rollbackPreservesCurrentViewAndDoesNotAppendHistory()throws Exception {
  var original=before(1);try(var tx=new Graph("a").createTransaction(scope("a"))){tx.ceaseObservations(List.of(cease(1,original)));tx.fail(new IllegalStateException("abort"));}
  assertEquals(2,currentCount());assertTrue(new Graph("a").getObservationHistory(1,scope("a")).isEmpty());
 }
 @Test void oneStaleRowRollsBackWholeBatchBeforeAnyTargetMutation()throws Exception {
  var first=before(1);var second=before(2);rows("MATCH(n:Observation{id:2}) SET n.observable='changed'");
  try(var tx=new Graph("a").createTransaction(scope("a"))){assertThrows(RuntimeException.class,()->tx.ceaseObservations(List.of(cease(1,first),cease(2,second))));}
  assertEquals(2,currentCount());assertTrue(new Graph("a").getObservationHistory(1,scope("a")).isEmpty());
 }
 @Test void foreignContextAndIncompleteMembershipCannotPublish()throws Exception {
  var original=before(1);try(var tx=new Graph("b").createTransaction(scope("b"))){assertThrows(RuntimeException.class,()->tx.ceaseObservations(List.of(cease(1,original))));}
  var incomplete=new ConsequenceReadSet(original.contextId(),original.assets(),original.selections().stream().filter(s->!(s.assetId()==10&&s.relationship()==GraphModel.Relationship.HAS_MEMBER)).toList(),original.edges());
  try(var tx=new Graph("a").createTransaction(scope("a"))){assertThrows(RuntimeException.class,()->tx.ceaseObservations(List.of(cease(1,incomplete))));}
  assertEquals(2,currentCount());assertThrows(RuntimeException.class,()->new Graph("a").getObservationHistory(1,scope("b")));
 }
 @Test void successfulEmptyCurrentCohortRetainsBothHistoriesAndAllOriginalEdges()throws Exception {
  var first=before(1);var second=before(2);try(var tx=new Graph("a").createTransaction(scope("a"))){var changes=tx.ceaseObservations(List.of(cease(1,first),cease(2,second)));assertEquals(List.of(),changes.getFirst().afterMembers());}
  assertEquals(0,currentCount());assertEquals(2L,rows("MATCH(:Cohort{id:10})-[r:HAS_MEMBER]->() RETURN count(r) AS n").getFirst().get("n"));assertEquals(1,new Graph("a").getObservationHistory(1,scope("a")).size());assertEquals(1,new Graph("a").getObservationHistory(2,scope("a")).size());
 }
 @Test void staleQueuedWritesCannotModifyOrResurrectAnInoperativeTarget()throws Exception {
  var original=before(1);try(var tx=new Graph("a").createTransaction(scope("a"))){tx.ceaseObservations(List.of(cease(1,original)));}
  var stale=new ClassificationPersistenceTest().observation(1,"forged-resurrection");stale.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));stale.getMetadata().put(KnowledgeGraph.CURRENT_PROPERTY,true);
  try(var tx=new Graph("a").createTransaction(scope("a"))){assertThrows(RuntimeException.class,()->tx.update(stale));}
  try(var tx=new Graph("a").createTransaction(scope("a"))){assertThrows(RuntimeException.class,()->tx.updateSemantics(stale,"test:Subject"));}
  assertFalse(new Graph("a").isObservationCurrent(stale,scope("a")));assertEquals("test:Subject",rows("MATCH(n:Observation{id:1}) RETURN n.observable AS observable").getFirst().get("observable"));
  assertEquals(1,new Graph("a").getObservationHistory(1,scope("a")).size());
 }
 @Test void ordinaryMetadataCannotForgeLifecycleFieldsAndUnownedSentinelsAreNotCurrent()throws Exception {
  var ordinary=new ClassificationPersistenceTest().observation(2,"test:Subject");ordinary.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));ordinary.getMetadata().put(KnowledgeGraph.CURRENT_PROPERTY,false);ordinary.getMetadata().put("klabCessationHistory",List.of("forged"));
  try(var tx=new Graph("a").createTransaction(scope("a"))){tx.update(ordinary);}
  assertTrue(new Graph("a").isObservationCurrent(ordinary,scope("a")));assertTrue(new Graph("a").getObservationHistory(2,scope("a")).isEmpty());
  var unowned=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();assertFalse(new Graph("a").isObservationCurrent(unowned,scope("a")));
 }

 @Test void preparedBeforeimageAllowsOwnJointBirthAndReceiptDescribesFinalNativeParentState()throws Exception {
  var original=before(1);CohortMembershipChange change;
  var born=new ClassificationPersistenceTest().observation(-1,"test:Subject");born.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));
  var cohort=new org.integratedmodelling.common.knowledge.CohortImpl();cohort.setId(10);
  try(var tx=new Graph("a").createTransaction(scope("a"))) {
    var handle=tx.prepareCessations(List.of(cease(1,original)));tx.store(born);tx.link(cohort,born,GraphModel.Relationship.HAS_MEMBER);
    change=tx.publishCessations(handle).getFirst();assertEquals(List.of(2L,born.getId()),change.afterMembers());assertEquals(2,change.afterRevision());
  }
  assertEquals(2,currentCount());assertEquals(10,born.getParentId());
  try(var guard=new Graph("a").createTransaction(scope("a"))){var actual=guard.captureConsequenceInputs(change.afterReadSet().assetIds(),change.afterReadSet().selections());assertEquals(change.afterReadSet(),actual);guard.validateConsequenceInputs(change.afterReadSet());}
  assertEquals(List.of(1L,2L),change.beforeMembers());assertEquals(List.of(cease(1,original)),new Graph("a").getObservationHistory(1,scope("a")));
 }
 @Test void preparedHandleCannotBeTransferredOrUsedAfterTargetMutation()throws Exception {
  var original=before(1);CessationPublication handle;
  try(var prepare=new Graph("a").createTransaction(scope("a"))){handle=prepare.prepareCessations(List.of(cease(1,original)));}
  var captured=handle;try(var foreign=new Graph("a").createTransaction(scope("a"))){assertThrows(RuntimeException.class,()->foreign.publishCessations(captured));}
  try(var tx=new Graph("a").createTransaction(scope("a"))){var prepared=tx.prepareCessations(List.of(cease(1,original)));var updated=new ClassificationPersistenceTest().observation(1,"changed");updated.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));tx.update(updated);assertThrows(RuntimeException.class,()->tx.publishCessations(prepared));}
  assertEquals(2,currentCount());assertTrue(new Graph("a").getObservationHistory(1,scope("a")).isEmpty());
 }

 @Test void displayNameIsStillPartOfTheExactRawNativeBeforeimageFence()throws Exception {
  var captured=before(1);rows("MATCH(n:Observation{id:1}) SET n.name='changed after capture'");
  try(var tx=new Graph("a").createTransaction(scope("a"))){assertThrows(RuntimeException.class,()->tx.ceaseObservations(List.of(cease(1,captured))));}
  assertEquals(2,currentCount());assertTrue(new Graph("a").getObservationHistory(1,scope("a")).isEmpty());
 }

}
