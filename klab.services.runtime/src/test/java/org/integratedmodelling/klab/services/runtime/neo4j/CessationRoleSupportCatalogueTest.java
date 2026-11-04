package org.integratedmodelling.klab.services.runtime.neo4j;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.junit.jupiter.api.*;
class CessationRoleSupportCatalogueTest {
 @BeforeAll static void start(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();ClassificationPersistenceTest.start();}
 @AfterAll static void stop(){ClassificationPersistenceTest.stop();}
 @BeforeEach void seed(){new ModeledCessationPersistenceTest().seed();ModeledCessationPersistenceTest.rows("MATCH(p:Provenance{id:'a.PROVENANCE'}) CREATE(p)-[:HAS_CHILD]->(:Activity{id:101}), (p)-[:HAS_CHILD]->(:Activity{id:102})");}
 static KnowledgeGraph.Transaction tx(String context){return new ModeledCessationPersistenceTest.Graph(context).createTransaction(ModeledCessationPersistenceTest.scope(context));}
 @Test void exactCatalogueIncludesLegacyRowsAndAllIndependentRecipientSupports()throws Exception {
  ModeledCessationPersistenceTest.rows("MATCH(a:Activity{id:100}),(b:Activity{id:101}),(r:Observation{id:1}) CREATE(a)-[:CLASSIFIED{sourceId:3,ruleId:'legacy'}]->(r),(b)-[:CLASSIFIED{sourceId:2,supportContract:'role-support-v1'}]->(r)");
  try(var capture=tx("a")){var catalogue=capture.captureRoleSupportCatalogue(List.of(3L));assertEquals(1,catalogue.matches().size());assertFalse(catalogue.matches().getFirst().properties().containsKey("supportContract"));assertTrue(catalogue.inputs().assetIds().containsAll(List.of(1L,2L,3L,100L,101L)));assertEquals(2,catalogue.inputs().edges().size());}
 }
 @Test void changedAuditPayloadOrForeignSourceCannotValidate()throws Exception {
  ModeledCessationPersistenceTest.rows("MATCH(a:Activity{id:100}),(r:Observation{id:1}) CREATE(a)-[:CLASSIFIED{sourceId:3,supportContract:'role-support-v1'}]->(r)");
  KnowledgeGraph.RoleSupportCatalogue proof;try(var capture=tx("a")){proof=capture.captureRoleSupportCatalogue(List.of(3L));}
  ModeledCessationPersistenceTest.rows("MATCH(a:Activity{id:100}) SET a.metadata='changed-scientific-proof'");
  try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateRoleSupportCatalogue(proof));}
  try(var foreign=tx("b")){assertThrows(RuntimeException.class,()->foreign.captureRoleSupportCatalogue(List.of(3L)));}
 }
 @Test void emptySourceSupportPhantomWaitsForActualContextThenInvalidatesCatalogue()throws Exception {
  var executor=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();Future<?> writer;KnowledgeGraph.RoleSupportCatalogue proof;
  try {
   try(var guard=tx("a")){
    proof=guard.captureRoleSupportCatalogue(List.of(3L));assertTrue(proof.matches().isEmpty());guard.lockConsequenceInputs(proof.inputs().assetIds());guard.validateRoleSupportCatalogue(proof);
    writer=executor.submit(()->{thread.set(Thread.currentThread());try(var change=tx("a")){var activity=Activity.of(Activity.Type.CLASSIFICATION);activity.setId(102);change.link(activity,ConfigurationDependencyReadSetTest.observation(1),GraphModel.Relationship.CLASSIFIED,"sourceId",3L,"supportContract","role-support-v1");}catch(Exception failure){throw new RuntimeException(failure);}});
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;while(System.nanoTime()<deadline&&!writer.isDone()){var current=thread.get();if(current!=null&&Arrays.stream(current.getStackTrace()).anyMatch(frame->frame.getClassName().contains("locking.forseti"))){waiting=true;break;}Thread.sleep(5);}assertTrue(waiting);assertFalse(writer.isDone());
   }
   writer.get(5,TimeUnit.SECONDS);var expected=proof;try(var guard=tx("a")){assertThrows(RuntimeException.class,()->guard.validateRoleSupportCatalogue(expected));}
  }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
 }
 @Test void qualifierLossFindsSupportEvenWhenScientificSourceRemainsLive()throws Exception {
  ModeledCessationPersistenceTest.rows("MATCH(a:Activity{id:100}),(r:Observation{id:1}) CREATE(a)-[:CLASSIFIED{sourceId:3,observerId:2,focusId:1,supportContract:'role-support-v1'}]->(r)");
  try(var capture=tx("a")){var catalogue=capture.captureRoleSupportCatalogue(List.of(2L));assertEquals(1,catalogue.matches().size());assertTrue(catalogue.inputs().assetIds().containsAll(List.of(1L,2L,3L,100L)));}
 }

}
