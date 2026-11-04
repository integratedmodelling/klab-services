package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.runtime.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.runtime.digitaltwin.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Private completion boundary; the source-backend and Resolver/compiler are controlled seams,
 * not substitutes for integrated detection or scientific execution acceptance. */
class ConfigurationDetectionCompletionTest {
  static final String BASE="semantic-content-v2:"+"a".repeat(64);
  @BeforeAll static void configure(){ConfigurationExplanationPreparationTest.configure();}
  final ConfigurationExplanationPreparationTest f=new ConfigurationExplanationPreparationTest();
  final Reasoner reasoner=mock(Reasoner.class);
  final Activity activity=Activity.of(Activity.Type.DETECTION);
  final SemanticDependencyRequest request=new SemanticDependencyRequest("reasoner",3,BASE,List.of("test:Network","test:Source"));
  final GuardedConfigurationPublisher.Completion completion;
  ConfigurationDetectionCompletionTest(){
    when(f.scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",3,BASE,"lease")));
    when(f.scope.getService(Reasoner.class)).thenReturn(reasoner);
    var caps=mock(Reasoner.Capabilities.class);when(caps.getServiceId()).thenReturn("reasoner");
    when(caps.getKnowledgeRevision()).thenReturn(3L);when(caps.getSemanticContentFingerprint()).thenReturn(BASE);
    when(caps.getSemanticSessionId()).thenReturn("session");when(reasoner.capabilities(f.scope)).thenReturn(caps);
    when(reasoner.semanticDependencies(any(),same(f.scope))).thenAnswer(call->proof(call.getArgument(0),3));
    when(f.transaction.getActivity()).thenReturn(activity);when(f.transaction.ownsExecutionActivity(activity)).thenReturn(true);
    when(f.transaction.readObservation(f.configuration)).thenReturn(f.configuration);
    when(f.transaction.outgoing(any())).thenReturn(List.of());
    var captured=new AtomicReference<GuardedConfigurationPublisher.Completion>();
    try(var backend=mockConstruction(DefaultConfigurationBackend.class,(mock,context)->
        captured.set((GuardedConfigurationPublisher.Completion)context.arguments().get(2)))) {
      f.runtime.installConsequenceHandlers(mock(ConsequenceEngine.class),f.cycles);
    }
    completion=Objects.requireNonNull(captured.get());
  }
  static SemanticDependencyWitness proof(SemanticDependencyRequest request,long revision){
    return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "reasoner",revision,BASE,request.inputHash(),List.of(),"controlled semantic authority");
  }
  List<ConfigurationConsequenceRuntime.Update> updates(){
    var reads=mock(KnowledgeGraph.ConsequenceReadSet.class);when(reads.contextId()).thenReturn("context");when(reads.assetIds()).thenReturn(List.of(41L));
    var receipt=new ConfigurationConsequenceRuntime.AffectsReceipt(41,"source-backend-validated-model",request,proof(request,3),reads);
    return List.of(new ConfigurationConsequenceRuntime.Update(
        new ConfigurationConsequenceRuntime.Revision(41,1,9,List.of(),"1"),
        new ConfigurationConsequenceRuntime.Revision(41,2,10,List.of(),"1"),List.of(reads),true,List.of(receipt)));
  }
  RuntimeService.ConfigurationPreparation noModel()throws Exception {
    var flow=new DataflowImpl();flow.setEmpty(true);flow.setResolutionOutcome(Dataflow.ResolutionOutcome.NO_MODEL);
    when(f.resolver.resolve(any(),same(f.scope))).thenReturn(CompletableFuture.completedFuture(flow));
    return f.runtime.prepareConfigurationExplanation(f.configuration,f.scope);
  }
  void complete(RuntimeService.ConfigurationPreparation receipt)throws Exception {
    completion.complete(f.transaction,List.of(f.configuration),List.of(new GuardedConfigurationPublisher.Prepared(
        GuardedConfigurationPublisher.Preparation.valueOf(receipt.status().name()),receipt.support(),receipt)),updates(),List.of(),f.scope);
  }
  @Test void actualTypedNoModelFinishesBoundedDetectionWithoutClaimingScientificExecution()throws Exception {
    complete(noModel());var binding=Utils.Json.parseObject((String)activity.getMetadata().get(SemanticEvidenceBinding.DATA_KEY),SemanticEvidenceBinding.class);
    assertTrue(binding.complete());assertTrue(binding.obligations().containsAll(request.obligations()));
    verify(f.transaction).update(activity);verify(f.transaction,never()).registerExecutors();
  }
  @Test void privatePreparationAndOwnedDetectionAreMandatory()throws Exception {
    var receipt=noModel();when(f.transaction.ownsExecutionActivity(activity)).thenReturn(false);
    assertThrows(SecurityException.class,()->complete(receipt));when(f.transaction.ownsExecutionActivity(activity)).thenReturn(true);
    assertThrows(SecurityException.class,()->completion.complete(f.transaction,List.of(f.configuration),
        List.of(new GuardedConfigurationPublisher.Prepared(GuardedConfigurationPublisher.Preparation.NO_MODEL,receipt.support(),new Object())),updates(),List.of(),f.scope));
    assertFalse(activity.getMetadata().containsKey(SemanticEvidenceBinding.DATA_KEY));
  }
  @Test void sameContentWrongCurrentRevisionAndUnknownAuthorityCannotIssueProof()throws Exception {
    var receipt=noModel();when(reasoner.semanticDependencies(any(),same(f.scope))).thenAnswer(call->proof(call.getArgument(0),2));
    assertThrows(IllegalStateException.class,()->complete(receipt));
    when(reasoner.semanticDependencies(any(),same(f.scope))).thenAnswer(call->SemanticDependencyWitness.unknown(call.getArgument(0),"unavailable"));
    assertThrows(IllegalStateException.class,()->complete(receipt));assertFalse(activity.getMetadata().containsKey(SemanticEvidenceBinding.DATA_KEY));
  }
  @Test void successfulNativeOrCheckOnlyChildCannotReplacePreparedScientificExecution()throws Exception {
    var plan=new ActuatorImpl();plan.setId(41);plan.setActuatorType(Actuator.Type.OBSERVE);plan.setCoverage(f.configuration.getGeometry());
    var flow=new DataflowImpl();flow.setResolutionOutcome(Dataflow.ResolutionOutcome.RESOLVED);flow.setCoverage(f.configuration.getGeometry());flow.getComputation().add(plan);
    when(f.resolver.resolve(any(),same(f.scope))).thenReturn(CompletableFuture.completedFuture(flow));
    try(var compiled=mockConstruction(CompiledDataflow.class,(mock,context)->{
      when(mock.compile(plan)).thenReturn(true);when(mock.store(f.transaction)).thenReturn(true);
      when(mock.scientificRootCompletionSupport(f.configuration,f.scope)).thenReturn(Optional.empty());
    })) {
      var receipt=f.runtime.prepareConfigurationExplanation(f.configuration,f.scope);
      var invocation=receipt.executionScope(f.configuration,f.scope);
      receipt.recordExecutionSupport(f.configuration,invocation,f.configuration.getGeometry(),Scheduler.Event.initialization());
      var child=Activity.of(Activity.Type.CLASSIFICATION);child.setOutcome(Activity.Outcome.SUCCESS);
      var evidence=new SemanticExecutionEvidence.Collector();evidence.scope(f.scope);evidence.term("test:Network");
      child.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(evidence.finish(f.scope)));
      when(f.transaction.outgoing(activity)).thenReturn(List.of(new org.integratedmodelling.klab.api.data.impl.LinkImpl(activity,child,GraphModel.Relationship.TRIGGERED)));
      when(f.transaction.ownsExecutionActivity(child)).thenReturn(true);
      when(f.transaction.outgoing(child)).thenReturn(List.of(new org.integratedmodelling.klab.api.data.impl.LinkImpl(child,f.configuration,GraphModel.Relationship.DETECTED)));
      assertThrows(IllegalStateException.class,()->complete(receipt));assertFalse(activity.getMetadata().containsKey(SemanticEvidenceBinding.DATA_KEY));
    }
  }
}

