package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

/** Native kind discovery only selects IDs; existing strict capture remains input authority. */
class ConfigurationInputDiscoveryTest {
 static DefaultConfigurationBackendTest.Fixture fixture()throws Exception {
  var f=new DefaultConfigurationBackendTest.Fixture();
  when(f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.UNAVAILABLE,null,List.of(),null,"unavailable"));
  doAnswer(call->{
   Collection<Long> ids=call.getArgument(0);Collection<ConsequenceEdgeSelection> selections=call.getArgument(1);
   for(var id:ids) {
    var asset=f.nativeInputs.assets.get(id);
    if(asset==null||Collections.disjoint(asset.labels(),List.of(GraphModel.Labels.OBSERVATION,GraphModel.Labels.COHORT,GraphModel.Labels.ACTIVITY)))
     throw new org.integratedmodelling.klab.api.exceptions.KlabStorageException("Missing or ambiguous consequence input "+id);
   }
   var edges=f.nativeInputs.edges.stream().filter(edge->selections.stream().anyMatch(s->s.relationship()==edge.relationship()
       &&Objects.equals(s.direction()==GraphModel.Relationship.Direction.OUTGOING?edge.source().id():edge.target().id(),s.assetId()))).toList();
   return new ConsequenceReadSet("context",ids.stream().sorted().map(f.nativeInputs.assets::get).toList(),List.copyOf(selections),edges);
  }).when(f.nativeInputs.transaction).captureConsequenceInputs(anyCollection(),anyCollection());
  return f;
 }
 @Test void realStorageChangeAndProvenanceChildDoNotBecomeObservationInputs()throws Exception {
  var f=fixture();f.commit.getAddedObservations().clear();f.commit.getModifiedAssets().add(500L);
  f.commit.getAddedLinks().add(org.integratedmodelling.klab.api.collections.Triple.of(100L,501L,"HAS_CHILD"));
  f.nativeInputs.assets.put(500L,new ConsequenceAssetSnapshot(500,"data500",List.of(GraphModel.Labels.DATA),Map.of(),null,null));
  f.nativeInputs.assets.put(501L,new ConsequenceAssetSnapshot(501,"actuator501",List.of(GraphModel.Labels.ACTUATOR),Map.of(),null,null));
  var quality=ConfigurationNativeInputsTest.observation(42,false);
  quality.getObservable().getSemantics().getType().clear();quality.getObservable().getSemantics().getType().addAll(EnumSet.of(SemanticType.QUALITY,SemanticType.LENGTH,SemanticType.OBSERVABLE));
  f.nativeInputs.observations.put(42L,quality);f.nativeInputs.assets.put(42L,new ConsequenceAssetSnapshot(42,"quality42",List.of(GraphModel.Labels.OBSERVATION),Map.of(),"1",null));
  var journal=new SchedulerJournal(1,"event",null,Scheduler.Event.Type.TEMPORAL_TRANSITION,0,1,"registration","plan","1",10,List.of(42L),true);
  var encoded=org.integratedmodelling.klab.utilities.Utils.Json.asString(Map.of("version",1,"contextId","context","commit",Map.of("id",10),"journals",List.of(journal)));
  f.nativeInputs.assets.put(100L,new ConsequenceAssetSnapshot(100,"activity",List.of(GraphModel.Labels.ACTIVITY),Map.of(TransitionCommit.METADATA_KEY,encoded),null,null));
  assertEquals(Consequence.Outcome.DEFERRED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());
  var requests=org.mockito.ArgumentCaptor.forClass(ConsequenceRuleQuery.class);verify(f.reasoner).consequenceRules(requests.capture());
  assertEquals(List.of(quality.getObservable().getUrn()),requests.getValue().ingredientTriggers().stream().map(c->c.getUrn()).toList());
  var captures=org.mockito.ArgumentCaptor.forClass(Collection.class);verify(f.nativeInputs.transaction,atLeastOnce()).captureConsequenceInputs(captures.capture(),any());
  assertTrue(captures.getAllValues().stream().noneMatch(ids->ids.contains(500L)||ids.contains(501L)));
  assertTrue(captures.getAllValues().stream().anyMatch(ids->ids.contains(42L)));
 }
 @Test void declaredObservationCannotBeReclassifiedAsBookkeeping()throws Exception {
  var f=fixture();f.commit.getAddedObservations().add(500L);
  f.nativeInputs.assets.put(500L,new ConsequenceAssetSnapshot(500,"data500",List.of(GraphModel.Labels.DATA),Map.of(),null,null));
  assertThrows(SecurityException.class,()->f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope));verifyNoInteractions(f.reasoner);
 }
 @Test void missingOrAmbiguousNativeKindsCannotBecomeSuccessfulEmpty()throws Exception {
  for(boolean ambiguous:new boolean[]{false,true}) {
   var f=fixture();f.nativeRows.put(1L,ambiguous?List.of(f.nativeInputs.observations.get(1L),f.nativeInputs.observations.get(1L)):List.of());
   assertEquals(Consequence.Outcome.DEFERRED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());verifyNoInteractions(f.reasoner);
  }
 }
 @Test void nativeObservationTypeDriftAfterDiscoveryStillDefers()throws Exception {
  var f=fixture();f.nativeRows.put(1L,List.of(f.nativeInputs.observations.get(1L)));
  f.nativeInputs.assets.put(1L,new ConsequenceAssetSnapshot(1,"e1",List.of(GraphModel.Labels.ACTIVITY),Map.of(),null,null));
  assertEquals(Consequence.Outcome.DEFERRED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());verifyNoInteractions(f.reasoner);
 }
}
