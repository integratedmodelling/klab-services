package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

/** Share the actual acknowledgment constructor with the temporal native fixture. */
public final class InstantiationPolicyTestSupport {
  private InstantiationPolicyTestSupport() {}
  public static Actuator acknowledgment(Observation child) {
    return InstantiationPlanning.acknowledgment(child).getComputation().getFirst();
  }
}
