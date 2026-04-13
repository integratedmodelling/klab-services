package org.integratedmodelling.klab.runtime.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * A server side implementation of a dataflow that uses the knowledge graph and can be adapted to a
 * serializable form for clients.
 */
public class DataflowGraph implements Dataflow {

  private final KnowledgeGraph database;
  private final ContextScope scope;
  private long transientId = Klab.getNextId();
  private String name;
  private List<Notification> notifications = new ArrayList<>();

  public DataflowGraph(KnowledgeGraph database, ContextScope contextScope) {
    this.database = database;
    this.scope = contextScope;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public ResourceSet getRequirements() {
    return null;
  }

  @Override
  public long getParentTransientId() {
    return -1000;
  }

  @Override
  public Geometry getCoverage() {
    return null;
  }

  @Override
  public List<Actuator> getComputation() {
    return List.of();
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  @Override
  public int getChildrenCount() {
    return -1;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  public DataflowImpl adapt() {
    return null;
  }

  @Override
  public long getId() {
    return DATAFLOW_ASSET.getId();
  }

  @Override
  public long getParentId() {
    return DATAFLOW_ASSET.getParentId();
  }

  @Override
  public List<Notification> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }
}
