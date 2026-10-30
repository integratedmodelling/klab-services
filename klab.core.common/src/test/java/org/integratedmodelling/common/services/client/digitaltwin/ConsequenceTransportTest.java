package org.integratedmodelling.common.services.client.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.junit.jupiter.api.Test;

class ConsequenceTransportTest {
  static Consequence.Cause cause(String context) {
    return new Consequence.Cause(1, context, 1, 100, 10, "worldview", null, "decision",
        Consequence.Kind.CONFIGURATION_DETECTION);
  }
  static CommitImpl commit() {
    var commit = new CommitImpl(); commit.setId(2);
    commit.setConsequenceCause(cause("context"));
    commit.getModifiedAssets().add(10L);
    commit.getAddedAssets().add(11L);
    commit.getAddedObservations().add(11L);
    commit.getAddedLinks().add(Triple.of(10L, 11L, "HAS_CHILD"));
    return commit;
  }

  @Test void bothNewMessageTypesRoundTripWithTypedPayloads() throws Exception {
    var decision = new Consequence.Decision(cause("context"), Consequence.Outcome.DEFERRED,
        "Awaiting emergence semantics", List.of(10L));
    var message = Message.create("context", Message.MessageClass.DigitalTwin,
        Message.MessageType.ConsequenceDecision, decision);
    var copy = Utils.Json.parseObject(Utils.Json.asString(message), Message.class);
    assertEquals(decision, copy.getPayload(Consequence.Decision.class));
    message = Message.create("context", Message.MessageClass.DigitalTwin,
        Message.MessageType.ConsequenceCommitted, new Consequence.Commit(cause("context"), commit()));
    copy = Utils.Json.parseObject(Utils.Json.asString(message), Message.class);
    var payload = copy.getPayload(Consequence.Commit.class);
    assertEquals(cause("context"), payload.cause());
    assertEquals(cause("context"), payload.commit().getConsequenceCause());
    assertEquals(commit().getAddedLinks(), payload.commit().getAddedLinks());
    assertEquals(commit().getModifiedAssets(), payload.commit().getModifiedAssets());
  }

  @Test void twoClientsApplyConsequenceChangesBeforeConsumerNotificationAndShareCommitDeduplication() {
    var scope = mock(ContextScope.class); when(scope.getId()).thenReturn("context");
    var runtime = mock(RuntimeClient.class); when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var first = new ClientDigitalTwin(scope, "context", false);
    var second = new ClientDigitalTwin(scope, "context", false);
    try {
      var old = new ObservationImpl(); old.setId(10);
      var fresh = new ObservationImpl(); fresh.setId(10);
      var added = new ObservationImpl(); added.setId(11);
      when(runtime.getAsset(10, RuntimeAsset.class, scope)).thenReturn(fresh);
      when(runtime.getAsset(11, RuntimeAsset.class, scope)).thenReturn(added);
      var received = new ArrayList<Message>();
      for (var twin : List.of(first, second)) {
        twin.getKnowledgeGraph().ingest(old);
        twin.addEventConsumer(event -> {
          assertSame(fresh, twin.getKnowledgeGraph().getAsset(10, scope, RuntimeAsset.class));
          assertSame(added, twin.getKnowledgeGraph().getAsset(11, scope, RuntimeAsset.class));
          assertTrue(twin.getKnowledgeGraph().getCommitQueue().stream()
              .anyMatch(commit -> commit.getAddedObservations().contains(11L)));
          assertTrue(twin.getKnowledgeGraph().getLinks(fresh, GraphModel.Relationship.Direction.OUTGOING,
              scope, GraphModel.Relationship.HAS_CHILD).stream().anyMatch(link -> link.target().getId() == 11));
          received.add(event);
        });
      }
      var notification = new Consequence.Commit(cause("context"), commit());
      var event = Message.create("context", Message.MessageClass.DigitalTwin,
          Message.MessageType.ConsequenceCommitted, notification);
      first.ingest(event); second.ingest(event);
      assertEquals(2, received.size());
      assertFalse(first.getKnowledgeGraph().ingestConsequence(notification));
      var submission = new ObservationImpl(); submission.setId(11);
      submission.getMetadata().put(Metadata.IM_COMMIT_ID, 2L);
      first.getKnowledgeGraph().ingest(submission);
      verify(runtime, never()).getCommit(anyLong(), any());
      assertSame(notification.commit(), submission.getMetadata().get(Metadata.IM_COMMIT));
      assertThrows(IllegalArgumentException.class, () -> first.getKnowledgeGraph().ingestConsequence(
          new Consequence.Commit(cause("foreign"), commit())));
    } finally { first.dispose(); second.dispose(); }
  }

  @Test void ordinaryCommitCanArriveBeforeItsConsequenceMessageWithoutLosingDecision() {
    var scope = mock(ContextScope.class); when(scope.getId()).thenReturn("context");
    var runtime = mock(RuntimeClient.class); when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var twin = new ClientDigitalTwin(scope, "context", false);
    try {
      var submission = new ObservationImpl(); submission.setId(11);
      submission.getMetadata().put(Metadata.IM_COMMIT_ID, 2L);
      when(runtime.getCommit(2, scope)).thenReturn(commit());
      twin.getKnowledgeGraph().ingest(submission);
      assertFalse(twin.getKnowledgeGraph().ingestConsequence(new Consequence.Commit(cause("context"), commit())));
      var decisions = new ArrayList<Message>(); twin.addEventConsumer(decisions::add);
      var decision = new Consequence.Decision(cause("context"), Consequence.Outcome.APPLIED, "Done", List.of(11L));
      twin.ingest(Message.create("context", Message.MessageClass.DigitalTwin,
          Message.MessageType.ConsequenceDecision, decision));
      assertEquals(decision, decisions.getFirst().getPayload(Consequence.Decision.class));
    } finally { twin.dispose(); }
  }
}
