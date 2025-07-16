package org.integratedmodelling.klab.services.configuration;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;

import java.util.HashMap;
import java.util.Map;

/** Abstract base class for all service configurations that defines common fields and methods. */
public abstract class ServiceConfiguration {

  private String serviceId;
  private String serviceName;
  private String serviceOwner;
  private String serviceDescription;
  private String serviceDisclaimers;
  private Map<CRUDOperation, ResourcePrivileges> permissions = new HashMap<>();

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getServiceOwner() {
    return serviceOwner;
  }

  public void setServiceOwner(String serviceOwner) {
    this.serviceOwner = serviceOwner;
  }

  public String getServiceDescription() {
    return serviceDescription;
  }

  public void setServiceDescription(String serviceDescription) {
    this.serviceDescription = serviceDescription;
  }

  public String getServiceDisclaimers() {
    return serviceDisclaimers;
  }

  public void setServiceDisclaimers(String serviceDisclaimers) {
    this.serviceDisclaimers = serviceDisclaimers;
  }

  public Map<CRUDOperation, ResourcePrivileges> getPermissions() {
    return permissions;
  }

  public void setPermissions(Map<CRUDOperation, ResourcePrivileges> permissions) {
    this.permissions = permissions;
  }
}
