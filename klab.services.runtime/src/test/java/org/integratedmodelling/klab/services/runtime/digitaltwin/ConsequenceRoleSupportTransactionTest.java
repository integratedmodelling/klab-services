package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy;
import org.junit.jupiter.api.*;

/** Actual DT staging/commit regressions; the existing certified semantic fixture is controlled. */
class ConsequenceRoleSupportTransactionTest {
  ConsequenceRoleTransactionTest f;
  @BeforeAll static void configure() { ConsequenceRoleTransactionTest.configure(); }
  @BeforeEach void setup() throws Exception { f=new ConsequenceRoleTransactionTest();f.setup(); }

  ConsequenceRoleSupportResult stage() {
    for(var asset:f.reads.assets())when(f.f.kg.materializeConsequenceObservation(asset,f.f.scope))
        .thenReturn(asset.id()==f.source.getId()?f.source:f.recipient);
    f.f.root.guardConsequenceBaseline(f.reads,f.f.scope);
    return f.f.child.stageConsequenceRoleSupports(List.of(f.input()),f.f.scope,f.admission);
  }
  @SuppressWarnings("unchecked") List<Map<String,Object>> rows() {
    return (List<Map<String,Object>>)f.f.activity.getMetadata().get(RoleAttributionEvents.METADATA_KEY);
  }

  @Test void existingRoleStillCommitsAdmittedSupportWithoutNewMeaningOrAcquisition() throws Exception {
    when(f.f.reasoner.hasRole(any(),any())).thenReturn(true);
    var result=stage();assertTrue(result.supportChanged());assertTrue(result.acquiredRecipientIds().isEmpty());
    assertSame(f.before,f.recipient.getObservable());assertEquals(f.before.getUrn(),f.f.root.readObservation(f.recipient).getObservable().getUrn());
    var row=rows().getFirst();assertEquals(false,row.get("acquired"));assertEquals(row.get("before"),row.get("after"));
    assertEquals(ConsequenceRoleSupportResult.CONTRACT,row.get("supportContract"));
    assertEquals("context",row.get("contextId"));assertEquals(0L,row.get("observerId"));assertEquals(0L,row.get("focusId"));
    verify(f.admission).validate(argThat(values->values.size()==1&&values.getFirst().before().getUrn().equals(values.getFirst().after().getUrn())),eq(f.f.scope));
    assertTrue(f.f.root.commit()>0);verify(f.admission).admitCommit();
    verify(f.f.storage,never()).updateSemantics(any(),any());
    verify(f.f.reasoner,never()).buildObservable(any(ObservableBuildStrategy.class),any());
    verify(f.f.scope,never()).observation(any());
  }

  @Test void independentSecondOriginRetainsItsOwnAuditWithoutReacquiringTheRole() {
    assertEquals(List.of(41L),stage().acquiredRecipientIds());
    var second=f.f.pending(71,false).member();((org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)second).setObservable(f.source.getObservable());
    f.source=second;f.reads=new KnowledgeGraph.ConsequenceReadSet("context",List.of(f.snapshot(second),f.snapshot(f.recipient)),List.of(),List.of());
    var support=stage();assertTrue(support.supportChanged());assertTrue(support.acquiredRecipientIds().isEmpty());
    assertEquals(Set.of(70L,71L),rows().stream().map(row->((Number)row.get("sourceId")).longValue()).collect(java.util.stream.Collectors.toSet()));
    assertEquals(List.of(true,false),rows().stream().map(row->row.get("acquired")).toList());
    assertEquals(f.after.getUrn(),f.f.root.readObservation(f.recipient).getObservable().getUrn());
  }

  @Test void supportOnlyCommitStillRejectsExpiredRuleAndNativeSourceDrift() throws Exception {
    when(f.f.reasoner.hasRole(any(),any())).thenReturn(true);stage();
    doThrow(new IllegalStateException("source rule changed")).when(f.admission).admitCommit();
    assertEquals(-1,f.f.root.commit());verify(f.f.storage,never()).store(any());
    setup();when(f.f.reasoner.hasRole(any(),any())).thenReturn(true);stage();
    doThrow(new IllegalStateException("native source changed")).when(f.f.storage).validateConsequenceInputs(f.reads);
    assertEquals(-1,f.f.root.commit());verify(f.f.storage,never()).store(any());
  }

  @Test void missingExecutionAccountingCannotMintSupportForAnAlreadyPresentRole() {
    when(f.f.reasoner.hasRole(any(),any())).thenReturn(true);when(f.admission.semanticEvidence()).thenReturn(null);
    assertThrows(IllegalStateException.class,this::stage);assertSame(f.before,f.recipient.getObservable());
    verify(f.f.storage,never()).store(any());
  }
}
