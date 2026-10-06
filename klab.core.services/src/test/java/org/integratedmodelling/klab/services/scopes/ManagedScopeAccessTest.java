package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.Test;

class ManagedScopeAccessTest {
  private UserScope user(String username) {
    var scope = mock(UserScope.class);
    var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn(username);
    when(scope.getUser()).thenReturn(identity);
    return scope;
  }

  @Test void privateContextAllowsOwnerButNotAnotherValidUser() {
    var context = mock(ServiceContextScope.class);
    var config = DigitalTwin.Configuration.builder().owner("alice")
        .accessRights(ResourcePrivileges.create("alice")).build();
    when(context.getConfiguration()).thenReturn(config);
    assertTrue(ScopeManager.allowsManagedScope(context, user("alice")));
    assertFalse(ScopeManager.allowsManagedScope(context, user("bob")));
  }

  @Test void explicitSharingIsPreservedAndSessionOwnershipIsNotBypassed() {
    var context = mock(ServiceContextScope.class);
    when(context.getConfiguration()).thenReturn(DigitalTwin.Configuration.builder()
        .owner("alice").accessRights(ResourcePrivileges.PUBLIC).build());
    assertTrue(ScopeManager.allowsManagedScope(context, user("bob")));
    var session = mock(ServiceSessionScope.class);
    var alice = user("alice").getUser();
    when(session.getUser()).thenReturn(alice);
    assertTrue(ScopeManager.allowsManagedScope(session, user("alice")));
    assertFalse(ScopeManager.allowsManagedScope(session, user("bob")));
  }
}
