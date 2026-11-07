package org.integratedmodelling.klab.api.services.resources.workflow;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Server-owned immutable snapshots. Closing a cycle records provenance, never acceptance. */
public final class ReviewCycles {
  private ReviewCycles() {}
  public enum Audience { REVIEWER, COMMUNITY }
  public enum Operation { OPEN_CYCLE, OPEN_ROUND, SAVE_CONTRIBUTION, CLOSE_ROUND, CLOSE_CYCLE, ENTER_COALESCING, TERMINATE }
  public enum Phase { BOOTSTRAP, REVIEW, COALESCING, TERMINATED }
  public record Stage(String id, String schemaId, String cycleId, String roundId, boolean open,
      boolean canEdit, boolean canContribute) implements Serializable {}
  /** expectedRevision is mandatory; refresh and explicitly retry after a conflict. */
  public record Command(long expectedRevision, Operation operation, String sourceStateId,
      String cycleId, String roundId, Audience audience, String contributionId, String text,
      boolean submit, String proposalAttachmentId, String ontologyAttachmentId, ProposalInput proposal) implements Serializable {
    public Command(long expectedRevision, Operation operation, String sourceStateId, String cycleId,
        String roundId, Audience audience, String contributionId, String text, boolean submit,
        String proposalAttachmentId, String ontologyAttachmentId) {
      this(expectedRevision, operation, sourceStateId, cycleId, roundId, audience, contributionId, text,
          submit, proposalAttachmentId, ontologyAttachmentId, null);
    }
    public Command(long expectedRevision, Operation operation, String sourceStateId, String cycleId,
        String roundId, Audience audience, String contributionId, String text, boolean submit) {
      this(expectedRevision, operation, sourceStateId, cycleId, roundId, audience, contributionId, text, submit, null, null);
    }
  }
  /** An alternative proposal against the enclosing round's frozen candidate. Null source retains
   * that candidate's ontology; it never means an empty ontology. Server derives all identity/checks. */
  public record ProposalInput(String document, String ontologySource) implements Serializable {}
  public record ProposalSnapshot(String document, String ontologySource, String documentChecksum,
      String ontologyChecksum, String proposalId, String revisionId, String supersedesRevision,
      String contextDigest, List<String> actionIds, ProposalReview.Check schemaCheck) implements Serializable {
    public ProposalSnapshot { actionIds = List.copyOf(actionIds); }
  }
  public record View(long revision, List<Cycle> cycles, boolean canManage, String identity,
      List<String> contributableRoundIds, Phase phase, String editableStageId, List<Stage> stages,
      boolean canOpenCycle, boolean canCoalesce, boolean canTerminate, boolean canOpenRound) implements Serializable {
    public View(long revision, List<Cycle> cycles, boolean canManage, String identity, List<String> rounds) {
      this(revision, cycles, canManage, identity, rounds, null, null, List.of(), canManage, false, false, canManage);
    }
    public View(long revision, List<Cycle> cycles) { this(revision, cycles, false, null, List.of()); }
    public View { cycles = List.copyOf(cycles); contributableRoundIds = List.copyOf(contributableRoundIds); stages = List.copyOf(stages); }
  }
  public record Contribution(String id, String owner, String text, boolean submitted,
      Instant updatedAt, ProposalSnapshot proposal) implements Serializable {
    public Contribution(String id, String owner, String text, boolean submitted, Instant updatedAt) {
      this(id, owner, text, submitted, updatedAt, null);
    }
  }
  public record Round(String id, Audience audience, String openedBy, Instant openedAt,
      Instant closedAt, List<Contribution> contributions, String stageId) implements Serializable {
    public Round(String id, Audience audience, String openedBy, Instant openedAt, Instant closedAt, List<Contribution> contributions) {
      this(id, audience, openedBy, openedAt, closedAt, contributions, null);
    }
    public Round { contributions = List.copyOf(contributions); }
  }
  public record Cycle(String id, String sourceStateId, long sourceFlowRevision,
      ProposalReview.Candidate candidate, String openedBy, Instant openedAt,
      Instant closedAt, List<Round> rounds, Long closedFlowRevision) implements Serializable {
    public Cycle(String id, String sourceStateId, long sourceFlowRevision,
        ProposalReview.Candidate candidate, String openedBy, Instant openedAt,
        Instant closedAt, List<Round> rounds) {
      this(id, sourceStateId, sourceFlowRevision, candidate, openedBy, openedAt, closedAt, rounds, null);
    }
    public Cycle { rounds = List.copyOf(rounds); }
  }
}
