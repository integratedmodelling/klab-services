package org.integratedmodelling.klab.api.data.impl;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;

public class LinkInfoImpl implements KnowledgeGraph.LinkInfo {

  private GraphModel.Relationship type;
  private Parameters<String> properties;
  private long sourceId;
  private long targetId;

  @Override
  public GraphModel.Relationship getType() {
    return this.type;
  }

  @Override
  public Parameters<String> getProperties() {
    return this.properties;
  }

  @Override
  public long getSourceId() {
    return this.sourceId;
  }

  @Override
  public long getTargetId() {
    return this.targetId;
  }

  public void setType(GraphModel.Relationship type) {
    this.type = type;
  }

  public void setProperties(Parameters<String> properties) {
    this.properties = properties;
  }

  public void setSourceId(long sourceId) {
    this.sourceId = sourceId;
  }

  public void setTargetId(long targetId) {
    this.targetId = targetId;
  }
}
