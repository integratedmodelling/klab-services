package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Portable consumer view; the runtime validates its source in the authorized context. */
public class ObservationExportRequest {
  private Observation observation;
  private Parameters<String> parameters = Parameters.create();

  public Observation getObservation() { return observation; }
  public void setObservation(Observation observation) { this.observation = observation; }
  public Parameters<String> getParameters() { return parameters; }
  public void setParameters(Parameters<String> parameters) { this.parameters = parameters; }
}
