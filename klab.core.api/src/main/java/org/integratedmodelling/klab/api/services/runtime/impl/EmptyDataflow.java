package org.integratedmodelling.klab.api.services.runtime.impl;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class EmptyDataflow implements Dataflow {

  private List<Notification> notifications = new ArrayList<>();
  private Coverage coverage = Coverage.empty();
  private String name = "Empty Dataflow";
  private List<Actuator> computation = new ArrayList<>();
  private ResourceSet requirements = ResourceSet.empty();
  private boolean empty = true;
  private long id;
  private long parentId;
  private long transientId;
  private int childrenCount;
  private long parentTransientId;

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public boolean isEmpty() {
    return this.empty;
  }

  @Override
  public ResourceSet getRequirements() {
    return this.requirements;
  }

  @Override
  public Geometry getCoverage() {
    return coverage;
  }

  @Override
  public List<Actuator> getComputation() {
    return computation;
  }

  @Override
  public List<Notification> getNotifications() {
    return notifications;
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public long getParentId() {
    return parentId;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  @Override
  public int getChildrenCount() {
    return childrenCount;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  public void setCoverage(Coverage coverage) {
    this.coverage = coverage;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setComputation(List<Actuator> computation) {
    this.computation = computation;
  }

  public void setRequirements(ResourceSet requirements) {
    this.requirements = requirements;
  }

  public void setEmpty(boolean empty) {
    this.empty = empty;
  }

  public void setId(long id) {
    this.id = id;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  public void setChildrenCount(int childrenCount) {
    this.childrenCount = childrenCount;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }
}
