package org.integratedmodelling.klab.api.lang.kim.impl;

import java.util.*;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class KimObservationStrategyImpl implements KimObservationStrategy {

  private Metadata metadata = Metadata.create();
  private List<Annotation> annotations = new ArrayList<>();
  private int length;
  private int offsetInDocument;
  private String deprecation;
  private boolean deprecated;
  private Collection<Notification> notifications = new ArrayList<>();
  private String urn;
  private String description;
  private Scope scope = Scope.PUBLIC;
  private String namespace;
  private String projectName;
  private int modelVersion = 2;
  private KimObservationPlan.Source source;
  private KimObservationPlan.StrategySelection selection;
  private List<KimObservationPlan.StrategySetup> setup = new ArrayList<>();
  private KimObservationPlan.PlanBody plan;
  private int rank;
  private KnowledgeClass documentClass = KnowledgeClass.OBSERVATION_STRATEGY;
  private Type type;
  private String serviceId;

  public KimObservationStrategyImpl() {}

  @Override
  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  @Override
  public Metadata getMetadata() {
    return this.metadata;
  }

  @Override
  public int getOffsetInDocument() {
    return this.offsetInDocument;
  }

  @Override
  public int getLength() {
    return this.length;
  }

  @Override
  public List<Annotation> getAnnotations() {
    return this.annotations;
  }

  @Override
  public String getDeprecation() {
    return this.deprecation;
  }

  @Override
  public boolean isDeprecated() {
    return this.deprecated;
  }

  @Override
  public Collection<Notification> getNotifications() {
    return this.notifications;
  }

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public String getDescription() {
    return this.description;
  }

  @Override
  public int getRank() {
    return this.rank;
  }

  @Override public int getModelVersion() { return modelVersion; }
  public void setModelVersion(int modelVersion) { this.modelVersion = modelVersion; }
  @Override public KimObservationPlan.Source getSource() { return source; }
  public void setSource(KimObservationPlan.Source source) { this.source = source; }
  @Override public KimObservationPlan.StrategySelection getSelection() { return selection; }
  public void setSelection(KimObservationPlan.StrategySelection selection) { this.selection = selection; }
  @Override public List<KimObservationPlan.StrategySetup> getSetup() { return setup; }
  public void setSetup(List<KimObservationPlan.StrategySetup> setup) { this.setup = setup; }
  @Override public KimObservationPlan.PlanBody getPlan() { return plan; }
  public void setPlan(KimObservationPlan.PlanBody plan) { this.plan = plan; }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public void setOffsetInDocument(int offsetInDocument) {
    this.offsetInDocument = offsetInDocument;
  }

  public void setDeprecation(String deprecation) {
    this.deprecation = deprecation;
  }

  public void setDeprecated(boolean deprecated) {
    this.deprecated = deprecated;
  }

  public void setNotifications(Collection<Notification> notifications) {
    this.notifications = notifications;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setScope(Scope scope) {
    this.scope = scope;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public void setRank(int rank) {
    this.rank = rank;
  }

  @Override
  public Scope getScope() {
    return scope;
  }

  @Override
  public <T> T format(CodeAppender<T> appender) {
    return null;
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  @Override
  public KnowledgeClass getDocumentClass() {
    return documentClass;
  }

  public void setDocumentClass(KnowledgeClass documentClass) {
    this.documentClass = documentClass;
  }

  @Override
  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof KimObservationStrategy kimObservationStrategy)) return false;
    return Objects.equals(urn, kimObservationStrategy.getUrn())
        && Objects.equals(namespace, kimObservationStrategy.getNamespace());
  }

  @Override
  public int hashCode() {
    return Objects.hash(urn, namespace);
  }

}
