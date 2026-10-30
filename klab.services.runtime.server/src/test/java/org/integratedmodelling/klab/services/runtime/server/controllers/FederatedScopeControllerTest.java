package org.integratedmodelling.klab.services.runtime.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class FederatedScopeControllerTest {
  private ServiceUserScope user(String name, RuntimeService runtime) {
    var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn(name);
    when(identity.getId()).thenReturn(name + "-credential");
    when(identity.getGroups()).thenReturn(Set.of());
    var data = Parameters.<String>create();
    data.put(UserIdentity.FEDERATION_DATA_PROPERTY, new Federation("test.federation", null));
    when(identity.getData()).thenReturn(data);
    var user = new ServiceUserScope(identity, runtime);
    user.setId(name);
    return user;
  }

  private Fixture fixture(boolean administrator) throws Exception {
    var runtime = mock(RuntimeService.class);
    when(runtime.serviceId()).thenReturn("runtime");
    var manager = new ScopeManager(runtime);
    when(runtime.getScopeManager()).thenReturn(manager);
    var alice = user("alice", runtime);
    var bob = user("bob", runtime);
    manager.registerScope(alice);
    manager.registerScope(bob);
    var session = spy(new ServiceSessionScope(alice));
    session.setId("test_federation");
    manager.registerScope(session);
    var server = mock(RuntimeServer.class);
    when(server.klabService()).thenReturn(runtime);
    var controller = new RuntimeServerController();
    var field = RuntimeServerController.class.getDeclaredField("runtimeService");
    field.setAccessible(true);
    field.set(controller, server);
    var authorization = new EngineAuthorization("hub", "bob", "bob-credential", Map.of(), List.of(),
        administrator ? List.of(Role.ROLE_ADMINISTRATOR) : List.of(Role.ROLE_USER));
    authorization.setAuthenticated(true);
    authorization.setScope(bob);
    return new Fixture(controller, runtime, session, bob, authorization);
  }

  @Test void defaultSessionReuseIsBoundedToVerifiedMembers() throws Exception {
    var f = fixture(false);
    var request = new ScopeRequest();
    request.setConfiguration(DigitalTwin.Configuration.builder().id("test_federation").build());
    assertEquals("test_federation", f.controller.createSession(request, f.authorization, mock(HttpServletResponse.class), null));
    f.bob.getUser().getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, new Federation("other", null));
    assertThrows(KlabAuthorizationException.class, () -> f.controller.createSession(request, f.authorization, mock(HttpServletResponse.class), null));
  }

  @Test void creatingUnderAlicesSharedSessionUsesBobsIdentityAndOwner() throws Exception {
    var f = fixture(false);
    f.authorization.setScope(f.session.forRequest(f.bob));
    var request = new ScopeRequest();
    request.setConfiguration(DigitalTwin.Configuration.builder().name("new twin").owner("alice").build());
    when(f.runtime.declareContextScope(any(), any(), any())).thenAnswer(call -> {
      var context = call.getArgument(0, ServiceContextScope.class);
      assertEquals("bob", context.getUser().getUsername());
      assertEquals("bob", ((UserIdentity) context.getIdentity()).getUsername());
      assertEquals("bob", context.getConfiguration().getOwner());
      assertEquals("test.federation", context.getConfiguration().getSessionFederationId());
      assertFalse(context.getRoles().contains(Role.ROLE_ADMINISTRATOR));
      context.setId("test_federation.context");
      return context.getConfiguration();
    });
    assertEquals("bob", f.controller.createContext(request, null, f.authorization, null, mock(HttpServletResponse.class)).getOwner());
    assertEquals("alice", f.session.getUser().getUsername());
  }

  @Test void ordinaryMembersCannotReleaseTheWholeSharedSession() throws Exception {
    var f = fixture(false);
    f.authorization.setScope(f.session.forRequest(f.bob));
    assertThrows(KlabAuthorizationException.class, () -> f.controller.closeSession(f.authorization));
    verify(f.session, never()).close();
    var admin = fixture(true);
    var aliceContext = mock(ServiceContextScope.class);
    var bobContext = mock(ServiceContextScope.class);
    when(aliceContext.getId()).thenReturn("test_federation.alice");
    when(bobContext.getId()).thenReturn("test_federation.bob");
    when(aliceContext.getType()).thenReturn(org.integratedmodelling.klab.api.scope.Scope.Type.CONTEXT);
    when(bobContext.getType()).thenReturn(org.integratedmodelling.klab.api.scope.Scope.Type.CONTEXT);
    admin.runtime.getScopeManager().registerScope(aliceContext);
    admin.runtime.getScopeManager().registerScope(bobContext);
    admin.authorization.setScope(admin.session.forRequest(admin.bob));
    assertTrue(admin.controller.closeSession(admin.authorization));
    verify(admin.session).close();
    verify(aliceContext).close();
    verify(bobContext).close();
    assertNull(admin.runtime.getScopeManager().getScope("test_federation", ServiceSessionScope.class));
  }

  @Test void directRuntimeReleaseUsesTheSameAdministratorBoundary() throws Exception {
    var ordinary = fixture(false);
    doCallRealMethod().when(ordinary.runtime).releaseSession(any());
    assertFalse(ordinary.runtime.releaseSession(ordinary.session.forRequest(ordinary.bob)));
    verify(ordinary.session, never()).close();
    var admin = fixture(true);
    doCallRealMethod().when(admin.runtime).releaseSession(any());
    var requester = admin.runtime.getScopeManager().getOrCreateUserScope(admin.authorization);
    assertTrue(admin.runtime.releaseSession(admin.session.forRequest(requester)));
    verify(admin.session).close();
  }

  private record Fixture(RuntimeServerController controller, RuntimeService runtime,
      ServiceSessionScope session, ServiceUserScope bob, EngineAuthorization authorization) {}
}
