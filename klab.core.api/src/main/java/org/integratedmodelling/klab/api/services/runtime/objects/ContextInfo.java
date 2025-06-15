package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.Persistence;

public class ContextInfo {

  private String name;
  private String id;
  private String behavior;
  private long creationTime;
  private long idleTimeMs;
  private Persistence persistence;
  private long creditsSoFar;
  private long observations;
  private long size;
  private String user;
  private DigitalTwin.Configuration configuration;

  public String getBehavior() {
    return behavior;
  }

  public void setBehavior(String behavior) {
    this.behavior = behavior;
  }

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

  public Persistence getPersistence() {
    return persistence;
  }

  public void setPersistence(Persistence expiration) {
    this.persistence = expiration;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public long getIdleTimeMs() {
    return idleTimeMs;
  }

  public void setIdleTimeMs(long idleTimeMs) {
    this.idleTimeMs = idleTimeMs;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getObservations() {
    return observations;
  }

  public void setObservations(long observations) {
    this.observations = observations;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public DigitalTwin.Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(DigitalTwin.Configuration configuration) {
    this.configuration = configuration;
  }
}
