package org.integratedmodelling.klab.services.resources.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;

/** Pure aggregate changes. The manager serializes, checks revision, and persists each change. */
final class ReviewCycleProtocol {
  private ReviewCycleProtocol() {}

  static boolean editor(Workflow workflow, WorkflowParticipant actor) {
    return actor.isAuthenticated() && actor.isWorkflowPermitted(workflow)
        && (actor.getRoles().contains(WorkflowRole.EDITOR) || actor.getRoles().contains(WorkflowRole.ADMIN));
  }

  static boolean admitted(Workflow workflow, WorkflowParticipant actor, Audience audience) {
    return actor.isAuthenticated() && (audience == Audience.COMMUNITY
        || (actor.getRoles().contains(WorkflowRole.REVIEWER) && actor.isWorkflowPermitted(workflow)));
  }

  static void requireEditor(Workflow workflow, WorkflowParticipant actor) {
    if (!editor(workflow, actor)) throw new KlabResourceAccessException("Workflow editor required");
  }

  static List<Cycle> visible(Flow flow, Workflow workflow, WorkflowParticipant actor) {
    var result = new ArrayList<Cycle>();
    for (var cycle : flow.getReviewCycles()) {
      var rounds = new ArrayList<Round>();
      for (var round : cycle.rounds()) {
        if (!editor(workflow, actor) && !admitted(workflow, actor, round.audience())) continue;
        var contributions = round.contributions().stream()
            .filter(c -> c.submitted() || Objects.equals(c.owner(), actor.getIdentity())).toList();
        rounds.add(new Round(round.id(), round.audience(), round.openedBy(), round.openedAt(),
            round.closedAt(), contributions));
      }
      if (editor(workflow, actor) || !rounds.isEmpty()) result.add(new Cycle(cycle.id(),
          cycle.sourceStateId(), cycle.sourceFlowRevision(), cycle.candidate(), cycle.openedBy(),
          cycle.openedAt(), cycle.closedAt(), rounds));
    }
    return result;
  }

  static void openCycle(Flow flow, Workflow workflow, WorkflowParticipant actor, String stateId) {
    requireEditor(workflow, actor);
    if (flow.getReviewCycles().size() >= 256) fail("Review cycle limit reached");
    var state = flow.getStates().get(stateId);
    if (state == null || state.getProposalReview() == null) fail("An exact reviewed candidate is required");
    var candidate = state.getProposalReview().candidate();
    if (candidate == null) fail("An exact reviewed candidate is required");
    var cycles = new ArrayList<>(flow.getReviewCycles());
    cycles.add(new Cycle(UUID.randomUUID().toString(), stateId, flow.getRevision(), candidate,
        actor.getIdentity(), now(), null, List.of()));
    flow.setReviewCycles(cycles);
  }

  static void openRound(Flow flow, Workflow workflow, WorkflowParticipant actor, String cycleId,
      Audience audience) {
    requireEditor(workflow, actor);
    var cycle = cycle(flow, cycleId);
    if (audience == null) fail("Review audience is required");
    if (cycle.rounds().size() >= 256) fail("Review round limit reached");
    var rounds = new ArrayList<>(cycle.rounds());
    rounds.add(new Round(UUID.randomUUID().toString(), audience, actor.getIdentity(), now(), null, List.of()));
    replace(flow, cycle, rounds, null);
  }

  static void contribute(Flow flow, Workflow workflow, WorkflowParticipant actor, String cycleId,
      String roundId, String contributionId, String text, boolean submit) {
    var cycle = cycle(flow, cycleId);
    var round = round(cycle, roundId);
    if (!admitted(workflow, actor, round.audience()))
      throw new KlabResourceAccessException("Review audience does not admit this participant");
    if (round.closedAt() != null) fail("Review round is closed");
    if (text == null || text.isBlank() || text.length() > ProposalReview.MAX_RATIONALE_CHARS)
      fail("Contribution text is required and must fit the rationale limit");
    var contributions = new ArrayList<>(round.contributions());
    if (contributionId == null) {
      if (contributions.size() >= 256) fail("Contribution limit reached");
      contributions.add(new Contribution(UUID.randomUUID().toString(), actor.getIdentity(), text, submit, now()));
    } else {
      var prior = contributions.stream().filter(c -> contributionId.equals(c.id())).findFirst()
          .orElseThrow(() -> new KlabIllegalStateException("Unknown contribution"));
      if (!Objects.equals(actor.getIdentity(), prior.owner()))
        throw new KlabResourceAccessException("Contribution belongs to another participant");
      if (prior.submitted()) fail("Submitted feedback is immutable; add a new contribution");
      contributions.set(contributions.indexOf(prior), new Contribution(prior.id(), prior.owner(), text, submit, now()));
    }
    long characters = flow.getReviewCycles().stream().flatMap(c -> c.rounds().stream())
        .flatMap(r -> r.contributions().stream()).mapToLong(c -> c.text().length()).sum()
        - round.contributions().stream().mapToLong(c -> c.text().length()).sum()
        + contributions.stream().mapToLong(c -> c.text().length()).sum();
    if (characters > ProposalReview.MAX_STAGE_BYTES / 2) fail("Review feedback aggregate size limit reached");
    var rounds = new ArrayList<>(cycle.rounds());
    rounds.set(rounds.indexOf(round), new Round(round.id(), round.audience(), round.openedBy(),
        round.openedAt(), null, contributions));
    replace(flow, cycle, rounds, null);
  }

  static void close(Flow flow, Workflow workflow, WorkflowParticipant actor, String cycleId, String roundId) {
    requireEditor(workflow, actor);
    var cycle = cycle(flow, cycleId);
    Instant now = now();
    var rounds = new ArrayList<>(cycle.rounds());
    if (roundId == null) {
      rounds.replaceAll(r -> r.closedAt() == null ? closed(r, now) : r);
    } else {
      var round = round(cycle, roundId);
      if (round.closedAt() != null) fail("Review round is closed");
      rounds.set(rounds.indexOf(round), closed(round, now));
    }
    replace(flow, cycle, rounds, roundId == null ? now : null);
  }

  private static Round closed(Round round, Instant now) {
    return new Round(round.id(), round.audience(), round.openedBy(), round.openedAt(), now, round.contributions());
  }
  private static Cycle cycle(Flow flow, String id) {
    var cycle = flow.getReviewCycles().stream().filter(c -> c.id().equals(id)).findFirst()
        .orElseThrow(() -> new KlabIllegalStateException("Unknown review cycle"));
    if (cycle.closedAt() != null) fail("Review cycle is closed");
    return cycle;
  }
  private static Round round(Cycle cycle, String id) {
    return cycle.rounds().stream().filter(r -> r.id().equals(id)).findFirst()
        .orElseThrow(() -> new KlabIllegalStateException("Unknown review round"));
  }
  private static void replace(Flow flow, Cycle cycle, List<Round> rounds, Instant closedAt) {
    var cycles = new ArrayList<>(flow.getReviewCycles());
    cycles.set(cycles.indexOf(cycle), new Cycle(cycle.id(), cycle.sourceStateId(), cycle.sourceFlowRevision(),
        cycle.candidate(), cycle.openedBy(), cycle.openedAt(), closedAt, rounds));
    flow.setReviewCycles(cycles);
  }
  // The aggregate JSON mapper rounds sub-microsecond numeric timestamps; persist stable milliseconds.
  private static Instant now() { return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
  private static void fail(String message) { throw new KlabIllegalStateException(message); }
}
