package org.integratedmodelling.klab.services.application.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;

/** Service-local grants applied after identity verification, independently of transport secrets. */
public final class ServicePermissionPolicy {
  private ServicePermissionPolicy() {}

  public record Grant(Set<Role> roles, Set<CRUDOperation> permissions, boolean localOwner) {
    public Grant {
      roles = Set.copyOf(roles);
      permissions = Set.copyOf(permissions);
    }
  }

  /** The startup issuer must come from authenticated service startup data, not web overrides. */
  public static Grant resolve(boolean localService, Identity owner, String startupIssuer,
      String verifiedIssuer, String verifiedUsername, Collection<Role> verifiedRoles) {
    boolean localOwner = localService
        && owner instanceof UserIdentity user && user.isAuthenticated() && !user.isAnonymous()
        && verifiedUsername != null && verifiedUsername.equals(user.getUsername())
        && startupIssuer != null && !startupIssuer.isBlank() && startupIssuer.equals(verifiedIssuer);
    var roles = EnumSet.noneOf(Role.class);
    roles.addAll(verifiedRoles);
    if (localOwner) {
      roles.add(Role.ROLE_USER);
      roles.add(Role.ROLE_ADMINISTRATOR);
      roles.add(Role.ROLE_DATA_MANAGER);
    }
    var permissions = roles.contains(Role.ROLE_ADMINISTRATOR) || roles.contains(Role.ROLE_SYSTEM)
        ? EnumSet.allOf(CRUDOperation.class) : EnumSet.of(CRUDOperation.READ);
    return new Grant(roles, permissions, localOwner);
  }
}
