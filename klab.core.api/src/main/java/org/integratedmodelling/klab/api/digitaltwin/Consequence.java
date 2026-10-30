package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;

/**
 * Correlation and transport contracts for post-commit consequences. These messages describe engine
 * decisions and their resulting commits; ordinary user submissions keep their existing notifications.
 */
public final class Consequence {
  private Consequence() {}

  public static final String METADATA_KEY = "klab.consequence.cause";

  public enum Kind {
    CONFIGURATION_DETECTION,
    CHANGE_PROCESS,
    ROLE_CONFERRAL,
    ROLE_IMPLICATION,
    UNCERTAINTY
  }

  public enum Outcome {
    /** The handler completed its action. Resulting commits have their own notifications. */
    APPLIED,
    /** The handler evaluated this trigger and found no action to take. */
    NO_ACTION,
    /** Required semantic support or policy is not available; no evaluation is claimed. */
    DEFERRED,
    FAILED
  }

  /**
   * Identity of one decision, allocated before invoking a handler and inherited by its submissions.
   * The parent decision is null for ordinary commits. A zero observer ID means no selected observer.
   * The worldview fingerprint may be null for a context without a bound semantic environment.
   */
  public record Cause(
      int version,
      String contextId,
      long triggeringCommitId,
      long triggeringActivityId,
      long observerId,
      String worldviewFingerprint,
      String parentDecisionId,
      String decisionId,
      Kind kind) implements Serializable {
    public Cause {
      if (version != 1 || contextId == null || contextId.isBlank() || triggeringCommitId <= 0
          || triggeringActivityId <= 0 || observerId < 0
          || decisionId == null || decisionId.isBlank())
        throw new IllegalArgumentException("Invalid consequence cause");
      Objects.requireNonNull(kind);
    }
  }

  /** One handler outcome. Observation IDs are evidence, not a claim of successful resolution. */
  public record Decision(Cause cause, Outcome outcome, String explanation, List<Long> observationIds)
      implements Serializable {
    public Decision {
      Objects.requireNonNull(cause);
      Objects.requireNonNull(outcome);
      Objects.requireNonNull(explanation);
      observationIds = List.copyOf(observationIds);
    }
  }

  /** A successful root commit produced using the scope of a consequence decision. */
  public record Commit(Cause cause, KnowledgeGraph.Commit commit) implements Serializable {
    public Commit {
      Objects.requireNonNull(cause);
      if (commit == null || commit.getId() <= 0 || commit.getId() == cause.triggeringCommitId())
        throw new IllegalArgumentException("A consequence must identify its new committed changes");
    }
  }
}
