package org.integratedmodelling.common.services.client.engine;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EngineStatusImpl extends ServiceStatusImpl implements Engine.Status {

  private Map<KlabService.Type, KlabService.ServiceStatus> servicesStatus = new HashMap<>();
  private Set<String> connectedUsernames = new HashSet<>();

  public static EngineStatusImpl inop() {
    return new EngineStatusImpl();
  }

  @Override
  public Map<KlabService.Type, KlabService.ServiceStatus> getServicesStatus() {
    return servicesStatus;
  }

  public void setServicesStatus(Map<KlabService.Type, KlabService.ServiceStatus> servicesStatus) {
    this.servicesStatus = servicesStatus;
  }

  @Override
  public Set<String> getConnectedUsernames() {
    return connectedUsernames;
  }

  public void setConnectedUsernames(Set<String> connectedUsernames) {
    this.connectedUsernames = connectedUsernames;
  }

  @Override
  public String toString() {

    var ret = "[ENGINE available=" + isAvailable() + ", busy=" + isBusy();
    for (var status : servicesStatus.keySet()) {
      ret += "\n  " + status + "=" + servicesStatus.get(status);
    }
    return ret + "\n]";
  }
}
