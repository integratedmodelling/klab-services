package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.Persistence;

/**
 * Context information returned by the runtime upon request. Only containing the runtime data
 * relative to current status, idle time etc., plus the Configuration object for the static data.
 */
public class ContextInfo {

  private long creationTime;
  private long idleTimeMs;
  private long creditsSoFar;
  private long observationCount;
  private long size;
  private DigitalTwin.Configuration configuration;

  public long getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(long creationTime) {
    this.creationTime = creationTime;
  }

  public long getCreditsSoFar() {
    return creditsSoFar;
  }

  public void setCreditsSoFar(long creditsSoFar) {
    this.creditsSoFar = creditsSoFar;
  }

  public long getIdleTimeMs() {
    return idleTimeMs;
  }

  public void setIdleTimeMs(long idleTimeMs) {
    this.idleTimeMs = idleTimeMs;
  }

  public long getObservationCount() {
    return observationCount;
  }

  public void setObservationCount(long observationCount) {
    this.observationCount = observationCount;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public DigitalTwin.Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(DigitalTwin.Configuration configuration) {
    this.configuration = configuration;
  }
}
