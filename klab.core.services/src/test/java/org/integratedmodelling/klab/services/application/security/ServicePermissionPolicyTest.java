package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.junit.jupiter.api.Test;

class ServicePermissionPolicyTest {
  private UserIdentityImpl owner() {
    var user = new UserIdentityImpl();
    user.setUsername("alice");
    user.setAuthenticated(true);
    user.setAnonymous(false);
    return user;
  }

  @Test void verifiedLocalOwnerReceivesAnImmutableServiceGrant() {
    var grant = ServicePermissionPolicy.resolve(true, owner(), "im", "im", "alice", List.of(Role.ROLE_USER));
    assertTrue(grant.localOwner());
    assertTrue(grant.roles().contains(Role.ROLE_ADMINISTRATOR));
    assertTrue(grant.roles().contains(Role.ROLE_DATA_MANAGER));
    assertEquals(EnumSet.allOf(CRUDOperation.class), grant.permissions());
    assertThrows(UnsupportedOperationException.class, () -> grant.roles().clear());
    assertThrows(UnsupportedOperationException.class, () -> grant.permissions().clear());
  }

  @Test void usernameAloneOrNetworkLocalityCannotEstablishOwnership() {
    var roles = List.of(Role.ROLE_USER);
    for (var grant : List.of(
        ServicePermissionPolicy.resolve(true, owner(), "im", "other", "alice", roles),
        ServicePermissionPolicy.resolve(true, owner(), null, "im", "alice", roles),
        ServicePermissionPolicy.resolve(true, owner(), "im", "im", "bob", roles),
        ServicePermissionPolicy.resolve(false, owner(), "im", "im", "alice", roles),
        ServicePermissionPolicy.resolve(true, null, "im", "im", "alice", roles))) {
      assertFalse(grant.localOwner());
      assertFalse(grant.roles().contains(Role.ROLE_ADMINISTRATOR));
      assertEquals(EnumSet.of(CRUDOperation.READ), grant.permissions());
    }
  }

  @Test void anonymousOrUnauthenticatedStartupIdentityCannotGrantOwnership() {
    var user = owner();
    user.setAnonymous(true);
    assertFalse(ServicePermissionPolicy.resolve(true, user, "im", "im", "alice", List.of()).localOwner());
    user.setAnonymous(false);
    user.setAuthenticated(false);
    assertFalse(ServicePermissionPolicy.resolve(true, user, "im", "im", "alice", List.of()).localOwner());
  }

  @Test void hubAdministratorRetainsItsExistingGrantOnNetworkServices() {
    var grant = ServicePermissionPolicy.resolve(false, owner(), "im", "im", "bob",
        List.of(Role.ROLE_USER, Role.ROLE_ADMINISTRATOR));
    assertFalse(grant.localOwner());
    assertEquals(EnumSet.allOf(CRUDOperation.class), grant.permissions());
  }
}
