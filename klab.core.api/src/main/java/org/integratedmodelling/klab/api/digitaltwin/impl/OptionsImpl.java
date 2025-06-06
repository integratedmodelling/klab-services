package org.integratedmodelling.klab.api.digitaltwin.impl;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.Persistence;

import java.util.concurrent.TimeUnit;

public class OptionsImpl implements DigitalTwin.Options {

  private final ResourcePrivileges accessRights;
  private final Persistence persistence;
  private final String name;
  private final String id;
  private final long timeout;
  private final TimeUnit timeoutUnit;

  OptionsImpl(ResourcePrivileges accessRights, Persistence persistence, String name, String id, long timeout, TimeUnit timeoutUnit) {
    this.accessRights = accessRights;
    this.persistence = persistence;
    this.name = name;
    this.id = id;
    this.timeout = timeout;
    this.timeoutUnit = timeoutUnit;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  @Override
  public TimeUnit getTimeoutUnit() {
    return timeoutUnit;
  }

  @Override
  public ResourcePrivileges getAccessRights() {
    return accessRights;
  }

  @Override
  public Persistence getPersistence() {
    return persistence;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getId() {
    return id;
  }



}
