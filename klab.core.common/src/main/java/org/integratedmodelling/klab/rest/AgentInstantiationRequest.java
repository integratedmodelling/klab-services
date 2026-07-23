package org.integratedmodelling.klab.rest;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

/**
 * Sent to the runtime controller to drive the creation of an agent, with options to bind it to an
 * agent observation and to only respond with compilation.
 */
public class AgentInstantiationRequest {

  private KActorsBehavior behavior;
  private boolean compileOnly;
  private boolean reportJavaCode;
  private long observationId;
  private String suggestedName;

  public KActorsBehavior getBehavior() {
    return behavior;
  }

  public void setBehavior(KActorsBehavior behavior) {
    this.behavior = behavior;
  }

  public boolean isCompileOnly() {
    return compileOnly;
  }

  public void setCompileOnly(boolean compileOnly) {
    this.compileOnly = compileOnly;
  }

  public long getObservationId() {
    return observationId;
  }

  public void setObservationId(long observationId) {
    this.observationId = observationId;
  }

  public String getSuggestedName() {
    return suggestedName;
  }

  public void setSuggestedName(String suggestedName) {
    this.suggestedName = suggestedName;
  }

  public boolean isReportJavaCode() {
    return reportJavaCode;
  }

  public void setReportJavaCode(boolean reportJavaCode) {
    this.reportJavaCode = reportJavaCode;
  }
}
