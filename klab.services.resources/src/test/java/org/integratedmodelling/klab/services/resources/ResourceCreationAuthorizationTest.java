package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class ResourceCreationAuthorizationTest {
  @Test void missingOrReadOnlyScopeCannotCreateProjectAndUsesExplicitDenialType() {
    var provider = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var user = mock(ServiceUserScope.class);
    when(user.isAuthorized(CRUDOperation.READ)).thenReturn(true);
    assertThrows(KlabAuthorizationException.class,
        () -> provider.createProject("workspace", "new-project", null));
    assertThrows(KlabAuthorizationException.class,
        () -> provider.createProject("workspace", "new-project", user));
    verify(user).isAuthorized(CRUDOperation.CREATE);
    verify(user).isAuthorized(CRUDOperation.ADMINISTER);
  }

  @Test void projectOperationDenialIsExplicitAndAllowedGrantIsUnchanged() {
    var provider = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var user = mock(ServiceUserScope.class);
    doReturn(false).when(provider).canProjectOperation("project", CRUDOperation.CREATE, user);
    assertThrows(KlabAuthorizationException.class,
        () -> provider.requireProjectOperation("project", CRUDOperation.CREATE, user));
    doReturn(true).when(provider).canProjectOperation("project", CRUDOperation.CREATE, user);
    assertDoesNotThrow(() -> provider.requireProjectOperation("project", CRUDOperation.CREATE, user));
    verify(provider, times(2)).canProjectOperation("project", CRUDOperation.CREATE, user);
  }
}
