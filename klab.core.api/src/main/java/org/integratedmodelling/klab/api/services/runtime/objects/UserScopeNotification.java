package org.integratedmodelling.klab.api.services.runtime.objects;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;

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
    private KlabService.ServiceStatus status;

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

    public KlabService.ServiceStatus getStatus() {
      return status;
    }

    public void setStatus(KlabService.ServiceStatus status) {
      this.status = status;
    }

    public ServiceInfo copy() {
      var ret = new ServiceInfo();
      ret.setId(id);
      ret.setUrl(url);
      ret.setType(type);
      ret.setStatus(status);
      return ret;
    }
  }

  private List<ServiceInfo> services = new ArrayList<>();
  private String emailAddress;
  private boolean localFederation;

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

  public boolean isLocalFederation() {
    return localFederation;
  }

  public void setLocalFederation(boolean localFederation) {
    this.localFederation = localFederation;
  }

  public UserScopeNotification copy() {
    return copy(service -> true);
  }

  public UserScopeNotification withoutLocalServices() {
    return copy(service -> service.getUrl() != null && !Utils.URLs.isLocalHost(service.getUrl()));
  }

  private UserScopeNotification copy(Predicate<ServiceInfo> filter) {
    var ret = new UserScopeNotification();
    ret.setEmailAddress(emailAddress);
    ret.setLocalFederation(localFederation);
    for (var service : services) {
      if (filter.test(service)) {
        ret.getServices().add(service.copy());
      }
    }
    return ret;
  }

  /** Removes all services running on the local host. */
  public void removeLocalServices() {
    services = withoutLocalServices().getServices();
  }
}
