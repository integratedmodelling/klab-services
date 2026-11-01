package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.junit.jupiter.api.Test;

class ScopeRuntimeOriginTest {
  @Test
  void matchingRemoteOriginReturnsCallerViewsWithoutChangingRegisteredScopes() {
    var f = fixture(false);
    var context = f.manager.getScope(f.authorization, ServiceContextScope.class,
        f.context.getId(), "runtime-A");
    var session = f.manager.getScope(f.authorization, ServiceSessionScope.class,
        f.session.getId(), "runtime-A");
    assertNotSame(f.context, context);
    assertNotSame(f.session, session);
    assertEquals("runtime-A", context.getHostServiceId());
    assertEquals("runtime-A", session.getHostServiceId());
    assertSame(f.user.getJobManager(), context.getJobManager());
    assertEquals("alice", context.getUser().getUsername());
    assertSame(f.context, f.manager.getScope(f.context.getId(), ServiceContextScope.class));
    assertSame(f.session, f.manager.getScope(f.session.getId(), ServiceSessionScope.class));
  }

  @Test
  void rejectsWrongOriginForCachedContextAndSessionEvenWhenBothRuntimesAreAdvertised() {
    var f = fixture(false);
    f.user.addService(runtime("runtime-B"));
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceContextScope.class, f.context.getId(), "runtime-B"));
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceSessionScope.class, f.session.getId(), "runtime-B"));
    assertEquals("runtime-A", f.context.getHostServiceId());
    assertEquals("runtime-A", f.session.getHostServiceId());
  }

  @Test
  void rejectsMissingAndBlankOriginsForRemoteCachedScopes() {
    var f = fixture(false);
    for (String origin : new String[] {null, "", " "}) {
      assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
          f.authorization, ServiceContextScope.class, f.context.getId(), origin));
      assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
          f.authorization, ServiceSessionScope.class, f.session.getId(), origin));
    }
  }

  @Test
  void cachedOriginMustStillBeAvailableToTheCaller() {
    var f = fixture(false);
    when(f.runtime.status().isOperational()).thenReturn(false);
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceContextScope.class, f.context.getId(), "runtime-A"));
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceSessionScope.class, f.session.getId(), "runtime-A"));
  }

  @Test
  void remoteScopesWithoutARecordedHostFailClosed() {
    var f = fixture(false);
    f.context.setHostServiceId(null);
    f.session.setHostServiceId(null);
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceContextScope.class, f.context.getId(), "runtime-A"));
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceSessionScope.class, f.session.getId(), "runtime-A"));
  }

  @Test
  void localRuntimeAllowsMissingOriginButRejectsAnExplicitDifferentOrigin() {
    var f = fixture(true);
    assertNotNull(f.manager.getScope(f.authorization, ServiceContextScope.class,
        f.context.getId(), null));
    assertNotNull(f.manager.getScope(f.authorization, ServiceSessionScope.class,
        f.session.getId(), null));
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceContextScope.class, f.context.getId(), "runtime-B"));
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceSessionScope.class, f.session.getId(), "runtime-B"));
  }

  @Test
  void directParentSessionReuseAlsoRejectsWrongOrigin() {
    var f = fixture(false);
    f.user.addService(runtime("runtime-B"));
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getOrCreateSessionScope(
        f.session.getId(), f.authorization, f.user, "runtime-B"));
  }

  @Test
  void verifiedColdContextCannotReuseAParentFromAnotherRuntime() {
    var f = fixture(false);
    var other = runtime("runtime-B");
    f.user.addService(other);
    when(other.getConfiguration(eq("alice_agent.restored"), any())).thenReturn(
        DigitalTwin.Configuration.builder().id("alice_agent.restored").owner("alice").build());
    assertThrows(KlabAuthorizationException.class, () -> f.manager.getScope(
        f.authorization, ServiceContextScope.class, "alice_agent.restored", "runtime-B"));
    verify(f.service, never()).declareContextScope(any(), any(), any());
    assertEquals("runtime-A", f.session.getHostServiceId());
  }

  @Test
  void coldSessionDoesNotAcceptAnUnadvertisedRuntime() {
    var f = fixture(false);
    f.manager.releaseScope(f.session.getId());
    assertNull(f.manager.getOrCreateSessionScope("alice", f.authorization, f.user, "unknown"));
    assertNull(f.manager.getOrCreateSessionScope("alice", f.authorization, f.user, null));
    verify(f.service, never()).declareSessionScope(any(), any(), any());
  }

  @Test
  void coldContextUsesOnlyTheAdvertisedOriginAndRejectsMissingOrUnknownOrigins() {
    var f = fixture(false);
    assertNull(f.manager.getScope(f.authorization, ServiceContextScope.class,
        "alice.restored", null));
    assertNull(f.manager.getScope(f.authorization, ServiceContextScope.class,
        "alice.restored", "unknown"));
    when(f.runtime.getConfiguration(eq("alice_agent.restored"), any())).thenReturn(
        DigitalTwin.Configuration.builder().id("alice_agent.restored").owner("alice").build());
    var restored = f.manager.getScope(f.authorization, ServiceContextScope.class,
        "alice_agent.restored", "runtime-A");
    assertNotNull(restored);
    assertEquals("runtime-A", restored.getHostServiceId());
    assertEquals("runtime-A", ((ServiceSessionScope) restored.getParentScope()).getHostServiceId());
    verify(f.runtime).getConfiguration(eq("alice_agent.restored"), any());
  }

  private Fixture fixture(boolean local) {
    var runtime = runtime("runtime-A");
    var service = local ? runtime : mock(KlabService.class);
    if (!local) when(service.serviceId()).thenReturn("resolver");
    var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn("alice");
    when(identity.getData()).thenReturn(Parameters.create());
    var user = new ServiceUserScope(identity, service);
    user.setId("alice");
    user.addService(runtime);
    var manager = new ScopeManager(service);
    manager.registerScope(user);
    var session = new ServiceSessionScope(user);
    // User and session IDs share the same catalog: use a noncanonical parent here.
    session.setId("alice_agent");
    session.setHostServiceId("runtime-A");
    manager.registerScope(session);
    var context = new ServiceContextScope(session, DigitalTwin.Configuration.builder()
        .id("alice_agent.context").owner("alice").build(), identity);
    context.setId("alice_agent.context");
    context.setHostServiceId("runtime-A");
    manager.registerScope(context);
    var authorization = new EngineAuthorization(null, "alice", "token", Map.of(), List.of(), List.of());
    return new Fixture(manager, authorization, user, service, runtime, session, context);
  }

  private RuntimeService runtime(String id) {
    var runtime = mock(RuntimeService.class);
    when(runtime.serviceId()).thenReturn(id);
    var status = mock(KlabService.ServiceStatus.class);
    when(status.isOperational()).thenReturn(true);
    when(runtime.status()).thenReturn(status);
    return runtime;
  }

  private record Fixture(ScopeManager manager, EngineAuthorization authorization,
      ServiceUserScope user, KlabService service, RuntimeService runtime,
      ServiceSessionScope session, ServiceContextScope context) {}
}
