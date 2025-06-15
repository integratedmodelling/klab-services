package org.integratedmodelling.klab.api.digitaltwin.impl;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.scope.Persistence;

import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConfigurationImpl implements DigitalTwin.Configuration {

  private final ResourcePrivileges accessRights;
  private final Persistence persistence;
  private final String name;
  private final String id;
  private final long timeout;
  private final TimeUnit timeoutUnit;
  private final URL url;
  private String behavior;
  private long creationTime;
  private long idleTimeMs;
  private long creditsUsed;
  private long observations;
  private long size;
  private String owner;
  private int connectedUsers;

  ConfigurationImpl(
      ResourcePrivileges accessRights,
      Persistence persistence,
      String name,
      String id,
      long timeout,
      TimeUnit timeoutUnit,
      URL url) {
    this.accessRights = accessRights;
    this.persistence = persistence;
    this.name = name;
    this.id = id;
    this.timeout = timeout;
    this.timeoutUnit = timeoutUnit;
    this.url = url;
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

  @Override
  public DigitalTwin.Configuration validate() throws KlabValidationException {

    /*
    is URL filled in?
     */
    if (url != null) {

    /*
    Do we have a scope ID? Was the URL specifying a different one?
     */

    }


    /*
    Are we asking for rights that are incompatible with the scope?
     */

    return this;
  }

  @Override
  public URL getUrl() {
    return url;
  }
}
