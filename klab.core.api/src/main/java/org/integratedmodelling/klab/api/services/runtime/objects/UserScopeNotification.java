package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.services.KlabService;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Sent by the engine to all services upon service connection to create or refresh a user scope peer
 * for the calling user. The request is sent with the known user token that contains the
 * hub-assigned privileges, so no need for essential user info.
 *
 * <p>TODO eventually may contain options w.r.t. notifications, restriction of privileges, requests
 * for smaller payloads, defaults, transport format etc.
 */
public class UserScopeNotification {

  public static class ServiceInfo {
    private String id;
    private URL url;
    private KlabService.Type type;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public URL getUrl() {
      return url;
    }

    public void setUrl(URL url) {
      this.url = url;
    }

    public KlabService.Type getType() {
      return type;
    }

    public void setType(KlabService.Type type) {
      this.type = type;
    }
  }

  private List<ServiceInfo> services = new ArrayList<>();
  private String emailAddress;

  public List<ServiceInfo> getServices() {
    return services;
  }

  public void setServices(List<ServiceInfo> services) {
    this.services = services;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }
}
