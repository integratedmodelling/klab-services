package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ManagedScopeAccessTest {
  private UserScope user(String username) {
    var scope = mock(UserScope.class);
    var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn(username);
    when(identity.getGroups()).thenReturn(java.util.List.of());
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
    when(session.getData()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
    assertTrue(ScopeManager.allowsManagedScope(session, user("alice")));
    assertFalse(ScopeManager.allowsManagedScope(session, user("bob")));
    session.getData().put(ScopeManager.PERSISTED_SESSION_OWNER, "bob");
    assertFalse(ScopeManager.allowsManagedScope(session, user("alice")));
    assertTrue(ScopeManager.allowsManagedScope(session, user("bob")));
  }

  @TestFactory
  java.util.stream.Stream<DynamicTest> warmContextHonorsUserAndGroupExclusions() {
    return java.util.stream.Stream.of(
      "*;bob;READERS;true", "*,!bob;bob;READERS;false",
      "*,!READERS;bob;READERS;false", "READERS;bob;READERS;true",
      "READERS,!bob;bob;READERS;false", "bob,!READERS;bob;READERS;false",
      "alice;bob;READERS;false", "*,!alice;alice;READERS;true")
        .map(example -> DynamicTest.dynamicTest(example, () -> {
          var fields = example.split(";");
          String rights = fields[0], username = fields[1], groupName = fields[2];
          boolean expected = Boolean.parseBoolean(fields[3]);
          var requester = user(username);
          var group = mock(org.integratedmodelling.klab.api.identities.Group.class);
          when(group.getName()).thenReturn(groupName);
          when(requester.getUser().getGroups()).thenReturn(java.util.List.of(group));
          var context = mock(ServiceContextScope.class);
          when(context.getConfiguration()).thenReturn(DigitalTwin.Configuration.builder()
              .owner("alice").accessRights(ResourcePrivileges.create(rights)).build());
          assertEquals(expected, ScopeManager.allowsManagedScope(context, requester));
        }));
  }

  @Test void missingRequesterIdentityNeverAuthorizesPublicContext() {
    var context = mock(ServiceContextScope.class);
    when(context.getConfiguration()).thenReturn(DigitalTwin.Configuration.builder()
        .owner("alice").accessRights(ResourcePrivileges.PUBLIC).build());
    assertFalse(ScopeManager.allowsManagedScope(context, null));
    assertFalse(ScopeManager.allowsManagedScope(context, user(null)));
    assertFalse(ScopeManager.allowsManagedScope(context, user(" ")));
  }
}
