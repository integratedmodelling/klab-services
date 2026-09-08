package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KimObservationStrategiesImpl extends KlabDocumentImpl<KimObservationStrategy>
    implements KimObservationStrategyDocument {

  private int modelVersion = 2;
  private KimObservationPlan.Source source;
  private List<String> imports = new ArrayList<>();
  private Map<String, Object> coverage = new LinkedHashMap<>();
  private Set<String> referencedNamespaces = new LinkedHashSet<>();

  @Override public int getModelVersion() { return modelVersion; }
  public void setModelVersion(int modelVersion) { this.modelVersion = modelVersion; }
  @Override public KimObservationPlan.Source getSource() { return source; }
  public void setSource(KimObservationPlan.Source source) { this.source = source; }
  @Override public List<String> getImports() { return imports; }
  public void setImports(List<String> imports) { this.imports = imports; }
  @Override public Map<String, Object> getCoverage() { return coverage; }
  public void setCoverage(Map<String, Object> coverage) { this.coverage = coverage; }
  public Set<String> getReferencedNamespaces() { return referencedNamespaces; }
  public void setReferencedNamespaces(Set<String> referencedNamespaces) { this.referencedNamespaces = referencedNamespaces; }

  private List<KimObservationStrategy> statements = new ArrayList<>();
  private List<Annotation> annotations = new ArrayList<>();
  private KlabLanguage language = KlabLanguage.OBSERVATION;

  @Override
  public List<KimObservationStrategy> getStatements() {
    return this.statements;
  }

  public void setStatements(List<KimObservationStrategy> statements) {
    this.statements = statements;
  }

  @Override
  public Set<String> importedNamespaces(boolean withinType) {
    Set<String> ret = new LinkedHashSet<>(imports);
    if (!withinType) ret.addAll(referencedNamespaces);
    return ret;
  }

  @Override
  public List<Annotation> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  @Override
  public KlabLanguage getLanguage() {
    return language;
  }

  public void setLanguage(KlabLanguage language) {
    this.language = language;
  }
}
