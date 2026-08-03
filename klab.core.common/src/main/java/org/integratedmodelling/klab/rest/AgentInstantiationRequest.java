package org.integratedmodelling.klab.rest;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * Sent to the runtime controller to drive the creation of an agent, with options to bind it to an
 * agent observation and to only respond with compilation.
 */
public class AgentInstantiationRequest {

  private KActorsBehavior behavior;
  private boolean compileOnly;
  private boolean reportJavaCode;
  private boolean doNotStart;
  private boolean doNotBindSession;
  private boolean doNotBindObservation;
  private long observationId = Observation.UNASSIGNED_ID;
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

  public boolean isDoNotBindSession() {
    return doNotBindSession;
  }

  public void setDoNotBindSession(boolean doNotBindSession) {
    this.doNotBindSession = doNotBindSession;
  }

  public boolean isDoNotBindObservation() {
    return doNotBindObservation;
  }

  public void setDoNotBindObservation(boolean doNotBindObservation) {
    this.doNotBindObservation = doNotBindObservation;
  }

  public void setReportJavaCode(boolean reportJavaCode) {
    this.reportJavaCode = reportJavaCode;
  }

  public boolean isDoNotStart() {
    return doNotStart;
  }

  public void setDoNotStart(boolean doNotStart) {
    this.doNotStart = doNotStart;
  }
}
