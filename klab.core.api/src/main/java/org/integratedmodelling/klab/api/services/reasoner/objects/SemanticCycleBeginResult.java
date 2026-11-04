package org.integratedmodelling.klab.api.services.reasoner.objects;

/** Atomic cycle admission decision; unavailability is not an authorization or protocol failure. */
public record SemanticCycleBeginResult(Status status, SemanticCycleBinding binding, String reason)
    implements java.io.Serializable {
  public enum Status {
    BOUND,
    NO_ACCEPTED_VIEW
  }

  public SemanticCycleBeginResult {
    java.util.Objects.requireNonNull(status, "Semantic cycle decision");
    if ((status == Status.BOUND) != (binding != null))
      throw new IllegalArgumentException("Inconsistent semantic cycle decision");
    if (status == Status.NO_ACCEPTED_VIEW && (reason == null || reason.isBlank()))
      throw new IllegalArgumentException("Missing semantic unavailability reason");
  }

  public SemanticCycleBinding requireBinding() {
    if (status != Status.BOUND) throw new IllegalStateException(reason);
    return binding;
  }
}
