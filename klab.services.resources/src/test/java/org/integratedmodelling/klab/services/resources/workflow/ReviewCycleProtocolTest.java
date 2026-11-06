package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.junit.jupiter.api.Test;

class ReviewCycleProtocolTest {
  final ProposalReviewProtocolTest fixtures = new ProposalReviewProtocolTest();
  final WorkflowStore store;
  final WorkflowManager manager;
  final UserScope editor;
  final String flowId;
  final String source;

  ReviewCycleProtocolTest() throws Exception {
    store = fixtures.store(); manager = fixtures.manager(store); editor = fixtures.scope("editor", "EDITOR");
    var request = fixtures.initialization(true); request.setPublicRead(true);
    var flow = manager.initializeFlow(ProposalReviewProtocolTest.WORKFLOW, request, editor);
    flowId = flow.getId(); source = fixtures.current(flow).getId();
  }

  View act(Operation operation, String cycle, String round, Audience audience, String contribution,
      String text, boolean submit, UserScope actor) {
    long revision = manager.getReviewCycles(flowId, actor).revision();
    return manager.reviewCycle(flowId, new Command(revision, operation, source, cycle, round,
        audience, contribution, text, submit), actor);
  }
  Cycle open() { return act(Operation.OPEN_CYCLE, null, null, null, null, null, false, editor).cycles().getLast(); }
  Round round(Cycle cycle, Audience audience) {
    return act(Operation.OPEN_ROUND, cycle.id(), null, audience, null, null, false, editor)
        .cycles().getLast().rounds().getLast();
  }
  View save(Cycle cycle, Round round, String id, String text, boolean submit, UserScope actor) {
    return act(Operation.SAVE_CONTRIBUTION, cycle.id(), round.id(), null, id, text, submit, actor);
  }
  UserScope community(String name, boolean authenticated, boolean knownPerson) throws Exception {
    var original = fixtures.scope(name, "REVIEWER").getUser();
    var user = (UserIdentity) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{UserIdentity.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getGroups" -> List.of();
          case "getData" -> Metadata.create(WorkflowParticipant.KNOWN_REAL_PERSON_PROPERTY, knownPerson);
          case "isAuthenticated" -> authenticated;
          case "isAnonymous" -> !authenticated;
          default -> method.invoke(original, args);
        });
    return (UserScope) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{UserScope.class},
        (proxy, method, args) -> method.getName().equals("getUser") ? user : null);
  }

  @Test void overlappingRoundsFreezeExactSourceAndCloseWithoutAccepting() throws Exception {
    var cycle = open(); var peer = round(cycle, Audience.REVIEWER); var community = round(cycle, Audience.COMMUNITY);
    var reviewer = fixtures.scope("reviewer", "REVIEWER"); var citizen = community("citizen", true, false);
    save(cycle, peer, null, "peer feedback", true, reviewer);
    save(cycle, community, null, "community feedback", true, citizen);
    var closed = act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, editor).cycles().getFirst();
    assertNotNull(closed.closedAt()); assertTrue(closed.rounds().stream().allMatch(r -> r.closedAt() != null));
    var stored = store.getFlow(flowId);
    assertEquals(stored.getStates().get(source).getProposalReview().candidate(), closed.candidate());
    assertEquals(2, closed.sourceFlowRevision());
    assertEquals(Flow.Status.ACTIVE, stored.getStatus());
    assertEquals(ProposalReview.Status.IN_REVIEW, stored.getStates().get(source).getProposalReview().status());
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, peer, null, "late", true, reviewer));
    assertThrows(KlabIllegalStateException.class, () -> round(cycle, Audience.REVIEWER));
    open();
    assertEquals(closed, manager.getReviewCycles(flowId, editor).cycles().getFirst());
  }

  @Test void draftsStayPrivateEvenInPublicFlowsAndForEditors() throws Exception {
    var cycle = open(); var peer = round(cycle, Audience.REVIEWER); round(cycle, Audience.COMMUNITY);
    var alice = fixtures.scope("alice", "REVIEWER"); var bob = fixtures.scope("bob", "REVIEWER");
    save(cycle, peer, null, "private", false, alice);
    assertEquals(1, manager.getReviewCycles(flowId, alice).cycles().getFirst().rounds().getFirst().contributions().size());
    assertTrue(manager.getReviewCycles(flowId, bob).cycles().getFirst().rounds().getFirst().contributions().isEmpty());
    assertTrue(manager.getReviewCycles(flowId, editor).cycles().getFirst().rounds().getFirst().contributions().isEmpty());
    assertFalse(Utils.Json.asString(manager.getFlow(flowId, editor)).contains("private\""));
    var citizen = community("citizen", true, false);
    assertEquals(1, manager.getReviewCycles(flowId, citizen).cycles().getFirst().rounds().size());
    assertEquals(Audience.COMMUNITY, manager.getReviewCycles(flowId, citizen).cycles().getFirst().rounds().getFirst().audience());
    var draft = manager.getReviewCycles(flowId, alice).cycles().getFirst().rounds().getFirst().contributions().getFirst();
    save(cycle, peer, draft.id(), "submitted", true, alice);
    assertEquals("submitted", manager.getReviewCycles(flowId, bob).cycles().getFirst().rounds().getFirst().contributions().getFirst().text());
    assertEquals(1, manager.getReviewCycles(flowId, editor).cycles().getFirst().rounds().getFirst().contributions().size());
    assertFalse(Utils.Json.asString(manager.getFlow(flowId, citizen)).contains("submitted"));
  }

  @Test void contributionsAreIndependentlyOwnedAndSubmittedHistoryIsImmutable() throws Exception {
    var cycle = open(); var peer = round(cycle, Audience.REVIEWER);
    var alice = fixtures.scope("alice", "REVIEWER"); var bob = fixtures.scope("bob", "REVIEWER");
    var draft = save(cycle, peer, null, "draft", false, alice).cycles().getFirst().rounds().getFirst().contributions().getFirst();
    assertThrows(KlabResourceAccessException.class, () -> save(cycle, peer, draft.id(), "stolen", true, bob));
    assertThrows(KlabResourceAccessException.class, () -> save(cycle, peer, draft.id(), "editor overwrite", true, editor));
    save(cycle, peer, draft.id(), "final", true, alice);
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, peer, draft.id(), "rewrite", true, alice));
    save(cycle, peer, null, "second", true, bob);
    assertEquals(2, manager.getReviewCycles(flowId, alice).cycles().getFirst().rounds().getFirst().contributions().size());
  }

  @Test void reviewerRoleMustBeRealAndCommunityOnlyNeedsAuthentication() throws Exception {
    var cycle = open(); var peer = round(cycle, Audience.REVIEWER); var publicRound = round(cycle, Audience.COMMUNITY);
    var known = community("known", true, true);
    assertTrue(WorkflowParticipant.from(known).getRoles().isEmpty());
    assertThrows(KlabResourceAccessException.class, () -> save(cycle, peer, null, "not reviewer", true, known));
    save(cycle, publicRound, null, "authenticated", true, known);
    var anonymous = community("anonymous", false, false);
    assertThrows(KlabResourceAccessException.class, () -> manager.getReviewCycles(flowId, anonymous));
    var method = WorkflowManagerAuthorizationTest.class.getDeclaredMethod("scope", String.class, String.class, String.class);
    method.setAccessible(true);
    var wrongWorkflow = (UserScope) method.invoke(new WorkflowManagerAuthorizationTest(), "other", "REVIEWER", "other-workflow");
    assertThrows(KlabResourceAccessException.class, () -> save(cycle, peer, null, "wrong workflow", true, wrongWorkflow));
    save(cycle, publicRound, null, "community allowed", true, wrongWorkflow);
    assertThrows(KlabResourceAccessException.class, () -> act(Operation.OPEN_CYCLE, null, null, null, null, null, false, known));
  }

  @Test void closedRoundRemainsReadableAndOtherRoundCanContinue() throws Exception {
    var cycle = open(); var peer = round(cycle, Audience.REVIEWER); var publicRound = round(cycle, Audience.COMMUNITY);
    var reviewer = fixtures.scope("reviewer", "REVIEWER");
    save(cycle, peer, null, "retained", true, reviewer);
    act(Operation.CLOSE_ROUND, cycle.id(), peer.id(), null, null, null, false, editor);
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, peer, null, "late", true, reviewer));
    save(cycle, publicRound, null, "still open", true, community("citizen", true, false));
    assertEquals("retained", manager.getReviewCycles(flowId, editor).cycles().getFirst().rounds().getFirst().contributions().getFirst().text());
  }

  @Test void simultaneousSameRevisionWritesHaveExactlyOneWinner() throws Exception {
    var cycle = open(); var peer = round(cycle, Audience.REVIEWER);
    var alice = fixtures.scope("alice", "REVIEWER"); var bob = fixtures.scope("bob", "REVIEWER");
    long revision = store.getFlow(flowId).getRevision(); var gate = new CountDownLatch(1);
    var request = new Command(revision, Operation.SAVE_CONTRIBUTION, source, cycle.id(), peer.id(), null, null, "feedback", true);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = List.of(alice, bob).stream().map(actor -> executor.submit(() -> {
        gate.await();
        try { manager.reviewCycle(flowId, request, actor); return true; }
        catch (KlabIllegalStateException conflict) { return false; }
      })).toList();
      gate.countDown(); int winners = 0;
      for (var future : futures) if (future.get()) winners++;
      assertEquals(1, winners);
    }
    assertEquals(revision + 1, store.getFlow(flowId).getRevision());
    assertEquals(1, manager.getReviewCycles(flowId, editor).cycles().getFirst().rounds().getFirst().contributions().size());
  }

  @Test void failedAggregateWriteLeavesNoContributionAndNoRevisionChange() throws Exception {
    var cycle = open(); var peer = round(cycle, Audience.REVIEWER);
    var fail = new AtomicBoolean(false);
    var proxy = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class},
        (p, method, args) -> method.getName().equals("putFlow") && fail.get() ? false : method.invoke(store, args));
    var rejecting = fixtures.manager(proxy); var reviewer = fixtures.scope("reviewer", "REVIEWER");
    long revision = store.getFlow(flowId).getRevision(); fail.set(true);
    var request = new Command(revision, Operation.SAVE_CONTRIBUTION, source, cycle.id(), peer.id(), null, null, "feedback", true);
    assertThrows(KlabIllegalStateException.class, () -> rejecting.reviewCycle(flowId, request, reviewer));
    assertEquals(revision, store.getFlow(flowId).getRevision());
    assertTrue(store.getFlow(flowId).getReviewCycles().getFirst().rounds().getFirst().contributions().isEmpty());
  }

  @Test void cyclesRoundTripAndNewCycleDoesNotReopenClosedWorkflow() throws Exception {
    var flow = store.getFlow(flowId);
    flow = manager.transition(flowId, fixtures.command(flow, "accept-peer-review", fixtures.current(flow).getProposalReview().candidate()), editor);
    var accepted = flow.getStates().values().stream().filter(s -> "accepted".equals(s.getSchemaId())).findFirst().orElseThrow();
    var view = manager.reviewCycle(flowId, new Command(flow.getRevision(), Operation.OPEN_CYCLE,
        accepted.getId(), null, null, null, null, null, false), editor);
    assertEquals(Flow.Status.CLOSED, store.getFlow(flowId).getStatus());
    assertEquals(ProposalReview.Status.ACCEPTED, store.getFlow(flowId).getStates().get(accepted.getId()).getProposalReview().status());
    var copy = Utils.Json.parseObject(Utils.Json.asString(store.getFlow(flowId)), Flow.class);
    assertEquals(view.cycles(), copy.getReviewCycles());
  }
}
