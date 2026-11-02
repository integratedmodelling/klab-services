package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

/** An HTTP-derived scope must not replace the registered root's lifecycle ownership. */
class RuntimeContextReleaseTest {
  private record Fixture(RuntimeService runtime, ScopeManager manager, ServiceContextScope root,
      ServiceContextScope requester, ServiceContextScope.ActivityNotificationSink sink, DigitalTwin twin) {}

  private Fixture fixture() {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    doReturn("release-fixture-runtime").when(runtime).serviceId();
    var manager = mock(ScopeManager.class); doReturn(manager).when(runtime).getScopeManager();
    var user = mock(UserIdentity.class); when(user.getUsername()).thenReturn("alice");
    var session = new ServiceSessionScope(new ServiceUserScope(user, runtime));
    var root = spy(new ServiceContextScope(session,
        DigitalTwin.Configuration.builder().id("s.a").owner("alice").build(), user));
    root.setId("s.a");
    var twin = mock(DigitalTwin.class); root.setDigitalTwin(twin);
    var sink = mock(ServiceContextScope.ActivityNotificationSink.class); root.installActivityNotifications(sink);
    var requester = spy(new ServiceContextScope(root));
    doReturn(runtime).when(root).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    doReturn(runtime).when(requester).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(manager.getScope("s.a", ServiceContextScope.class)).thenReturn(root);
    return new Fixture(runtime, manager, root, requester, sink, twin);
  }

  @Test void derivedReleaseClosesTheRegisteredPublisherOwnerNotTheRequestCopy() {
    var f = fixture();
    assertTrue(f.runtime.releaseContext(f.requester));
    verify(f.root).close(); verify(f.requester, never()).close();
    verify(f.sink).close(); verify(f.twin).dispose();
  }

  @Test void aRegisteredRootCanBeReleasedDirectly() {
    var f = fixture();
    assertTrue(f.runtime.releaseContext(f.root));
    verify(f.root).close(); verify(f.sink).close();
  }

  @Test void missingRegistrationDoesNotDisposeAnUnregisteredCopyOrItsTwin() {
    var f = fixture(); when(f.manager.getScope("s.a", ServiceContextScope.class)).thenReturn(null);
    assertFalse(f.runtime.releaseContext(f.requester));
    verify(f.root, never()).close(); verify(f.requester, never()).close();
    verify(f.twin, never()).dispose(); verify(f.sink, never()).close();
  }

  @Test void unauthorizedRequesterCannotReleaseTheRegisteredOwner() {
    var f = fixture();
    var other = mock(UserIdentity.class); when(other.getUsername()).thenReturn("mallory");
    doReturn(other).when(f.requester).getUser();
    assertFalse(f.runtime.releaseContext(f.requester));
    verify(f.root, never()).close(); verify(f.requester, never()).close();
    verify(f.twin, never()).dispose(); verify(f.sink, never()).close();
  }

  @Test void failureStillClosesTheRootSinkAndDoesNotFallBackToTheCopy() {
    var f = fixture(); doThrow(new IllegalStateException("controlled twin failure")).when(f.twin).dispose();
    assertFalse(f.runtime.releaseContext(f.requester));
    verify(f.root).close(); verify(f.requester, never()).close(); verify(f.sink).close();
  }

  @Test void nullReleaseIsRejected() {
    var f = fixture(); assertFalse(f.runtime.releaseContext(null));
    verify(f.manager, never()).getScope(anyString(), eq(ServiceContextScope.class));
    verify(f.root, never()).close();
  }

  @Test void missingOrBlankContextIdsDoNotReachTheRegistry() {
    for (String id : new String[] {null, "", " "}) {
      var f = fixture(); doReturn(id).when(f.requester).getId();
      assertFalse(f.runtime.releaseContext(f.requester));
      verify(f.manager, never()).getScope(any(), eq(ServiceContextScope.class));
      verify(f.root, never()).close(); verify(f.requester, never()).close();
    }
  }

  @Test void foreignScopeImplementationsCannotSelectAnOwnedRoot() {
    var f = fixture(); var other = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(other.getId()).thenReturn("s.a");
    assertFalse(f.runtime.releaseContext(other));
    verify(f.manager, never()).getScope(any(), eq(ServiceContextScope.class));
    verify(other, never()).close(); verify(f.root, never()).close();
  }

  @Test void explicitContextAclRemainsAnAdmittedReleaseBoundary() {
    var f = fixture(); var user = mock(UserIdentity.class); when(user.getUsername()).thenReturn("bob");
    doReturn(user).when(f.requester).getUser();
    var rights = org.integratedmodelling.klab.api.authentication.ResourcePrivileges.empty();
    rights.setAllowedUsers(java.util.Set.of("bob"));
    doReturn(DigitalTwin.Configuration.builder().id("s.a").owner("alice").accessRights(rights).build())
        .when(f.root).getConfiguration();
    assertTrue(f.runtime.releaseContext(f.requester));
    verify(f.root).close(); verify(f.sink).close(); verify(f.requester, never()).close();
  }

  @Test void releasedRegistryEntryIsNotReplayedAgainstTheRequestCopy() {
    var f = fixture();
    when(f.manager.releaseScope("s.a")).thenAnswer(invocation -> {
      when(f.manager.getScope("s.a", ServiceContextScope.class)).thenReturn(null); return true;
    });
    assertTrue(f.runtime.releaseContext(f.requester));
    assertFalse(f.runtime.releaseContext(f.requester));
    verify(f.root, times(1)).close(); verify(f.sink, times(1)).close();
    verify(f.requester, never()).close(); verify(f.twin, times(1)).dispose();
  }
}
