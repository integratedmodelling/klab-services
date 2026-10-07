package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuntimeContextAuthorizationTest {
  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
      "*;bob;READERS;true", "*,!bob;bob;READERS;false",
      "*,!READERS;bob;READERS;false", "READERS;bob;READERS;true",
      "READERS,!bob;bob;READERS;false", "bob,!READERS;bob;READERS;false",
      "alice;bob;READERS;false", "*,!alice;alice;READERS;true"})
  void configurationLookupHonorsActualAcl(String rights, String username,
      String groupName, boolean expected) {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var manager = mock(ScopeManager.class);
    doReturn(manager).when(runtime).getScopeManager();
    var context = mock(ContextScope.class);
    var configuration = DigitalTwin.Configuration.builder().id("s.c").owner("alice")
        .accessRights(ResourcePrivileges.create(rights)).build();
    when(context.getConfiguration()).thenReturn(configuration);
    when(manager.getScope("s.c", ContextScope.class)).thenReturn(context);
    var user = mock(UserIdentity.class);
    var group = mock(Group.class);
    when(user.getUsername()).thenReturn(username);
    when(group.getName()).thenReturn(groupName);
    when(user.getGroups()).thenReturn(List.of(group));
    var requester = mock(UserScope.class);
    when(requester.getUser()).thenReturn(user);
    assertEquals(expected ? configuration : null, runtime.getConfiguration("s.c", requester));
  }
}
