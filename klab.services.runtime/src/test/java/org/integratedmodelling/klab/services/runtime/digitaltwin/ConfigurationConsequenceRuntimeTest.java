package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;
class ConfigurationConsequenceRuntimeTest {
  static final String FINGERPRINT="semantic-content-v2:"+"a".repeat(64);
  static final Consequence.Cause CAUSE=new Consequence.Cause(1,"context",10,11,0,FINGERPRINT,null,"decision",Consequence.Kind.CONFIGURATION_DETECTION);
  static final class Fixture {
    final ServiceContextScope scope=mock(ServiceContextScope.class);final KnowledgeGraph.Commit commit=mock(KnowledgeGraph.Commit.class);
    final ConfigurationConsequenceRuntime.Backend backend=mock(ConfigurationConsequenceRuntime.Backend.class);final Reasoner reasoner=mock(Reasoner.class);
    final ConfigurationConsequenceRuntime runtime=new ConfigurationConsequenceRuntime(backend);
    Fixture()throws Exception {
      when(commit.getId()).thenReturn(10L);when(scope.getId()).thenReturn("context");
      when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
          new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,FINGERPRINT,"lease")));
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(backend.changes(any(),any(),any())).thenReturn(
          new ConfigurationConsequenceRuntime.Changes(ConfigurationConsequenceRuntime.Status.COMPLETE,List.of(),"captured"));
      when(backend.affected(any(),any(),any())).thenReturn(List.of());
      when(backend.publish(any(),any(),any())).thenAnswer(call->new ConfigurationConsequenceRuntime.Effect(Consequence.Outcome.APPLIED,
          List.of(((ConfigurationConsequenceRuntime.Update)call.getArgument(0)).next().observationId()),"recomputed"));
    }
  }
  static ConsequenceReadSet read(long... ids) {
    return new ConsequenceReadSet("context",Arrays.stream(ids).mapToObj(id->new ConsequenceAssetSnapshot(id,"e"+id,List.of("Observation"),Map.of(),"1",null)).toList(),List.of(),List.of());
  }
  static ConfigurationConsequenceRuntime.AffectsReceipt authority() {
    var request=new SemanticDependencyRequest("reasoner",1,FINGERPRINT,List.of("test:Network"));
    var proof=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FINGERPRINT,request.inputHash(),List.of(),null);
    return new ConfigurationConsequenceRuntime.AffectsReceipt(90,"{modeledNetwork:true}",request,proof,read(90,1,3));
  }
  static ConfigurationConsequenceRuntime.Affected affected(List<Long> participants) {
    var previous=new ConfigurationConsequenceRuntime.Revision(90,2,8,List.of(1L,2L),"1");
    return new ConfigurationConsequenceRuntime.Affected(new ConfigurationConsequenceRuntime.Existing(previous,read(90)),List.of(2L),participants,"1",read(1,3),authority());
  }
  @Test void cessationRecomputesSameConfigurationAndPreservesPreviousHistory()throws Exception {
    var f=new Fixture();when(f.commit.getDeletedAssets()).thenReturn(Set.of(2L));var event=affected(List.of(1L));
    when(f.backend.affected(any(),any(),any())).thenReturn(List.of(event));
    assertEquals(Consequence.Outcome.APPLIED,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());
    var update=org.mockito.ArgumentCaptor.forClass(ConfigurationConsequenceRuntime.Update.class);verify(f.backend).publish(update.capture(),eq(CAUSE),eq(f.scope));
    assertEquals(90,update.getValue().next().observationId());assertEquals(3,update.getValue().next().number());
    assertEquals(List.of(1L),update.getValue().next().participants());assertEquals(List.of(1L,2L),event.existing().previous().participants());
    assertTrue(update.getValue().recompute());verifyNoInteractions(f.reasoner);
  }
  @Test void explicitAffectsAssociationKeepsIdentityEvenWithCompleteParticipantReplacement()throws Exception {
    var f=new Fixture();when(f.commit.getDeletedAssets()).thenReturn(Set.of(2L));when(f.backend.affected(any(),any(),any())).thenReturn(List.of(affected(List.of(3L))));
    assertEquals(Consequence.Outcome.APPLIED,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());
    var update=org.mockito.ArgumentCaptor.forClass(ConfigurationConsequenceRuntime.Update.class);verify(f.backend).publish(update.capture(),any(),any());
    assertEquals(90,update.getValue().next().observationId());assertEquals(List.of(3L),update.getValue().next().participants());
  }
  @Test void definitionDriftWithoutModeledEventDoesNotRewriteOrDeactivateConfigurations()throws Exception {
    var f=new Fixture();assertEquals(Consequence.Outcome.NO_ACTION,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());verifyNoInteractions(f.backend,f.reasoner);
  }
  @Test void disabledCycleHasNoAutomaticBackendOrSemanticSideEffects()throws Exception {
    var f=new Fixture();when(f.scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",false,null));
    assertEquals(Consequence.Outcome.NO_ACTION,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());verifyNoInteractions(f.backend,f.reasoner);
  }
  @Test void unavailableSemanticsDoesNotFallbackToCurrentReasoner()throws Exception {
    var f=new Fixture();when(f.scope.getResolutionCycleBinding()).thenReturn(null);
    assertEquals(Consequence.Outcome.DEFERRED,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());verifyNoInteractions(f.backend,f.reasoner);
  }
  @Test void missingNewParticipantNativeEvidenceCannotReachPublication()throws Exception {
    var f=new Fixture();when(f.commit.getDeletedAssets()).thenReturn(Set.of(2L));when(f.backend.affected(any(),any(),any())).thenReturn(List.of(affected(List.of(999L))));
    assertThrows(IllegalArgumentException.class,()->f.runtime.evaluate(CAUSE,f.commit,f.scope));verify(f.backend,never()).publish(any(),any(),any());
  }
  @Test void updatesAreImmutableAndCannotChangeObservationIdentityOrSkipRevision() {
    var old=new ConfigurationConsequenceRuntime.Revision(90,2,8,List.of(1L),"1");
    assertThrows(IllegalArgumentException.class,()->new ConfigurationConsequenceRuntime.Update(old,
        new ConfigurationConsequenceRuntime.Revision(91,3,10,List.of(1L),"1"),List.of(read(1,90)),true,List.of(authority())));
    assertThrows(IllegalArgumentException.class,()->new ConfigurationConsequenceRuntime.Update(old,
        new ConfigurationConsequenceRuntime.Revision(90,4,10,List.of(1L),"1"),List.of(read(1,90)),true,List.of(authority())));
    assertThrows(UnsupportedOperationException.class,()->old.participants().add(2L));
  }
  @Test void sourceAndRecipientLinkChangesAreIncludedWithoutAGlobalObservationScan() {
    var commit=mock(KnowledgeGraph.Commit.class);when(commit.getAddedObservations()).thenReturn(Set.of(1L));when(commit.getModifiedAssets()).thenReturn(Set.of(2L));
    when(commit.getDeletedAssets()).thenReturn(Set.of(3L));when(commit.getDeletedLinks()).thenReturn(Set.of(
        org.integratedmodelling.klab.api.collections.Triple.of(4L,5L,"HAS_RELATIONSHIP_SOURCE")));
    assertEquals(Set.of(1L,2L,3L,4L,5L),ConfigurationConsequenceRuntime.changedIds(commit));
  }
  static void matchingCandidate(Fixture f)throws Exception {
    var meaning=new org.integratedmodelling.common.knowledge.ConceptImpl();meaning.setUrn("test:Quality");meaning.setType(EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY));
    when(f.commit.getModifiedAssets()).thenReturn(Set.of(3L));when(f.backend.changes(any(),any(),any())).thenReturn(
        new ConfigurationConsequenceRuntime.Changes(ConfigurationConsequenceRuntime.Status.COMPLETE,List.of(meaning),"captured"));
    var target=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();target.setName("test:Quality");
    target.setType(EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY));target.resetDefinition();
    var recipe=new ConsequenceRuleResponse.Entry("rule","test:Pattern",meaning,new org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule(
        org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule.Kind.EMERGES_FROM,target,
        org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule.Recipient.implicit(),null,null,
        new org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.DeclarationClause("emergesFromClause","test:Quality",0,12)),"test","1","source");
    var epoch=new ConsequenceRuleResponse.Epoch("reasoner",1,FINGERPRINT,"session");
    var witness=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "reasoner",1,FINGERPRINT,"b".repeat(64),List.of(),null);
    when(f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.COMPLETE,epoch,List.of(recipe),witness,null));
    var placement=new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Placement(
        org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER,100);
    var facts=List.of(new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Fact(1,"test:Quality","1",true,100,0),
        new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Fact(3,"test:Quality","1",true,100,0));
    var bearer=new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Fact(100,"test:Bearer","1",false,0,200);
    var snapshot=new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Snapshot("context",0,epoch,facts,Map.of(placement,bearer),true,read(1,3,100));
    var semantics=mock(org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Semantics.class);
    when(semantics.epoch()).thenReturn(epoch);when(semantics.matches(any(),any())).thenReturn(org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Decision.YES);
    when(semantics.applicable(any(),any(),any())).thenReturn(org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Decision.YES);
    when(f.backend.domains(any(),any(),any())).thenReturn(List.of(new ConfigurationConsequenceRuntime.Domain(snapshot,semantics,(e,c,p)->"1")));
    when(f.backend.existing(any(),any(),any())).thenReturn(Optional.of(new ConfigurationConsequenceRuntime.Existing(
        new ConfigurationConsequenceRuntime.Revision(90,2,8,List.of(1L,2L),"1"),read(90))));
  }
  @Test void changedFactsQueryOnlyEmergenceCandidatesOnRetainedCycleAndUpdateExistingIdentity()throws Exception {
    var f=new Fixture();matchingCandidate(f);assertEquals(Consequence.Outcome.APPLIED,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());
    var query=org.mockito.ArgumentCaptor.forClass(ConsequenceRuleQuery.class);verify(f.reasoner).consequenceRules(query.capture());
    assertTrue(query.getValue().ownerTriggers().isEmpty());assertEquals("test:Quality",query.getValue().ingredientTriggers().getFirst().getUrn());
    assertEquals(1,query.getValue().expectedEpoch().knowledgeRevision());verify(f.backend).domains(any(),eq(Set.of(3L)),eq(f.scope));
    var update=org.mockito.ArgumentCaptor.forClass(ConfigurationConsequenceRuntime.Update.class);verify(f.backend).publish(update.capture(),any(),any());
    assertEquals(90,update.getValue().next().observationId());assertEquals(List.of(1L,3L),update.getValue().next().participants());assertEquals(2,update.getValue().reads().size());
  }
  @Test void newOrAmbiguousIdentityIsExplicitlyDeferredWithoutPublication()throws Exception {
    var f=new Fixture();matchingCandidate(f);when(f.backend.existing(any(),any(),any())).thenReturn(Optional.empty());
    assertEquals(Consequence.Outcome.DEFERRED,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());verify(f.backend,never()).publish(any(),any(),any());
  }
  @Test void unavailableCandidateResponseIsNotSuccessfulEmptyDetection()throws Exception {
    var f=new Fixture();matchingCandidate(f);when(f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(
        ConsequenceRuleResponse.Status.UNAVAILABLE,null,List.of(),null,"unavailable"));
    assertEquals(Consequence.Outcome.DEFERRED,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());verify(f.backend,never()).domains(any(),any(),any());verify(f.backend,never()).publish(any(),any(),any());
  }

  @Test void allRecipeReceiptsSurviveCoalescingTheSameConfigurationUpdate()throws Exception {
    var f=new Fixture();matchingCandidate(f);var response=f.reasoner.consequenceRules(null);var first=response.rules().getFirst();
    var second=new ConsequenceRuleResponse.Entry("other-rule",first.declarationUrn(),first.owner(),first.rule(),first.sourceOntology(),first.sourceVersion(),first.sourceHash());
    when(f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(response.status(),response.epoch(),List.of(first,second),response.witness(),null));
    assertEquals(Consequence.Outcome.APPLIED,f.runtime.evaluate(CAUSE,f.commit,f.scope).outcome());
    var update=org.mockito.ArgumentCaptor.forClass(ConfigurationConsequenceRuntime.Update.class);verify(f.backend).publish(update.capture(),any(),any());
    assertEquals(2,update.getValue().authorities().size());
    assertTrue(update.getValue().authorities().stream().allMatch(a->a instanceof ConfigurationConsequenceRuntime.SourceReceipt));
    assertEquals(2,update.getValue().authorities().stream().map(a->((ConfigurationConsequenceRuntime.SourceReceipt)a).recipeJson()).distinct().count());
  }
  @Test void providerDriftAtAdmissionCannotPublishAPlannedUpdate()throws Exception {
    var f=new Fixture();matchingCandidate(f);doThrow(new IllegalStateException("Provider changed")).when(f.backend).admit(any(),any());
    assertThrows(IllegalStateException.class,()->f.runtime.evaluate(CAUSE,f.commit,f.scope));verify(f.backend,never()).publish(any(),any(),any());
  }
  @Test void authorityExpiryAfterPlanningCannotPublish()throws Exception {
    var f=new Fixture();matchingCandidate(f);var response=f.reasoner.consequenceRules(null);var clock=new java.util.concurrent.atomic.AtomicLong(100);
    var expiring=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FINGERPRINT,
        response.witness().inputHash(),List.of(new SemanticDependencyWitness.AuthorityUse("AUTH",SemanticDependencyWitness.Kind.CAPTURED,"c".repeat(64),200)),null);
    when(f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(response.status(),response.epoch(),response.rules(),expiring,null));
    doAnswer(call->{clock.set(200);return null;}).when(f.backend).admit(any(),any());
    assertThrows(IllegalStateException.class,()->new ConfigurationConsequenceRuntime(f.backend,clock::get).evaluate(CAUSE,f.commit,f.scope));
    verify(f.backend,never()).publish(any(),any(),any());
  }

}
