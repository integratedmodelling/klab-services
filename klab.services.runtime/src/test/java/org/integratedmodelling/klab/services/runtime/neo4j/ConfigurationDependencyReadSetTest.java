package org.integratedmodelling.klab.services.runtime.neo4j;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.*;
class ConfigurationDependencyReadSetTest {
 @BeforeAll static void start(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();ClassificationPersistenceTest.start();}
 @AfterAll static void stop(){ClassificationPersistenceTest.stop();}
 @BeforeEach void seed(){rows("MATCH(n) DETACH DELETE n");rows("CREATE (:Context{id:'a'}),(:Context{id:'b'}),(:Observation{id:1,ownerContextId:'a'}),(:Observation{id:2,ownerContextId:'a'})");}
 static List<Map<String,Object>> rows(String query){return ClassificationPersistenceTest.rows(query,Map.of());}
 static KnowledgeGraph.Transaction tx(String context){return new ClassificationPersistenceTest.Fixture(context).createTransaction(mock(ContextScope.class));}
 static ObservationImpl observation(long id){var value=new ObservationImpl();value.setId(id);return value;}
 @Test void selectedDependencyRemovalCannotRevalidateAnOldReadSet()throws Exception {
  for(var type:List.of(GraphModel.Relationship.AFFECTS,GraphModel.Relationship.EMERGED_FROM)) {
   seed();rows("MATCH(a:Observation{id:1}),(b:Observation{id:2}) CREATE(a)-[:"+type+"]->(b)");
   KnowledgeGraph.ConsequenceReadSet read;
   try(var capture=tx("a")){read=capture.captureConsequenceInputs(List.of(1L,2L),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,type,GraphModel.Relationship.Direction.OUTGOING)));}
   rows("MATCH(:Observation{id:1})-[r:"+type+"]->() DELETE r");
   try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateConsequenceInputs(read));}
  }
 }
 @Test void denseCrossContextDependencyPhantomsWaitForTheGuard()throws Exception {
  for(var type:List.of(GraphModel.Relationship.AFFECTS,GraphModel.Relationship.EMERGED_FROM)) {
   seed();rows("MATCH(a:Observation{id:1}) UNWIND range(100,180) AS id CREATE(a)-[:"+type+"]->(:Observation{id:id,ownerContextId:'a'})");
   var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();Future<?> writer;
   try {
    try(var guard=tx("a")) {
     guard.captureConsequenceInputs(List.of(1L),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,type,GraphModel.Relationship.Direction.OUTGOING)));
     writer=executor.submit(()->{thread.set(Thread.currentThread());try(var foreign=tx("b")){foreign.link(observation(1),observation(2),type);}catch(Exception e){throw new RuntimeException(e);}});
     long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
     while(System.nanoTime()<deadline&&!writer.isDone()) {
      var current=thread.get();if(current!=null&&Arrays.stream(current.getStackTrace()).anyMatch(frame->frame.getClassName().contains("locking.forseti"))){waiting=true;break;}Thread.sleep(5);
     }assertTrue(waiting,"Participating dense dependency writer must wait on native input lock");
    }
    writer.get(5,TimeUnit.SECONDS);assertEquals(1L,rows("MATCH(:Observation{id:1})-[r:"+type+"]->(:Observation{id:2}) RETURN count(r) AS n").getFirst().get("n"));
   }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
 }
 static org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl configuration() {
  var observation=new ClassificationPersistenceTest().observation(1,"test:Network");
  when(observation.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.CONFIGURATION)).thenReturn(true);
  observation.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));return observation;
 }
 static KnowledgeGraph.ConsequenceReadSet configurationRead()throws Exception {
  try(var capture=tx("a")){return capture.captureConsequenceInputs(List.of(1L,2L),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(
      1,GraphModel.Relationship.EMERGED_FROM,GraphModel.Relationship.Direction.OUTGOING)));}
 }
 @Test void configurationRevisionRetainsIdentityAndHistoryWhileReplacingCurrentParticipants()throws Exception {
  rows("MATCH(n:Observation{id:1}),(p:Observation{id:2}) SET n.observable='test:Network',n.configurationRevision=1,n.configurationHistory=['prior'] "
      +"CREATE(n)-[:EMERGED_FROM]->(p),(n)-[:HAS_GEOMETRY]->(:Geometry{definition:'previous-immutable-support'})");
  var read=configurationRead();
  try(var update=tx("a")){update.updateConfiguration(configuration(),read,List.of(2L),2,"new-revision");}
  var actual=rows("MATCH(n:Observation{id:1}) RETURN n.configurationRevision AS revision,n.configurationHistory AS history").getFirst();
  assertEquals(2L,actual.get("revision"));assertArrayEquals(new String[]{"prior","new-revision"},(String[])actual.get("history"));
  assertEquals(1L,rows("MATCH(n:Observation{id:1})-[:EMERGED_FROM]->(:Observation{id:2}) RETURN count(n) AS n").getFirst().get("n"));
  assertEquals(1L,rows("MATCH(g:Geometry{definition:'previous-immutable-support'}) RETURN count(g) AS n").getFirst().get("n"));
  assertEquals("1",rows("MATCH(:Observation{id:1})-[:HAS_GEOMETRY]->(g) RETURN g.definition AS support").getFirst().get("support"));
 }
 @Test void staleConfigurationRevisionRollsBackWithoutHistoryOrParticipantMutation()throws Exception {
  rows("MATCH(n:Observation{id:1}) SET n.observable='test:Network',n.configurationRevision=1,n.configurationHistory=['prior']");
  var read=configurationRead();rows("MATCH(n:Observation{id:1}) SET n.configurationRevision=2");
  try(var update=tx("a")){assertThrows(RuntimeException.class,()->update.updateConfiguration(configuration(),read,List.of(2L),2,"forbidden"));}
  assertArrayEquals(new String[]{"prior"},(String[])rows("MATCH(n:Observation{id:1}) RETURN n.configurationHistory AS history").getFirst().get("history"));
  assertEquals(0L,rows("MATCH(:Observation{id:1})-[r:EMERGED_FROM]->() RETURN count(r) AS n").getFirst().get("n"));
 }
 @Test void explicitRollbackDoesNotPublishConfigurationHistoryOrSupport()throws Exception {
  rows("MATCH(n:Observation{id:1}) SET n.observable='test:Network',n.configurationRevision=1,n.configurationHistory=['prior']");
  var read=configurationRead();try(var update=tx("a")){update.updateConfiguration(configuration(),read,List.of(2L),2,"forbidden");update.fail(new Exception("abort"));}
  assertEquals(1L,rows("MATCH(n:Observation{id:1}) RETURN n.configurationRevision AS revision").getFirst().get("revision"));
  assertArrayEquals(new String[]{"prior"},(String[])rows("MATCH(n:Observation{id:1}) RETURN n.configurationHistory AS history").getFirst().get("history"));
 }

 @Test void zeroParticipantConfigurationPersistsWithoutTerminationOrHistoryRewrite()throws Exception {
  rows("MATCH(n:Observation{id:1}),(p:Observation{id:2}) SET n.observable='test:Network',n.configurationRevision=1,n.configurationHistory=['prior'] CREATE(n)-[:EMERGED_FROM]->(p)");
  var read=configurationRead();try(var update=tx("a")){update.updateConfiguration(configuration(),read,List.of(),2,"empty-current-network");}
  assertEquals(1L,rows("MATCH(n:Observation{id:1}) RETURN count(n) AS n").getFirst().get("n"));
  assertEquals(0L,rows("MATCH(:Observation{id:1})-[r:EMERGED_FROM]->() RETURN count(r) AS n").getFirst().get("n"));
  assertArrayEquals(new String[]{"prior","empty-current-network"},(String[])rows("MATCH(n:Observation{id:1}) RETURN n.configurationHistory AS history").getFirst().get("history"));
 }
 @Test void completionActivitiesAreReadOnlyProofsAuthorizedByActualContextProvenance()throws Exception {
  rows("MATCH(a:Context{id:'a'}),(b:Context{id:'b'}),(o:Observation{id:1}) CREATE (a)-[:HAS_PROVENANCE]->(:Provenance{id:'a.PROVENANCE'})-[:HAS_CHILD]->(proof:Activity{id:10,outcome:'SUCCESS'}),"
      +"(b)-[:HAS_PROVENANCE]->(:Provenance{id:'b.PROVENANCE'})-[:HAS_CHILD]->(:Activity{id:11,outcome:'SUCCESS'}),(proof)-[:INSTANTIATED]->(o)");
  KnowledgeGraph.ConsequenceReadSet read;
  try(var guard=tx("a")) {
   read=guard.captureConsequenceInputs(List.of(1L,10L),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.INSTANTIATED,GraphModel.Relationship.Direction.INCOMING)));
   assertEquals(List.of("Activity"),read.assets().stream().filter(a->a.id()==10).findFirst().orElseThrow().labels());
  }
  try(var foreign=tx("a")){assertThrows(RuntimeException.class,()->foreign.captureConsequenceInputs(List.of(11L),List.of()));}
  rows("MATCH(n:Activity{id:10}) SET n.outcome='FAILURE'");
  try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateConsequenceInputs(read));}
 }
 @Test void denseActivityCompletionWriterParticipatesInTheNativeProofLockProtocol()throws Exception {
  rows("MATCH(a:Context{id:'a'}) CREATE(a)-[:HAS_PROVENANCE]->(:Provenance{id:'a.PROVENANCE'})-[:HAS_CHILD]->(:Activity{id:10,outcome:'SUCCESS'})");
  rows("MATCH(a:Activity{id:10}) UNWIND range(100,180) AS id CREATE(a)-[:CONNECTED]->(:Observation{id:id,ownerContextId:'a'})");
  var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();Future<?> writer;
  try {
   try(var guard=tx("a")) {
    guard.captureConsequenceInputs(List.of(10L),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(10,GraphModel.Relationship.CONNECTED,GraphModel.Relationship.Direction.OUTGOING)));
    writer=executor.submit(()->{thread.set(Thread.currentThread());var activity=(org.integratedmodelling.klab.api.provenance.impl.ActivityImpl)org.integratedmodelling.klab.api.provenance.Activity.of(org.integratedmodelling.klab.api.provenance.Activity.Type.CONNECTION);
      activity.setId(10);new ClassificationPersistenceTest.Fixture("b").link(activity,observation(2),GraphModel.Relationship.CONNECTED,null);});
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
    while(System.nanoTime()<deadline&&!writer.isDone()) {var current=thread.get();if(current!=null&&Arrays.stream(current.getStackTrace()).anyMatch(frame->frame.getClassName().contains("locking.forseti"))){waiting=true;break;}Thread.sleep(5);}
    assertTrue(waiting,"Dense completion writer must wait on captured Activity proof");
   }writer.get(5,TimeUnit.SECONDS);
  }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
 }

 static void standaloneDriver() {
  when(ClassificationPersistenceTest.driver.executableQuery(anyString())).thenAnswer(call->{
   String query=call.getArgument(0);var parameters=new AtomicReference<Map<String,Object>>(Map.of());var executable=mock(org.neo4j.driver.ExecutableQuery.class);
   when(executable.withParameters(anyMap())).thenAnswer(args->{parameters.set(args.getArgument(0));return executable;});
   when(executable.execute()).thenAnswer(ignored->{var values=ClassificationPersistenceTest.rows(query,parameters.get());var records=new ArrayList<org.neo4j.driver.Record>();
    for(var value:values){var record=mock(org.neo4j.driver.Record.class);when(record.get(anyString())).thenAnswer(key->org.neo4j.driver.Values.value(value.get(key.getArgument(0))));records.add(record);}
    var eager=mock(org.neo4j.driver.EagerResult.class);when(eager.records()).thenReturn(records);return eager;});return executable;
  });
 }
 @Test void scopedConfigurationParticipantOverlayReplacesBothDirectionsAndKeepsOtherLinks() {
  standaloneDriver();rows("CREATE(:Observation{id:3,ownerContextId:'a'})");
  rows("MATCH(a:Observation{id:1}),(b:Observation{id:2}) CREATE(a)-[:EMERGED_FROM]->(b),(a)-[:AFFECTS]->(b)");
  var graph=spy(new ClassificationPersistenceTest.Fixture("a"));var scope=mock(ContextScope.class);var transaction=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction.class);
  when(scope.getId()).thenReturn("a");
  var config=observation(1);var previous=observation(2);var current=observation(3);
  when(scope.getCurrentTransaction()).thenReturn(transaction);when(transaction.assets()).thenReturn(List.of(config,previous,current));
  when(transaction.configurationParticipantReplacements()).thenReturn(Set.of(1L));
  var link=new org.integratedmodelling.klab.api.data.impl.LinkImpl();link.setSource(config);link.setTarget(current);link.setRelationship(GraphModel.Relationship.EMERGED_FROM);
  when(transaction.outgoing(config)).thenReturn(List.of(link));when(transaction.incoming(current)).thenReturn(List.of(link));when(transaction.incoming(previous)).thenReturn(List.of());
  doReturn(config).when(graph).getAsset(1L,scope,org.integratedmodelling.klab.api.data.RuntimeAsset.class);
  doReturn(previous).when(graph).getAsset(2L,scope,org.integratedmodelling.klab.api.data.RuntimeAsset.class);
  doReturn(current).when(graph).getAsset(3L,scope,org.integratedmodelling.klab.api.data.RuntimeAsset.class);
  assertEquals(List.of(3L),graph.getLinks(config,GraphModel.Relationship.Direction.OUTGOING,scope,GraphModel.Relationship.EMERGED_FROM).stream().map(l->l.target().getId()).toList());
  assertTrue(graph.getLinks(previous,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.EMERGED_FROM).isEmpty());
  assertEquals(1,graph.getLinks(current,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.EMERGED_FROM).size());
  assertEquals(1,graph.getLinks(config,GraphModel.Relationship.Direction.OUTGOING,scope,GraphModel.Relationship.AFFECTS).size());
  assertEquals(1L,rows("MATCH(:Observation{id:1})-[r:EMERGED_FROM]->(:Observation{id:2}) RETURN count(r) AS n").getFirst().get("n"));
 }

 @Test void exactClassifierCertificatePropertiesAndRemovalInvalidateTheNativeProof()throws Exception {
  for(var type:List.of(GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.CHARACTERIZED)) {
  seed();rows("MATCH(a:Context{id:'a'}),(o:Observation{id:1}) CREATE(a)-[:HAS_PROVENANCE]->(:Provenance{id:'a.PROVENANCE'})-[:HAS_CHILD]->(proof:Activity{id:10,outcome:'SUCCESS'}),"
      +"(proof)-[:"+type+"{family:'test:Role',after:'test:Certified'}]->(o)");
  KnowledgeGraph.ConsequenceReadSet read;try(var guard=tx("a")){read=guard.captureConsequenceInputs(List.of(1L,10L),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,type,GraphModel.Relationship.Direction.INCOMING)));}
  rows("MATCH(:Activity{id:10})-[r:"+type+"]->() SET r.after='tampered'");
  try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateConsequenceInputs(read));}
  rows("MATCH(:Activity{id:10})-[r:"+type+"]->() DELETE r");
  try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateConsequenceInputs(read));}  }
 }

 @Test void denseIncomingClassifierAndCharacterizationPhantomsCannotBypassAnEmptySelectedSet()throws Exception {
  for(var type:List.of(GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.CHARACTERIZED)) {
   seed();rows("MATCH(a:Context{id:'a'}) CREATE(a)-[:HAS_PROVENANCE]->(:Provenance{id:'a.PROVENANCE'})-[:HAS_CHILD]->(:Activity{id:10,outcome:'SUCCESS'})");
   rows("MATCH(o:Observation{id:1}) UNWIND range(100,180) AS id CREATE(:Activity{id:id,ownerContextId:'a'})-[:HAS_CHILD]->(o)");
   var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();Future<?> writer;
   try {
    try(var guard=tx("a")) {
     var read=guard.captureConsequenceInputs(List.of(1L),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,type,GraphModel.Relationship.Direction.INCOMING)));
     assertTrue(read.edges().isEmpty());
     writer=executor.submit(()->{thread.set(Thread.currentThread());var activity=(org.integratedmodelling.klab.api.provenance.impl.ActivityImpl)org.integratedmodelling.klab.api.provenance.Activity.of(org.integratedmodelling.klab.api.provenance.Activity.Type.CLASSIFICATION);
       activity.setId(10);new ClassificationPersistenceTest.Fixture("b").link(activity,observation(1),type,null);});
     long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
     while(System.nanoTime()<deadline&&!writer.isDone()){var current=thread.get();if(current!=null&&Arrays.stream(current.getStackTrace()).anyMatch(frame->frame.getClassName().contains("locking.forseti"))){waiting=true;break;}Thread.sleep(5);}
     assertTrue(waiting,"Selected empty native certificate set must block dense participating phantom writer");
    }writer.get(5,TimeUnit.SECONDS);
   }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
 }

 static KnowledgeGraph.ConsequenceReadSet batchRead(List<Long> ids,List<Long> configurations)throws Exception {
  try(var transaction=tx("a")){return transaction.captureConsequenceInputs(ids,configurations.stream().map(id->new KnowledgeGraph.ConsequenceEdgeSelection(id,GraphModel.Relationship.EMERGED_FROM,GraphModel.Relationship.Direction.OUTGOING)).toList());}
 }
 void batchSeed() {
  rows("MATCH(n:Observation{id:1}) SET n.observable='test:Network',n.configurationRevision=1,n.configurationHistory=['first-prior']");
  rows("CREATE(:Observation{id:3,ownerContextId:'a',observable:'test:Network',configurationRevision:1,configurationHistory:['second-prior']}),(:Observation{id:4,ownerContextId:'a'})");
 }
 @Test void crossReadBatchValidatesAllOriginalRowsBeforeMutatingAnyConfiguration()throws Exception {
  batchSeed();var read=batchRead(List.of(1L,2L,3L,4L),List.of(1L,3L));var second=configuration();second.setId(3);
  try(var transaction=tx("a")){transaction.updateConfigurations(List.of(new KnowledgeGraph.ConfigurationUpdate(configuration(),read,List.of(2L),2,"first-next"),
      new KnowledgeGraph.ConfigurationUpdate(second,read,List.of(4L),2,"second-next")));}
  assertArrayEquals(new String[]{"first-prior","first-next"},(String[])rows("MATCH(n:Observation{id:1}) RETURN n.configurationHistory AS history").getFirst().get("history"));
  assertArrayEquals(new String[]{"second-prior","second-next"},(String[])rows("MATCH(n:Observation{id:3}) RETURN n.configurationHistory AS history").getFirst().get("history"));
 }
 @Test void staleLaterBatchRowRollsBackWithoutMutatingEarlierValidConfiguration()throws Exception {
  batchSeed();var first=batchRead(List.of(1L,2L),List.of(1L));var later=batchRead(List.of(3L,4L),List.of(3L));var second=configuration();second.setId(3);
  rows("MATCH(n:Observation{id:3}) SET n.configurationRevision=2");
  try(var transaction=tx("a")){assertThrows(RuntimeException.class,()->transaction.updateConfigurations(List.of(
      new KnowledgeGraph.ConfigurationUpdate(configuration(),first,List.of(2L),2,"forbidden-first"),new KnowledgeGraph.ConfigurationUpdate(second,later,List.of(4L),2,"forbidden-later"))));}
  assertArrayEquals(new String[]{"first-prior"},(String[])rows("MATCH(n:Observation{id:1}) RETURN n.configurationHistory AS history").getFirst().get("history"));
  assertEquals(1L,rows("MATCH(n:Observation{id:1}) RETURN n.configurationRevision AS revision").getFirst().get("revision"));
 }


 @Test void managedDependenciesReplaceOnlyTheirRoleAndRetainOriginalReceiptInHistory()throws Exception {
  rows("MATCH(n:Observation{id:1}),(p:Observation{id:2}) SET n.observable='test:Network',n.configurationRevision=1 CREATE(q:Observation{id:3,ownerContextId:'a'}),(q)-[:AFFECTS{role:'configuration-input',receipt:'old-proof'}]->(n),(p)-[:AFFECTS{role:'scientific-model',receipt:'preserve'}]->(n)");
  KnowledgeGraph.ConsequenceReadSet read;
  try(var capture=tx("a")){read=capture.captureConsequenceInputs(List.of(1L,2L,3L),List.of(
   new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.EMERGED_FROM,GraphModel.Relationship.Direction.OUTGOING),
   new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.AFFECTS,GraphModel.Relationship.Direction.INCOMING)));}
  var replacement=configuration();replacement.getMetadata().put("klab.configuration.managedInputs",true);
  replacement.getMetadata().put("klab.configuration.authorities","source-proof");replacement.getMetadata().put("klab.configuration.explanation","model-proof");
  try(var update=tx("a")){update.updateConfiguration(replacement,read,List.of(2L),2,"{\"revision\":2}");}
  assertEquals(0L,rows("MATCH(:Observation{id:3})-[r:AFFECTS{role:'configuration-input'}]->(:Observation{id:1}) RETURN count(r) AS n").getFirst().get("n"));
  assertEquals(1L,rows("MATCH(:Observation{id:2})-[r:AFFECTS{role:'scientific-model',receipt:'preserve'}]->(:Observation{id:1}) RETURN count(r) AS n").getFirst().get("n"));
  assertEquals("source-proof",rows("MATCH(:Observation{id:2})-[r:AFFECTS{role:'configuration-input'}]->(:Observation{id:1}) RETURN r.authorities AS proof").getFirst().get("proof"));
  var history=(String[])rows("MATCH(n:Observation{id:1}) RETURN n.configurationHistory AS history").getFirst().get("history");
  assertTrue(history[0].contains("old-proof"));assertTrue(history[0].contains("previousManagedDependencies"));
 }
 @Test void managedDependencyPublicationNeedsItsExactSelectedIncomingSet()throws Exception {
  rows("MATCH(n:Observation{id:1}) SET n.observable='test:Network',n.configurationRevision=1");
  var read=configurationRead();var replacement=configuration();replacement.getMetadata().put("klab.configuration.managedInputs",true);
  try(var update=tx("a")){assertThrows(RuntimeException.class,()->update.updateConfiguration(replacement,read,List.of(2L),2,"{}"));}
  assertEquals(1L,rows("MATCH(n:Observation{id:1}) RETURN n.configurationRevision AS r").getFirst().get("r"));
 }

 @Test void actualSimulationSourceActivityIsGuardedButForeignAndUnsupportedProofsReject()throws Exception {
  rows("MATCH(a:Context{id:'a'}),(b:Context{id:'b'}),(o:Observation{id:1}) CREATE(a)-[:HAS_PROVENANCE]->(:Provenance{id:'a.PROVENANCE'})-[:HAS_CHILD]->(proof:Activity{id:10,outcome:'SUCCESS'}),"
      +"(b)-[:HAS_PROVENANCE]->(:Provenance{id:'b.PROVENANCE'})-[:HAS_CHILD]->(foreign:Activity{id:11,outcome:'SUCCESS'}),(proof)-[:SIMULATED{proof:'actual-execution'}]->(o),(foreign)-[:SIMULATED]->(o)");
  var selected=List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.SIMULATED,GraphModel.Relationship.Direction.INCOMING));
  KnowledgeGraph.ConsequenceReadSet read;
  try(var capture=tx("a")){read=capture.captureConsequenceInputs(List.of(1L,10L),selected);}
  assertEquals(2,read.edges().size());assertTrue(read.assets().stream().anyMatch(a->a.id()==10&&a.labels().contains("Activity")));
  try(var capture=tx("a")){assertThrows(RuntimeException.class,()->capture.captureConsequenceInputs(List.of(1L,11L),selected));}
  assertThrows(IllegalArgumentException.class,()->new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.RESOLVED,GraphModel.Relationship.Direction.INCOMING));
  rows("MATCH(:Activity{id:10})-[r:SIMULATED]->() SET r.proof='changed'");
  try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateConsequenceInputs(read));}
 }

 @Test void ownedCreatedActivityIsRawGuardEvidenceAndNeverBypassesContextOwnership()throws Exception {
  rows("MATCH(a:Context{id:'a'}),(b:Context{id:'b'}),(o:Observation{id:1}) CREATE(a)-[:HAS_PROVENANCE]->(:Provenance{id:'a.PROVENANCE'})-[:HAS_CHILD]->(proof:Activity{id:10,outcome:'SUCCESS'}),"
      +"(b)-[:HAS_PROVENANCE]->(:Provenance{id:'b.PROVENANCE'})-[:HAS_CHILD]->(foreign:Activity{id:11,outcome:'SUCCESS'}),(proof)-[:CREATED]->(o),(foreign)-[:CREATED]->(o)");
  var selected=List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.CREATED,GraphModel.Relationship.Direction.INCOMING));
  KnowledgeGraph.ConsequenceReadSet read;try(var capture=tx("a")){read=capture.captureConsequenceInputs(List.of(1L,10L),selected);}
  assertEquals(2,read.edges().size());try(var capture=tx("a")){assertThrows(RuntimeException.class,()->capture.captureConsequenceInputs(List.of(1L,11L),selected));}
  rows("MATCH(:Activity{id:10})-[r:CREATED]->() DELETE r");
  try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateConsequenceInputs(read));}
  // No completion or semantic authority is synthesized by raw graph capture.
  assertFalse(read.assets().stream().filter(a->a.id()==10).findFirst().orElseThrow().nativeProperties().containsKey("semanticExecutionEvidence"));
 }

 @Test void applicationContextIsADependencyNotAMemberAndCannotChangeIdentity()throws Exception {
  rows("MATCH(n:Observation{id:1}) SET n.observable='test:Network',n.configurationRevision=1,n.`klab.configuration.applicationContext`=3 CREATE(:Observation{id:3,ownerContextId:'a'})");
  KnowledgeGraph.ConsequenceReadSet read;var selections=List.of(new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.EMERGED_FROM,GraphModel.Relationship.Direction.OUTGOING),new KnowledgeGraph.ConsequenceEdgeSelection(1,GraphModel.Relationship.AFFECTS,GraphModel.Relationship.Direction.INCOMING));
  try(var capture=tx("a")){read=capture.captureConsequenceInputs(List.of(1L,2L,3L),selections);}
  var replacement=configuration();replacement.getMetadata().put("klab.configuration.managedInputs",true);replacement.getMetadata().put("klab.configuration.applicationContext",3L);
  replacement.getMetadata().put("klab.configuration.authorities","source-proof");replacement.getMetadata().put("klab.configuration.explanation","model-proof");
  try(var update=tx("a")){update.updateConfiguration(replacement,read,List.of(2L),2,"{}");}
  assertEquals(1L,rows("MATCH(:Observation{id:3})-[r:AFFECTS{role:'configuration-input'}]->(:Observation{id:1}) RETURN count(r) AS n").getFirst().get("n"));
  assertEquals(0L,rows("MATCH(:Observation{id:1})-[r:EMERGED_FROM]->(:Observation{id:3}) RETURN count(r) AS n").getFirst().get("n"));
  try(var capture=tx("a")){read=capture.captureConsequenceInputs(List.of(1L,2L,3L),selections);}
  var original=read;replacement.getMetadata().put("klab.configuration.applicationContext",2L);
  try(var update=tx("a")){assertThrows(RuntimeException.class,()->update.updateConfiguration(replacement,original,List.of(2L),3,"{}"));}
  assertEquals(2L,rows("MATCH(n:Observation{id:1}) RETURN n.configurationRevision AS r").getFirst().get("r"));
 }
}
