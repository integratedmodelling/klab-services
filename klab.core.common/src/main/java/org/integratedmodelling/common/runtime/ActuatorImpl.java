package org.integratedmodelling.common.runtime;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class ActuatorImpl implements Actuator {

  @Serial private static final long serialVersionUID = 2500101522003062757L;
  private String name;
  private Artifact.Type type;
  private Observable observable;
  private String strategyUrn;
  private List<Actuator> children = new ArrayList<>();
  private List<ServiceCall> computation = new ArrayList<>();
  private Geometry coverage = Geometry.UNIVERSAL;
  private Parameters<String> data = Parameters.create();
  private Geometry resolvedGeometry = Geometry.UNIVERSAL;
  private Actuator.Type actuatorType;
  private long id;
  private double resolvedCoverage;
  private List<Annotation> annotations = new ArrayList<>();
  private long transientId = Klab.getNextId();
  private Data.ShardingStrategy shardingStrategy;
  private long parentTransientId;
  private long parentId = -1;
  private int childrenCount = 0;

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Artifact.Type getType() {
    return this.type;
  }

  @Override
  public Observable getObservable() {
    return this.observable;
  }

  @Override
  public List<Actuator> getChildren() {
    return this.children;
  }

  @Override
  public List<ServiceCall> getComputation() {
    return this.computation;
  }

  @Override
  public String getStrategyUrn() {
    return strategyUrn;
  }

  public void setStrategyUrn(String strategyUrn) {
    this.strategyUrn = strategyUrn;
  }

  @Override
  public Geometry getCoverage() {
    return this.coverage;
  }

  @Override
  public Parameters<String> getData() {
    return this.data;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setType(Artifact.Type type) {
    this.type = type;
  }

  public void setObservable(Observable observable) {
    this.observable = observable;
  }

  public void setChildren(List<Actuator> children) {
    this.children = children;
  }

  public void setComputation(List<ServiceCall> computation) {
    this.computation = computation;
  }

  public void setCoverage(Geometry coverage) {
    this.coverage = coverage;
  }

  public void setData(Parameters<String> data) {
    this.data = data;
  }

  @Override
  public Type getActuatorType() {
    return actuatorType;
  }

  public void setActuatorType(Type actuatorType) {
    this.actuatorType = actuatorType;
  }

  public double getResolvedCoverage() {
    return resolvedCoverage;
  }

  public void setResolvedCoverage(double resolvedCoverage) {
    this.resolvedCoverage = resolvedCoverage;
  }

  @Override
  public List<Annotation> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  public Geometry getResolvedGeometry() {
    return resolvedGeometry;
  }

  public void setResolvedGeometry(Geometry resolvedGeometry) {
    this.resolvedGeometry = resolvedGeometry;
  }

  @Override
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Override
  public Data.ShardingStrategy getShardingStrategy() {
    return shardingStrategy;
  }

  public void setShardingStrategy(Data.ShardingStrategy shardingStrategy) {
    this.shardingStrategy = shardingStrategy;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  @Override
  public String toString() {
    return "A(" + this.getId() + ", " + this.observable + ")";
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  @Override
  public int getChildrenCount() {
    return childrenCount;
  }

  public void setChildrenCount(int childrenCount) {
    this.childrenCount = childrenCount;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }

  @Override
  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }
}
