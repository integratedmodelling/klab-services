package org.integratedmodelling.klab.api.digitaltwin.impl;

import com.sun.jdi.PrimitiveValue;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;

import java.util.HashSet;
import java.util.Set;

public class CommitImpl implements KnowledgeGraph.Commit {

  private String id;
  private long timestamp;
  private Set<Long> newAssets = new HashSet<>();
  private Set<Triple<Long, Long, GraphModel.Relationship>> newLinks = new HashSet<>();

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public Set<Long> getNewAssets() {
    return newAssets;
  }

  public void setNewAssets(Set<Long> newAssets) {
    this.newAssets = newAssets;
  }

  @Override
  public Set<Triple<Long, Long, GraphModel.Relationship>> getNewLinks() {
    return newLinks;
  }

  public void setNewLinks(Set<Triple<Long, Long, GraphModel.Relationship>> newLinks) {
    this.newLinks = newLinks;
  }
}
