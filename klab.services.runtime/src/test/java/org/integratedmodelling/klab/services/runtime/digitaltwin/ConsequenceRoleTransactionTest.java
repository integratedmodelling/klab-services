package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.*;

class ConsequenceRoleTransactionTest {
  ClassificationTransactionTest f;
  Observation source;
  ObservationImpl recipient;
  ObservableImpl before,after;
  ConceptImpl role;
  ConsequenceRoleAdmission admission;
  KnowledgeGraph.ConsequenceReadSet reads;
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  @BeforeEach void setup() throws Exception {setup(new ClassificationTransactionTest());}
  void setup(ClassificationTransactionTest fixture) throws Exception {
    f=fixture;f.setup();
    var software=mock(org.integratedmodelling.klab.api.provenance.Agent.class);
    when(software.getName()).thenReturn("k.LAB");when(software.getId()).thenReturn(3L);when(f.kg.klab()).thenReturn(software);
    var value=f.pending(41,true);recipient=(ObservationImpl)value.member();source=f.pending(70,false).member();
    var meaning=new ConceptImpl();meaning.setUrn("test:Recipient");
    meaning.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
    before=new ObservableImpl();before.setUrn(meaning.getUrn());before.setSemantics(meaning);before.setDescriptionType(Contextualization.ACKNOWLEDGEMENT);before.setArtifactType(Artifact.Type.forSemantics(meaning.getType()));recipient.setObservable(before);
    var processMeaning=new ConceptImpl();processMeaning.setUrn("test:Process");processMeaning.setType(EnumSet.of(SemanticType.PROCESS,SemanticType.OBSERVABLE));
    var processObservable=new ObservableImpl();processObservable.setUrn(processMeaning.getUrn());processObservable.setSemantics(processMeaning);processObservable.setDescriptionType(Contextualization.SIMULATION);
    processObservable.setArtifactType(Artifact.Type.forSemantics(processMeaning.getType()));((ObservationImpl)source).setObservable(processObservable);
    after=new ObservableImpl(before);after.setUrn("test:ConferredRecipient");
    when(f.reasoner.buildObservable(any(ObservableBuildStrategy.class),eq(f.scope))).thenReturn(after);
    when(f.reasoner.hasRole(argThat((Semantics candidate)->candidate!=null&&after.getUrn().equals(candidate.getUrn())),any())).thenReturn(true);
    role=new ConceptImpl();role.setUrn("test:Role");role.setType(EnumSet.of(SemanticType.ROLE,SemanticType.PREDICATE));
    when(f.scope.getConsequenceCause()).thenReturn(new Consequence.Cause(1,"context",1,2,0,null,null,"decision",Consequence.Kind.ROLE_CONFERRAL));
    admission=mock(ConsequenceRoleAdmission.class);when(admission.provenance()).thenReturn(Map.of("ruleId","rule","semanticSession","frozen-cycle"));
    var epoch=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding(
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding.VERSION,"reasoner","session",1,
        DefaultRoleConsequenceRuntimeTest.FP,"lease");
    if(f.scope.getResolutionCycle()==null)when(f.scope.getResolutionCycleBinding()).thenReturn(new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(1,"cycle","context","runtime",true,epoch));
    var request=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest("reasoner",1,epoch.semanticFingerprint(),List.of(before.getUrn(),after.getUrn(),role.getUrn()));
    when(admission.semanticEvidence()).thenReturn(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,request.obligations(),DefaultRoleConsequenceRuntimeTest.witness(request.inputHash())));
    when(f.reasoner.semanticDependencies(any(),any())).thenAnswer(call->DefaultRoleConsequenceRuntimeTest.witness(call.getArgument(0,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest.class).inputHash()));
    reads=new KnowledgeGraph.ConsequenceReadSet("context",List.of(snapshot(source),snapshot(recipient)),List.of(),List.of());
  }
  KnowledgeGraph.ConsequenceAssetSnapshot snapshot(Observation o) {
    return new KnowledgeGraph.ConsequenceAssetSnapshot(o.getId(),"native-"+o.getId(),List.of("Observation"),Map.of(),"S2",null);
  }
  ConsequenceRoleAttribution input(){return new ConsequenceRoleAttribution(source,recipient,role,"rule");}
  List<Long> stage() {
    for(var asset:reads.assets())when(f.kg.materializeConsequenceObservation(asset,f.scope))
        .thenReturn(asset.id()==source.getId()?source:recipient);
    f.root.guardConsequenceBaseline(reads,f.scope);
    return f.child.stageConsequenceRoles(List.of(input()),f.scope,admission);
  }
  @Test void additiveRoleStagesWithoutCharacterizationAndPreservesRecipientIdentity() {
    assertEquals(List.of(41L),stage());
    assertSame(before,recipient.getObservable());
    var staged=f.root.readObservation(recipient);
    assertEquals(after.getUrn(),staged.getObservable().getUrn());assertEquals(41,staged.getId());
    assertEquals(recipient.getParentId(),staged.getParentId());
    verify(f.scope,never()).observation(any());
    var rows=org.mockito.ArgumentCaptor.forClass(List.class);verify(admission).validate(rows.capture(),eq(f.scope));
    var row=(ConsequenceRoleAdmission.Prepared)rows.getValue().getFirst();
    assertEquals(70,row.sourceId());assertEquals(41,row.recipientId());assertEquals("rule",row.ruleId());
    assertTrue(f.activity.getMetadata().containsKey("klab.consequence.roleAttributions"));
    var accounting=org.integratedmodelling.klab.utilities.Utils.Json.parseObject((String)f.activity.getMetadata().get(SemanticEvidenceBinding.DATA_KEY),SemanticEvidenceBinding.class);
    assertEquals(admission.semanticEvidence(),accounting);
  }
  @Test void absentOrMismatchedExecutionAccountingCannotPublishIntrinsicRole() {
    when(admission.semanticEvidence()).thenReturn(null);
    assertThrows(IllegalStateException.class,this::stage);assertSame(before,recipient.getObservable());
    verify(f.storage,never()).store(any());
  }
  @Test void inputMismatchedExecutionProofCannotPublishIntrinsicRole() {
    var original=admission.semanticEvidence();
    when(admission.semanticEvidence()).thenReturn(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,List.of("test:DifferentInputs"),original.witness()));
    assertThrows(IllegalStateException.class,this::stage);assertSame(before,recipient.getObservable());verify(f.storage,never()).store(any());
  }
  @Test void existingInheritedRoleIsANoOpAndDoesNotStartAClassifierOrCharacterizer() {
    when(f.reasoner.hasRole(any(),any())).thenReturn(true);
    assertTrue(stage().isEmpty());assertSame(before,recipient.getObservable());
    verify(f.reasoner,never()).buildObservable(any(),any());verify(f.scope,never()).observation(any());
  }
  @Test void causalHeaderAloneCannotStageRolesWithoutReadEvidenceAndAdmission() {
    assertThrows(NullPointerException.class,()->f.child.stageConsequenceRoles(List.of(input()),f.scope,null));
    f.root.guardConsequenceReadSet(reads);
    assertThrows(IllegalArgumentException.class,()->f.child.stageConsequenceRoles(List.of(input()),f.scope,admission));
    assertSame(before,recipient.getObservable());verify(f.storage,never()).store(any());
  }
  @Test void nativeMaterializationRunsOutsideTheGraphMonitorAndPinsTheEarliestSnapshot() throws Exception {
    var graphField=DigitalTwinImpl.TransactionImpl.class.getDeclaredField("graph");graphField.setAccessible(true);var graph=graphField.get(f.root);
    try(var callback=Executors.newSingleThreadExecutor()) {
      when(f.kg.materializeConsequenceObservation(any(),eq(f.scope))).thenAnswer(call->{
        assertFalse(Thread.holdsLock(graph));
        callback.submit(()->f.root.readObservation(recipient)).get(2,TimeUnit.SECONDS);
        var snapshot=call.getArgument(0,KnowledgeGraph.ConsequenceAssetSnapshot.class);
        return snapshot.id()==source.getId()?source:recipient;
      });
      f.root.guardConsequenceBaseline(reads,f.scope);
      var changed=new KnowledgeGraph.ConsequenceReadSet("context",List.of(
          new KnowledgeGraph.ConsequenceAssetSnapshot(70,"replacement-native-node",List.of("Observation"),Map.of(),"S2",null),snapshot(recipient)),List.of(),List.of());
      assertThrows(IllegalStateException.class,()->f.root.guardConsequenceBaseline(changed,f.scope));
      verify(f.storage,never()).store(any());assertTrue(f.root.isFailed());
    }
  }
  @Test void admissionFailureDoesNotExposePartialRoleEffects() {
    doThrow(new IllegalStateException("rule evidence changed")).when(admission).validate(anyList(),eq(f.scope));
    assertThrows(IllegalStateException.class,this::stage);
    assertTrue(f.root.isFailed());assertSame(before,recipient.getObservable());verify(f.storage,never()).store(any());
  }
  @Test void sourceMutationWhilePreparingRejectsTheEffect() {
    doAnswer(call->{source.getMetadata().put("changed",true);return null;}).when(admission).validate(anyList(),eq(f.scope));
    assertThrows(IllegalStateException.class,this::stage);
    assertSame(before,recipient.getObservable());assertTrue(f.root.isFailed());
  }
  @Test void semanticCommitAdmissionRunsOutsideTheGraphMonitorAndBeforeNativeWrites() throws Exception {
    stage();
    var graphField=DigitalTwinImpl.TransactionImpl.class.getDeclaredField("graph");graphField.setAccessible(true);var graph=graphField.get(f.root);
    try(var callback=Executors.newSingleThreadExecutor()) {
      doAnswer(call->{
        assertFalse(Thread.holdsLock(graph));
        assertEquals(after.getUrn(),callback.submit(()->f.root.readObservation(recipient).getObservable().getUrn()).get(2,TimeUnit.SECONDS));
        verify(f.storage,never()).store(any());return null;
      }).when(admission).admitCommit();
      assertTrue(f.root.commit()>0);
    }
    verify(admission).admitCommit();
    var order=inOrder(f.storage);order.verify(f.storage).lockConsequenceInputs(new TreeSet<>(List.of(41L,70L)));
    order.verify(f.storage).validateConsequenceInputs(reads);
    verify(f.storage).updateSemantics(any(),eq(before.getUrn()));
  }
  @Test void staleBoundAuthorityOrNativeReadEvidencePreventsDurableWrites() throws Exception {
    stage();doThrow(new IllegalStateException("frozen proof unavailable")).when(admission).admitCommit();
    assertEquals(-1,f.root.commit());assertSame(before,recipient.getObservable());verify(f.storage,never()).store(any());
    setup();stage();doThrow(new IllegalStateException("source edge changed")).when(f.storage).validateConsequenceInputs(reads);
    assertEquals(-1,f.root.commit());assertSame(before,recipient.getObservable());verify(f.storage,never()).store(any());
  }
}
