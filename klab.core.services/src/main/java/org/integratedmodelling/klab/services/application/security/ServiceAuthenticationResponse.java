package org.integratedmodelling.klab.services.application.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.rest.AuthenticatedIdentity;
import org.integratedmodelling.klab.rest.AuthenticatedIdentityImpl;
import org.integratedmodelling.klab.rest.GroupImpl;

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

  public ServiceAuthenticationResponse() {}

  public ServiceAuthenticationResponse(
          AuthenticatedIdentityImpl userData,
      String authenticatingNodeId,
      Collection<GroupImpl> groups,
      String publicKey) {
    super();
    this.userData = userData;
    this.authenticatingHub = authenticatingNodeId;
    this.publicKey = publicKey;
    this.groups.addAll(groups);
  }

  @Override
  public String toString() {
    return "NodeAuthenticationResponse [userData=" + userData + "]";
  }
}
