package org.integratedmodelling.klab.api.authentication;

import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Permissions in k.LAB are either "*" for public and/or a list of comma-separated groups
 * (uppercase) and/or usernames (lowercase). An empty permission string means "owner only" (and
 * possibly admin, left to implementations). Prefixing either with a ! denies the permission for the
 * user or group (supposedly to narrow a previous more general one: e.g. *,!BADGUYS).
 *
 * <p>This class parses a permission string and has methods to establish authorization given a
 * username and a set of groups.
 */
public class ResourcePrivileges implements Serializable {

  private boolean isPublic;
  private Set<String> allowedGroups = new HashSet<>();
  private Set<String> excludedGroups = new HashSet<>();
  private Set<String> allowedUsers = new HashSet<>();
  private Set<String> excludedUsers = new HashSet<>();
  private Set<String> allowedServices = new HashSet<>();

  /** Use this constant instead of building an object to define publicly accessible resources. */
  public static ResourcePrivileges PUBLIC;

  static {
    PUBLIC = new ResourcePrivileges();
    PUBLIC.setPublic(true);
  }

  public ResourcePrivileges() {}

  private ResourcePrivileges(String s) {
    if (s != null && !s.isEmpty()) {
      String[] ss = s.split(",");
      for (String token : ss) {
        token = token.trim();
        if ("*".equals(token)) {
          this.isPublic = true;
        } else {
          if (!token.equals(token.toUpperCase())) {
            // lowercase
            if (token.startsWith("!")) {
              this.excludedUsers.add(token.substring(1));
            } else {
              this.allowedUsers.add(token);
            }
          } else {
            if (token.startsWith("!")) {
              this.excludedGroups.add(token.substring(1));
            } else {
              this.allowedGroups.add(token);
            }
          }
        }
      }
    }
  }

  /**
   * Create an empty permission object (to add to if wished). Its toString() method will produce the
   * permission string. Note that empty permissions don't prevent access to the owner and (possibly)
   * a root administrator.
   *
   * @return
   */
  public static ResourcePrivileges empty() {
    return new ResourcePrivileges(null);
  }

  public boolean invalid() {
    return !isPublic && allowedUsers.isEmpty() && allowedGroups.isEmpty();
  }

  /**
   * Create a permission object from a string.
   *
   * @param permissions
   * @return
   */
  public static ResourcePrivileges create(String permissions) {
    return new ResourcePrivileges(permissions);
  }

  public static ResourcePrivileges create(Scope scope) {

    if (scope instanceof UserScope userScope) {
      if (!userScope.getUser().isAnonymous() && userScope.getUser().isAuthenticated()) {
        return create(userScope.getUser().getUsername());
      }
    }

    return empty();
  }

  public boolean checkAuthorization(Scope scope) {

    // Only way to get a ServiceScope here is if the same service is requesting the resource. This
    // also
    // covers scope == null, which is OK if the resource is public.
    if (isPublic || scope instanceof ServiceScope) {
      return true;
    }

    if (scope instanceof UserScope userScope) {
      return checkAuthorization(userScope.getUser().getUsername(), userScope.getUser().getGroups());
    }

    return true;
  }

  public boolean checkAuthorization(String username, Collection<Group> groups) {
    boolean authorized = isPublic;
    if (!authorized) {
      authorized = allowedUsers.contains(username);
    }
    if (!authorized) {
      for (var group : groups) {
        if (allowedGroups.contains(group.getName())) {
          authorized = true;
          break;
        }
      }
    }

    boolean prevented = false;
    if (authorized) {
      // check if prevented
      prevented = excludedUsers.contains(username);
      if (!prevented) {
        for (var group : groups) {
          if (excludedGroups.contains(group.getName())) {
            prevented = true;
            break;
          }
        }
      }
    }

    return authorized && !prevented;
  }

  public void setAllowedGroups(Set<String> allowedGroups) {
    this.allowedGroups = allowedGroups;
  }

  public void setExcludedGroups(Set<String> excludedGroups) {
    this.excludedGroups = excludedGroups;
  }

  public void setAllowedUsers(Set<String> allowedUsers) {
    this.allowedUsers = allowedUsers;
  }

  public void setExcludedUsers(Set<String> excludedUsers) {
    this.excludedUsers = excludedUsers;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public void setPublic(boolean isPublic) {
    this.isPublic = isPublic;
  }

  public Set<String> getAllowedGroups() {
    return allowedGroups;
  }

  public Set<String> getExcludedGroups() {
    return excludedGroups;
  }

  public Set<String> getAllowedUsers() {
    return allowedUsers;
  }

  public Set<String> getExcludedUsers() {
    return excludedUsers;
  }

  @Override
  public String toString() {
    return encode();
  }

  private String encode() {
    StringBuffer buffer = new StringBuffer(256);
    if (isPublic) buffer.append("*");
    for (String group : allowedGroups) {
      buffer.append(buffer.isEmpty() ? "" : ",").append(group);
    }
    for (String user : allowedUsers) {
      buffer.append(buffer.isEmpty() ? "" : ",").append(user);
    }
    for (String group : excludedGroups) {
      buffer.append(buffer.isEmpty() ? "" : ",").append("!").append(group);
    }
    for (String user : excludedUsers) {
      buffer.append(buffer.isEmpty() ? "" : ",").append("!").append(user);
    }
    return buffer.toString();
  }

  public Set<String> getAllowedServices() {
    return allowedServices;
  }

  public void setAllowedServices(Set<String> allowedServices) {
    this.allowedServices = allowedServices;
  }

  /**
   * This is returned by the API and should only show the privileges that are not beyond the passed
   * scope's pay grade.
   *
   * @param scope
   * @return
   */
  public ResourcePrivileges asSeenByScope(Scope scope) {
    // TODO filter privileges to those visible by the scope
    return this;
  }
}
