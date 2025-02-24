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
  private Map<KlabService.Type, KlabService.ServiceCapabilities> servicesCapabilities =
      new HashMap<>();
  private Set<String> connectedUsernames = new HashSet<>();

  public static Engine.Status inop() {
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
  public Map<KlabService.Type, KlabService.ServiceCapabilities> getServicesCapabilities() {
    return servicesCapabilities;
  }

  public void setServicesCapabilities(
      Map<KlabService.Type, KlabService.ServiceCapabilities> servicesCapabilities) {
    this.servicesCapabilities = servicesCapabilities;
  }

  @Override
  public Set<String> getConnectedUsernames() {
    return connectedUsernames;
  }

  public void setConnectedUsernames(Set<String> connectedUsernames) {
    this.connectedUsernames = connectedUsernames;
  }
}
