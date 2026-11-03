package org.integratedmodelling.klab.runtime.storage;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import java.nio.file.Path;import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.*;import org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding;
import org.integratedmodelling.klab.api.provenance.Activity;import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.runtime.CompiledDataflow;import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.*;
import org.integratedmodelling.klab.utilities.Utils;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
/** Actual restored assignment-only process INIT, with the existing transactional native fixture. */
class ProcessBirthAccountingTest {
 @TempDir Path directory;
 @Test void realAssignmentOnlyBirthRecordsUnknownAccountingWhenModelProofIsUnavailable()throws Exception {
  TemporalProcessIntegrationTest.configure();var owner=new TemporalProcessIntegrationTest();owner.directory=directory;
  try(var f=owner.new Fixture("elevation - 10")) {
   f.quality.setEventTimestamps(new ArrayList<>(List.of(0L)));
   var registration=Utils.Json.parseObject(f.process.getMetadata().remove(OccurrenceRegistration.METADATA_KEY).toString(),OccurrenceRegistration.class);
   var plan=Utils.Json.parseObject(registration.plan(),Actuator.class);f.scheduler.close();when(f.graph.getScheduledObservations(f.root)).thenReturn(List.of());f.scheduler=new SchedulerImpl(f.root,f.twin);
   var scope=f.root.executingFresh(Activity.of(Activity.Type.SUBMISSION),f.bearer);
   scope.getCurrentTransaction().resolveWith(f.process,new CompiledDataflow(f.runtime,f.process,scope).restoreOccurrenceExecutor(plan));
   assertTrue(f.scheduler.submit(f.process,scope),f::failureDescription);
   assertTrue(f.journals.isEmpty(),"Birth must not execute a simulation");
   assertArrayEquals(new double[]{100,200,350},f.values(TemporalProcessIntegrationTest.START));
   var births=scope.getCurrentTransaction().incoming(f.process).stream().filter(l->l.type()==GraphModel.Relationship.CREATED&&l.source() instanceof Activity a&&a.getType()==Activity.Type.INITIALIZATION).map(l->(Activity)l.source()).toList();
   assertEquals(1,births.size());assertFalse(births.getFirst().getType().isContextualization());
   var encoded=births.getFirst().getMetadata().get(SemanticEvidenceBinding.DATA_KEY);
   assertNotNull(encoded,"The real process-birth operation must account its model/input closure before returning");
   var evidence=Utils.Json.parseObject(encoded.toString(),SemanticEvidenceBinding.class);assertFalse(evidence.complete(),"Missing admitted model/input execution evidence must stay UNKNOWN");
   scope.getCurrentTransaction().fail(new IllegalStateException("test cleanup"));
  }
 }
 static final String BASE="semantic-content-v2:"+"a".repeat(64);
 private org.integratedmodelling.klab.api.services.Reasoner reasoner;
 private void semantics() {
  reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);var caps=mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
  when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticContentFingerprint()).thenReturn(BASE);when(caps.getSemanticSessionId()).thenReturn("owner-one");
  when(reasoner.capabilities(any())).thenReturn(caps);when(reasoner.semanticDependencies(any(),any())).thenAnswer(call->witness(call.getArgument(0)));
 }
 private static org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness witness(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest request) {
  return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.VERSION,
    org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.COMPLETE,request.expectedServiceId(),request.expectedKnowledgeRevision(),request.expectedBaseFingerprint(),request.inputHash(),List.of(),null);
 }
 class ScientificFixture extends TemporalProcessIntegrationTest.Fixture {
  ScientificFixture(TemporalProcessIntegrationTest owner)throws Exception{owner.super("elevation - 10");}
  @Override void configureScope(org.integratedmodelling.klab.services.scopes.ServiceContextScope scope,org.integratedmodelling.klab.api.identities.UserIdentity user) {
   super.configureScope(scope,user);doReturn(reasoner).when(scope).getService(org.integratedmodelling.klab.api.services.Reasoner.class);when(scope.getConsequenceSubmissionAdmission()).thenReturn(null);
  }
 }
 private Actuator admittedPlan(ScientificFixture f,boolean inputProof)throws Exception {
  // The numeric fixture intentionally has VOID observables. This proof fixture supplies the
  // actual scientific phases of its simulated process and measured prerequisite.
  ((org.integratedmodelling.common.knowledge.ObservableImpl)f.quality.getObservable()).setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.MEASURE);
  ((org.integratedmodelling.common.knowledge.ObservableImpl)f.process.getObservable()).setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.SIMULATION);
  f.quality.setEventTimestamps(new ArrayList<>(List.of(0L)));f.creators.remove(f.process.getId());
  var registration=Utils.Json.parseObject(f.process.getMetadata().remove(OccurrenceRegistration.METADATA_KEY).toString(),OccurrenceRegistration.class);var plan=Utils.Json.parseObject(registration.plan(),Actuator.class);
  ((org.integratedmodelling.common.knowledge.ObservableImpl)plan.getObservation().getObservable()).setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.SIMULATION);
  for(var child:plan.getChildren())((org.integratedmodelling.common.knowledge.ObservableImpl)child.getObservation().getObservable()).setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.MEASURE);
  var model=new org.integratedmodelling.common.knowledge.ModelImpl();model.setUrn("fixture.processBirth");model.setNamespace("fixture");model.getObservables().add(f.process.getObservable());model.getDependencies().add(f.quality.getObservable());for(var call:plan.getComputation())model.getComputation().add(new org.integratedmodelling.common.lang.ContextualizableImpl(call));
  var snapshot=org.integratedmodelling.klab.runtime.language.ModelValidationGate.snapshot(model);var terms=org.integratedmodelling.klab.runtime.language.ModelValidationGate.declaredSemanticObligations(snapshot);
  var request=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest("reasoner",1,BASE,terms);
  var receipt=new org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt(model.getUrn(),model.getNamespace(),"1","b".repeat(64),snapshot.executableHash(),"reasoner",1,List.of(),BASE,terms,true,witness(request),"owner-one");
  plan.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of(model.getUrn()));plan.getData().put("modelAdmissionRevision",1L);plan.getData().put("modelAdmissionReasonerId","reasoner");
  plan.getData().put(org.integratedmodelling.klab.runtime.language.ModelValidationGate.PRODUCER_ADMISSIONS,List.of(Utils.Json.asString(receipt)));plan.getData().put(org.integratedmodelling.klab.runtime.language.ModelValidationGate.PRODUCER_SNAPSHOTS,List.of(Utils.Json.asString(snapshot)));
  var original=Activity.of(Activity.Type.forContextualization(f.quality.getObservable().getContextualization()));original.setId(900);original.setOutcome(Activity.Outcome.SUCCESS);
  var sourceRequest=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest("reasoner",1,BASE,List.of(f.quality.getObservable().getSemantics().getUrn(),"test:OriginalInputModel"));
  if(inputProof)original.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,sourceRequest.obligations(),witness(sourceRequest))));
  when(f.graph.query(any(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class),eq(Activity.class),any())).thenAnswer(call->{
   var query=(org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery<?>)call.getArgument(0);if(query.getTarget()==null)return List.of();
   if(query.getTarget().getId()==f.quality.getId()&&query.getRelationship()==GraphModel.Relationship.forContextualization(f.quality.getObservable().getContextualization()))return List.of(original);
   return query.getRelationship()==GraphModel.Relationship.CREATED?f.creators.getOrDefault(query.getTarget().getId(),List.of()):query.getRelationship()==GraphModel.Relationship.TRIGGERED?f.activityParents.getOrDefault(query.getTarget().getId(),List.of()):List.of();});
  return plan;
 }
 @Test void legitimateBornProcessCarriesOriginalAdmittedModelAndInitializedInputClosure()throws Exception {
  TemporalProcessIntegrationTest.configure();semantics();var owner=new TemporalProcessIntegrationTest();owner.directory=directory;
  try(var f=new ScientificFixture(owner)){
   var plan=admittedPlan(f,true);f.scheduler.close();when(f.graph.getScheduledObservations(f.root)).thenReturn(List.of());f.scheduler=new SchedulerImpl(f.root,f.twin);
   var scope=f.root.executingFresh(Activity.of(Activity.Type.SUBMISSION),f.bearer);scope.getCurrentTransaction().resolveWith(f.process,new CompiledDataflow(f.runtime,f.process,scope).restoreOccurrenceExecutor(plan));
   assertTrue(f.scheduler.submit(f.process,scope),f::failureDescription);assertTrue(f.journals.isEmpty());
   var birth=scope.getCurrentTransaction().incoming(f.process).stream().filter(l->l.type()==GraphModel.Relationship.CREATED&&l.source() instanceof Activity a&&a.getType()==Activity.Type.INITIALIZATION).map(l->(Activity)l.source()).findFirst().orElseThrow();
   var encoded=birth.getMetadata().get(SemanticEvidenceBinding.DATA_KEY);assertNotNull(encoded);
   var evidence=Utils.Json.parseObject(encoded.toString(),SemanticEvidenceBinding.class);assertTrue(evidence.complete(),birth.getMetadata().toString());assertNotNull(evidence.witness());
   assertTrue(evidence.obligations().containsAll(List.of("test:erosion","test:relief","test:OriginalInputModel")),evidence.obligations().toString());
   assertTrue(CompiledDataflow.processBirthAccounting(birth.getType(),birth.getMetadata(),f.process));
   assertFalse(CompiledDataflow.processBirthAccounting(Activity.Type.SUBMISSION,birth.getMetadata(),f.process));
   var copied=new HashMap<String,Object>(birth.getMetadata());copied.remove(CompiledDataflow.PROCESS_BIRTH_ACCOUNTING_KEY);assertFalse(CompiledDataflow.processBirthAccounting(birth.getType(),copied,f.process));
   copied=new HashMap<>(birth.getMetadata());copied.remove(SemanticEvidenceBinding.DATA_KEY);assertFalse(CompiledDataflow.processBirthAccounting(birth.getType(),copied,f.process));
   var sources=Class.forName("org.integratedmodelling.klab.services.runtime.SemanticExecutionEvidence").getDeclaredMethod("sourceActivities",org.integratedmodelling.klab.api.knowledge.observation.Observation.class,org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);sources.setAccessible(true);
   assertTrue(((List<?>)sources.invoke(null,f.process,scope)).contains(birth));
   scope.getCurrentTransaction().fail(new IllegalStateException("test cleanup"));assertArrayEquals(new double[]{100,200,350},f.values(TemporalProcessIntegrationTest.START));
  }
 }
 @Test void missingInitializedInputAccountingCannotBecomeCompleteBirthAuthority()throws Exception {
  TemporalProcessIntegrationTest.configure();semantics();var owner=new TemporalProcessIntegrationTest();owner.directory=directory;
  try(var f=new ScientificFixture(owner)){
   var plan=admittedPlan(f,false);f.scheduler.close();when(f.graph.getScheduledObservations(f.root)).thenReturn(List.of());f.scheduler=new SchedulerImpl(f.root,f.twin);
   var scope=f.root.executingFresh(Activity.of(Activity.Type.SUBMISSION),f.bearer);scope.getCurrentTransaction().resolveWith(f.process,new CompiledDataflow(f.runtime,f.process,scope).restoreOccurrenceExecutor(plan));assertTrue(f.scheduler.submit(f.process,scope),f::failureDescription);
   var birth=scope.getCurrentTransaction().incoming(f.process).stream().filter(l->l.type()==GraphModel.Relationship.CREATED&&l.source() instanceof Activity a&&a.getType()==Activity.Type.INITIALIZATION).map(l->(Activity)l.source()).findFirst().orElseThrow();
   var evidence=Utils.Json.parseObject(birth.getMetadata().get(SemanticEvidenceBinding.DATA_KEY).toString(),SemanticEvidenceBinding.class);assertFalse(evidence.complete());assertFalse(CompiledDataflow.processBirthAccounting(birth.getType(),birth.getMetadata(),f.process));scope.getCurrentTransaction().fail(new IllegalStateException("test cleanup"));
  }
 }

}
