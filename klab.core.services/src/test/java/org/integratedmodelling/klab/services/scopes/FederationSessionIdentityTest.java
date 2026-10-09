package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.application.security.Role;
import org.junit.jupiter.api.Test;

class FederationSessionIdentityTest {
  @Test void currentCredentialAndRolesReplaceCachedAuthorityWithoutMutatingTheCache() {
    var runtime = mock(RuntimeService.class);
    var cached = user("alice", runtime, runtime);
    cached.setRoles(List.of(Role.ROLE_ADMINISTRATOR));
    cached.setPermissions(List.of(CRUDOperation.values()));
    var authorization = new org.integratedmodelling.klab.services.application.security.EngineAuthorization(
        "hub", "alice", "new-credential", java.util.Map.of(), List.of(), List.of(Role.ROLE_USER));
    authorization.setAuthenticated(true);
    var request = cached.forAuthorization(authorization);
    assertEquals("new-credential", request.getUser().getId());
    assertFalse(request.isAuthorized(CRUDOperation.ADMINISTER));
    assertFalse(request.getRoles().contains(Role.ROLE_ADMINISTRATOR));
    assertTrue(cached.isAuthorized(CRUDOperation.ADMINISTER));
    assertEquals("alice-credential", cached.getUser().getId());
    assertSame(cached.getJobManager(), request.getJobManager());
    assertEquals("test.federation", org.integratedmodelling.klab.api.Klab.INSTANCE.getFederationData(request.getUser()).getId());
  }
  private ServiceUserScope user(String username, KlabService service, RuntimeService runtime) {
    var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn(username);
    when(identity.getId()).thenReturn(username + "-credential");
    var data = Parameters.<String>create();
    data.put(UserIdentity.FEDERATION_DATA_PROPERTY, new Federation("test.federation", null));
    when(identity.getData()).thenReturn(data);
    var user = new ServiceUserScope(identity, service);
    user.setId(username);
    user.addService(runtime);
    return user;
  }

  @Test void peersAttributeContextCreationToCallerWithoutMutatingSharedSession() {
    var runtime = mock(RuntimeService.class);
    when(runtime.serviceId()).thenReturn("runtime");
    when(runtime.status()).thenReturn(mock(KlabService.ServiceStatus.class));
    when(runtime.status().isOperational()).thenReturn(true);
    var alice = user("alice", runtime, runtime);
    var bob = user("bob", runtime, runtime);
    alice.setRoles(List.of(Role.ROLE_ADMINISTRATOR));
    alice.setPermissions(List.of(CRUDOperation.values()));
    var registered = new ServiceSessionScope(alice);
    registered.setId("test_federation");
    registered.setName("shared session");
    registered.setHostServiceId("runtime");
    var peer = registered.forRequest(bob);
    when(runtime.declareContextScope(any(), any(), any())).thenAnswer(call -> {
      var requester = call.getArgument(2, ServiceUserScope.class);
      assertSame(bob, requester);
      return DigitalTwin.Configuration.builder().id("test_federation.context")
          .owner(requester.getUser().getUsername()).name("test context").build();
    });
    var context = peer.createContext(DigitalTwin.Configuration.builder().name("test context").build());
    assertEquals("bob", context.getUser().getUsername());
    assertEquals("bob", context.getConfiguration().getOwner());
    assertEquals("bob", context.getParentScope(Scope.Type.USER, ServiceUserScope.class).getUser().getUsername());
    assertFalse(peer.getRoles().contains(Role.ROLE_ADMINISTRATOR));
    assertFalse(peer.isAuthorized(CRUDOperation.ADMINISTER));
    assertEquals("alice", registered.getUser().getUsername());
    assertSame(alice.getIdentity(), registered.getIdentity());
    assertSame(bob.getIdentity(), peer.getIdentity());
    assertNotSame(alice.getJobManager(), peer.getJobManager());
    assertEquals(registered.getId(), peer.getId());
  }

  @Test void membershipDoesNotGrantContextAccessOrPrivateSessionAccess() {
    var runtime = mock(RuntimeService.class);
    var alice = user("alice", runtime, runtime);
    var bob = user("bob", runtime, runtime);
    var session = new ServiceSessionScope(alice);
    session.setId("test_federation");
    assertTrue(ScopeManager.allowsManagedScope(session, bob));
    var context = new ServiceContextScope(session, DigitalTwin.Configuration.builder()
        .id("test_federation.context").owner("alice").accessRights(ResourcePrivileges.empty()).build(), alice.getUser());
    assertFalse(ScopeManager.allowsManagedScope(context, bob));
    context.getConfiguration().getAccessRights().getAllowedUsers().add("bob");
    assertTrue(ScopeManager.allowsManagedScope(context, bob));
    var peer = context.forRequest(bob);
    assertEquals("bob", peer.getUser().getUsername());
    assertEquals("bob", peer.getParentScope(Scope.Type.USER, ServiceUserScope.class).getUser().getUsername());
    assertEquals("alice", peer.getConfiguration().getOwner());
    session.setId("private-session");
    assertFalse(ScopeManager.allowsManagedScope(session, bob));
    session.setId("test_federation");
    session.getData().put(ScopeManager.PERSISTED_SESSION_OWNER, "alice");
    assertFalse(ScopeManager.allowsManagedScope(session, bob));
    var other = user("other", runtime, runtime);
    other.getUser().getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, new Federation("another.federation", null));
    session.getData().remove(ScopeManager.PERSISTED_SESSION_OWNER);
    assertFalse(ScopeManager.allowsManagedScope(session, other));
    var excluded = new ServiceContextScope(session, DigitalTwin.Configuration.builder()
        .id("test_federation.excluded").owner("alice").accessRights(ResourcePrivileges.create("*,!bob")).build(), alice.getUser());
    assertFalse(ScopeManager.allowsManagedScope(excluded, bob));
  }

  @Test void authenticatedLookupAlwaysChecksTheAclBeforeBindingTheCaller() {
    var runtime = mock(RuntimeService.class);
    var alice = user("alice", runtime, runtime);
    var bob = user("bob", runtime, runtime);
    alice.setRoles(List.of(Role.ROLE_ADMINISTRATOR));
    alice.setPermissions(List.of(CRUDOperation.values()));
    var manager = new ScopeManager(runtime);
    manager.registerScope(bob);
    var session = new ServiceSessionScope(alice);
    session.setId("test_federation");
    manager.registerScope(session);
    var authorization = new org.integratedmodelling.klab.services.application.security.EngineAuthorization(
        "hub", "bob", "bob-credential", java.util.Map.of(), List.of(), List.of(Role.ROLE_USER));
    authorization.setAuthenticated(true);
    var peer = manager.getScope(authorization, ServiceSessionScope.class, session.getId(), "runtime");
    assertEquals("bob", peer.getIdentity() instanceof UserIdentity u ? u.getUsername() : null);
    assertFalse(peer.isAuthorized(CRUDOperation.ADMINISTER));
    var configuration = DigitalTwin.Configuration.builder().id("test_federation.context")
        .owner("alice").accessRights(ResourcePrivileges.empty()).build();
    var context = new ServiceContextScope(session, configuration, alice.getUser());
    manager.registerScope(context);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class,
        () -> manager.getScope(authorization, ServiceContextScope.class, context.getId(), "runtime"));
    configuration.getAccessRights().getAllowedUsers().add("bob");
    var connected = manager.getScope(authorization, ServiceContextScope.class, context.getId(), "runtime");
    assertEquals("bob", connected.getUser().getUsername());
    assertEquals("bob", ((UserIdentity) connected.getIdentity()).getUsername());
    assertEquals("bob", connected.within(null).getRootContextScope().getUser().getUsername());
    assertEquals("alice", connected.getConfiguration().getOwner());
    assertFalse(connected.isAuthorized(CRUDOperation.ADMINISTER));
    assertSame(bob.getJobManager(), connected.getJobManager());
    assertEquals("alice", context.getUser().getUsername());
    assertTrue(context.isAuthorized(CRUDOperation.ADMINISTER));
    session.getData().put(ScopeManager.PERSISTED_SESSION_OWNER, "alice");
    assertFalse(ScopeManager.allowsManagedScope(session, bob));
    session.getData().put(ScopeManager.SESSION_FEDERATION, "test.federation");
    assertTrue(ScopeManager.allowsManagedScope(session, bob));
  }

  @Test void collaboratorFirstRecoveryDoesNotTransferThePrivateParentSession() {
    var ownerService = mock(org.integratedmodelling.klab.api.services.KlabService.class);
    var runtime = mock(RuntimeService.class);
    when(runtime.serviceId()).thenReturn("runtime");
    when(runtime.status()).thenReturn(mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class));
    when(runtime.status().isOperational()).thenReturn(true);
    var alice = user("alice", ownerService, runtime);
    var bob = user("bob", ownerService, runtime);
    var manager = new ScopeManager(ownerService);
    manager.registerScope(bob);
    doAnswer(call -> {
      var session = call.getArgument(0, ServiceSessionScope.class);
      manager.registerScope(session);
      return session.getId();
    }).when(ownerService).declareSessionScope(any(), any(), any());
    when(runtime.getConfiguration(eq("alice_agent.context"), any())).thenReturn(
        DigitalTwin.Configuration.builder().id("alice_agent.context").owner("alice")
            .accessRights(ResourcePrivileges.create("bob")).build());
    var authorization = new org.integratedmodelling.klab.services.application.security.EngineAuthorization(
        "hub", "bob", "bob-credential", java.util.Map.of(), List.of(), List.of(Role.ROLE_USER));
    authorization.setAuthenticated(true);
    var connected = manager.getScope(authorization, ServiceContextScope.class, "alice_agent.context", "runtime");
    assertEquals("bob", connected.getUser().getUsername());
    assertEquals("alice", connected.getConfiguration().getOwner());
    var restored = manager.getScope("alice_agent", ServiceSessionScope.class);
    assertEquals("alice", restored.getData().get(ScopeManager.PERSISTED_SESSION_OWNER));
    assertFalse(ScopeManager.allowsManagedScope(restored, bob));
    assertTrue(ScopeManager.allowsManagedScope(restored, alice));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class,
        () -> manager.getScope(authorization, ServiceSessionScope.class, "alice_agent", "runtime"));
  }
}
