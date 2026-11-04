package org.integratedmodelling.klab.services.runtime;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.*;import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;import org.integratedmodelling.klab.api.digitaltwin.*;import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;import org.integratedmodelling.klab.api.knowledge.observation.Observation;import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;import org.integratedmodelling.klab.api.services.Reasoner;import org.integratedmodelling.klab.api.services.reasoner.objects.*;import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;import org.integratedmodelling.klab.runtime.language.ModelValidationGate;import org.integratedmodelling.klab.services.scopes.ServiceContextScope;import org.integratedmodelling.klab.utilities.Utils;import org.junit.jupiter.api.*;
/** Actual compiled root execution and native child transaction. Semantic/persistence services are controlled fixtures. */
class ScientificRootCompletionTest {
 static final String BASE="semantic-content-v2:"+"a".repeat(64);
 @BeforeAll static void setup(){CollectiveCompletionQueryTest.configure();}
 static SemanticDependencyWitness witness(SemanticDependencyRequest request){return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,request.expectedServiceId(),request.expectedKnowledgeRevision(),request.expectedBaseFingerprint(),request.inputHash(),List.of(),null);}
 class Fixture {
  final CollectiveCompletionQueryTest nativeGraph=new CollectiveCompletionQueryTest();final ObservationImpl target=new ObservationImpl();final ActuatorImpl plan=new ActuatorImpl();
  final Reasoner reasoner=mock(Reasoner.class);final RuntimeService runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);final AtomicInteger actualPublications=new AtomicInteger();
  final ResolutionCycleBinding cycle=new ResolutionCycleBinding(1,"cycle","completion-test","runtime",true,new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,BASE,"lease"));
  CompiledDataflow compiled;boolean failChild;
  Fixture(boolean producer)throws Exception {this(producer,true);}
  Fixture(boolean producer,boolean preparation)throws Exception {
   org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();nativeGraph.setup();var scope=nativeGraph.scope;var concept=new ConceptImpl();concept.setUrn("test:Configuration");concept.setType(EnumSet.of(SemanticType.CONFIGURATION,SemanticType.OBSERVABLE));
   var observable=new ObservableImpl();observable.setSemantics(concept);observable.setUrn(concept.getUrn());observable.setArtifactType(Artifact.Type.OBJECT);observable.setDescriptionType(Contextualization.DETECTION);
   target.setId(42);target.setObservable(observable);target.setGeometry(Geometry.create("1"));nativeGraph.root.setTarget(target);
   plan.setId(42);plan.setName("configuration");plan.setObservation(target);plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
   when(scope.getResolutionCycleBinding()).thenReturn(cycle);when(scope.getObserverId()).thenReturn(null);when(scope.getConsequenceSubmissionAdmission()).thenReturn(null);when(scope.getActivity()).thenReturn(nativeGraph.root.getActivity());
   when(scope.getService(Reasoner.class)).thenReturn(reasoner);doReturn(runtime).when(scope).getService(RuntimeService.class);doReturn(runtime).when(scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
   var caps=mock(Reasoner.Capabilities.class);when(reasoner.capabilities(any())).thenReturn(caps);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticContentFingerprint()).thenReturn(BASE);when(caps.getSemanticSessionId()).thenReturn("session");when(reasoner.semanticDependencies(any(),any())).thenAnswer(call->witness(call.getArgument(0)));
   when(scope.getObservation(42)).thenReturn(target);when(scope.getContextObservation()).thenReturn(null);
   doAnswer(call->{var activity=call.getArgument(0,Activity.class);var execution=mock(ServiceContextScope.class,org.mockito.AdditionalAnswers.delegatesTo(scope));var tx=nativeGraph.root.getChild(activity,execution,(Object[])call.getRawArguments()[1]);when(execution.getCurrentTransaction()).thenReturn(tx);when(execution.getActivity()).thenReturn(activity);when(execution.commit()).thenAnswer(ignored->tx.commit());doAnswer(error->{tx.fail(error.getArgument(0));return null;}).when(execution).fail(any(Throwable.class));return execution;}).when(scope).executing(any(Activity.class),any(Object[].class));
   doAnswer(call->{actualPublications.incrementAndGet();if(failChild)nativeGraph.root.fail(new IllegalStateException("controlled child failure"));return call.callRealMethod();}).when(runtime).submitContextualizationResult(any(),any(),any());
   if(producer){var model=new ModelImpl();model.setUrn("fixture.configuration");model.setNamespace("fixture");model.getObservables().add(observable);var snapshot=ModelValidationGate.snapshot(model);var terms=ModelValidationGate.declaredSemanticObligations(snapshot);var request=new SemanticDependencyRequest("reasoner",1,BASE,terms);var receipt=new ModelValidationGate.AdmissionReceipt(model.getUrn(),model.getNamespace(),"1","b".repeat(64),snapshot.executableHash(),"reasoner",1,List.of(),BASE,terms,true,witness(request),"session");plan.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of(model.getUrn()));plan.getData().put("modelAdmissionRevision",1L);plan.getData().put("modelAdmissionReasonerId","reasoner");plan.getData().put(ModelValidationGate.PRODUCER_ADMISSIONS,List.of(Utils.Json.asString(receipt)));plan.getData().put(ModelValidationGate.PRODUCER_SNAPSHOTS,List.of(Utils.Json.asString(snapshot)));}
   if(preparation){
    var software=mock(org.integratedmodelling.klab.api.provenance.Agent.class);when(software.getId()).thenReturn(3L);when(software.getName()).thenReturn("k.LAB");when(nativeGraph.graph.klab()).thenReturn(software);
    when(scope.getConstraint(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ConfigurationExplanation,Long.class)).thenReturn(target.getId());
    when(scope.getConsequenceCause()).thenReturn(new Consequence.Cause(1,"completion-test",10,2,0,null,null,"decision",Consequence.Kind.CONFIGURATION_DETECTION));
    var detection=Activity.of(Activity.Type.DETECTION);var detectionTx=nativeGraph.root.getChild(detection,scope,target);
    when(scope.getCurrentTransaction()).thenReturn(detectionTx);when(scope.getActivity()).thenReturn(detection);
   }
   compiled=new CompiledDataflow(runtime,target,scope);if(preparation)compiled.enableScientificConfigurationAccounting();assertTrue(compiled.compile(plan));
  }
  boolean run(){return compiled.executeRoot(target.getGeometry(),Scheduler.Event.initialization(),nativeGraph.scope);}
 }
 @Test void actualSourceAdmittedModelMustRunAndCommitItsOwnRootBeforeCompletion()throws Exception {
  var f=new Fixture(true);assertTrue(f.compiled.scientificRootCompletionSupport(f.target,f.nativeGraph.scope).isEmpty());
  assertTrue(f.run());assertEquals(1,f.actualPublications.get());assertEquals(f.target.getGeometry().encode(),f.compiled.scientificRootCompletionSupport(f.target,f.nativeGraph.scope).orElseThrow().encode());
  assertTrue(f.compiled.scientificRootCompletionSupport(Observation.forTransport(f.target),f.nativeGraph.scope).isEmpty(),"An ID-equivalent transported target is not this preparation's target");
  when(f.nativeGraph.scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"other-cycle","completion-test","runtime",true,f.cycle.semanticBinding()));assertTrue(f.compiled.scientificRootCompletionSupport(f.target,f.nativeGraph.scope).isEmpty());
 }
 @Test void nativeAndCheckOnlyOwnedChildrenCannotStandInForScientificRootExecution()throws Exception {
  var f=new Fixture(true);for(var type:List.of(Activity.Type.DETECTION,Activity.Type.CLASSIFICATION)){
   var activity=Activity.of(type);var request=new SemanticDependencyRequest("reasoner",1,BASE,List.of("test:Configuration"));activity.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,request.obligations(),witness(request))));var child=f.nativeGraph.root.getChild(activity,f.nativeGraph.scope,f.target);assertEquals(0,child.commit());assertTrue(f.compiled.scientificRootCompletionSupport(f.target,f.nativeGraph.scope).isEmpty());
  }assertEquals(0,f.actualPublications.get());
 }
 @Test void ordinaryCompiledModelsNeverRetainScientificCompletion()throws Exception {
  var f=new Fixture(true,false);assertThrows(IllegalStateException.class,f.compiled::enableScientificConfigurationAccounting);assertTrue(f.run());assertEquals(1,f.actualPublications.get());
  assertTrue(f.compiled.scientificRootCompletionSupport(f.target,f.nativeGraph.scope).isEmpty());
  for(var field:List.of("scientificPlan","scientificCompletion")){var member=CompiledDataflow.class.getDeclaredField(field);member.setAccessible(true);assertNull(member.get(f.compiled));}
 }
 @Test void optionalRequestSyntaxCannotReplaceTheBoundNativeConfigurationMeaning()throws Exception {
  var f=new Fixture(true);var declaration=Observation.forTransport(f.target);
  var optional=new ObservableImpl((ObservableImpl)f.target.getObservable());optional.setOptional(true);optional.setUrn(optional.getUrn()+" optional");
  ((ObservationImpl)declaration).setObservable(optional);f.plan.setObservation(declaration);
  var compiled=new CompiledDataflow(f.runtime,f.target,f.nativeGraph.scope);
  var failure=assertThrows(IllegalArgumentException.class,()->compiled.compile(f.plan));
  assertTrue(failure.getMessage().contains("bound target identity"));assertEquals("test:Configuration",f.target.getObservable().getUrn());
 }
 @Test void missingProducerAndFailedChildNeverMintCompletion()throws Exception {
  var nativeOnly=new Fixture(false);assertTrue(nativeOnly.run());assertTrue(nativeOnly.compiled.scientificRootCompletionSupport(nativeOnly.target,nativeOnly.nativeGraph.scope).isEmpty());
  var failed=new Fixture(true);failed.failChild=true;assertFalse(failed.run());assertTrue(failed.compiled.scientificRootCompletionSupport(failed.target,failed.nativeGraph.scope).isEmpty());
 }
}
