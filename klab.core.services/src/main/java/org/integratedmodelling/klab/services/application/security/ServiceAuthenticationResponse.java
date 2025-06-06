package org.integratedmodelling.klab.services.application.security;

import java.util.*;

import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.services.Service;
import org.integratedmodelling.klab.rest.AuthenticatedIdentity;
import org.integratedmodelling.klab.rest.AuthenticatedIdentityImpl;
import org.integratedmodelling.klab.rest.GroupImpl;
import org.integratedmodelling.klab.rest.ServiceReference;

/**
 * Sent by a hub to a node upon authentication. Communicates all groups and the public key for JWT
 * authorization.
 *
 * @author Ferd
 */
public class ServiceAuthenticationResponse {

  private AuthenticatedIdentityImpl userData;
  private String authenticatingHub;
  private String publicKey;
  private Set<GroupImpl> groups = new HashSet<>();
  private List<ServiceReference> services = new ArrayList<>();

  public AuthenticatedIdentityImpl getUserData() {
    return userData;
  }

  public void setUserData(AuthenticatedIdentityImpl userData) {
    this.userData = userData;
  }

  public String getAuthenticatingHub() {
    return authenticatingHub;
  }

  public void setAuthenticatingHub(String authenticatingHubId) {
    this.authenticatingHub = authenticatingHubId;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public Set<GroupImpl> getGroups() {
    return groups;
  }

  public void setGroups(Set<GroupImpl> groups) {
    this.groups = groups;
  }

  public List<ServiceReference> getServices() {
    return services;
  }

  public void setServices(List<ServiceReference> services) {
    this.services = services;
  }

  public ServiceAuthenticationResponse() {}

  public ServiceAuthenticationResponse(
          AuthenticatedIdentityImpl userData,
      String authenticatingNodeId,
      Collection<GroupImpl> groups,
      String publicKey,
      Collection<ServiceReference> services) {
    super();
    this.userData = userData;
    this.authenticatingHub = authenticatingNodeId;
    this.publicKey = publicKey;
    this.groups.addAll(groups);
    this.services.addAll(services);
  }

  @Override
  public String toString() {
    return "NodeAuthenticationResponse [userData=" + userData + "]";
  }
}
