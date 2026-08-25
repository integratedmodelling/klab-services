package org.integratedmodelling.klab.api.services.impl;

import java.net.URL;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

public abstract class AbstractServiceCapabilities implements KlabService.ServiceCapabilities {

  private String serviceName;
  private String serviceId;
  private String serverId;
  private URL url;
  private Map<String, List<ResourceTransport.Schema>> importSchemata = new HashMap<>();
  private Map<String, List<ResourceTransport.Schema>> exportSchemata = new HashMap<>();
  private List<Extensions.ComponentDescriptor> components = new ArrayList<>();
  private Set<CRUDOperation> permissions = EnumSet.of(CRUDOperation.READ);

  @Override
  public String getServiceName() {
    return this.serviceName;
  }

  @Override
  public String getServiceId() {
    return this.serviceId;
  }

  @Override
  public String getServerId() {
    return this.serverId;
  }

  @Override
  public URL getUrl() {
    return this.url;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public void setServerId(String serverId) {
    this.serverId = serverId;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

  @Override
  public Map<String, List<ResourceTransport.Schema>> getExportSchemata() {
    return exportSchemata;
  }

  @Override
  public Map<String, List<ResourceTransport.Schema>> getImportSchemata() {
    return importSchemata;
  }

  public void setImportSchemata(Map<String, List<ResourceTransport.Schema>> importSchemata) {
    this.importSchemata = importSchemata;
  }

  public void setExportSchemata(Map<String, List<ResourceTransport.Schema>> exportSchemata) {
    this.exportSchemata = exportSchemata;
  }

  @Override
  public Set<CRUDOperation> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<CRUDOperation> permissions) {
    this.permissions = permissions;
  }

  @Override
  public List<Extensions.ComponentDescriptor> getComponents() {
    return components;
  }

  public void setComponents(List<Extensions.ComponentDescriptor> components) {
    this.components = components;
  }
}
