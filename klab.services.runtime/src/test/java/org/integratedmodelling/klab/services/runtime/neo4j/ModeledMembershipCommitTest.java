package org.integratedmodelling.klab.services.runtime.neo4j;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.common.knowledge.*;import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.configuration.*;import org.integratedmodelling.klab.api.data.*;import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;import org.integratedmodelling.klab.api.knowledge.observation.Observation;import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;import org.integratedmodelling.klab.api.services.*;import org.integratedmodelling.klab.api.services.reasoner.objects.*;import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;import org.integratedmodelling.klab.services.runtime.*;import org.integratedmodelling.klab.services.runtime.digitaltwin.*;import org.integratedmodelling.klab.services.scopes.*;import org.integratedmodelling.klab.utilities.Utils;import org.junit.jupiter.api.*;
/** Real compiled invocation, admitted cycle, native root commit and private callback issuance.
 * Reflection injects outer-twin collaborators only; it never creates authority or commit state. */
class ModeledMembershipCommitTest {
 static final String BASE="semantic-content-v2:"+"a".repeat(64);
 @BeforeAll static void start(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();ClassificationPersistenceTest.start();}
 @AfterAll static void stop(){ClassificationPersistenceTest.stop();}
 static void inject(Object twin,String name,Object value)throws Exception{var f=DigitalTwinImpl.class.getDeclaredField(name);f.setAccessible(true);f.set(twin,value);}
 static SemanticDependencyWitness witness(SemanticDependencyRequest r){return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,r.expectedServiceId(),r.expectedKnowledgeRevision(),r.expectedBaseFingerprint(),r.inputHash(),List.of(),null);}
 static ObservationImpl observation(long id,String urn,Contextualization kind){var c=new ConceptImpl();c.setUrn(urn);c.setReferenceName(urn.replace(':','_'));c.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));c.setDescriptionType(kind);var o=new ObservableImpl();o.setSemantics(c);o.setUrn(urn);o.setDescriptionType(kind);o.setArtifactType(Artifact.Type.OBJECT);var result=new ObservationImpl();result.setId(id);result.setObservable(o);result.setGeometry(Geometry.create("1"));result.setResolvedCoverage(1);return result;}
 @Test void actualCompiledSourceAndRootCommitMintTokenOnlyAfterNativePublication()throws Exception {exercise(false,false);}
 @Test void idEquivalentUnownedCloneCannotBecomeModeledCessationAuthority()throws Exception {exercise(true,false);}
 @Test void directRootCancellationDrainsBlockedStageBeforeReleasingCycle()throws Exception {exercise(false,true);}
 @Test void actualModeledCessationDeactivatesDependentAndIncidentRelationshipPreservingSurvivorsAndHistory()throws Exception {exercise(false,false,true);}
 void exercise(boolean forged,boolean cancel)throws Exception {exercise(forged,cancel,false);}
 void exercise(boolean forged,boolean cancel,boolean cascade)throws Exception {
  ClassificationPersistenceTest.rows("MATCH(n) DETACH DELETE n",Map.of());
  ClassificationPersistenceTest.rows("CREATE(:Context{id:'a'}),(:Provenance{id:'a.PROVENANCE'})",Map.of());
  var graph=new ModeledCessationPersistenceTest.Graph("a");var first=observation(-7,"test:Tree",Contextualization.ACKNOWLEDGEMENT);var second=observation(-8,"test:Tree",Contextualization.ACKNOWLEDGEMENT);var source=observation(-9,"test:Controller",Contextualization.ACKNOWLEDGEMENT);
  var cohort=new CohortImpl();cohort.setId(-10);cohort.setObservable(first.getObservable());cohort.setGeometry(first.getGeometry());
  try(var tx=graph.createTransaction(ModeledCessationPersistenceTest.scope("a"))){tx.store(cohort);tx.store(first);tx.store(second);tx.store(source);tx.link(RuntimeAsset.CONTEXT_ASSET,cohort,GraphModel.Relationship.HAS_CHILD);tx.link(cohort,first,GraphModel.Relationship.HAS_MEMBER);tx.link(cohort,second,GraphModel.Relationship.HAS_MEMBER);tx.link(RuntimeAsset.CONTEXT_ASSET,source,GraphModel.Relationship.HAS_CHILD);}
  var dependent=observation(-11,"test:Height",Contextualization.QUANTIFICATION);((ConceptImpl)dependent.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));((ObservableImpl)dependent.getObservable()).setArtifactType(Artifact.Type.NUMBER);dependent.setParentId(first.getId());dependent.getMetadata().put("test.audit","immutable dependent payload");
  var survivingDependent=observation(-12,"test:OtherHeight",Contextualization.QUANTIFICATION);((ConceptImpl)survivingDependent.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));((ObservableImpl)survivingDependent.getObservable()).setArtifactType(Artifact.Type.NUMBER);survivingDependent.setParentId(second.getId());
  var relationship=observation(-13,"test:Connection",Contextualization.ACKNOWLEDGEMENT);((ConceptImpl)relationship.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));
  var independentRelationship=observation(-14,"test:OtherConnection",Contextualization.ACKNOWLEDGEMENT);((ConceptImpl)independentRelationship.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));

  var runtime=mock(org.integratedmodelling.klab.services.runtime.RuntimeService.class,CALLS_REAL_METHODS);var settings=mock(Settings.class);doReturn(settings).when(runtime).settings();doReturn("runtime").when(runtime).serviceId();doReturn(java.net.URI.create("http://localhost:1").toURL()).when(runtime).getUrl();
  var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(user.getId()).thenReturn("tester");var scope=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,runtime)),DigitalTwin.Configuration.builder().id("a").name("isolated").build(),user);scope.setId("a");
  var reasoner=mock(Reasoner.class);var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(reasoner.status()).thenReturn(status);doReturn(status).when(runtime).status();scope.addService(reasoner);scope.addService(runtime);
  when(reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenReturn(new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,BASE,"lease"),null));when(reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(reasoner);
  var caps=mock(Reasoner.Capabilities.class);when(reasoner.capabilities(any())).thenReturn(caps);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticSessionId()).thenReturn("session");when(caps.getSemanticContentFingerprint()).thenReturn(BASE);when(reasoner.semanticDependencies(any(),any())).thenAnswer(c->witness(c.getArgument(0)));
  when(reasoner.resolveObservable(anyString())).thenAnswer(c->{String urn=c.getArgument(0);return java.util.stream.Stream.of(first,source,dependent,survivingDependent,relationship,independentRelationship).filter(o->o.getObservable().getUrn().equals(urn)).findFirst().orElseThrow().getObservable();});
  if(!forged&&!cancel)assertNotNull(new ModeledCessationPersistenceTest.Graph("a").getAsset(second.getId(),scope,Observation.class),
      "A fresh native reload of the survivor must work even before cessation");
  var twin=mock(DigitalTwinImpl.class,CALLS_REAL_METHODS);var scheduler=mock(Scheduler.class);var cycles=new ResolutionCycles(runtime,"a");inject(twin,"knowledgeGraph",graph);inject(twin,"commitCache",CacheBuilder.newBuilder().build());inject(twin,"scheduler",scheduler);inject(twin,"resolutionCycles",cycles);scope.setDigitalTwin(twin);
  if(cascade) { var store=org.integratedmodelling.klab.runtime.storage.MembershipStorageFactory.open(scope,java.nio.file.Files.createTempDirectory("cessation-native-storage-"));
   try {
   doReturn(store).when(twin).getStorageManager();
   try(var tx=graph.createTransaction(scope)) {
   tx.store(dependent);tx.store(survivingDependent);tx.store(relationship);tx.store(independentRelationship);
   tx.link(first,dependent,GraphModel.Relationship.HAS_CHILD);tx.link(second,survivingDependent,GraphModel.Relationship.HAS_CHILD);
   tx.link(RuntimeAsset.CONTEXT_ASSET,relationship,GraphModel.Relationship.HAS_CHILD);tx.link(RuntimeAsset.CONTEXT_ASSET,independentRelationship,GraphModel.Relationship.HAS_CHILD);
   tx.link(relationship,first,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE);tx.link(relationship,second,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET);
   tx.link(independentRelationship,second,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE);tx.link(independentRelationship,source,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET);
  }
   } finally {store.close();}
  }
  var tokens=new ArrayList<DigitalTwinImpl.CommittedMembershipChange>();var callbackClock=new AtomicBoolean();
  var immutableBefore=new HashMap<Long,String>();
  if(cascade)for(var item:List.of(first,dependent,relationship)) {
   var row=ClassificationPersistenceTest.rows("MATCH(n:Observation{id:$id}) RETURN properties(n) AS props",Map.of("id",item.getId())).getFirst();
   immutableBefore.put(item.getId(),Utils.Json.asString(new TreeMap<>((Map<String,Object>)row.get("props"))));
  }
  doAnswer(c->{assertTrue(callbackClock.get());var token=(DigitalTwinImpl.CommittedMembershipChange)c.getArgument(0);token.require((ServiceContextScope)c.getArgument(1));assertNull(graph.getAsset(first.getId(),scope,Observation.class));try(var tx=graph.createTransaction(scope)){tx.validateConsequenceInputs(token.change().afterReadSet());}tokens.add(token);return Scheduler.MembershipPropagationOutcome.APPLIED;}).when(scheduler).membershipChanged(any(),any());
  var stageEntered=new CountDownLatch(1);var stageRelease=new CountDownLatch(1);var staging=new AtomicBoolean();
  if(cancel){doAnswer(c->{staging.set(true);return c.callRealMethod();}).when(runtime).stageModeledCessations(any(),any());doAnswer(c->{if(staging.get()){stageEntered.countDown();if(!stageRelease.await(5,TimeUnit.SECONDS))throw new IllegalStateException("stage test timeout");}return witness(c.getArgument(0));}).when(reasoner).semanticDependencies(any(),any());}
  try(var admission=cancel?null:cycles.admit(scope,true).get(5,TimeUnit.SECONDS)) {
   var active=admission==null?scope:admission.scope();
   assertNotNull(active.getObservation(first.getId()),"Cache the live member before cessation");
   var submission=active.executing(Activity.of(Activity.Type.SUBMISSION),source);var root=(DigitalTwinImpl.TransactionImpl)submission.getCurrentTransaction();root.afterCommit(()->callbackClock.set(true));
   var plan=new ActuatorImpl();plan.setId(source.getId());plan.setName("modeled-input");plan.setObservation(source);plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
   var model=new ModelImpl();model.setUrn("fixture.cessation");model.setNamespace("fixture");model.getObservables().add(source.getObservable());var snapshot=ModelValidationGate.snapshot(model);var terms=ModelValidationGate.declaredSemanticObligations(snapshot);var req=new SemanticDependencyRequest("reasoner",1,BASE,terms);var receipt=new ModelValidationGate.AdmissionReceipt(model.getUrn(),model.getNamespace(),"1","b".repeat(64),snapshot.executableHash(),"reasoner",1,List.of(),BASE,terms,true,witness(req),"session");plan.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of(model.getUrn()));plan.getData().put("modelAdmissionRevision",1L);plan.getData().put("modelAdmissionReasonerId","reasoner");plan.getData().put(ModelValidationGate.PRODUCER_ADMISSIONS,List.of(Utils.Json.asString(receipt)));plan.getData().put(ModelValidationGate.PRODUCER_SNAPSHOTS,List.of(Utils.Json.asString(snapshot)));
   // Controlled model I/O reports the effect through the actual result, never a fabricated capability.
   doAnswer(c->{var result=c.getArgument(0,org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope.class);var input=graph.getAsset(first.getId(),active,Observation.class);result.getCessations().add(forged?Observation.forTransport(input):input);return c.callRealMethod();}).when(runtime).submitContextualizationResult(any(),any(),any());
   var compiled=new CompiledDataflow(runtime,source,submission);assertTrue(compiled.compile(plan),"compile: "+source.getNotifications());boolean executed;
   if(cancel){
    var work=CompletableFuture.supplyAsync(()->ModeledMembershipInvocation.execute(compiled,source.getGeometry(),submission));
    assertTrue(stageEntered.await(5,TimeUnit.SECONDS),"actual modeled stage reached its semantic RPC");
    root.fail(new java.util.concurrent.CancellationException("cancel actual direct root"));
    verify(reasoner,never()).releaseSemanticCycle(any(),anyString(),anyString(),anyString(),any());
    var next=cycles.admit(scope,true);assertFalse(next.isDone(),"cancelled source still owns in-flight stage");
    stageRelease.countDown();assertFalse(work.get(5,TimeUnit.SECONDS));
    try(var following=next.get(5,TimeUnit.SECONDS)){assertNotNull(following.scope().getResolutionCycleBinding());}
    assertTrue(tokens.isEmpty());assertTrue(graph.isObservationCurrent(first,scope));assertTrue(graph.getObservationHistory(first.getId(),scope).isEmpty());return;
   }
   executed=ModeledMembershipInvocation.execute(compiled,source.getGeometry(),submission);
   if(forged){assertFalse(executed);assertTrue(tokens.isEmpty());assertTrue(submission.commit()<0);assertTrue(graph.isObservationCurrent(first,scope));assertTrue(graph.getObservationHistory(first.getId(),scope).isEmpty());return;}
   assertTrue(executed,"execute: "+source.getNotifications());assertTrue(tokens.isEmpty(),"Child success does not issue a committed token");long committed=submission.commit();assertTrue(committed>0,"root commit failed: "+source.getNotifications());assertEquals(1,tokens.size());assertEquals(List.of(second.getId()),tokens.getFirst().change().afterMembers());assertEquals(committed,tokens.getFirst().commitId());assertEquals(1,graph.getObservationHistory(first.getId(),scope).size());assertNull(active.getObservation(first.getId()),"A stale scope cache cannot return ceased current identity");assertNotNull(active.getObservation(second.getId()));
   if(cascade) {
    for(var inactive:List.of(first,dependent,relationship)) {
     assertFalse(graph.isObservationCurrent(inactive,active));assertNull(active.getObservation(inactive.getId()));
     assertNull(new ModeledCessationPersistenceTest.Graph("a").getAsset(inactive.getId(),active,Observation.class));
     var history=new ModeledCessationPersistenceTest.Graph("a").getObservationHistory(inactive.getId(),active);assertEquals(1,history.size());
     var past=history.getFirst().before().assets().stream().filter(a->a.id()==inactive.getId()).findFirst().orElseThrow();
     assertEquals(inactive.getObservable().getUrn(),past.properties().get("observable"));assertFalse(past.properties().containsKey(KnowledgeGraph.CURRENT_PROPERTY));
     assertEquals(immutableBefore.get(inactive.getId()),Utils.Json.asString(new TreeMap<>(past.properties())),"Immutable complete pre-cessation native payload must remain available through a fresh historical read");
    }
    for(var survivor:List.of(second,survivingDependent,independentRelationship)) {assertTrue(graph.isObservationCurrent(survivor,active));assertNotNull(active.getObservation(survivor.getId()));assertTrue(graph.getObservationHistory(survivor.getId(),active).isEmpty());}
    assertEquals(1L,ClassificationPersistenceTest.rows("MATCH(:Observation{id:$a})-[:HAS_CHILD]->(:Observation{id:$q}) RETURN count(*) AS n",Map.of("a",first.getId(),"q",dependent.getId())).getFirst().get("n"));
    assertEquals(2L,ClassificationPersistenceTest.rows("MATCH(:Observation{id:$r})-[:HAS_RELATIONSHIP_SOURCE|HAS_RELATIONSHIP_TARGET]->() RETURN count(*) AS n",Map.of("r",relationship.getId())).getFirst().get("n"));
    org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.CurrentLifecycleSchedulingProbe.verify(scope,twin,List.of(first,dependent,relationship),List.of(second,survivingDependent));
   }
  }finally{stageRelease.countDown();cycles.close();cycles.awaitQuiescence(5,TimeUnit.SECONDS);}
 }
}
