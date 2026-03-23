package org.integratedmodelling.common.services.client.engine;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;

import java.util.*;

public class EngineStatusImpl extends ServiceStatusImpl implements Engine.Status {

  private Map<KlabService.Type, KlabService.ServiceStatus> servicesStatus = new HashMap<>();
  private Set<String> connectedUsernames = new HashSet<>();
  private Map<KlabService.Type, ServiceProvision> servicesProvision = new HashMap<>();
  private EngineCondition condition = EngineCondition.INOPERATIVE;
  private Set<Distribution.Product.Type> activeAuxiliaryServices =
      EnumSet.noneOf(Distribution.Product.Type.class);

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
  public Map<KlabService.Type, ServiceProvision> getServicesProvision() {
    return servicesProvision;
  }

  public void setServicesProvision(Map<KlabService.Type, ServiceProvision> servicesProvision) {
    this.servicesProvision = servicesProvision;
  }

  @Override
  public Set<Distribution.Product.Type> getActiveAuxiliaryServices() {
    return activeAuxiliaryServices;
  }

  @Override
  public EngineCondition getCondition() {
    return condition;
  }

  public void setCondition(EngineCondition condition) {
    this.condition = condition;
  }

  @Override
  public String toString() {

    StringBuilder ret = new StringBuilder("[ENGINE " + condition + " (");
    int i = 0;
    for (var type : servicesProvision.keySet()) {
      ret.append(i++ == 0 ? "" : ",").append(type).append("=").append(servicesProvision.get(type));
    }
    return ret.append(")]").toString();
  }
}
