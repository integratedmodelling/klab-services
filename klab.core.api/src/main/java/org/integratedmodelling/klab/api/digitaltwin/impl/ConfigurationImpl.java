package org.integratedmodelling.klab.api.digitaltwin.impl;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;

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
  private URL serviceUrl;
  private String owner;

  // for the object mapper, do not remove
  public ConfigurationImpl() {}

  ConfigurationImpl(
      ResourcePrivileges accessRights,
      Persistence persistence,
      String name,
      String id,
      long timeout,
      TimeUnit timeoutUnit,
      URL url,
      URL serviceUrl) {
    this.accessRights = accessRights;
    this.persistence = persistence;
    this.name = name;
    this.id = id;
    this.timeout = timeout;
    this.timeoutUnit = timeoutUnit;
    this.url = url;
    this.serviceUrl = serviceUrl;
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

  public void setAccessRights(ResourcePrivileges accessRights) {
    this.accessRights = accessRights;
  }

  public void setPersistence(Persistence persistence) {
    this.persistence = persistence;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }

  public void setTimeoutUnit(TimeUnit timeoutUnit) {
    this.timeoutUnit = timeoutUnit;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

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

  public long getIdleTimeMs() {
    return idleTimeMs;
  }

  public void setIdleTimeMs(long idleTimeMs) {
    this.idleTimeMs = idleTimeMs;
  }

  @Override
  public URL getServiceUrl() {
    return serviceUrl;
  }

  public void setServiceUrl(URL serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  @Override
  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }
}
