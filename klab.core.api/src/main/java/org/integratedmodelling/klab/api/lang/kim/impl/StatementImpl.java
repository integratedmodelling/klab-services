package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.Collection;
import java.util.List;

/**
 * Base bean for all statements. TODO currently used only in edge cases. Should be used across the
 * codebase.
 */
public class StatementImpl implements Statement {
  private int offsetInDocument;
  private int length;
  private List<Annotation> annotations;
  private String deprecation;
  private boolean deprecated;
  private Collection<Notification> notifications;

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

  public void setOffsetInDocument(int offsetInDocument) {
    this.offsetInDocument = offsetInDocument;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
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

}
