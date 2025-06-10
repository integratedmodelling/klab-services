package org.integratedmodelling.klab.api.digitaltwin.impl;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.Persistence;

import java.net.URL;
import java.util.concurrent.TimeUnit;

public class OptionsBuilder {
  private ResourcePrivileges accessRights;
  private Persistence persistence;
  private String name;
  private String id;
  private long timeout;
  private TimeUnit timeoutUnit;
  private URL url;

  public OptionsBuilder accessRights(ResourcePrivileges accessRights) {
    this.accessRights = accessRights;
    return this;
  }

  public OptionsBuilder persistence(Persistence persistence) {
    this.persistence = persistence;
    return this;
  }

  public OptionsBuilder name(String name) {
    this.name = name;
    return this;
  }

  public OptionsBuilder id(String id) {
    this.id = id;
    return this;
  }

  public OptionsBuilder url(URL url) {
    this.url = url;
    return this;
  }


  public OptionsBuilder timeout(long timeout, TimeUnit timeoutUnit) {
    this.timeout = timeout;
    this.timeoutUnit = timeoutUnit;
    return this;
  }

  public DigitalTwin.Configuration build() {
    return new ConfigurationImpl(accessRights, persistence, name, id, this.timeout, this.timeoutUnit, this.url);
  }
}
