package org.integratedmodelling.common.knowledge;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

public class ModelImpl implements Model {

  public static class ResolutionInfoImpl implements Model.ResolutionInfo {

    private KlabStatement.Scope scope;
    private boolean inScenario;
    private Map<String, Integer> resolutionCriteria = new HashMap<>();
    private Geometry coverage = Geometry.UNIVERSAL;

    @Override
    public KlabStatement.Scope getScope() {
      return this.scope;
    }

    @Override
    public boolean isInScenario() {
      return this.inScenario;
    }

    @Override
    public Geometry getCoverage() {
      return this.coverage;
    }

    @Override
    public Map<String, Integer> getResolutionCriteria() {
      return this.resolutionCriteria;
    }

    public void setScope(KlabStatement.Scope scope) {
      this.scope = scope;
    }

    public void setInScenario(boolean inScenario) {
      this.inScenario = inScenario;
    }

    public void setResolutionCriteria(Map<String, Integer> resolutionCriteria) {
      this.resolutionCriteria = resolutionCriteria;
    }

    public void setCoverage(Geometry coverage) {
      this.coverage = coverage;
    }
  }

  @Serial private static final long serialVersionUID = -303420101007056751L;

  private String urn;
  private Metadata metadata = Metadata.create();
  private List<Annotation> annotations = new ArrayList<>();
  private String namespace;
  private String projectName;
  private Type type;
  private List<Observable> observables = new ArrayList<>();
  private List<Observable> dependencies = new ArrayList<>();
  private Geometry coverage;
  private List<Contextualizable> computation = new ArrayList<>();
  private Contextualization contextualization;
  private Concept observerType;
  private ResolutionInfo resolutionInfo;
  private String serviceId;

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public Metadata getMetadata() {
    return this.metadata;
  }

  @Override
  public List<Annotation> getAnnotations() {
    return this.annotations;
  }

  @Override
  public String getNamespace() {
    return this.namespace;
  }

  @Override
  public Type getType() {
    return this.type;
  }

  @Override
  public List<Observable> getObservables() {
    return this.observables;
  }

  @Override
  public List<Observable> getDependencies() {
    return this.dependencies;
  }

  @Override
  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  @Override
  public Geometry getCoverage() {
    return this.coverage;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public void setObservables(List<Observable> observables) {
    this.observables = observables;
  }

  public void setDependencies(List<Observable> dependencies) {
    this.dependencies = dependencies;
  }

  public void setCoverage(Geometry coverage) {
    this.coverage = coverage;
  }

  @Override
  public List<Contextualizable> getComputation() {
    return computation;
  }

  @Override
  public Contextualization getDescriptionType() {
    return this.contextualization;
  }

  public void setComputation(List<Contextualizable> computation) {
    this.computation = computation;
  }

  public void setDescriptionType(Contextualization contextualization) {
    this.contextualization = contextualization;
  }

  @Override
  public String toString() {
    return "(M) " + urn;
  }

  @Override
  public ResolutionInfo getResolutionInfo() {
    return resolutionInfo;
  }

  public void setResolutionInfo(ResolutionInfo resolutionInfo) {
    this.resolutionInfo = resolutionInfo;
  }

  @Override
  public Concept getObserverType() {
    return observerType;
  }

  public void setObserverType(Concept observerType) {
    this.observerType = observerType;
  }

  @Override
  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }
}
