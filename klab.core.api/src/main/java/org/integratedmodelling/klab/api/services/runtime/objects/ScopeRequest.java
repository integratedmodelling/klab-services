package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Request for a scope made by the client. The request endpoint specifies which kind of scope is
 * requested. The request service IDs for all services available to the client, which will are
 * expected to have been advertised before this call using the UserScopeNotification sent upon
 * authentication. If any service is unknown, the scope request will fail.
 */
public class ScopeRequest {

  private DigitalTwin.Configuration configuration;
  private Set<String> serviceIds = new HashSet<>();
  private String behaviorUrn;

  public DigitalTwin.Configuration getConfiguration() {
    return configuration;
  }

  public String getBehaviorUrn() {
    return behaviorUrn;
  }

  public void setBehaviorUrn(String behaviorUrn) {
    this.behaviorUrn = behaviorUrn;
  }

  public void setConfiguration(DigitalTwin.Configuration configuration) {
    this.configuration = configuration;
  }

  public Set<String> getServiceIds() {
    return serviceIds;
  }

  public void setServiceIds(Set<String> serviceIds) {
    this.serviceIds = serviceIds;
  }
}
