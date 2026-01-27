package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KimObservationStrategiesImpl extends KlabDocumentImpl<KimObservationStrategy>
    implements KimObservationStrategyDocument {

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
    Set<String> ret = new HashSet<>();
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
