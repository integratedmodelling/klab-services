package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

/** Mutable transport bean for applicability and ordered relationship endpoints. */
public class ApplicableConceptImpl implements KimConceptStatement.ApplicableConcept {
  private KimConcept source, target, originalObservable;

  public ApplicableConceptImpl() {}

  public ApplicableConceptImpl(KimConcept source, KimConcept target) {
    this.source = source;
    this.target = target;
  }

  public KimConcept getSource() {
    return source;
  }

  public void setSource(KimConcept value) {
    source = value;
  }

  public KimConcept getTarget() {
    return target;
  }

  public void setTarget(KimConcept value) {
    target = value;
  }

  public KimConcept getOriginalObservable() {
    return originalObservable;
  }

  public void setOriginalObservable(KimConcept value) {
    originalObservable = value;
  }
}
