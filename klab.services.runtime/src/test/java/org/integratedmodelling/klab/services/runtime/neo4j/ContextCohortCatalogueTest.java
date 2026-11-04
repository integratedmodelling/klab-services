package org.integratedmodelling.klab.services.runtime.neo4j;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.junit.jupiter.api.*;
/** Real server-disabled Neo4j: exact catalogue, header drift and context lock protocol. */
class ContextCohortCatalogueTest {
 @BeforeAll static void start(){ClassificationPersistenceTest.start();}
 @AfterAll static void stop(){ClassificationPersistenceTest.stop();}
 @BeforeEach void seed(){rows("MATCH(n) DETACH DELETE n");rows("CREATE(a:Context{id:'a'}),(b:Context{id:'b'}),(c:Cohort{id:10,ownerContextId:'a',observable:'test:RoadConnection'}),(a)-[:HAS_CHILD]->(c)");}
 static List<Map<String,Object>> rows(String cypher){return ClassificationPersistenceTest.rows(cypher,Map.of());}
 static KnowledgeGraph.Transaction tx(String context)throws Exception{return ConfigurationDependencyReadSetTest.tx(context);}
 static CohortImpl cohort(long id){var c=new CohortImpl();c.setId(id);return c;}
 static KnowledgeGraph.ContextCohortCatalogue capture(String context)throws Exception {try(var tx=tx(context)){return tx.captureContextCohortCatalogue();}}
 @Test void completeCatalogueRetainsStringContextAndRejectsHeaderOrNativeIdentityDrift()throws Exception {
  var expected=capture("a");assertEquals("a",expected.contextId());assertEquals(List.of(10L),expected.cohortIds());assertEquals("a",expected.edges().getFirst().source().id());
  rows("MATCH(c:Cohort{id:10}) SET c.observable='test:RailConnection'");
  var first=expected;try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateContextCohortCatalogue(first));}
  seed();expected=capture("a");rows("MATCH(c:Cohort{id:10}) DETACH DELETE c WITH count(*) AS removed CREATE(c:Cohort{id:10,ownerContextId:'a',observable:'test:RoadConnection'}) WITH c MATCH(a:Context{id:'a'}) CREATE(a)-[:HAS_CHILD]->(c)");
  var original=expected;try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateContextCohortCatalogue(original));}
 }
 @Test void participatingCreationChangesRevisionButRollbackAndOtherContextDoNot()throws Exception {
  var before=capture("a");rows("CREATE(:Cohort{id:20,ownerContextId:'a',observable:'test:RailConnection'}),(:Cohort{id:30,ownerContextId:'b',observable:'test:Other'})");
  try(var change=tx("a")){change.link(RuntimeAsset.CONTEXT_ASSET,cohort(20),GraphModel.Relationship.HAS_CHILD);change.fail(new IllegalStateException("abort"));}
  assertEquals(before,capture("a"));
  try(var change=tx("b")){change.link(RuntimeAsset.CONTEXT_ASSET,cohort(30),GraphModel.Relationship.HAS_CHILD);}
  try(var guard=tx("a")){guard.validateContextCohortCatalogue(before);}
  try(var change=tx("a")){change.link(RuntimeAsset.CONTEXT_ASSET,cohort(20),GraphModel.Relationship.HAS_CHILD);}
  var after=capture("a");assertEquals(before.revision()+1,after.revision());assertEquals(List.of(10L,20L),after.cohortIds());
  try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateContextCohortCatalogue(before));}
 }
 @Test void denseNewCohortPhantomWaitsOnActualContextBeforePositiveNodeLocks()throws Exception {
  rows("MATCH(a:Context{id:'a'}) UNWIND range(100,180) AS id CREATE(a)-[:HAS_CHILD]->(:Cohort{id:id,ownerContextId:'a',observable:'test:Other'})");
  rows("CREATE(:Cohort{id:20,ownerContextId:'a',observable:'test:RailConnection'})");
  var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();Future<?> writer;
  KnowledgeGraph.ContextCohortCatalogue expected;
  try {
   try(var guard=tx("a")) {
    expected=guard.captureContextCohortCatalogue();guard.lockConsequenceInputs(new TreeSet<>(expected.cohortIds()));guard.validateContextCohortCatalogue(expected);
    writer=executor.submit(()->{thread.set(Thread.currentThread());try(var change=tx("a")){change.link(RuntimeAsset.CONTEXT_ASSET,cohort(20),GraphModel.Relationship.HAS_CHILD);}catch(Exception failure){throw new RuntimeException(failure);}});
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
    while(System.nanoTime()<deadline&&!writer.isDone()) {var current=thread.get();if(current!=null&&Arrays.stream(current.getStackTrace()).anyMatch(frame->frame.getClassName().contains("locking.forseti"))){waiting=true;break;}Thread.sleep(5);}
    assertTrue(waiting,"Context catalogue must prevent a new cohort phantom even when dense");assertFalse(writer.isDone());
   }
   writer.get(5,TimeUnit.SECONDS);assertTrue(capture("a").cohortIds().contains(20L));
   var original=expected;try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateContextCohortCatalogue(original));}
  }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
 }
 @Test void finalPositiveHeaderLockSerializesCompetingHeaderMutation()throws Exception {
  var expected=capture("a");var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();Future<?> writer;
  try {
   try(var guard=tx("a")) {
    guard.lockConsequenceInputs(new TreeSet<>(expected.cohortIds()));guard.validateContextCohortCatalogue(expected);
    writer=executor.submit(()->{thread.set(Thread.currentThread());rows("MATCH(c:Cohort{id:10}) SET c.observable='test:Changed'");});
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
    while(System.nanoTime()<deadline&&!writer.isDone()){var current=thread.get();if(current!=null&&Arrays.stream(current.getStackTrace()).anyMatch(frame->frame.getClassName().contains("locking.forseti"))){waiting=true;break;}Thread.sleep(5);}
    assertTrue(waiting,"Final union must hold header payload stable through publication");assertFalse(writer.isDone());
   }
   writer.get(5,TimeUnit.SECONDS);try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateContextCohortCatalogue(expected));}
  }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
 }

}
