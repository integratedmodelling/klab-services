package org.integratedmodelling.klab.api.data.impl;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;

public class LinkImpl implements KnowledgeGraph.Link {

  private RuntimeAsset source;
  private RuntimeAsset target;
  private GraphModel.Relationship relationship;
  private int sequence = -1;
  private Geometry geometry;
  private Parameters<String> properties = Parameters.create();

  public LinkImpl() {}

  public LinkImpl(RuntimeAsset source, RuntimeAsset target, GraphModel.Relationship relationship) {
    this.source = source;
    this.target = target;
    this.relationship = relationship;
  }

  public void setSource(RuntimeAsset source) {
    this.source = source;
  }

  public void setTarget(RuntimeAsset target) {
    this.target = target;
  }

  public void setRelationship(GraphModel.Relationship relationship) {
    this.relationship = relationship;
  }

  public void setSequence(int sequence) {
    this.sequence = sequence;
  }

  public void setGeometry(Geometry geometry) {
    this.geometry = geometry;
  }

  @Override
  public GraphModel.Relationship type() {
    return relationship;
  }

  @Override
  public Parameters<String> properties() {
    return properties;
  }

  @Override
  public RuntimeAsset source() {
    return source;
  }

  @Override
  public RuntimeAsset target() {
    return target;
  }

  @Override
  public int sequence() {
    return sequence;
  }

  @Override
  public Geometry geometry() {
    return geometry;
  }

  @Override
  public long getId() {
    return -1;
  }

  @Override
  public long getParentId() {
    return -1;
  }

  @Override
  public long getTransientId() {
    return -1;
  }

  @Override
  public int getChildrenCount() {
    return 0;
  }

  @Override
  public long getParentTransientId() {
    return -1;
  }

  @Override
  public RuntimeAsset.Type classify() {
    return RuntimeAsset.Type.LINK;
  }

  public RuntimeAsset getSource() {
    return source;
  }

  public RuntimeAsset getTarget() {
    return target;
  }

  public GraphModel.Relationship getRelationship() {
    return relationship;
  }

  public int getSequence() {
    return sequence;
  }

  public Geometry getGeometry() {
    return geometry;
  }

  public Parameters<String> getProperties() {
    return properties;
  }

  public void setProperties(Parameters<String> properties) {
    this.properties = properties;
  }
}
