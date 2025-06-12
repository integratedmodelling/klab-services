package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;

public class VisualizationRequest {

  public static final String VIEWPORT_KEY = "viewport";

  private Geometry geometry;
  private Parameters<String> parameters;

  public Geometry getGeometry() {
    return geometry;
  }

  public void setGeometry(Geometry geometry) {
    this.geometry = geometry;
  }

  public Parameters<String> getParameters() {
    return parameters;
  }

  public void setParameters(Parameters<String> parameters) {
    this.parameters = parameters;
  }
}
