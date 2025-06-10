package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.collections.Parameters;

import java.io.Serializable;

/**
 * Data to include with a resolution constraint for observations that are submitted with associated
 * resolution information.
 */
public class ResolutionData implements Serializable {

  private String adapter;
  private Parameters<String> adapterParameters = Parameters.create();

  public String getAdapter() {
    return adapter;
  }

  public void setAdapter(String adapter) {
    this.adapter = adapter;
  }

  public Parameters<String> getAdapterParameters() {
    return adapterParameters;
  }

  public void setAdapterParameters(Parameters<String> adapterParameters) {
    this.adapterParameters = adapterParameters;
  }
}
