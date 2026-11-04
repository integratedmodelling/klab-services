package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** An additive role effect proposed by a server-owned, semantically bound consequence rule. */
public record ConsequenceRoleAttribution(
    Observation source, Observation recipient, Concept role, String ruleId) {
  public ConsequenceRoleAttribution {
    Objects.requireNonNull(source);
    Objects.requireNonNull(recipient);
    Objects.requireNonNull(role);
    if (ruleId == null || ruleId.isBlank())
      throw new IllegalArgumentException("A role attribution requires its source rule");
  }
}
