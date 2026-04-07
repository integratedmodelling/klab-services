package org.integratedmodelling.common.runtime;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowImpl implements Dataflow {

  @Serial private static final long serialVersionUID = 873406284216826384L;

  private boolean empty;
  private ResourceSet requirements;
  private Coverage coverage;
  private List<Actuator> computation = new ArrayList<>();
  private long transientId = Klab.getNextId();
  private long parentTransientId = -1000;
  private long parentId = -1000;
  private double resolvedCoverage;
  private int childrenCount = -1;
  private String name;
  @Deprecated private long id;

  @Override
  public boolean isEmpty() {
    return this.empty;
  }

  @Override
  public Coverage getCoverage() {
    return this.coverage;
  }

  @Override
  public int getChildrenCount() {
    return childrenCount;
  }

  public void setChildrenCount(int childrenCount) {
    this.childrenCount = childrenCount;
  }

  @Override
  public List<Actuator> getComputation() {
    return this.computation;
  }

  public void setEmpty(boolean empty) {
    this.empty = empty;
  }

  public void setCoverage(Coverage coverage) {
    this.coverage = coverage;
  }

  public void setComputation(List<Actuator> computation) {
    this.computation = computation;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public ResourceSet getRequirements() {
    return requirements;
  }

  public void setRequirements(ResourceSet requirements) {
    this.requirements = requirements;
  }

  public void add(Dataflow dataflow, ContextScope scope) {

    /*
     * Find the "hook point" using the observation ID
     */
    if (scope.getContextObservation() == null) {
      computation.addAll(dataflow.getComputation());
    } else {
      Actuator actuator = findActuator(scope.getContextObservation().getId());
      if (actuator == null) {
        throw new KlabIllegalArgumentException(
            "cannot add dataflow: observation ID does not correspond to an actuator");
      }
      actuator.getChildren().addAll(dataflow.getComputation());
    }

    computeCoverage();
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  private Actuator findActuator(long id) {
    for (Actuator actuator : getComputation()) {
      Actuator found = findActuator(actuator, id);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private Actuator findActuator(Actuator actuator, long id) {
    if (id == actuator.getId()) {
      return actuator;
    }
    for (Actuator child : actuator.getChildren()) {
      Actuator found = findActuator(child, id);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  public void computeCoverage() {
    // TODO Auto-generated method stub
  }

  @Override
  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }

  @Override
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public double getResolvedCoverage() {
    return resolvedCoverage;
  }

  public void setResolvedCoverage(double resolvedCoverage) {
    this.resolvedCoverage = resolvedCoverage;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }
}
