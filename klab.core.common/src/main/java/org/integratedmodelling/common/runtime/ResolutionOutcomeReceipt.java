package org.integratedmodelling.common.runtime;

import java.util.Optional;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

/** Typed terminal resolution evidence. Absence is not evidence of successful resolution. */
public final class ResolutionOutcomeReceipt {
  public static final String KEY = "runtime.resolutionOutcome";

  private ResolutionOutcomeReceipt() {}

  public static Optional<Dataflow.ResolutionOutcome> read(Observation observation) {
    if (observation == null) return Optional.empty();
    Object value = observation.getMetadata().get(KEY);
    if (value instanceof Dataflow.ResolutionOutcome outcome) return Optional.of(outcome);
    if (value instanceof String text)
      try {
        return Optional.of(Dataflow.ResolutionOutcome.valueOf(text));
      } catch (IllegalArgumentException ignored) {
      }
    return Optional.empty();
  }

  /** Detach notifications and metadata, including the globally shared empty sentinel. */
  public static Observation terminal(Observation observation, Dataflow.ResolutionOutcome outcome) {
    var detached = Observation.forTransport(observation);
    detached.getMetadata().put(KEY, java.util.Objects.requireNonNull(outcome).name());
    return detached;
  }
}
