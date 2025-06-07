package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Request for a scope made by the client. The request endpoint specifies which kind of scope is
 * requested. The request may contain the URLs of all the services available to the client, which
 * must be validated to ensure they can cross network boundaries at the server side. Only the
 * services that do not correspond to the hosting service itself will be routinely used, and the
 * service may override with services from its own scope.
 */
public class ScopeRequest {

  private String name;
  private DigitalTwin.Configuration configuration;
  private List<URL> resourceServices = new ArrayList<>();
  private List<URL> resolverServices = new ArrayList<>();
  private List<URL> reasonerServices = new ArrayList<>();
  private List<URL> runtimeServices = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<URL> getResourceServices() {
    return resourceServices;
  }

  public void setResourceServices(List<URL> resourceServices) {
    this.resourceServices = resourceServices;
  }

  public List<URL> getResolverServices() {
    return resolverServices;
  }

  public void setResolverServices(List<URL> resolverServices) {
    this.resolverServices = resolverServices;
  }

  public List<URL> getReasonerServices() {
    return reasonerServices;
  }

  public void setReasonerServices(List<URL> reasonerServices) {
    this.reasonerServices = reasonerServices;
  }

  public List<URL> getRuntimeServices() {
    return runtimeServices;
  }

  public void setRuntimeServices(List<URL> runtimeServices) {
    this.runtimeServices = runtimeServices;
  }

  public DigitalTwin.Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(DigitalTwin.Configuration configuration) {
    this.configuration = configuration;
  }
}
