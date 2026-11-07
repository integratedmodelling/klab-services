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

  static boolean editor(Flow flow, Workflow workflow, WorkflowParticipant actor) {
    if (!actor.isAuthenticated() || !actor.isWorkflowPermitted(workflow)) return false;
    if (ReviewStageProtocol.enabled(workflow) && ReviewStageProtocol.phase(flow) == Phase.BOOTSTRAP
        && !ReviewStageProtocol.creator(flow, actor)) return false;
    if (actor.getRoles().contains(WorkflowRole.ADMIN)) return true;
    return actor.getRoles().contains(WorkflowRole.EDITOR)
        && (Objects.equals(flow.getOwner(), actor.getIdentity()) || flow.getStates().values().stream()
            .anyMatch(state -> Objects.equals(state.getOwner(), actor.getIdentity())
                || state.getAssignees().contains(actor.getIdentity())));
  }

  static boolean admitted(Workflow workflow, WorkflowParticipant actor, Audience audience) {
    return actor.isAuthenticated() && (audience == Audience.COMMUNITY
        || (actor.getRoles().contains(WorkflowRole.REVIEWER) && actor.isWorkflowPermitted(workflow)));
  }

  static void requireEditor(Flow flow, Workflow workflow, WorkflowParticipant actor) {
    if (!editor(flow, workflow, actor)) throw new KlabResourceAccessException("Flow owner or assigned workflow editor required");
  }

  static List<Cycle> visible(Flow flow, Workflow workflow, WorkflowParticipant actor) {
    var result = new ArrayList<Cycle>();
    for (var cycle : flow.getReviewCycles()) {
      var rounds = new ArrayList<Round>();
      for (var round : cycle.rounds()) {
        if (!editor(flow, workflow, actor) && !admitted(workflow, actor, round.audience())) continue;
        var contributions = round.contributions().stream()
            .filter(c -> c.submitted() || Objects.equals(c.owner(), actor.getIdentity())).toList();
        rounds.add(new Round(round.id(), round.audience(), round.openedBy(), round.openedAt(),
            round.closedAt(), contributions, round.stageId()));
      }
      if (editor(flow, workflow, actor) || !rounds.isEmpty()) result.add(new Cycle(cycle.id(),
          cycle.sourceStateId(), cycle.sourceFlowRevision(), cycle.candidate(), cycle.openedBy(),
          cycle.openedAt(), cycle.closedAt(), rounds, cycle.closedFlowRevision()));
    }
    return result;
  }

  static List<String> contributableRounds(List<Cycle> visible, Workflow workflow, WorkflowParticipant actor) {
    return visible.stream().filter(c -> c.closedAt() == null).flatMap(c -> c.rounds().stream())
        .filter(r -> r.closedAt() == null && admitted(workflow, actor, r.audience())).map(Round::id).toList();
  }

  static void openCycle(Flow flow, Workflow workflow, WorkflowParticipant actor, String stateId,
      String proposalId, String ontologyId, java.util.function.Function<String, byte[]> payload) {
    openCycle(flow, workflow, actor, stateId, proposalId, ontologyId, payload, Audience.REVIEWER);
  }

  static void openCycle(Flow flow, Workflow workflow, WorkflowParticipant actor, String stateId,
      String proposalId, String ontologyId, java.util.function.Function<String, byte[]> payload, Audience audience) {
    requireEditor(flow, workflow, actor);
    if (flow.getReviewCycles().size() >= 256) fail("Review cycle limit reached");
    var state = flow.getStates().get(stateId);
    if (state == null) fail("A source stage is required");
    ReviewStageProtocol.requireCycleSource(flow, workflow, actor, state);
    ProposalReview.Candidate candidate;
    if (proposalId != null) {
      if (state.getStatus() != Flow.StateStatus.OPEN || !flow.getCurrentStateIds().contains(stateId))
        fail("New candidates require a current open editing stage");
      var proposal = artifact(state, proposalId, "application/vnd.klab.proposal+yaml", payload);
      var ontology = ontologyId == null ? null : artifact(state, ontologyId, "application/vnd.klab.ontology", payload);
      var check = ProposalSchemaValidator.bundled().validate(payload.apply(proposalId));
      if (check.status() != ProposalReview.CheckStatus.PASS) fail("Candidate proposal must pass the bundled document schema");
      candidate = ProposalReviewProtocol.readCandidate(proposal, ontology, payload);
    } else {
      if (ontologyId != null) fail("A proposal attachment is required");
      candidate = state.getProposalReview() == null ? null : state.getProposalReview().candidate();
      if (candidate != null) {
        // Recheck payload identity before creating another durable reference, including closed flows.
        artifact(flow, candidate.proposal(), "application/vnd.klab.proposal+yaml", payload);
        if (candidate.ontology() != null) artifact(flow, candidate.ontology(), "application/vnd.klab.ontology", payload);
      }
    }
    if (candidate == null) fail("An exact reviewed candidate is required");
    // Submitted alternatives bind revision identities just as frozen candidates do.
    ReviewContributionProposal.requireFreshIdentity(flow, candidate);
    var cycles = new ArrayList<>(flow.getReviewCycles());
    cycles.add(new Cycle(UUID.randomUUID().toString(), stateId, flow.getRevision(), candidate,
        actor.getIdentity(), now(), null, List.of()));
    flow.setReviewCycles(cycles);
    ReviewStageProtocol.freezeSource(flow, workflow, state);
    if (ReviewStageProtocol.enabled(workflow)) {
      openRound(flow, workflow, actor, cycles.getLast().id(), audience == null ? Audience.REVIEWER : audience);
      ReviewWorkflowNotifications.stage(flow, new Command(flow.getRevision(), Operation.OPEN_ROUND, null,
          cycles.getLast().id(), null, audience, null, null, false));
    }
  }

  static void openRound(Flow flow, Workflow workflow, WorkflowParticipant actor, String cycleId,
      Audience audience) {
    requireEditor(flow, workflow, actor);
    var cycle = cycle(flow, cycleId);
    if (audience == null) fail("Review audience is required");
    if (cycle.rounds().size() >= 256) fail("Review round limit reached");
    var rounds = new ArrayList<>(cycle.rounds());
    var stageId = ReviewStageProtocol.openRoundStage(flow, workflow, actor, cycle, audience);
    rounds.add(new Round(UUID.randomUUID().toString(), audience, actor.getIdentity(), now(), null, List.of(), stageId));
    replace(flow, cycle, rounds, null);
  }

  static void contribute(Flow flow, Workflow workflow, WorkflowParticipant actor, String cycleId,
      String roundId, String contributionId, String text, boolean submit, ProposalInput proposalInput) {
    var cycle = cycle(flow, cycleId);
    var round = round(cycle, roundId);
    if (!admitted(workflow, actor, round.audience()))
      throw new KlabResourceAccessException("Review audience does not admit this participant");
    if (round.closedAt() != null) fail("Review round is closed");
    var contributions = new ArrayList<>(round.contributions());
    Contribution prior = null;
    if (contributionId != null) {
      prior = contributions.stream().filter(c -> contributionId.equals(c.id())).findFirst()
          .orElseThrow(() -> new KlabIllegalStateException("Unknown contribution"));
      if (!Objects.equals(actor.getIdentity(), prior.owner()))
        throw new KlabResourceAccessException("Contribution belongs to another participant");
      if (prior.submitted()) fail("Submitted feedback is immutable; add a new contribution");
    } else if (contributions.size() >= 256) fail("Contribution limit reached");
    text = text == null ? "" : text;
    if ((text.isBlank() && (proposalInput == null || proposalInput.document() == null || proposalInput.document().isBlank()))
        || text.length() > ProposalReview.MAX_RATIONALE_CHARS)
      fail("Feedback or an alternative proposal is required; feedback must fit the rationale limit");
    var proposal = ReviewContributionProposal.snapshot(proposalInput, cycle, submit);
    if (submit) ReviewContributionProposal.requireFreshIdentity(flow, proposal, contributionId);
    if (contributionId == null) {
      contributions.add(new Contribution(UUID.randomUUID().toString(), actor.getIdentity(), text, submit, now(), proposal));
    } else {
      contributions.set(contributions.indexOf(prior), new Contribution(prior.id(), prior.owner(), text, submit, now(), proposal));
    }
    long bytes = flow.getReviewCycles().stream().flatMap(c -> c.rounds().stream())
        .flatMap(r -> r.contributions().stream()).mapToLong(ReviewContributionProposal::bytes).sum()
        - round.contributions().stream().mapToLong(ReviewContributionProposal::bytes).sum()
        + contributions.stream().mapToLong(ReviewContributionProposal::bytes).sum();
    if (bytes > ProposalReview.MAX_STAGE_BYTES) fail("Review feedback aggregate size limit reached");
    var rounds = new ArrayList<>(cycle.rounds());
    rounds.set(rounds.indexOf(round), new Round(round.id(), round.audience(), round.openedBy(),
        round.openedAt(), null, contributions, round.stageId()));
    replace(flow, cycle, rounds, null);
  }

  static void close(Flow flow, Workflow workflow, WorkflowParticipant actor, String cycleId, String roundId) {
    requireEditor(flow, workflow, actor);
    var cycle = cycle(flow, cycleId);
    Instant now = now();
    var rounds = new ArrayList<>(cycle.rounds());
    if (roundId == null) {
      rounds.replaceAll(r -> {
        if (r.closedAt() != null) return r;
        ReviewStageProtocol.closeStage(flow, r.stageId());
        return closed(r, now);
      });
    } else {
      var round = round(cycle, roundId);
      if (round.closedAt() != null) fail("Review round is closed");
      ReviewStageProtocol.closeStage(flow, round.stageId());
      rounds.set(rounds.indexOf(round), closed(round, now));
    }
    replace(flow, cycle, rounds, roundId == null ? now : null);
  }

  private static Round closed(Round round, Instant now) {
    return new Round(round.id(), round.audience(), round.openedBy(), round.openedAt(), now, round.contributions(), round.stageId());
  }
  static boolean references(Flow flow, String attachmentId) {
    return flow.getReviewCycles().stream().anyMatch(c -> c.candidate().proposal().attachmentId().equals(attachmentId)
        || c.candidate().ontology() != null && c.candidate().ontology().attachmentId().equals(attachmentId));
  }

  private static ProposalReview.Artifact artifact(Flow.State state, String id, String media,
      java.util.function.Function<String, byte[]> payload) {
    var descriptor = state.getAttachments().stream().filter(a -> Objects.equals(id, a.getId())).findFirst()
        .orElseThrow(() -> new KlabIllegalStateException("Candidate attachment must belong to the source stage"));
    byte[] bytes = payload.apply(id);
    if (!media.equals(descriptor.getMediaType()) || bytes == null
        || !Objects.equals(descriptor.getChecksum(), ProposalReviewProtocol.digest(bytes)))
      fail("Candidate attachment media or checksum mismatch");
    return new ProposalReview.Artifact(id, descriptor.getChecksum());
  }

  static void artifact(Flow flow, ProposalReview.Artifact ref, String media,
      java.util.function.Function<String, byte[]> payload) {
    var state = flow.getStates().values().stream().filter(s -> s.getAttachments().stream()
        .anyMatch(a -> Objects.equals(ref.attachmentId(), a.getId()))).findFirst()
        .orElseThrow(() -> new KlabIllegalStateException("Candidate artifact is unavailable"));
    if (!ref.equals(artifact(state, ref.attachmentId(), media, payload))) fail("Candidate checksum mismatch");
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
        cycle.candidate(), cycle.openedBy(), cycle.openedAt(), closedAt, rounds,
        closedAt == null ? null : flow.getRevision() + 1));
    flow.setReviewCycles(cycles);
  }
  // The aggregate JSON mapper rounds sub-microsecond numeric timestamps; persist stable milliseconds.
  private static Instant now() { return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
  private static void fail(String message) { throw new KlabIllegalStateException(message); }
}
