package org.integratedmodelling.klab.api.authentication;

import java.util.Objects;

/**
 * Request bean for the Hub authentication endpoint.
 *
 * TODO these should be synchronized with the older k.LAB API.
 */
public class UserAuthenticationRequest {

  private String username;
  private String password;
  private boolean remote = false;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isRemote() {
    return remote;
  }

  public void setRemote(boolean jwtToken) {
    this.remote = jwtToken;
  }

  @Override
  public int hashCode() {
    return Objects.hash(remote, password, username);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof UserAuthenticationRequest)) {
      return false;
    }
    UserAuthenticationRequest other = (UserAuthenticationRequest) obj;
    return remote == other.remote
        && Objects.equals(password, other.password)
        && Objects.equals(username, other.username);
  }

  @Override
  public String toString() {
    return "UserAuthenticationRequest [username="
        + username
        + ", password="
        + password
        + ", remote="
        + remote
        + "]";
  }
}
