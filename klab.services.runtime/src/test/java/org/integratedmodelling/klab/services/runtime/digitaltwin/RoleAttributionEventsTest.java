package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.junit.jupiter.api.Test;

class RoleAttributionEventsTest {
  static class Fixture {
    final ConsequenceRecipientsTest.Fixture f=new ConsequenceRecipientsTest.Fixture();
    final CommitImpl commit=new CommitImpl();
    final Activity activity=mock(Activity.class);
    final Concept role=mock(Concept.class);
    final Metadata metadata=Metadata.create();
    Fixture() {
      when(role.getUrn()).thenReturn("test:Role");when(role.is(SemanticType.ROLE)).thenReturn(true);
      when(activity.getMetadata()).thenReturn(metadata);
      when(f.graph.getAsset(100L,f.scope,Activity.class)).thenReturn(activity);
      commit.getAddedAssets().add(100L);commit.getAddedLinks().add(Triple.of(100L,20L,"CLASSIFIED"));
    }
    Map<String,Object> row(boolean conferred) {
      var row=new HashMap<String,Object>();row.put(conferred?"role":"predicate","test:Role");
      row.put(conferred?"recipientId":"memberId",20L);row.put("before","test:Subject");row.put("after","test:RoleSubject");
      if(conferred){row.put("sourceId",10L);row.put("ruleId","source-rule");}return row;
    }
    RoleConsequenceRuntime.Arrivals read(){return RoleAttributionEvents.read(commit,f.scope,urn->role);}
  }
  @Test void exactNewCommitEvidenceDistinguishesConferralFromOrdinaryClassification() {
    var f=new Fixture();f.metadata.put(RoleAttributionEvents.METADATA_KEY,List.of(f.row(true)));
    var arrival=f.read().values().getFirst();assertTrue(arrival.characterize());assertEquals(10,arrival.sourceId());assertEquals("source-rule",arrival.ruleId());
    f.metadata.clear();f.metadata.put(Metadata.IM_ATTRIBUTIONS,List.of(f.row(false)));
    assertFalse(f.read().values().getFirst().characterize());
  }
  @Test void historicalSynchronizationLinksCannotRetriggerRoleAcquisition() {
    var f=new Fixture();f.commit.getAddedAssets().clear();
    assertTrue(f.read().values().isEmpty());verifyNoInteractions(f.activity);
  }
  @Test void duplicateAuditRowsAreOneAcquisitionAndUnchangedMeaningIsNotAcquisition() {
    var f=new Fixture();var row=f.row(true);f.metadata.put(RoleAttributionEvents.METADATA_KEY,List.of(row,row));
    f.metadata.put(Metadata.IM_ATTRIBUTIONS,List.of(f.row(false)));assertEquals(1,f.read().values().size());assertTrue(f.read().values().getFirst().characterize());
    f.metadata.clear();row.put("after",row.get("before"));f.metadata.put(RoleAttributionEvents.METADATA_KEY,List.of(row));assertTrue(f.read().values().isEmpty());
  }
  @Test void IncompleteAuditCannotExposePartialRoleEffects() {
    var f=new Fixture();f.metadata.put(RoleAttributionEvents.METADATA_KEY,List.of(f.row(true),Map.of("role","test:Role")));
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.read().status());assertTrue(f.read().values().isEmpty());
    f.metadata.clear();assertEquals(ConsequenceRecipients.Status.DEFERRED,f.read().status());
  }
  @Test void birthTransactionFinalizesTransientRoleMemberInStoredAuditAndCommittedEdge() throws Exception {
    var f=new ClassificationTransactionTest();f.setup();
    var value=f.pending(-7,true);f.root.add(value.member());
    f.child.stageAttributions(List.of(value),f.scope);
    var before=(List<Map<String,Object>>)f.activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS);
    assertEquals(-7L,before.getFirst().get("memberId"));
    var ids=new java.util.concurrent.atomic.AtomicLong(1000);var stored=new java.util.concurrent.atomic.AtomicBoolean();
    doAnswer(call->{
      var asset=call.getArgument(0);
      if(asset instanceof org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl observation) {
        observation.setId(ids.incrementAndGet());observation.setUrn("context."+observation.getId());
      }
      if(asset instanceof org.integratedmodelling.klab.api.provenance.impl.ActivityImpl activity) {
        activity.setId(ids.incrementAndGet());
        if(activity==f.activity) {
          var rows=(List<Map<String,Object>>)activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS);
          assertTrue(((Number)rows.getFirst().get("memberId")).longValue()>0);stored.set(true);
        }
      }
      return null;
    }).when(f.storage).store(any());
    var id=f.root.commit();assertTrue(id>0);assertTrue(stored.get());assertTrue(value.member().getId()>0);
    var durable=value.member().getId();var row=before.getFirst();assertEquals(durable,row.get("memberId"));assertEquals(-7L,row.get("memberIdBeforeCommit"));
    when(f.scope.getDigitalTwin()).thenReturn(f.twin);when(f.twin.getKnowledgeGraph()).thenReturn(f.kg);
    when(f.twin.getCommit(id)).thenCallRealMethod();when(f.kg.getAsset(f.activity.getId(),f.scope,Activity.class)).thenReturn(f.activity);
    var arrivals=RoleAttributionEvents.read(f.twin.getCommit(id),f.scope,urn->value.predicate());
    assertEquals(ConsequenceRecipients.Status.COMPLETE,arrivals.status());assertEquals(durable,arrivals.values().getFirst().recipientId());
    var classified=mockingDetails(f.storage).getInvocations().stream().filter(call->call.getMethod().getName().equals("link")&&call.getArgument(2)==org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.CLASSIFIED).findFirst().orElseThrow();
    var properties=new HashMap<String,Object>();var args=classified.getArguments();for(int i=3;i<args.length;i+=2)properties.put(args[i].toString(),args[i+1]);
    assertEquals(durable,properties.get("memberId"));assertEquals(-7L,properties.get("memberIdBeforeCommit"));
  }
}
