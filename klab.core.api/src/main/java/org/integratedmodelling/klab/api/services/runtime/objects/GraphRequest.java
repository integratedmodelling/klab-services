package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.collections.Parameters;

/**
 * A generic POST request for a graph focused on a URN and with a specified depth and set of
 * filters. Endpoints accepting this should quickly return a {@link
 * org.integratedmodelling.klab.api.data.GraphReference}.
 */
public class GraphRequest {

  private String graphType;
  private String focusUrn;
  private int depth = -1;
  private Parameters<String> filters = Parameters.create();

  public String getGraphType() {
    return graphType;
  }

  public void setGraphType(String graphType) {
    this.graphType = graphType;
  }

  public String getFocusUrn() {
    return focusUrn;
  }

  public void setFocusUrn(String focusUrn) {
    this.focusUrn = focusUrn;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public Parameters<String> getFilters() {
    return filters;
  }

  public void setFilters(Parameters<String> filters) {
    this.filters = filters;
  }
}
