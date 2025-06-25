package org.integratedmodelling.klab.api.digitaltwin.impl;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConfigurationBuilder {
  private ResourcePrivileges accessRights;
  private Persistence persistence;
  private String name;
  private String id;
  private long timeout;
  private TimeUnit timeoutUnit;
  private URL url;
  private URL serverUrl;
  private List<Notification> notifications = new ArrayList<>();
  private boolean createWhenAbsent;

  public ConfigurationBuilder() {}

  public ConfigurationBuilder(DigitalTwin.Configuration configuration) {
    this.accessRights = configuration.getAccessRights();
    this.persistence = configuration.getPersistence();
    this.name = configuration.getName();
    this.id = configuration.getId();
    this.timeout = configuration.getTimeout();
    this.timeoutUnit = configuration.getTimeoutUnit();
    this.url = configuration.getUrl();
    this.serverUrl = configuration.getServiceUrl();
    this.notifications.addAll(configuration.getNotifications());
    this.createWhenAbsent = configuration.isCreateWhenAbsent();
  }

  public ConfigurationBuilder accessRights(ResourcePrivileges accessRights) {
    this.accessRights = accessRights;
    return this;
  }

  public ConfigurationBuilder persistence(Persistence persistence) {
    this.persistence = persistence;
    return this;
  }

  public ConfigurationBuilder name(String name) {
    this.name = name;
    return this;
  }

  public ConfigurationBuilder createWhenAbsent(boolean b) {
    this.createWhenAbsent = b;
    return this;
  }

  public ConfigurationBuilder id(String id) {
    this.id = id;
    return this;
  }

  public ConfigurationBuilder url(URL url) {
    this.url = url;
    return this;
  }

  public ConfigurationBuilder serverUrl(URL serverUrl) {
    this.serverUrl = serverUrl;
    return this;
  }

  public ConfigurationBuilder withNotification(Notification notification) {
    this.notifications.add(notification);
    return this;
  }

  public ConfigurationBuilder timeout(long timeout, TimeUnit timeoutUnit) {
    this.timeout = timeout;
    this.timeoutUnit = timeoutUnit;
    return this;
  }

  public DigitalTwin.Configuration build() {
    return new ConfigurationImpl(
        accessRights,
        persistence,
        name,
        id,
        this.timeout,
        this.timeoutUnit,
        this.url,
        this.serverUrl,
        this.notifications,
        this.createWhenAbsent);
  }
}
