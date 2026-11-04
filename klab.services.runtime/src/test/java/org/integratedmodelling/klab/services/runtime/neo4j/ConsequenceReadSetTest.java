package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.*;

/** Real embedded Cypher/locks, with no Bolt/HTTP server or shared graph. */
class ConsequenceReadSetTest {
  @BeforeAll static void start(){ClassificationPersistenceTest.start();}
  @AfterAll static void stop(){ClassificationPersistenceTest.stop();}
  @BeforeEach void seed(){
    rows("MATCH (n) DETACH DELETE n");
    rows("CREATE (a:Context {id:'a'}),(b:Context {id:'b'}),"
        + "(c:Cohort {id:10,ownerContextId:'a',observable:'region'}),"
        + "(bearer:Observation {id:41,ownerContextId:'a',observable:'region'}),"
        + "(process:Observation {id:43,ownerContextId:'a',observable:'process',semanticRevision:2}),"
        + "(source:Observation {id:44,ownerContextId:'a',observable:'source'}),"
        + "(target:Observation {id:45,ownerContextId:'a',observable:'target'}),"
        + "(other:Observation {id:46,ownerContextId:'a',observable:'other'}),"
        + "(:Observation {id:99,ownerContextId:'b',observable:'foreign'}),"
        + "(a)-[:HAS_CHILD]->(c),(c)-[:HAS_MEMBER]->(bearer),(bearer)-[:HAS_CHILD]->(process),"
        + "(process)-[:HAS_RELATIONSHIP_SOURCE {evidence:'original'}]->(source),"
        + "(process)-[:HAS_RELATIONSHIP_TARGET]->(target),"
        + "(process)-[:HAS_GEOMETRY]->(:Geometry {definition:'S2'}),"
        + "(process)-[:PERCEIVES_GEOMETRY]->(:Geometry {definition:'T1'})");
  }
  private static List<Map<String,Object>> rows(String cypher){return ClassificationPersistenceTest.rows(cypher,Map.of());}
  private static KnowledgeGraph.Transaction tx(String context){return new ClassificationPersistenceTest.Fixture(context).createTransaction(mock(ContextScope.class));}
  private static List<ConsequenceEdgeSelection> selections(){return List.of(
      new ConsequenceEdgeSelection(43,GraphModel.Relationship.HAS_CHILD,GraphModel.Relationship.Direction.INCOMING),
      new ConsequenceEdgeSelection(43,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,GraphModel.Relationship.Direction.OUTGOING),
      new ConsequenceEdgeSelection(43,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,GraphModel.Relationship.Direction.OUTGOING));}
  private static ConsequenceReadSet capture()throws Exception {
    try(var tx=tx("a")){return tx.captureConsequenceInputs(List.of(45L,43L,41L,44L),selections());}
  }
  private static void reject(ConsequenceReadSet read)throws Exception {
    try(var tx=tx("a")){assertThrows(RuntimeException.class,()->tx.validateConsequenceInputs(read));}
  }
  private static ObservationImpl observation(long id){var o=new ObservationImpl();o.setId(id);return o;}

  private static ConsequenceReadSet completeBearerFamily()throws Exception {
    rows("MATCH(b:Observation{id:41}),(a:Observation{id:44}),(u:Observation{id:46}) SET u.resolvedCoverage=0 CREATE(b)-[:HAS_CHILD]->(a),(b)-[:HAS_CHILD]->(u)");
    try(var capture=tx("a")){return capture.captureConsequenceInputs(List.of(41L,43L,44L,46L),List.of(
        new ConsequenceEdgeSelection(41,GraphModel.Relationship.HAS_CHILD,GraphModel.Relationship.Direction.OUTGOING),
        new ConsequenceEdgeSelection(46,GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.Direction.INCOMING)));}
  }
  @Test void excludedUnresolvedSiblingPayloadStillInvalidatesTheFullCapturedFamily()throws Exception {
    var read=completeBearerFamily();assertTrue(read.assetIds().contains(46L));
    rows("MATCH(n:Observation{id:46}) SET n.observable='became-an-ingredient'");reject(read);
  }
  @Test void newLocalSiblingCannotEvadeAProjectedRecipeDomain()throws Exception {
    var read=completeBearerFamily();rows("MATCH(b:Observation{id:41}) CREATE(b)-[:HAS_CHILD]->(:Observation{id:47,ownerContextId:'a',observable:'new-ingredient'})");reject(read);
  }
  @Test void newAttributionOnAnExcludedSiblingInvalidatesItsCapturedMeaning()throws Exception {
    var read=completeBearerFamily();rows("MATCH(n:Observation{id:46}) CREATE(:Activity{id:500,ownerContextId:'a'})-[:CLASSIFIED]->(n)");reject(read);
  }
  @Test void exactIndependentInputsAndDirectionalStructureCanBeRevalidated()throws Exception {
    var read=capture();assertEquals(List.of(41L,43L,44L,45L),read.assetIds());assertEquals(3,read.edges().size());
    var process=read.assets().stream().filter(a->a.id()==43).findFirst().orElseThrow();
    assertEquals("S2",process.geometry());assertEquals("T1",process.perceivedGeometry());
    assertEquals(2L,process.properties().get("semanticRevision"));
    rows("MATCH (b:Observation {id:41}),(n:Observation {id:46}) CREATE (b)-[:HAS_CHILD]->(n)");
    try(var tx=tx("a")){tx.validateConsequenceInputs(read);}
  }
  @Test void nativeGuardRejectsNoncanonicalProducerOrderWithoutAnyGraphMutation() throws Exception {
    var canonical=capture();var reversed=new ArrayList<>(canonical.selections());Collections.reverse(reversed);
    assertNotEquals(canonical.selections(),reversed);
    var malformed=new ConsequenceReadSet(canonical.contextId(),canonical.assets(),reversed,canonical.edges());
    reject(malformed);
    try(var transaction=tx("a")){transaction.validateConsequenceInputs(canonical);}
    // Canonicalization never authorizes a changed scientific input.
    rows("MATCH(n:Observation{id:43}) SET n.semanticRevision=3");reject(canonical);
  }
  @Test void payloadAndRevisionDriftCannotBeHiddenByAnUnchangedObservable()throws Exception {
    var read=capture();rows("MATCH (n:Observation {id:43}) SET n.name='changed',n.semanticRevision=3");reject(read);
  }
  @Test void supportChangesAreGuarded()throws Exception {
    for(var edge:List.of("HAS_GEOMETRY","PERCEIVES_GEOMETRY")){
      seed();var read=capture();rows("MATCH (:Observation {id:43})-[:"+edge+"]->(g) SET g.definition='changed'");reject(read);
    }
  }
  @Test void selectedEndpointAdditionDeletionReplacementAndPropertyChangesAreGuarded()throws Exception {
    for(var mutation:List.of(
        "MATCH (p:Observation {id:43}),(o:Observation {id:46}) CREATE (p)-[:HAS_RELATIONSHIP_SOURCE]->(o)",
        "MATCH (:Observation {id:43})-[r:HAS_RELATIONSHIP_SOURCE]->() DELETE r",
        "MATCH (p:Observation {id:43})-[r:HAS_RELATIONSHIP_SOURCE]->(o) DELETE r CREATE (p)-[:HAS_RELATIONSHIP_SOURCE {evidence:'original'}]->(o)",
        "MATCH (:Observation {id:43})-[r:HAS_RELATIONSHIP_SOURCE]->() SET r.evidence='changed'")){
      seed();var read=capture();rows(mutation);reject(read);
    }
  }
  @Test void capturedAssetsCannotDisappearOrBeReplacedWithMatchingPublicIds()throws Exception {
    var read=capture();rows("MATCH (p:Observation {id:43}) DETACH DELETE p CREATE (:Observation {id:43,ownerContextId:'a',observable:'process',semanticRevision:2})");reject(read);
  }
  @Test void missingForeignAmbiguousAndInvalidInputsFailClosed()throws Exception {
    for(long id:List.of(999L,99L,0L,-1L))try(var tx=tx("a")){
      assertThrows(RuntimeException.class,()->tx.captureConsequenceInputs(List.of(id),List.of()));
    }
    try(var tx=tx("a")){
      assertThrows(RuntimeException.class,()->tx.captureConsequenceInputs(List.of(41L),selections()));
    }
    rows("CREATE (:Observation {id:43,ownerContextId:'a'})");
    try(var tx=tx("a")){assertThrows(RuntimeException.class,()->tx.captureConsequenceInputs(List.of(43L),List.of()));}
  }
  @Test void contextMismatchAndCaughtValidationFailureRollBackEarlierWrites()throws Exception {
    var read=capture();
    try(var tx=tx("b")){assertThrows(RuntimeException.class,()->tx.validateConsequenceInputs(read));}
    var forged=new ConsequenceReadSet("other",read.assets(),read.selections(),read.edges());
    try(var transaction=tx("a")) {
      var nativeField=transaction.getClass().getDeclaredField("transaction");nativeField.setAccessible(true);
      var nativeTx=(org.neo4j.driver.Transaction)nativeField.get(transaction);
      nativeTx.run("MATCH (n:Observation {id:41}) SET n.tentative=true",Map.of()).consume();
      assertThrows(RuntimeException.class,()->transaction.validateConsequenceInputs(forged));
    }
    assertNull(rows("MATCH (n:Observation {id:41}) RETURN n.tentative AS value").getFirst().get("value"));
  }
  @Test void nativePropertiesAreDetachedAndBinaryValuesHaveValueEquality(){
    var bytes=new byte[]{1,2};var list=new ArrayList<Object>(List.of(bytes));var properties=new HashMap<String,Object>();properties.put("payload",list);
    var snapshot=new ConsequenceAssetSnapshot(41,"element",List.of("Observation"),properties,null,null);
    bytes[0]=9;list.clear();properties.clear();
    var copy=snapshot.nativeProperties();assertArrayEquals(new byte[]{1,2},(byte[])((List<?>)copy.get("payload")).getFirst());
    ((byte[])((List<?>)copy.get("payload")).getFirst())[0]=7;
    assertArrayEquals(new byte[]{1,2},(byte[])((List<?>)snapshot.nativeProperties().get("payload")).getFirst());
    assertThrows(UnsupportedOperationException.class,()->snapshot.properties().clear());
    assertThrows(IllegalArgumentException.class,()->new ConsequenceAssetSnapshot(41,"element",List.of("Observation"),
        Map.of("mutable",new java.util.concurrent.atomic.AtomicLong(1)),null,null));
  }
  @Test void capturedMaterializationDoesNotFetchNewerMeaningPayloadOrSupport()throws Exception {
    var graph=new ClassificationPersistenceTest.Fixture("a");
    var original=new ClassificationPersistenceTest().observation(43,"before");original.setUrn("a.43");
    original.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("S2"));
    var properties=graph.properties(original);properties.put("ownerContextId","a");
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:43}) SET n=$properties",Map.of("properties",properties));
    ConsequenceAssetSnapshot snapshot;
    try(var transaction=graph.createTransaction(mock(ContextScope.class))){snapshot=transaction.captureConsequenceInputs(List.of(43L),List.of()).assets().getFirst();}
    rows("MATCH (n:Observation {id:43}) SET n.observable='newer',n.name='newer' WITH n MATCH (n)-[:HAS_GEOMETRY]->(g) SET g.definition='S3'");
    var scope=mock(ContextScope.class);var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class))
        .thenReturn(mock(org.integratedmodelling.klab.api.services.RuntimeService.class));
    when(reasoner.resolveObservable("before")).thenReturn(original.getObservable());
    var decoded=graph.materializeConsequenceObservation(snapshot,scope);
    assertSame(original.getObservable(),decoded.getObservable());assertEquals(original.getName(),decoded.getName());
    assertEquals(org.integratedmodelling.klab.api.geometry.Geometry.create(snapshot.geometry()).encode(),decoded.getGeometry().encode());
    assertEquals(snapshot.id(),decoded.getId());verify(reasoner,never()).resolveObservable("newer");
  }

  private static void awaitNativeWait(Thread thread,Future<?> future)throws Exception {
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    while(System.nanoTime()<deadline){
      if(future.isDone()){future.get();fail("Writer completed while guarded native lock was held");}
      if(Arrays.stream(thread.getStackTrace()).anyMatch(frame->frame.getClassName().contains("locking.forseti")))return;
      Thread.sleep(5);
    }
    fail("Writer did not reach the native lock wait");
  }
  @Test void sparseAndDenseCrossContextStructuralWritersWaitForGuardedInputs()throws Exception {
    for(boolean dense:List.of(false,true)){
      seed();if(dense)rows("MATCH (p:Observation {id:43}) UNWIND range(100,180) AS id CREATE (p)-[:HAS_RELATIONSHIP_SOURCE]->(:Observation {id:id,ownerContextId:'a'})");
      var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();var entered=new CountDownLatch(1);Future<?> writer;
      try {
        try(var guard=tx("a")){
          guard.captureConsequenceInputs(List.of(43L),List.of(selections().get(1)));
          writer=executor.submit(()->{thread.set(Thread.currentThread());try(var foreign=tx("b")){
            entered.countDown();foreign.link(observation(43),observation(46),GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE);
          }catch(Exception failure){throw new RuntimeException(failure);}});
          assertTrue(entered.await(5,TimeUnit.SECONDS));awaitNativeWait(thread.get(),writer);
        }
        writer.get(5,TimeUnit.SECONDS);
        assertEquals(1L,rows("MATCH (:Observation {id:43})-[r:HAS_RELATIONSHIP_SOURCE]->(:Observation {id:46}) RETURN count(r) AS n").getFirst().get("n"));
      }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
  }
  @Test void directStructuralWritersParticipateInTheSameProtocol()throws Exception {
    var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();var entered=new CountDownLatch(1);Future<?> writer;
    try {
      try(var guard=tx("a")) {
        guard.captureConsequenceInputs(List.of(43L),List.of(selections().get(1)));
        writer=executor.submit(()->{thread.set(Thread.currentThread());entered.countDown();
          new ClassificationPersistenceTest.Fixture("b").link(observation(43),observation(46),GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,null);});
        assertTrue(entered.await(5,TimeUnit.SECONDS));awaitNativeWait(thread.get(),writer);
      }
      writer.get(5,TimeUnit.SECONDS);
    }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
  @Test void guardedRelationshipPropertiesAndDeletionWaitUntilValidationFinishes()throws Exception {
    for(var mutation:List.of("SET r.evidence='changed'","DELETE r")) {
      seed();var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();var entered=new CountDownLatch(1);Future<?> writer;
      try {
        try(var guard=tx("a")) {
          guard.captureConsequenceInputs(List.of(43L),List.of(selections().get(1)));
          writer=executor.submit(()->{thread.set(Thread.currentThread());entered.countDown();rows("MATCH (:Observation {id:43})-[r:HAS_RELATIONSHIP_SOURCE]->() "+mutation);});
          assertTrue(entered.await(5,TimeUnit.SECONDS));awaitNativeWait(thread.get(),writer);
        }
        writer.get(5,TimeUnit.SECONDS);
      }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
  }

  private record CompetingOutcome(long id,boolean committed,String failure) {}
  private static CompetingOutcome compete(long owned,long other,String context,CountDownLatch ready) {
    try(var transaction=tx(context)) {
      transaction.lockConsequenceInputs(List.of(owned));
      var field=transaction.getClass().getDeclaredField("transaction");field.setAccessible(true);
      var nativeTx=(org.neo4j.driver.Transaction)field.get(transaction);
      nativeTx.run("MATCH (n:Observation {id:$id}) SET n.tentative=true",Map.of("id",owned)).consume();
      ready.countDown();if(!ready.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Competing writer not ready");
      // Simulate independent multi-step native writers whose complete input union was not known.
      // A Neo4j abort must escape, not be retried while retaining the earlier write.
      transaction.lockConsequenceInputs(List.of(other));
      return new CompetingOutcome(owned,true,null);
    } catch(Exception failure) {
      var names=new ArrayList<String>();for(Throwable cause=failure;cause!=null;cause=cause.getCause())names.add(cause.getClass().getName());
      return new CompetingOutcome(owned,false,String.join(" -> ",names));
    }
  }
  @Test void competingWriterDeadlockAbortsAndRollsBackRatherThanWeakeningTheGuard()throws Exception {
    var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);
    try {
      var a=executor.submit(()->compete(41,43,"a",ready));
      var b=executor.submit(()->compete(43,41,"b",ready));
      var outcomes=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
      assertEquals(1,outcomes.stream().filter(CompetingOutcome::committed).count(),outcomes.toString());
      var rejected=outcomes.stream().filter(outcome->!outcome.committed()).findFirst().orElseThrow();
      assertTrue(rejected.failure().contains("Deadlock"),rejected.failure());
      assertNull(rows("MATCH (n:Observation {id:"+rejected.id()+"}) RETURN n.tentative AS value").getFirst().get("value"));
      var accepted=outcomes.stream().filter(CompetingOutcome::committed).findFirst().orElseThrow();
      assertEquals(true,rows("MATCH (n:Observation {id:"+accepted.id()+"}) RETURN n.tentative AS value").getFirst().get("value"));
    }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }

  @Test void nativeCloseDoesNotWaitForGraphMonitorWhileItHoldsNativeLocks()throws Exception {
    var graph=new ClassificationPersistenceTest.Fixture("a");var executor=Executors.newFixedThreadPool(2);
    var captured=new CountDownLatch(1);var monitorHeld=new CountDownLatch(1);
    var closeNow=new CountDownLatch(1);var releaseMonitor=new CountDownLatch(1);
    try {
      var nativeOwner=executor.submit(()->{
        try(var transaction=graph.createTransaction(mock(ContextScope.class))) {
          transaction.captureConsequenceInputs(List.of(43L),List.of());captured.countDown();
          if(!closeNow.await(5,TimeUnit.SECONDS))throw new AssertionError("Close signal missing");
        }catch(Exception failure){throw new RuntimeException(failure);}
      });
      assertTrue(captured.await(5,TimeUnit.SECONDS));
      var monitorOwner=executor.submit(()->{synchronized(graph){monitorHeld.countDown();
        try{if(!releaseMonitor.await(5,TimeUnit.SECONDS))throw new AssertionError("Monitor release missing");}
        catch(InterruptedException failure){Thread.currentThread().interrupt();throw new RuntimeException(failure);}}});
      assertTrue(monitorHeld.await(5,TimeUnit.SECONDS));closeNow.countDown();
      nativeOwner.get(2,TimeUnit.SECONDS);assertFalse(monitorOwner.isDone());
      releaseMonitor.countDown();monitorOwner.get(5,TimeUnit.SECONDS);
    }finally{closeNow.countDown();releaseMonitor.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
  @Test void offlineAtCloseRollsBackInsteadOfPublishingEarlierNativeWrites()throws Exception {
    var online=new java.util.concurrent.atomic.AtomicBoolean(true);
    var graph=new ClassificationPersistenceTest.Fixture("a"){@Override public boolean isOnline(){return online.get();}};
    var transaction=graph.createTransaction(mock(ContextScope.class));
    transaction.captureConsequenceInputs(List.of(43L),List.of());
    var field=transaction.getClass().getDeclaredField("transaction");field.setAccessible(true);
    ((org.neo4j.driver.Transaction)field.get(transaction)).run("MATCH (n:Observation {id:43}) SET n.tentative=true",Map.of()).consume();
    online.set(false);assertThrows(RuntimeException.class,transaction::close);
    assertNull(rows("MATCH (n:Observation {id:43}) RETURN n.tentative AS value").getFirst().get("value"));
  }
  private static org.neo4j.driver.Driver allocationDriver() {
    var driver=mock(org.neo4j.driver.Driver.class);var session=mock(org.neo4j.driver.Session.class);
    when(driver.session()).thenReturn(session);
    when(session.beginTransaction(any(org.neo4j.driver.TransactionConfig.class))).thenAnswer(call->{
      var embedded=ClassificationPersistenceTest.database.defaultDatabaseService().beginTx();
      var nativeTx=mock(org.neo4j.driver.Transaction.class);var open=new java.util.concurrent.atomic.AtomicBoolean(true);
      when(nativeTx.isOpen()).thenAnswer(ignored->open.get());
      when(nativeTx.run(anyString(),anyMap())).thenAnswer(query->{
        var records=new ArrayList<org.neo4j.driver.Record>();
        try(var result=embedded.execute(query.getArgument(0),query.getArgument(1))) {
          while(result.hasNext()) {var row=result.next();var record=mock(org.neo4j.driver.Record.class);
            when(record.get(anyString())).thenAnswer(key->org.neo4j.driver.Values.value(row.get(key.getArgument(0))));
            var value=mock(org.neo4j.driver.Value.class);
            when(value.asNode()).thenReturn(mock(org.neo4j.driver.types.Node.class));when(record.get(0)).thenReturn(value);records.add(record);
          }
        }
        var result=mock(org.neo4j.driver.Result.class);when(result.list()).thenReturn(records);
        var iterator=records.iterator();when(result.hasNext()).thenAnswer(ignored->iterator.hasNext());
        when(result.next()).thenAnswer(ignored->iterator.next());return result;
      });
      doAnswer(ignored->{embedded.commit();open.set(false);return null;}).when(nativeTx).commit();
      doAnswer(ignored->{embedded.rollback();open.set(false);return null;}).when(nativeTx).rollback();return nativeTx;
    });
   when(driver.executableQuery(anyString())).thenAnswer(call->{
    String cypher=call.getArgument(0);var parameters=new AtomicReference<Map<String,Object>>(Map.of());
    var query=mock(org.neo4j.driver.ExecutableQuery.class);
    when(query.withParameters(anyMap())).thenAnswer(p->{parameters.set(p.getArgument(0));return query;});
    when(query.execute()).thenAnswer(ignored->{
     try(var tx=ClassificationPersistenceTest.database.defaultDatabaseService().beginTx()) {

      var records=new ArrayList<org.neo4j.driver.Record>();List<String> keys;
      try(var rows=tx.execute(cypher,parameters.get())) {
       keys=rows.columns();
       while(rows.hasNext()) {var row=rows.next();var record=mock(org.neo4j.driver.Record.class);
        when(record.get(anyString())).thenAnswer(k->org.neo4j.driver.Values.value(row.get(k.getArgument(0))));records.add(record);}
      }
      tx.commit();var eager=mock(org.neo4j.driver.EagerResult.class);when(eager.records()).thenReturn(records);when(eager.keys()).thenReturn(keys);return eager;
     }
    });return query;
   });

    return driver;
  }
  private static ClassificationPersistenceTest.Fixture allocationGraph() {
    return new ClassificationPersistenceTest.Fixture("a"){{driver=allocationDriver();}};
  }
  @Test void guardedStoreAllocatesWithoutGraphMonitorHeldByCompetingStandaloneUpdate()throws Exception {
    var graph=allocationGraph();var executor=Executors.newFixedThreadPool(2);
    var captured=new CountDownLatch(1);var storeNow=new CountDownLatch(1);var writerThread=new AtomicReference<Thread>();
    try {
      var owner=executor.submit(()->{
        try(var transaction=graph.createTransaction(mock(ContextScope.class))) {
          transaction.captureConsequenceInputs(List.of(43L),List.of());captured.countDown();
          if(!storeNow.await(5,TimeUnit.SECONDS))throw new AssertionError("Store signal missing");
          var activity=org.integratedmodelling.klab.api.provenance.Activity.of(
              org.integratedmodelling.klab.api.provenance.Activity.Type.SUBMISSION);
          transaction.store(activity);return activity.getId();
        }catch(Exception failure){throw new RuntimeException(failure);}
      });
      assertTrue(captured.await(5,TimeUnit.SECONDS));
      var writer=executor.submit(()->{writerThread.set(Thread.currentThread());
        graph.update(new ClassificationPersistenceTest().observation(43,"process"),null);});
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
      while(writerThread.get()==null&&System.nanoTime()<deadline)Thread.onSpinWait();
      assertNotNull(writerThread.get());awaitNativeWait(writerThread.get(),writer);
      storeNow.countDown();long id=owner.get(5,TimeUnit.SECONDS);assertTrue(id>0);writer.get(5,TimeUnit.SECONDS);
      assertEquals(1L,rows("MATCH (n:Activity {id:"+id+"}) RETURN count(n) AS n").getFirst().get("n"));
    }finally{storeNow.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
  @Test void sameInstanceConcurrentAllocationRemainsUnique()throws Exception {
    var graph=allocationGraph();var executor=Executors.newFixedThreadPool(4);
    try {
      var futures=new ArrayList<Future<Long>>();for(int i=0;i<24;i++)futures.add(executor.submit(graph::nextKey));
      var ids=new HashSet<Long>();for(var future:futures){long id=future.get(5,TimeUnit.SECONDS);assertTrue(id>0);assertTrue(ids.add(id));}
      assertEquals(24,ids.size());
    }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
  }
  @Test void offlineAllocatorFailsAndGuardedStoreRollsBack()throws Exception {
    var online=new java.util.concurrent.atomic.AtomicBoolean(true);
    var graph=new ClassificationPersistenceTest.Fixture("a"){{driver=allocationDriver();}
      @Override public boolean isOnline(){return online.get();}};
    var transaction=graph.createTransaction(mock(ContextScope.class));
    transaction.captureConsequenceInputs(List.of(43L),List.of());
    var field=transaction.getClass().getDeclaredField("transaction");field.setAccessible(true);
    ((org.neo4j.driver.Transaction)field.get(transaction)).run("MATCH (n:Observation {id:43}) SET n.tentative=true",Map.of()).consume();
    online.set(false);assertEquals(-1,graph.nextKey());
    assertThrows(RuntimeException.class,()->transaction.store(org.integratedmodelling.klab.api.provenance.Activity.of(
        org.integratedmodelling.klab.api.provenance.Activity.Type.SUBMISSION)));
    transaction.close();
    assertNull(rows("MATCH (n:Observation {id:43}) RETURN n.tentative AS value").getFirst().get("value"));
  }

}
