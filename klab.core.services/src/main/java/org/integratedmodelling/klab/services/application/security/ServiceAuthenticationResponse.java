package org.integratedmodelling.klab.services.application.security;

import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.rest.AuthenticatedIdentity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Sent by a hub to a node upon authentication. Communicates all groups and the
 * public key for JWT authorization.
 * 
 * @author Ferd
 *
 */
public class ServiceAuthenticationResponse {

	private AuthenticatedIdentity userData;
	private String authenticatingHub;
	private String publicKey;
	private Set<Group> groups = new HashSet<>();

	public AuthenticatedIdentity getUserData() {
		return userData;
	}

	public void setUserData(AuthenticatedIdentity userData) {
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

	public Set<Group> getGroups() {
		return groups;
	}

	public void setGroups(Set<Group> groups) {
		this.groups = groups;
	}

	public ServiceAuthenticationResponse() {
	}

	public ServiceAuthenticationResponse(AuthenticatedIdentity userData, String authenticatingNodeId, Collection<Group> groups, String publicKey) {
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
