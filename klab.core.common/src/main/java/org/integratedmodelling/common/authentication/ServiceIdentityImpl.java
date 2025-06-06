package org.integratedmodelling.common.authentication;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.ServiceIdentity;
import org.integratedmodelling.klab.api.services.runtime.Channel;

import java.net.URL;
import java.util.*;

public class ServiceIdentityImpl extends IdentityImpl implements ServiceIdentity {

  private String name;
  private Date bootTime;
  private Collection<String> urls = new ArrayList<String>();
  private boolean online = false;

  public ServiceIdentityImpl(String id, String name, Date bootTime, List<URL> urls) {
    this.setId(id);
    this.name = name;
    this.bootTime = bootTime;
    for (URL u : urls) {
      this.urls.add(u.toString());
    }

  }
  @Override
  public String getName() {
    return name;
  }

  @Override
  public Date getBootTime() {
    return bootTime;
  }

  @Override
  public Collection<String> getUrls() {
    return urls;
  }

  @Override
  public boolean isOnline() {
    return online;
  }

  @Override
  public boolean stop() {
    return false;
  }

  @Override
  public Channel getMonitor() {
    return null;
  }

  @Override
  public Parameters<String> getState() {
    return null;
  }
}
