package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

class ConsequenceInputEvidenceTest {
  static final String FP=DefaultRoleConsequenceRuntimeTest.FP;
  static Observation observation(long id,SemanticType type) {
    var concept=new ConceptImpl();concept.setUrn("test:Input"+id);concept.setType(EnumSet.of(type,SemanticType.OBSERVABLE));
    var observable=new ObservableImpl();observable.setSemantics(concept);observable.setUrn(concept.getUrn());observable.setDescriptionType(type==SemanticType.PROCESS?Contextualization.SIMULATION:Contextualization.ACKNOWLEDGEMENT);
    var result=new ObservationImpl();result.setId(id);result.setObservable(observable);return result;
  }
  static KnowledgeGraph.ConsequenceAssetRef ref(long id,String label){return new KnowledgeGraph.ConsequenceAssetRef("native-"+id,id,List.of(label));}
  static class Fixture {
    final Reasoner reasoner=mock(Reasoner.class);final ServiceContextScope scope=mock(ServiceContextScope.class);
    final Map<Long,Observation> observations=Map.of(70L,observation(70,SemanticType.PROCESS),41L,observation(41,SemanticType.SUBJECT),88L,observation(88,SemanticType.AGENT));
    final List<KnowledgeGraph.ConsequenceAssetSnapshot> assets=new ArrayList<>();final List<KnowledgeGraph.ConsequenceEdgeSnapshot> edges=new ArrayList<>();
    Fixture() {
      when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,FP,"lease")));
      when(reasoner.semanticDependencies(any(),eq(scope))).thenAnswer(call->DefaultRoleConsequenceRuntimeTest.witness(call.getArgument(0,SemanticDependencyRequest.class).inputHash()));
      var request=new SemanticDependencyRequest("reasoner",1,FP,List.of("test:ActualSourceModel"));
      var binding=new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,request.obligations(),DefaultRoleConsequenceRuntimeTest.witness(request.inputHash()));
      activity(3,binding);edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("source",ref(3,GraphModel.Labels.ACTIVITY),ref(70,GraphModel.Labels.OBSERVATION),GraphModel.Relationship.SIMULATED,Map.of()));
    }
    void activity(long id,SemanticEvidenceBinding binding) {
      assets.add(new KnowledgeGraph.ConsequenceAssetSnapshot(id,"native-"+id,List.of(GraphModel.Labels.ACTIVITY),Map.of(GraphModel.Fields.OUTCOME,Activity.Outcome.SUCCESS.name(),GraphModel.Fields.METADATA,Utils.Json.asString(binding==null?Map.of():Map.of(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(binding)))),null,null));
    }
    ConsequenceNativeInputs.Snapshot snapshot(){return new ConsequenceNativeInputs.Snapshot(new KnowledgeGraph.ConsequenceReadSet("context",assets,List.of(),edges),observations,88L,41);}
  }
  @Test void modelFreeRecipientAndObserverMeaningsDoNotInventSourceModelRequirements() {
    var f=new Fixture();var terms=ConsequenceInputEvidence.inherit(f.snapshot(),List.of(70L),List.of(41L,88L),f.reasoner,f.scope);
    assertTrue(terms.containsAll(List.of("test:ActualSourceModel","test:Input70","test:Input41","test:Input88")));
  }
  @Test void observableUnitsDoNotBecomeUnparseableSemanticObligationsOrReplaceExecutionProof() {
    var f=new Fixture();var observable=(ObservableImpl)f.observations.get(70L).getObservable();
    observable.setUrn("test:Input70 in m");
    var terms=ConsequenceInputEvidence.inherit(f.snapshot(),List.of(70L),f.reasoner,f.scope);
    assertTrue(terms.containsAll(List.of("test:Input70","test:ActualSourceModel")));
    assertFalse(terms.contains("test:Input70 in m"));assertEquals("test:Input70 in m",observable.getUrn());
    f.edges.clear();
    assertThrows(IllegalStateException.class,()->ConsequenceInputEvidence.inherit(f.snapshot(),List.of(70L),f.reasoner,f.scope));
  }
  @Test void missingConsumedPredicateAccountingCannotBeIgnoredForMeaningOnlyRecipient() {
    var f=new Fixture();f.activity(4,null);f.edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("unknown-attribution",ref(4,GraphModel.Labels.ACTIVITY),ref(41,GraphModel.Labels.OBSERVATION),GraphModel.Relationship.CLASSIFIED,Map.of()));
    assertThrows(IllegalStateException.class,()->ConsequenceInputEvidence.inherit(f.snapshot(),List.of(70L),List.of(41L,88L),f.reasoner,f.scope));
  }
  @Test void actualScientificSourceStillRequiresItsOriginalCompleteExecutionAccounting() {
    var f=new Fixture();f.edges.clear();
    assertThrows(IllegalStateException.class,()->ConsequenceInputEvidence.inherit(f.snapshot(),List.of(70L),List.of(41L,88L),f.reasoner,f.scope));
  }
  @Test void anotherInputsCompleteReceiptCannotCertifyTheConsumedSource() {
    var f=new Fixture();f.edges.clear();
    f.edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("unrelated-source",ref(3,GraphModel.Labels.ACTIVITY),
        ref(71,GraphModel.Labels.OBSERVATION),GraphModel.Relationship.SIMULATED,Map.of()));
    var rejected=assertThrows(IllegalStateException.class,()->ConsequenceInputEvidence.inherit(
        f.snapshot(),List.of(70L),List.of(41L,88L),f.reasoner,f.scope));
    assertEquals("Unknown consequence source execution accounting for 70",rejected.getMessage());
    verifyNoInteractions(f.reasoner);
  }
  @Test void diagnosticTraceCannotAdmitMissingScientificAccounting() {
    var previous=System.getProperty("klab.consequences.trace");
    System.setProperty("klab.consequences.trace","true");
    try {
      var f=new Fixture();f.assets.clear();f.activity(3,null);
      var rejected=assertThrows(IllegalStateException.class,()->ConsequenceInputEvidence.inherit(
          f.snapshot(),List.of(70L),List.of(41L,88L),f.reasoner,f.scope));
      assertEquals("Unknown consequence source execution accounting for 70",rejected.getMessage());
      verifyNoInteractions(f.reasoner);
    } finally {
      if(previous==null)System.clearProperty("klab.consequences.trace");else System.setProperty("klab.consequences.trace",previous);
    }
  }
  @Test void roleTriggerCannotInventAnAttributionEventFromNativeRoleTextAlone() {
    var f=new Fixture();assertThrows(IllegalStateException.class,()->ConsequenceInputEvidence.requireAttribution(f.snapshot(),41));
  }
}
