package org.integratedmodelling.common.authentication;

import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.PartnerIdentity;
import org.integratedmodelling.klab.api.identities.UserIdentity;

import java.util.HashSet;
import java.util.Set;

public class PartnerIdentityImpl extends IdentityImpl implements PartnerIdentity {
  private String name;
  private String emailAddress;
  private String address;
  private UserIdentity contactPerson;
  private Set<Group> groups = new HashSet<>();
  private String authenticatingHub;
  private String publicKey;
  private String token;
  private String url;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getEmailAddress() {
    return emailAddress;
  }

  @Override
  public Set<Group> getGroups() {
    return groups;
  }

  public void setGroups(Set<Group> groups) {
    this.groups = groups;
  }

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public UserIdentity getContactPerson() {
    return contactPerson;
  }

  @Override
  public String getAuthenticatingHub() {
    return authenticatingHub;
  }

  @Override
  public String getPublicKey() {
    return publicKey;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public void setContactPerson(UserIdentity contactPerson) {
    this.contactPerson = contactPerson;
  }

  public void setAuthenticatingHub(String authenticatingHub) {
    this.authenticatingHub = authenticatingHub;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public void setToken(String token) {this.token = token; }

  public String getToken() { return this.token; }

  public void setUrl(String url) { this.url = url; }

  public String getUrl() { return this.url; }

  @Override
  public String toString() {
    return name + " (" + emailAddress + ')';
  }
}
