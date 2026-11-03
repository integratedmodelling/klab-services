package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Logical support identity, not execution authority. Native issued evidence must prove its use. */
public record RolePresenceKey(
    String contextId,
    long observerId,
    long focusId,
    long recipientId,
    String roleUrn,
    String recipientObserverSemantics)
    implements java.io.Serializable {
  public RolePresenceKey {
    if (contextId == null
        || contextId.isBlank()
        || observerId < 0
        || focusId < 0
        || recipientId <= 0
        || roleUrn == null
        || roleUrn.isBlank()
        || recipientObserverSemantics == null)
      throw new IllegalArgumentException(
          "Role presence needs an exact durable semantic perspective");
  }

  static RolePresenceKey captured(
      String context, ConsequenceNativeInputs.Snapshot inputs, long recipient, Concept role) {
    Objects.requireNonNull(inputs);
    Objects.requireNonNull(role);
    Observation observation = inputs.observation(recipient);
    if (!Objects.equals(context, inputs.reads().contextId())
        || !role.is(SemanticType.ROLE)
        || observation.getObservable() == null)
      throw new IllegalStateException("Logical role source differs from captured native evidence");
    var observerMeaning = observation.getObservable().getObserverSemantics();
    return new RolePresenceKey(
        context,
        inputs.observerId() == null ? 0 : inputs.observerId(),
        inputs.focusId(),
        recipient,
        role.getUrn(),
        observerMeaning == null ? "" : observerMeaning.getUrn());
  }
}
