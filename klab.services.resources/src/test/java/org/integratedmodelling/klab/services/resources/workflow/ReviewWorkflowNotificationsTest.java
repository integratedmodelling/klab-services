package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.integratedmodelling.klab.services.base.ReviewNotificationOutbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewWorkflowNotificationsTest {
  @TempDir Path directory;
  ReviewNotificationOutbox outbox() throws Exception {
    return new ReviewNotificationOutbox(directory, Clock.systemUTC(), 100, 3, Duration.ofMinutes(5));
  }
  @Test void committedFactsReplayAfterLostWakeupAndDoNotSendTwice() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.REVIEWER);
    var alice = f.fixtures.scope("alice@example.invalid", "REVIEWER");
    f.save(cycle, round, null, "PRIVATE draft", false, alice);
    f.save(cycle, round, null, "SUBMITTED proposal feedback", true, alice);
    try (var box = outbox()) {
      assertEquals(2, ReviewWorkflowNotifications.replayCommitted(f.store, box));
      assertEquals(0, ReviewWorkflowNotifications.replayCommitted(f.store, box));
      assertEquals(2, box.dispatch(10, (event, recipient) -> {
        assertTrue(ReviewWorkflowNotifications.admitted(f.store.getFlow(f.flowId),
            f.store.getWorkflow(ProposalReviewProtocolTest.WORKFLOW), event, WorkflowParticipant.from(f.editor)));
        return Optional.of("editor@example.invalid");
      }, (mailbox, subject, body) -> {
        assertFalse(body.contains("SUBMITTED")); assertFalse(body.contains("PRIVATE"));
        assertFalse(body.contains("alice")); return ReviewNotificationOutbox.Result.ACCEPTED;
      }));
    }
    try (var box = outbox()) {
      assertEquals(0, ReviewWorkflowNotifications.replayCommitted(f.store, box));
      assertEquals(0, box.dispatch(10, (event, recipient) -> fail("Already accepted"),
          (mailbox, subject, body) -> fail("No duplicate")));
    }
  }
  @Test void rejectedConditionalWritePublishesNeitherContributionNorIntent() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.REVIEWER);
    String before = Utils.Json.asString(f.store.getFlow(f.flowId));
    var rejecting = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class},
        (proxy, method, args) -> method.getName().equals("compareAndSetFlow") ? false : method.invoke(f.store, args));
    var manager = f.fixtures.manager(rejecting);
    assertThrows(KlabIllegalStateException.class, () -> manager.reviewCycle(f.flowId, new Command(
        f.store.getFlow(f.flowId).getRevision(), Operation.SAVE_CONTRIBUTION, null, cycle.id(), round.id(),
        null, null, "rejected", true), f.fixtures.scope("alice", "REVIEWER")));
    assertEquals(before, Utils.Json.asString(f.store.getFlow(f.flowId)));
  }
  @Test void privateIntentMetadataNeverLeavesOwnerOrPublicFlowProjectionOrArchive() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.COMMUNITY);
    f.save(cycle, round, null, "public submission", true, f.community("citizen", true, false));
    f.act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, f.editor);
    var raw = f.store.getFlow(f.flowId); assertTrue(raw.getMetadata().containsKey(ReviewWorkflowNotifications.LEDGER_KEY));
    // Also protect historical transaction/state metadata from another version's integration.
    raw.getHistory().getFirst().getMetadata().put("klab.review.notificationIntents.v1", List.of("private-recipient"));
    raw.getStates().get(f.source).getMetadata().put("nested", new LinkedHashMap<>(Map.of("klab.review.notificationSecret", "private-recipient")));
    for (var actor : List.of(f.editor, f.community("reader", true, false))) {
      String projection = Utils.Json.asString(f.manager.getFlow(f.flowId, actor));
      assertFalse(projection.contains("klab.review.notification")); assertFalse(projection.contains("private-recipient"));
      assertFalse(projection.contains(ReviewWorkflowNotifications.LEDGER_KEY));
    }
    var archive = ReviewCycleArchiveMapping.snapshot(raw, cycle.id(), "fixture-project", f.store::getWorkflowAttachment);
    assertTrue(archive.entries().stream().noneMatch(e -> new String(e.content(), java.nio.charset.StandardCharsets.UTF_8).contains("notification")));
    assertTrue(raw.getMetadata().containsKey(ReviewWorkflowNotifications.LEDGER_KEY));
  }
  @Test void incomingMetadataCannotForgeNotificationRecipients() throws Exception {
    var f = new ReviewCycleProtocolTest();
    var initial = f.fixtures.initialization(true);
    initial.getInitialState().getMetadata().put("nested", Map.of("klab.review.notificationIntents.v1", List.of("forged")));
    assertThrows(KlabIllegalArgumentException.class, () -> f.manager.initializeFlow(ProposalReviewProtocolTest.WORKFLOW, initial, f.editor));
    var update = Flow.State.create(); update.setMetadata(Map.of(ReviewWorkflowNotifications.LEDGER_KEY, Map.of()));
    assertThrows(KlabIllegalArgumentException.class, () -> f.manager.updateState(f.flowId, f.source, update, f.editor));
    var cyclic = new LinkedHashMap<String, Object>(); cyclic.put("self", cyclic);
    assertThrows(KlabIllegalArgumentException.class, () -> ReviewWorkflowNotifications.requirePublicMetadata(cyclic));
  }
  @Test void phaseRecipientsAreFrozenAudienceSpecificAndReauthorized() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var peer = f.round(cycle, Audience.REVIEWER);
    var alice = f.fixtures.scope("alice", "REVIEWER"); var citizen = f.community("citizen", true, false);
    f.save(cycle, peer, null, "peer", true, alice);
    var community = f.round(cycle, Audience.COMMUNITY);
    f.save(cycle, community, null, "community", true, citizen);
    f.round(cycle, Audience.REVIEWER);
    try (var box = outbox()) {
      ReviewWorkflowNotifications.replayCommitted(f.store, box);
      var raw = f.store.getFlow(f.flowId); var workflow = f.store.getWorkflow(ProposalReviewProtocolTest.WORKFLOW);
      var lastPeer = raw.getReviewCycles().getFirst().rounds().getLast();
      var events = new ArrayList<ReviewNotificationOutbox.Event>();
      box.dispatch(100, (event, id) -> { events.add(event); return Optional.empty(); }, (to, subject, body) -> fail("Suppressed"));
      var opened = events.stream().filter(e -> e.stageId().equals("round/" + lastPeer.id())).findFirst().orElseThrow();
      assertTrue(opened.recipients().contains(ReviewWorkflowNotifications.recipientId("alice")));
      assertFalse(opened.recipients().contains(ReviewWorkflowNotifications.recipientId("citizen")));
      assertTrue(ReviewWorkflowNotifications.admitted(raw, workflow, opened, WorkflowParticipant.from(alice)));
      assertFalse(ReviewWorkflowNotifications.admitted(raw, workflow, opened, WorkflowParticipant.from(f.community("alice", true, false))));
      assertFalse(ReviewWorkflowNotifications.admitted(raw, workflow, opened, WorkflowParticipant.from(f.fixtures.scope("new-reviewer", "REVIEWER"))));
    }
  }
}
