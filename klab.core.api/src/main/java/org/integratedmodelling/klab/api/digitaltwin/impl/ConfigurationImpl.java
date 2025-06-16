package org.integratedmodelling.klab.api.digitaltwin.impl;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;

import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConfigurationImpl implements DigitalTwin.Configuration {

  private ResourcePrivileges accessRights;
  private Persistence persistence;
  private String name;
  private String id;
  private long timeout;
  private TimeUnit timeoutUnit;
  private URL url;
  private String behavior;
  private long creationTime;
  private long idleTimeMs;
  private long creditsUsed;
  private long observations;
  private long size;
  private String owner;
  private int connectedUsers;

  // for the object mapper, do not remove
  ConfigurationImpl() {}

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
  public DigitalTwin.Configuration validate(Scope scope) throws KlabValidationException {

    var statedScopeID = this.id;

    /*
    is URL filled in?
     */
    if (url != null) {

      /*
      Do we have a scope ID? Was the URL specifying a different one?
       */
      if (url.getPath().startsWith(ServicesAPI.RUNTIME.DIGITAL_TWIN_PREFIX)) {
        var urlId = url.getPath().substring(ServicesAPI.RUNTIME.DIGITAL_TWIN_PREFIX.length());
        if (statedScopeID != null && !statedScopeID.equals(urlId)) {
          throw new KlabValidationException("URL path and scope ID do not match");
        } else {
          this.id = statedScopeID = urlId;
        }
      }
    }

    switch (scope.getType()) {
      case SESSION -> {}
      case CONTEXT -> {}
      case USER -> {}
      default ->
          throw new KlabValidationException(
              "Cannot validate a digital twin configuration against a scope of type: "
                  + scope.getType());
    }

    /*
    Are we asking for rights that are incompatible with the scope?
     */
    if (this.accessRights == null) {}

    return this;
  }

  @Override
  public URL getUrl() {
    return url;
  }
}
