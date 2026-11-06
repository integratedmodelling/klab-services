package org.integratedmodelling.klab.api.services.resources.workflow;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Server-owned immutable snapshots. Closing a cycle records provenance, never acceptance. */
public final class ReviewCycles {
  private ReviewCycles() {}
  public enum Audience { REVIEWER, COMMUNITY }
  public enum Operation { OPEN_CYCLE, OPEN_ROUND, SAVE_CONTRIBUTION, CLOSE_ROUND, CLOSE_CYCLE }
  /** expectedRevision is mandatory; refresh and explicitly retry after a conflict. */
  public record Command(long expectedRevision, Operation operation, String sourceStateId,
      String cycleId, String roundId, Audience audience, String contributionId, String text,
      boolean submit, String proposalAttachmentId, String ontologyAttachmentId) implements Serializable {
    public Command(long expectedRevision, Operation operation, String sourceStateId, String cycleId,
        String roundId, Audience audience, String contributionId, String text, boolean submit) {
      this(expectedRevision, operation, sourceStateId, cycleId, roundId, audience, contributionId, text, submit, null, null);
    }
  }
  public record View(long revision, List<Cycle> cycles, boolean canManage, String identity,
      List<String> contributableRoundIds) implements Serializable {
    public View(long revision, List<Cycle> cycles) { this(revision, cycles, false, null, List.of()); }
    public View { cycles = List.copyOf(cycles); contributableRoundIds = List.copyOf(contributableRoundIds); }
  }
  public record Contribution(String id, String owner, String text, boolean submitted,
      Instant updatedAt) implements Serializable {}
  public record Round(String id, Audience audience, String openedBy, Instant openedAt,
      Instant closedAt, List<Contribution> contributions) implements Serializable {
    public Round { contributions = List.copyOf(contributions); }
  }
  public record Cycle(String id, String sourceStateId, long sourceFlowRevision,
      ProposalReview.Candidate candidate, String openedBy, Instant openedAt,
      Instant closedAt, List<Round> rounds) implements Serializable {
    public Cycle { rounds = List.copyOf(rounds); }
  }
}
