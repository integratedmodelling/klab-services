package org.integratedmodelling.klab.api.digitaltwin.impl;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

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
  private String serviceId;
  private String description;
  private String owner;
  private boolean empty;

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
    this.serviceId = configuration.getServiceId();
  }

  /**
   * Build a scope from a digital twin URL. The ID may not mention the session, which will be added
   * according to conventions.
   *
   * @param url
   * @param scope
   */
  public ConfigurationBuilder(URL url, Scope scope) {
    // set from a full URL with query options that determine what to do if the DT does not exist.
    var query = url.getQuery();
    var path = url.getPath();
    var index = path.lastIndexOf(ServicesAPI.RUNTIME.DIGITAL_TWIN_PREFIX);
    var undex = url.toString().lastIndexOf(ServicesAPI.RUNTIME.DIGITAL_TWIN_PREFIX);
    var serviceUrl = url.toString().substring(0, undex);
    this.url = url;
    this.serverUrl = Utils.URLs.newURL(serviceUrl);
    this.id = path.substring(index + ServicesAPI.RUNTIME.DIGITAL_TWIN_PREFIX.length());
    if (!this.id.contains(".") && scope instanceof UserScope userScope) {
      /*
      Add the default session ID from the scope
       */
      var federation = Klab.INSTANCE.getFederationData(userScope.getUser());
      this.id =
          (federation == null ? userScope.getUser().getUsername() : federation.getId())
                  .replace(".", "_")
              + "."
              + this.id;
    }
    this.createWhenAbsent = true; // default when creating from a URL
    this.persistence = Persistence.IDLE_TIMEOUT;
    this.timeout = 3;
    this.timeoutUnit = TimeUnit.HOURS;
    this.accessRights = ResourcePrivileges.create(scope);
    if (query != null) {
      var options = query.split("&");
      for (var option : options) {
        var parts = option.split("=");
        if (parts.length == 2) {
          switch (parts[0]) {
            case "rights":
              this.accessRights = ResourcePrivileges.create(parts[1]);
              break;
            case "persistence":
              this.persistence = Persistence.valueOf(parts[1]);
          }
        }
      }
    }
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

  public ConfigurationBuilder owner(String owner) {
    this.owner = owner;
    return this;
  }

  public ConfigurationBuilder empty() {
    this.empty = true;
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

  public ConfigurationBuilder serviceId(String id) {
    this.serviceId = id;
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

  public ConfigurationBuilder description(String description) {
    this.description = description;
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
        this.createWhenAbsent,
        this.serviceId,
        this.description,
        this.owner, this.empty);
  }
}
