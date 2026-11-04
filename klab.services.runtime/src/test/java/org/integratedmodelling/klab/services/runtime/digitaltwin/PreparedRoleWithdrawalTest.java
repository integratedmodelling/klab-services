package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Native-capture projection unit controls with the existing owned-cycle/certified semantic stub.
 * These do not claim private modeled-cessation publication or native closure acceptance. */
class PreparedRoleWithdrawalTest {
  DefaultRoleConsequenceRuntimeTest.Fixture f;
  final List<KnowledgeGraph.ConsequenceAssetSnapshot> assets=new ArrayList<>();
  final List<KnowledgeGraph.ConsequenceEdgeSnapshot> edges=new ArrayList<>();
  @BeforeAll static void configure(){DefaultRoleConsequenceRuntimeTest.configure();}
  @BeforeEach void setup()throws Exception {
    f=new DefaultRoleConsequenceRuntimeTest.Fixture();
    f.role.recipient.setObservable(f.role.after);
    when(f.role.f.reasoner.resolveConcept(f.role.role.getUrn())).thenReturn(f.role.role);
    when(f.role.f.reasoner.resolveObservable(f.role.before.getUrn())).thenReturn(f.role.before);
    when(f.role.f.reasoner.resolveObservable(f.role.after.getUrn())).thenReturn(f.role.after);
    when(f.role.f.reasoner.buildObservable(any(),eq(f.role.f.scope))).thenAnswer(call->{
      var strategy=call.getArgument(0,ObservableBuildStrategy.class);
      return strategy.getOperations().stream().anyMatch(op->op.getType()==ObservableBuildStrategy.OperationType.WITH_ROLE&&!op.getConcepts().isEmpty())
          ?f.role.after:f.role.before;
    });
    assets.add(snapshot(41,GraphModel.Labels.OBSERVATION,Map.of()));
    doAnswer(call->f.role.recipient).when(f.role.f.kg).materializeConsequenceObservation(argThat(value->value!=null&&value.id()==41),eq(f.role.f.scope));
  }
  @AfterEach void close(){DefaultRoleConsequenceRuntimeTest.cleanup();}
  KnowledgeGraph.ConsequenceAssetSnapshot snapshot(long id,String label,Map<String,Object> fields) {
    return new KnowledgeGraph.ConsequenceAssetSnapshot(id,"native-"+id,List.of(label),fields,"1",null);
  }
  void origin(long source,long activity,boolean acquired,boolean current,long observer) {
    assets.add(snapshot(source,GraphModel.Labels.OBSERVATION,Map.of("klabCurrent",current)));
    var row=new LinkedHashMap<String,Object>();row.put("recipientId",41L);row.put("sourceId",source);row.put("ruleId","rule");row.put("role",f.role.role.getUrn());
    row.put("before",acquired?f.role.before.getUrn():f.role.after.getUrn());row.put("after",f.role.after.getUrn());
    row.put("authority",Utils.Json.asString(Map.of("rule",Map.of("ruleId","rule"))));
    row.put("supportContract",ConsequenceRoleSupportResult.CONTRACT);row.put("acquired",acquired);
    row.put("contextId","context");row.put("observerId",observer);row.put("focusId",0L);row.put("recipientObserverSemantics","");
    var request=new SemanticDependencyRequest("reasoner",1,DefaultRoleConsequenceRuntimeTest.FP,List.of(f.role.before.getUrn(),f.role.after.getUrn(),f.role.role.getUrn()));
    var metadata=Map.of(RoleAttributionEvents.METADATA_KEY,List.of(row),SemanticEvidenceBinding.DATA_KEY,
        Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,request.obligations(),DefaultRoleConsequenceRuntimeTest.witness(request.inputHash()))),
        org.integratedmodelling.klab.api.digitaltwin.Consequence.METADATA_KEY,Utils.Json.asString(new org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause(
            1,"context",1,2,observer,DefaultRoleConsequenceRuntimeTest.FP,null,"role-"+activity,org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind.ROLE_CONFERRAL)));
    assets.add(snapshot(activity,GraphModel.Labels.ACTIVITY,Map.of(GraphModel.Fields.TYPE,"CLASSIFICATION",GraphModel.Fields.OUTCOME,"SUCCESS",GraphModel.Fields.METADATA,Utils.Json.asString(metadata))));
    edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("support-"+activity,new KnowledgeGraph.ConsequenceAssetRef("native-"+activity,activity,List.of(GraphModel.Labels.ACTIVITY)),
        new KnowledgeGraph.ConsequenceAssetRef("native-41",41L,List.of(GraphModel.Labels.OBSERVATION)),GraphModel.Relationship.CLASSIFIED,row));
    if(observer>0&&assets.stream().noneMatch(value->value.id()==observer)) {
      assets.add(snapshot(observer,GraphModel.Labels.OBSERVATION,Map.of()));var agent=DefaultRoleConsequenceRuntimeTest.actualObservation(observer,SemanticType.AGENT);
      doReturn(agent).when(f.role.f.kg).materializeConsequenceObservation(argThat(value->value!=null&&value.id()==observer),eq(f.role.f.scope));
    }
  }
  KnowledgeGraph.ConsequenceReadSet reads() {
    return new KnowledgeGraph.ConsequenceReadSet("context",assets,List.of(new KnowledgeGraph.ConsequenceEdgeSelection(41,GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.Direction.INCOMING)),edges);
  }
  PreparedRoleWithdrawal prepare(Long... ceased) {
    return PreparedRoleWithdrawal.prepare(41,Set.of(ceased),reads(),f.role.f.scope);
  }

  @Test void secondLiveOriginKeepsRoleWithoutTouchingTheRecipientIdentity() {
    origin(70,100,true,true,0);origin(71,101,false,true,0);
    var change=prepare(70L);assertEquals(f.role.after.getUrn(),change.replacement().getObservable().getUrn());
    assertEquals(41,change.replacement().getId());assertEquals(2,change.before().supports().size());
    assertEquals(1,change.after().supports().stream().filter(PreparedRoleWithdrawal.Support::current).count());
    assertTrue(change.exhaustedRoleKeys().isEmpty());change.require(f.role.f.scope);
  }
  @Test void droppingTheLastOriginRemovesOnlyTheRoleAndRetainsImmutableSupportHistory() {
    origin(70,100,true,false,0);origin(71,101,false,true,0);
    var change=prepare(71L);assertEquals(f.role.before.getUrn(),change.replacement().getObservable().getUrn());
    assertEquals(f.role.after.getUrn(),change.original().getObservable().getUrn());assertEquals(41,change.replacement().getId());
    assertEquals(2,change.after().supports().size());assertTrue(change.after().supports().stream().noneMatch(PreparedRoleWithdrawal.Support::current));
    assertEquals(1,change.exhaustedRoleKeys().size());assertEquals(f.role.after.getUrn(),f.role.recipient.getObservable().getUrn());
    change.replacement().getMetadata().put("mutated",true);assertFalse(change.replacement().getMetadata().containsKey("mutated"));
  }
  @Test void preexistingIntrinsicRoleIsNotOwnedByTheConsequenceOrigin() {
    origin(70,100,false,true,0);var change=prepare(70L);
    assertEquals(f.role.after.getUrn(),change.replacement().getObservable().getUrn());
    assertTrue(change.after().independentlySuppliedRoles().contains(f.role.role.getUrn()));assertTrue(change.exhaustedRoleKeys().isEmpty());
  }
  @Test void ordinaryClassifierProofRemainsIndependentOfTheRetiredConsequence() {
    origin(70,100,false,true,0);
    var row=Map.<String,Object>of("memberId",41L,"predicate",f.role.role.getUrn(),"before",f.role.before.getUrn(),"after",f.role.after.getUrn());
    var request=new SemanticDependencyRequest("reasoner",1,DefaultRoleConsequenceRuntimeTest.FP,List.of(f.role.before.getUrn(),f.role.after.getUrn(),f.role.role.getUrn()));
    var metadata=Map.of(Metadata.IM_ATTRIBUTIONS,List.of(row),SemanticEvidenceBinding.DATA_KEY,
        Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,request.obligations(),DefaultRoleConsequenceRuntimeTest.witness(request.inputHash()))));
    assets.add(snapshot(102,GraphModel.Labels.ACTIVITY,Map.of(GraphModel.Fields.TYPE,"CLASSIFICATION",GraphModel.Fields.OUTCOME,"SUCCESS",GraphModel.Fields.METADATA,Utils.Json.asString(metadata))));
    edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("ordinary-classifier",new KnowledgeGraph.ConsequenceAssetRef("native-102",102L,List.of(GraphModel.Labels.ACTIVITY)),
        new KnowledgeGraph.ConsequenceAssetRef("native-41",41L,List.of(GraphModel.Labels.OBSERVATION)),GraphModel.Relationship.CLASSIFIED,row));
    var change=prepare(70L);assertEquals(f.role.after.getUrn(),change.replacement().getObservable().getUrn());
    assertTrue(change.after().independentlySuppliedRoles().contains(f.role.role.getUrn()));assertEquals(2,change.reads().edges().size());
  }
  @Test void anotherObserverCannotKeepTheCeasedPerspectivesLogicalRoleFact() {
    origin(70,100,true,true,0);origin(71,101,false,true,7);
    var change=prepare(70L);assertEquals(f.role.after.getUrn(),change.replacement().getObservable().getUrn());
    assertEquals(Set.of(0L),change.exhaustedRoleKeys().stream().map(RolePresenceKey::observerId).collect(java.util.stream.Collectors.toSet()));
    assertEquals(7,change.after().supports().stream().filter(PreparedRoleWithdrawal.Support::current).findFirst().orElseThrow().key().observerId());
  }
  @Test void cessationOfTheQualifiedObserverDoesNotBorrowAnotherFramesSupport() {
    origin(70,100,true,true,0);origin(71,101,false,true,7);
    var change=prepare(70L,7L);assertEquals(f.role.before.getUrn(),change.replacement().getObservable().getUrn());
    assertTrue(change.after().supports().stream().noneMatch(PreparedRoleWithdrawal.Support::current));
    assertEquals(Set.of(0L,7L),change.exhaustedRoleKeys().stream().map(RolePresenceKey::observerId).collect(java.util.stream.Collectors.toSet()));
  }
  @Test void incompleteOrForgedNativeSupportCannotAuthorizeAProjection() {
    origin(70,100,true,true,0);
    var old=edges.getFirst();var forged=new LinkedHashMap<>(old.properties());forged.put("sourceId",71L);
    edges.set(0,new KnowledgeGraph.ConsequenceEdgeSnapshot(old.elementId(),old.source(),old.target(),old.relationship(),forged));
    assertThrows(IllegalStateException.class,()->prepare(70L));edges.set(0,old);
    assets.removeIf(asset->asset.id()==70);assertThrows(IllegalStateException.class,()->prepare(70L));
  }
  @Test void unknownOriginalAccountingAndWrongLiveCycleRejectBeforePublication() {
    origin(70,100,true,true,0);var change=prepare(70L);
    var original=f.role.f.scope.getResolutionCycleBinding();
    when(f.role.f.scope.getResolutionCycleBinding()).thenReturn(new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
        original.version(),"foreign",original.contextId(),original.runtimeServiceId(),original.consequencesEnabled(),original.semanticBinding()));
    assertThrows(IllegalStateException.class,()->change.require(f.role.f.scope));
    when(f.role.f.scope.getResolutionCycleBinding()).thenReturn(original);
    doAnswer(call->SemanticDependencyWitness.unknown(call.getArgument(0),"unavailable")).when(f.role.f.reasoner).semanticDependencies(any(),any());
    assertThrows(IllegalStateException.class,()->change.require(f.role.f.scope));
  }
}
