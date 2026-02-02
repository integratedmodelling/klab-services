package org.integratedmodelling.klab.api.digitaltwin.impl;

import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;

import java.util.HashSet;
import java.util.Set;

public class CommitImpl implements KnowledgeGraph.Commit {

  private String id;
  private long timestamp;
  private Set<Long> addedAssets = new HashSet<>();
  private Set<Triple<Long, Long, String>> addedLinks = new HashSet<>();
  private Set<Long> deletedAssets = new HashSet<>();
  private Set<Triple<Long, Long, String>> deletedLinks = new HashSet<>();
  private Set<Long> addedObservations = new HashSet<>();
  private Set<Long> addedCohorts = new HashSet<>();
  private Set<Long> modifiedAssets = new HashSet<>();

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
  public Set<Long> getAddedAssets() {
    return addedAssets;
  }

  public void setAddedAssets(Set<Long> addedAssets) {
    this.addedAssets = addedAssets;
  }

  @Override
  public Set<Long> getAddedObservations() {
    return addedObservations;
  }

  public void setAddedObservations(Set<Long> addedObservations) {
    this.addedObservations = addedObservations;
  }

  @Override
  public Set<Triple<Long, Long, String>> getAddedLinks() {
    return addedLinks;
  }

  public void setAddedLinks(Set<Triple<Long, Long, String>> addedLinks) {
    this.addedLinks = addedLinks;
  }

  @Override
  public Set<Long> getDeletedAssets() {
    return deletedAssets;
  }

  public void setDeletedAssets(Set<Long> deletedAssets) {
    this.deletedAssets = deletedAssets;
  }

  @Override
  public Set<Triple<Long, Long, String>> getDeletedLinks() {
    return deletedLinks;
  }

  public void setDeletedLinks(Set<Triple<Long, Long, String>> deletedLinks) {
    this.deletedLinks = deletedLinks;
  }

  @Override
  public Set<Long> getModifiedAssets() {
    return modifiedAssets;
  }

  public void setModifiedAssets(Set<Long> modifiedAssets) {
    this.modifiedAssets = modifiedAssets;
  }

  @Override
  public Set<Long> getAddedCohorts() {
    return addedCohorts;
  }

  public void setAddedCohorts(Set<Long> addedCohorts) {
    this.addedCohorts = addedCohorts;
  }
}
