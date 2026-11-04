package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.junit.jupiter.api.Test;

class RoleConsequenceHandlersTest {
  static Concept role(String urn){var role=mock(Concept.class);when(role.getUrn()).thenReturn(urn);return role;}
  static RoleConsequenceRuntime.Effect effect(Consequence.Outcome outcome,Long...ids) {
    return new RoleConsequenceRuntime.Effect(outcome,List.of(ids),outcome.name());
  }
  static class Fixture {
    final ConsequenceRecipientsTest.Fixture recipients=new ConsequenceRecipientsTest.Fixture();
    final RoleConsequenceRuntime runtime=mock(RoleConsequenceRuntime.class);
    final CommitImpl commit=new CommitImpl();
    final Concept role=role("test:Role");
    final RoleConsequenceRuntime.Rule rule=new RoleConsequenceRuntime.Rule("source-rule",role,ConsequenceRecipients.Selector.DEFAULT,null);
    Fixture() throws Exception {
      when(runtime.attributeSelected(any(),any(),any(),any(),any())).thenAnswer(call->runtime.attribute(
          call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3,ConsequenceRecipients.Selection.class).recipients(),call.getArgument(4)));
      when(runtime.resolveRecipients(any(),any(),any())).thenAnswer(call->runtime.resolveEach(
          call.getArgument(1,RoleConsequenceRuntime.Rule.class).recipient(),call.getArgument(2)));
      commit.setId(1);commit.getAddedObservations().add(recipients.process.getId());
      when(runtime.conferrals(recipients.process,recipients.scope)).thenReturn(new RoleConsequenceRuntime.Rules(ConsequenceRecipients.Status.COMPLETE,List.of(rule),"complete"));
      when(runtime.attribute(any(),eq(recipients.process),eq(rule),anyList(),eq(recipients.scope)))
          .thenReturn(effect(Consequence.Outcome.APPLIED,recipients.source.getId()));
      when(runtime.sourceImplications(any(),any(),eq(recipients.scope))).thenReturn(effect(Consequence.Outcome.NO_ACTION));
    }
  }
  @Test void conferralCommitsTheRoleWithoutAnyCharacterizationLookup() throws Exception {
    var f=new Fixture();var result=new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
    assertEquals(Consequence.Outcome.APPLIED,result.outcome());assertEquals(List.of(20L),result.observationIds());
    verify(f.runtime).attribute(null,f.recipients.process,f.rule,List.of(f.recipients.source),f.recipients.scope);
    verify(f.runtime,never()).characterize(any(),any(),any());verify(f.runtime,never()).implications(any(),any(),any());
  }
  @Test void noModelCharacterizationCannotUndoRolesAndEveryImplicationPrecedesIt() throws Exception {
    var f=new Fixture();var durableRoles=new HashSet<Long>();
    when(f.runtime.attribute(any(),any(),any(),anyList(),any())).thenAnswer(call->{
      for(var recipient:call.getArgument(3,List.class))durableRoles.add(((Observation)recipient).getId());
      return effect(Consequence.Outcome.APPLIED,20L);
    });
    assertEquals(Consequence.Outcome.APPLIED,new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope).outcome());
    var a=new RoleConsequenceRuntime.Arrival(10,20,f.role,"source-rule",true);
    var b=new RoleConsequenceRuntime.Arrival(11,21,role("test:OtherRole"),"other-rule",true);
    when(f.runtime.arrivals(f.commit,f.recipients.scope)).thenReturn(new RoleConsequenceRuntime.Arrivals(ConsequenceRecipients.Status.COMPLETE,List.of(a,b),"committed role effects"));
    var order=new ArrayList<String>();
    when(f.runtime.implications(any(),any(),any())).thenAnswer(call->{var arrival=call.getArgument(1,RoleConsequenceRuntime.Arrival.class);order.add("implies:"+arrival.recipientId());return effect(Consequence.Outcome.APPLIED,arrival.recipientId());});
    when(f.runtime.characterize(any(),any(),any())).thenAnswer(call->{var arrival=call.getArgument(1,RoleConsequenceRuntime.Arrival.class);order.add("characterize:"+arrival.recipientId());return effect(Consequence.Outcome.DEFERRED);});
    var result=new RoleImplicationHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
    assertEquals(List.of("implies:20","implies:21","characterize:20","characterize:21"),order);
    assertEquals(Consequence.Outcome.DEFERRED,result.outcome());assertEquals(Set.of(20L),durableRoles);
  }
  @Test void repeatedSupportDoesNotMasqueradeAsANewRoleAcquisition() throws Exception {
    var f=new Fixture();when(f.runtime.attribute(any(),any(),any(),anyList(),any())).thenReturn(effect(Consequence.Outcome.NO_ACTION));
    assertEquals(Consequence.Outcome.NO_ACTION,new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope).outcome());
    verify(f.runtime,never()).implications(any(),any(),any());verify(f.runtime,never()).characterize(any(),any(),any());
    f.commit.getAddedObservations().clear();f.commit.getModifiedAssets().add(20L);clearInvocations(f.runtime);
    assertEquals(Consequence.Outcome.NO_ACTION,new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope).outcome());
    verifyNoInteractions(f.runtime);
  }
  @Test void unavailableRulesOrRecipientsNeverApplyAPartialRole() throws Exception {
    var f=new Fixture();when(f.runtime.conferrals(any(),any())).thenReturn(new RoleConsequenceRuntime.Rules(ConsequenceRecipients.Status.DEFERRED,List.of(),"source rules unavailable"));
    var result=new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
    assertEquals(Consequence.Outcome.DEFERRED,result.outcome());
    assertTrue(result.explanation().contains("stage=rules: DEFERRED (source rules unavailable)"));
    verify(f.runtime,never()).attribute(any(),any(),any(),any(),any());
  }
  @Test void conferralFailuresRetainStageAndActualThrowableWithoutTransportingItsMessage() throws Exception {
    for(var stage:List.of("rules","recipient","attribution")) {
      var f=new Fixture();var failure=new IllegalStateException("https://private.invalid/?token=secret");
      if(stage.equals("rules"))when(f.runtime.conferrals(any(),any())).thenThrow(failure);
      else if(stage.equals("recipient"))when(f.recipients.scope.getParentOf(f.recipients.process)).thenThrow(failure);
      else doThrow(failure).when(f.runtime).attributeSelected(any(),any(),any(),any(),any());
      var result=new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
      var location="source="+f.recipients.process.getId()+" stage="+stage+(stage.equals("rules")?"":" rule=source-rule");
      assertEquals(Consequence.Outcome.FAILED,result.outcome());
      assertTrue(result.explanation().contains("0 applied, 0 deferred, 1 failed"));
      assertTrue(result.explanation().contains(location+": FAILED (IllegalStateException)"));
      assertFalse(result.explanation().contains("secret"));
      var safe=org.mockito.ArgumentCaptor.forClass(Throwable.class);
      verify(f.recipients.scope).warn(eq("Role conferral failed: "+location),safe.capture());
      assertArrayEquals(failure.getStackTrace(),safe.getValue().getStackTrace());
      var notification=org.integratedmodelling.klab.api.services.runtime.Notification.warning(
          "Role conferral failed: "+location,safe.getValue());
      assertFalse(notification.getMessage().contains("secret"));
      assertFalse(String.valueOf(notification.getStackTrace()).contains("secret"));
    }
  }
  @Test void unavailableRecipientAndAttributionReasonsSurviveTheDecision() throws Exception {
    var f=new Fixture();when(f.recipients.scope.getParentOf(f.recipients.process)).thenReturn(null);
    var result=new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
    assertEquals(Consequence.Outcome.DEFERRED,result.outcome());
    assertTrue(result.explanation().contains("stage=recipient rule=source-rule: DEFERRED (The process has no accessible substantial bearer)"));
    var g=new Fixture();doReturn(
        new RoleConsequenceRuntime.Effect(Consequence.Outcome.DEFERRED,List.of(),"native proof unavailable",RoleConsequenceRuntime.Resolution.UNRESOLVED))
        .when(g.runtime).attributeSelected(any(),any(),any(),any(),any());
    result=new RoleConferralHandler(g.runtime).evaluate(null,g.commit,g.recipients.scope);
    assertEquals(Consequence.Outcome.DEFERRED,result.outcome());
    assertTrue(result.explanation().contains("stage=attribution rule=source-rule: DEFERRED/UNRESOLVED (native proof unavailable)"));
  }
  @Test void cancellationIsRethrownWithoutBecomingAConferralFailure() throws Exception {
    var f=new Fixture();var cancellation=new InterruptedException("cancelled");
    when(f.runtime.conferrals(any(),any())).thenThrow(cancellation);
    assertSame(cancellation,assertThrows(InterruptedException.class,
        ()->new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope)));
  }
  @Test void diagnosticListenerFailureCannotReplaceTheConferralDecision() throws Exception {
    var f=new Fixture();
    when(f.runtime.conferrals(any(),any())).thenThrow(new IllegalStateException("native rejection"));
    doThrow(new IllegalArgumentException("listener failed")).when(f.recipients.scope).warn(anyString(),any(Throwable.class));
    var result=new RoleConferralHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
    assertEquals(Consequence.Outcome.FAILED,result.outcome());
    assertTrue(result.explanation().contains("stage=rules: FAILED (IllegalStateException)"));
    assertFalse(result.explanation().contains("listener failed"));
  }
  @Test void duplicateCommittedRoleRowsImplyOnceAndOrdinaryRolesAreNotCharacterizedAgain() throws Exception {
    var f=new Fixture();
    var arrival=new RoleConsequenceRuntime.Arrival(10,20,f.role,null,false);
    when(f.runtime.arrivals(any(),any())).thenReturn(new RoleConsequenceRuntime.Arrivals(ConsequenceRecipients.Status.COMPLETE,List.of(arrival,arrival),"model classification"));
    when(f.runtime.implications(any(),any(),any())).thenReturn(effect(Consequence.Outcome.APPLIED,20L));
    assertEquals(Consequence.Outcome.APPLIED,new RoleImplicationHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope).outcome());
    verify(f.runtime,times(1)).implications(null,arrival,f.recipients.scope);verify(f.runtime,never()).characterize(any(),any(),any());
  }
  @Test void optionalNoModelUnresolvedAndFailedRemainDistinctInEvidence() throws Exception {
    var f=new Fixture();var arrival=new RoleConsequenceRuntime.Arrival(10,20,f.role,"source-rule",true);
    when(f.runtime.arrivals(any(),any())).thenReturn(new RoleConsequenceRuntime.Arrivals(ConsequenceRecipients.Status.COMPLETE,List.of(arrival),"complete"));
    when(f.runtime.sourceImplications(any(),any(),any())).thenReturn(new RoleConsequenceRuntime.Effect(Consequence.Outcome.NO_ACTION,List.of(),"no characterization model",RoleConsequenceRuntime.Resolution.NO_MODEL));
    when(f.runtime.implications(any(),any(),any())).thenReturn(new RoleConsequenceRuntime.Effect(Consequence.Outcome.DEFERRED,List.of(),"target unresolved",RoleConsequenceRuntime.Resolution.UNRESOLVED));
    when(f.runtime.characterize(any(),any(),any())).thenReturn(new RoleConsequenceRuntime.Effect(Consequence.Outcome.FAILED,List.of(),"optional resolution failed",RoleConsequenceRuntime.Resolution.FAILED));
    var result=new RoleImplicationHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
    assertEquals(Consequence.Outcome.FAILED,result.outcome());
    assertTrue(result.explanation().contains("NO_MODEL"));assertTrue(result.explanation().contains("UNRESOLVED"));assertTrue(result.explanation().contains("FAILED"));
  }
  @Test void oneOptionalImplicationFailureDoesNotSkipOtherRolesOrCharacterization() throws Exception {
    var f=new Fixture();var a=new RoleConsequenceRuntime.Arrival(10,20,f.role,"a",true);
    var b=new RoleConsequenceRuntime.Arrival(11,21,role("test:B"),"b",true);
    when(f.runtime.arrivals(any(),any())).thenReturn(new RoleConsequenceRuntime.Arrivals(ConsequenceRecipients.Status.COMPLETE,List.of(a,b),"new roles"));
    when(f.runtime.implications(null,a,f.recipients.scope)).thenThrow(new IllegalStateException("optional failure"));
    when(f.runtime.implications(null,b,f.recipients.scope)).thenReturn(effect(Consequence.Outcome.APPLIED,21L));
    when(f.runtime.characterize(any(),any(),any())).thenReturn(effect(Consequence.Outcome.NO_ACTION));
    assertEquals(Consequence.Outcome.FAILED,new RoleImplicationHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope).outcome());
    var order=inOrder(f.runtime);order.verify(f.runtime).implications(null,a,f.recipients.scope);
    order.verify(f.runtime).implications(null,b,f.recipients.scope);order.verify(f.runtime).characterize(null,a,f.recipients.scope);
    order.verify(f.runtime).characterize(null,b,f.recipients.scope);
  }
  @Test void wrappedCharacterizationFailurePreservesSafeCauseAndNeverBecomesNoModel() throws Exception {
    var f=new Fixture();var arrival=new RoleConsequenceRuntime.Arrival(10,20,f.role,"a",true);
    when(f.runtime.arrivals(any(),any())).thenReturn(new RoleConsequenceRuntime.Arrivals(ConsequenceRecipients.Status.COMPLETE,List.of(arrival),"new role"));
    when(f.runtime.implications(any(),any(),any())).thenReturn(effect(Consequence.Outcome.APPLIED,21L));
    var cause=new IllegalStateException("https://private.invalid/?token=secret");
    var failure=new java.util.concurrent.ExecutionException(cause);
    when(f.runtime.characterize(any(),any(),any())).thenThrow(failure);
    var result=new RoleImplicationHandler(f.runtime).evaluate(null,f.commit,f.recipients.scope);
    assertEquals(Consequence.Outcome.FAILED,result.outcome());assertEquals(List.of(21L),result.observationIds());
    assertTrue(result.explanation().contains("characterize:20=FAILED (ExecutionException)"));
    assertFalse(result.explanation().contains("NO_MODEL"));assertFalse(result.explanation().contains("secret"));
    var safe=org.mockito.ArgumentCaptor.forClass(Throwable.class);
    verify(f.recipients.scope).warn(eq("Role characterization failed: recipient=20"),safe.capture());
    assertNotNull(safe.getValue().getCause());assertArrayEquals(cause.getStackTrace(),safe.getValue().getCause().getStackTrace());
    var notification=org.integratedmodelling.klab.api.services.runtime.Notification.warning("characterization failed",safe.getValue());
    assertFalse(String.valueOf(notification.getStackTrace()).contains("secret"));
  }
}
