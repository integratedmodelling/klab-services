package org.integratedmodelling.common.authentication;

import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.UserIdentity;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class UserIdentityImpl extends IdentityImpl implements UserIdentity {

  private String username;
  private Set<Group> groups = new HashSet<>();
  private boolean anonymous;
  private boolean online;
  private String serverUrl;
  private String emailAddress;
  private String firstName;
  private String lastName;
  private String initials;
  private String affiliation;
  private String comment;
  private Date lastLogin;

  @Override
  public String getUsername() {
    return this.username;
  }

  @Override
  public Set<Group> getGroups() {
    return this.groups;
  }

  @Override
  public boolean isAnonymous() {
    return this.anonymous;
  }

  @Override
  public boolean isOnline() {
    return this.online;
  }

  @Override
  public String getServerURL() {
    return this.serverUrl;
  }

  @Override
  public String getEmailAddress() {
    return this.emailAddress;
  }

  @Override
  public String getFirstName() {
    return this.firstName;
  }

  @Override
  public String getLastName() {
    return this.lastName;
  }

  @Override
  public String getInitials() {
    return this.initials;
  }

  @Override
  public String getAffiliation() {
    return this.affiliation;
  }

  @Override
  public String getComment() {
    return this.comment;
  }

  @Override
  public Date getLastLogin() {
    return this.lastLogin;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setGroups(Set<Group> groups) {
    this.groups = groups;
  }

  public void setAnonymous(boolean anonymous) {
    this.anonymous = anonymous;
  }

  public void setOnline(boolean online) {
    this.online = online;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public void setServerUrl(String serverUrl) {
    this.serverUrl = serverUrl;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public void setInitials(String initials) {
    this.initials = initials;
  }

  public void setAffiliation(String affiliation) {
    this.affiliation = affiliation;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public void setLastLogin(Date lastLogin) {
    this.lastLogin = lastLogin;
  }

  @Override
  public String toString() {
    return username + " (" + emailAddress + ')';
  }
}
