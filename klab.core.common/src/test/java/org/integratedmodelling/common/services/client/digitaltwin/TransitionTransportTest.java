package org.integratedmodelling.common.services.client.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.junit.jupiter.api.Test;

class TransitionTransportTest {
  @Test void repeatedRegistrationKeepsTwinAndReplacementDisposesOldPoller() {
    var scope=mock(org.integratedmodelling.common.services.client.scope.ClientContextScope.class);
    when(scope.getService(RuntimeService.class)).thenReturn(mock(RuntimeClient.class));
    doCallRealMethod().when(scope).createDigitalTwin(anyString());
    when(scope.getDigitalTwin()).thenCallRealMethod();
    scope.createDigitalTwin("context");
    var first=(ClientDigitalTwin)scope.getDigitalTwin();
    try {
      scope.createDigitalTwin("context");
      assertSame(first,scope.getDigitalTwin());
      scope.createDigitalTwin("replacement");
      assertTrue(first.isDisposed());
      assertNotSame(first,scope.getDigitalTwin());
    } finally { scope.getDigitalTwin().dispose();first.dispose(); }
  }

  @Test void closedTwinStopsRecoveryAndMessageIngestion() {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
    var runtime=mock(RuntimeClient.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var twin=new ClientDigitalTwin(scope,"context",false);
    twin.ingest(Message.create("context",Message.MessageClass.DigitalTwin,Message.MessageType.ContextClosed));
    assertTrue(twin.isDisposed());
    twin.recoverTransitions();
    twin.ingest(message(activity(transition(20,"a",false,Scheduler.Event.Type.EVENT))));
    assertTrue(twin.getTransitionHistory().commits().isEmpty());
    verify(runtime,never()).queryKnowledgeGraph(any(),any());
  }
  static TransitionCommit transition(long id,String event,boolean changed,Scheduler.Event.Type kind) {
    var commit=new CommitImpl();commit.setId(id);commit.getModifiedAssets().add(10L);
    var journal=new SchedulerJournal(1,event,null,kind,1000,2000,"registration","plan","T0(1)",id,
        changed?List.of(10L):List.of(),changed);
    return new TransitionCommit(1,"context",commit,List.of(journal),changed?
        List.of(new TemporalStateDelta(1,10,event,"T0(1)",List.of("shard-"+id))):List.of());
  }
  static ActivityImpl activity(TransitionCommit transition) {
    var activity=new ActivityImpl();activity.setId(transition.commit().getId()+1000);
    activity.setType(Activity.Type.SIMULATION);activity.setOutcome(Activity.Outcome.SUCCESS);
    activity.getMetadata().put(TransitionCommit.METADATA_KEY,Utils.Json.asString(transition));
    return activity;
  }
  static Message message(Activity activity) {
    return Message.create("context",Message.MessageClass.DigitalTwin,Message.MessageType.ActivityFinished,activity);
  }
  @Test void committedActivityTransportPreservesTypedJournalDeltaAndCommit() throws Exception {
    var transition=transition(20,"event-a",true,Scheduler.Event.Type.TEMPORAL_TRANSITION);
    var mapper=JacksonConfiguration.newObjectMapper();
    var transported=mapper.readValue(mapper.writeValueAsString(message(activity(transition))),Message.class);
    var envelope=Utils.Json.parseObject(transported.getPayload(Activity.class).getMetadata().get(TransitionCommit.METADATA_KEY).toString(),TransitionCommit.class);
    assertEquals(20,envelope.commit().getId());assertEquals(Set.of(10L),envelope.commit().getModifiedAssets());
    assertEquals(transition.journals(),envelope.journals());assertEquals(transition.qualities(),envelope.qualities());
  }
  @Test void duplicatesReorderingEqualTimesAndNoOpsConverge() {
    var a=transition(20,"a",true,Scheduler.Event.Type.TEMPORAL_TRANSITION);
    var b=transition(21,"b",true,Scheduler.Event.Type.TEMPORAL_TRANSITION);
    var none=transition(22,"none",false,Scheduler.Event.Type.TEMPORAL_TRANSITION);
    var observed=transition(23,"observed:42",false,Scheduler.Event.Type.EVENT);
    var reference=new TransitionHistory("context");var reordered=new TransitionHistory("context");
    List.of(a,b,none,observed).forEach(reference::accept);
    List.of(observed,b,none,a,b).forEach(reordered::accept);
    assertEquals(reference.timeline(),reordered.timeline());assertEquals(3,reordered.timeline().size());
    assertEquals(reference.qualityStates(10),reordered.qualityStates(10));
    assertThrows(IllegalArgumentException.class,()->new TransitionHistory("other").accept(a));
    assertThrows(IllegalArgumentException.class,()->new TransitionCommit(2,"context",a.commit(),a.journals(),a.qualities()));
  }
  @Test void clientInvalidatesDataBeforeNotificationAndRecoversWithoutCommitCache() {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
    var runtime=mock(RuntimeClient.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var twin=new ClientDigitalTwin(scope,"context",false);
    try {
      var old=new ObservationImpl();old.setId(10);twin.getKnowledgeGraph().ingest(old);
      var fresh=new ObservationImpl();fresh.setId(10);fresh.setEventTimestamps(List.of(2000L));
      when(runtime.getAsset(10,RuntimeAsset.class,scope)).thenReturn(fresh);
      when(runtime.getAsset(10,org.integratedmodelling.klab.api.knowledge.observation.Observation.class,scope)).thenReturn(fresh);
      var notifications=new ArrayList<Message>();
      twin.addEventConsumer(m -> {
        assertSame(fresh,twin.getKnowledgeGraph().getAsset(10,scope,RuntimeAsset.class));notifications.add(m);
      });
      var record=activity(transition(20,"a",true,Scheduler.Event.Type.TEMPORAL_TRANSITION));
      when(runtime.queryKnowledgeGraph(any(),eq(scope))).thenReturn((List)List.of(record));
      twin.recoverTransitions();twin.ingest(message(record));twin.recoverTransitions();
      assertEquals(1,notifications.size());assertEquals(1,twin.getTransitionHistory().timeline().size());
      verify(runtime,never()).getCommit(anyLong(),any());
    } finally { twin.dispose(); }
  }
  @Test void failedActivitiesCannotPublishPreparedEnvelopes() {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
    var runtime=mock(RuntimeClient.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var twin=new ClientDigitalTwin(scope,"context",false);
    try {
      var record=activity(transition(20,"a",true,Scheduler.Event.Type.TEMPORAL_TRANSITION));
      record.setOutcome(Activity.Outcome.INTERNAL_FAILURE);twin.ingest(message(record));
      assertTrue(twin.getTransitionHistory().commits().isEmpty());
    } finally { twin.dispose(); }
  }

  @Test void reconnectRetriesFailedHistoryAndASecondClientRebuildsTheSameTimeline() {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
    var runtime=mock(RuntimeClient.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var a=activity(transition(20,"a",true,Scheduler.Event.Type.TEMPORAL_TRANSITION));
    var b=activity(transition(21,"b",true,Scheduler.Event.Type.TEMPORAL_TRANSITION));
    var fresh=new ObservationImpl();fresh.setId(10);
    when(runtime.getAsset(eq(10L),any(),eq(scope))).thenReturn(fresh);
    when(runtime.queryKnowledgeGraph(any(),eq(scope))).thenThrow(new IllegalStateException("offline"))
        .thenReturn((List)List.of(b,a));
    var client=new ClientDigitalTwin(scope,"context",false);
    try {
      client.recoverTransitions();assertTrue(client.getTransitionHistory().commits().isEmpty());
      client.recoverTransitions();assertEquals(2,client.getTransitionHistory().timeline().size());
      var restored=new ClientDigitalTwin(scope,"context",false);
      try {
        restored.recoverTransitions();
        assertEquals(client.getTransitionHistory().timeline(),restored.getTransitionHistory().timeline());
        assertEquals(client.getTransitionHistory().qualityStates(10),restored.getTransitionHistory().qualityStates(10));
      } finally { restored.dispose(); }
    } finally { client.dispose(); }
  }

  @Test void aMissingQualitySnapshotRemainsPendingUntilRecoveryCanFetchIt() {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");
    var runtime=mock(RuntimeClient.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var record=activity(transition(20,"a",true,Scheduler.Event.Type.TEMPORAL_TRANSITION));
    when(runtime.queryKnowledgeGraph(any(),eq(scope))).thenReturn((List)List.of(record));
    var twin=new ClientDigitalTwin(scope,"context",false);
    try {
      var delivered=new ArrayList<Message>();twin.addEventConsumer(delivered::add);
      twin.recoverTransitions();assertTrue(delivered.isEmpty());assertTrue(twin.getTransitionHistory().timeline().isEmpty());
      var fresh=new ObservationImpl();fresh.setId(10);
      when(runtime.getAsset(eq(10L),any(),eq(scope))).thenReturn(fresh);
      twin.recoverTransitions();assertEquals(1,delivered.size());assertEquals(1,twin.getTransitionHistory().timeline().size());
    } finally { twin.dispose(); }
  }
}
