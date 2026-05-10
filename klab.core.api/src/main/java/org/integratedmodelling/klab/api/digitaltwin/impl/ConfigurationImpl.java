package org.integratedmodelling.klab.api.digitaltwin.impl;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class ConfigurationImpl implements DigitalTwin.Configuration {

  private ResourcePrivileges accessRights;
  private Persistence persistence;
  private String name;
  private String id;
  private long timeout;
  private TimeUnit timeoutUnit;
  private URL url;
  private String behavior;
  //  private long creationTime;
  //  private long idleTimeMs;
  private URL serviceUrl;
  private List<Notification> notifications = new ArrayList<>();
  private boolean createWhenAbsent;
  private String serviceId;
  private String description;
  private Observation observer;
  private Data.ShardingStrategy shardingStrategy = new Data.ShardingStrategy();
  private String owner;

  // for the object mapper, do not remove
  ConfigurationImpl() {}

  ConfigurationImpl(
      ResourcePrivileges accessRights,
      Persistence persistence,
      String name,
      String id,
      long timeout,
      TimeUnit timeoutUnit,
      URL url,
      URL serviceUrl,
      List<Notification> notifications,
      boolean createWhenAbsent,
      String serviceId,
      String description,
      String owner) {
    this.accessRights = accessRights;
    this.persistence = persistence;
    this.name = name;
    this.id = id;
    this.timeout = timeout;
    this.timeoutUnit = timeoutUnit;
    this.url = url;
    this.serviceUrl = serviceUrl;
    this.notifications.addAll(notifications);
    this.createWhenAbsent = createWhenAbsent;
    this.serviceId = serviceId;
    this.description = description;
    this.owner = owner;
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
  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
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
      case SERVICE -> {}
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

  //  public long getCreationTime() {
  //    return creationTime;
  //  }
  //
  //  public void setCreationTime(long creationTime) {
  //    this.creationTime = creationTime;
  //  }
  //
  //  public long getIdleTimeMs() {
  //    return idleTimeMs;
  //  }
  //
  //  public void setIdleTimeMs(long idleTimeMs) {
  //    this.idleTimeMs = idleTimeMs;
  //  }

  @Override
  public URL getServiceUrl() {
    return serviceUrl;
  }

  public void setServiceUrl(URL serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  public List<Notification> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  @Override
  public boolean isCreateWhenAbsent() {
    return createWhenAbsent;
  }

  public void setCreateWhenAbsent(boolean createWhenAbsent) {
    this.createWhenAbsent = createWhenAbsent;
  }

  @Override
  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void defineFromExisting(DigitalTwin.Configuration descriptor) {
    if (descriptor.getAccessRights() != null) {
      this.accessRights = descriptor.getAccessRights();
      if (this.accessRights != null
          && !this.accessRights.toString().equals(descriptor.getAccessRights().toString())) {
        notifications.add(
            Notification.warning("Existing access rights do not match those requested"));
      }
    }
    if (descriptor.getPersistence() != null) {
      this.persistence = descriptor.getPersistence();
      if (this.persistence != null && this.persistence != descriptor.getPersistence()) {
        notifications.add(
            Notification.warning("Existing persistence settings do not match those requested"));
      }
    }
    if (descriptor.getName() != null) {
      this.name = descriptor.getName();
      if (this.name != null && !this.name.equals(descriptor.getName())) {
        notifications.add(Notification.warning("Existing name does not match those requested"));
      }
    }
    if (descriptor.getDescription() != null) {
      this.description = descriptor.getDescription();
    }
    if (descriptor.getUrl() != null) {
      this.url = descriptor.getUrl();
    }
    if (descriptor.getId() != null) {
      this.id = descriptor.getId();
    }
    if (descriptor.getName() != null) {
      this.name = descriptor.getName();
    }
    if (descriptor.getTimeout() > 0) {
      this.timeout = descriptor.getTimeout();
    }
    if (descriptor.getTimeoutUnit() != null) {
      this.timeoutUnit = descriptor.getTimeoutUnit();
    }
    if (descriptor.getServiceUrl() != null) {
      this.serviceUrl = descriptor.getServiceUrl();
    }
  }

  @Override
  public Observation getObserver() {
    return observer;
  }

  public void setObserver(Observation observer) {
    this.observer = observer;
  }

  @Override
  public Data.ShardingStrategy getShardingStrategy() {
    return shardingStrategy;
  }

  public void setShardingStrategy(Data.ShardingStrategy shardingStrategy) {
    this.shardingStrategy = shardingStrategy;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
