package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.junit.jupiter.api.Test;

/** Cache-hit regression only; mocked origins are not two deployed Runtime acceptance. */
class CachedScopeRuntimeOriginTest {

  @Test void sessionAThenB() { cachedScopeCannotBeSelectedByAnotherRuntimeWithTheSameWireId("runtime-a", "runtime-b", false); }
  @Test void sessionBThenA() { cachedScopeCannotBeSelectedByAnotherRuntimeWithTheSameWireId("runtime-b", "runtime-a", false); }
  @Test void contextAThenB() { cachedScopeCannotBeSelectedByAnotherRuntimeWithTheSameWireId("runtime-a", "runtime-b", true); }
  @Test void contextBThenA() { cachedScopeCannotBeSelectedByAnotherRuntimeWithTheSameWireId("runtime-b", "runtime-a", true); }

  private void cachedScopeCannotBeSelectedByAnotherRuntimeWithTheSameWireId(
      String acceptedRuntime, String otherRuntime, boolean context) {
    var fixture = fixture(acceptedRuntime, context);
    var expectedClass = context ? ContextScope.class : SessionScope.class;
    var accepted = fixture.manager.getScope(
        fixture.authorization, expectedClass, fixture.scope.getId(), acceptedRuntime);
    assertNotNull(accepted);
    assertEquals(acceptedRuntime,
        ((ServiceUserScope) accepted).getHostServiceId());

    assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getScope(
        fixture.authorization, expectedClass, fixture.scope.getId(), otherRuntime));

    assertSame(fixture.scope, fixture.manager.getScope(fixture.scope.getId(), ServiceUserScope.class));
    assertEquals(acceptedRuntime, fixture.scope.getHostServiceId());
    assertNotNull(fixture.manager.getScope(
        fixture.authorization, expectedClass, fixture.scope.getId(), acceptedRuntime));
    verify(fixture.ownerService, never()).declareSessionScope(any(), any(), any());
    verify(fixture.ownerService, never()).declareContextScope(any(), any(), any());
  }

  @Test void directSessionAThenB() { directSessionCacheLookupCannotBypassTheRuntimeOrigin("runtime-a", "runtime-b"); }
  @Test void directSessionBThenA() { directSessionCacheLookupCannotBypassTheRuntimeOrigin("runtime-b", "runtime-a"); }

  private void directSessionCacheLookupCannotBypassTheRuntimeOrigin(String acceptedRuntime, String otherRuntime) {
    var fixture = fixture(acceptedRuntime, false);
    assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getOrCreateSessionScope(
        fixture.scope.getId(), fixture.authorization, fixture.user, otherRuntime));
    assertEquals(acceptedRuntime, fixture.scope.getHostServiceId());
  }

  @Test
  void matchingOriginKeepsDirectSessionRequestAuthority() {
    var fixture = fixture("runtime-a", false);
    var request = fixture.manager.getOrCreateSessionScope(
        fixture.scope.getId(), fixture.authorization, fixture.user, "runtime-a");
    assertNotSame(fixture.scope, request);
    assertEquals("runtime-a", request.getHostServiceId());
    assertEquals("origin.user", request.getUser().getUsername());
    assertSame(fixture.scope, fixture.manager.getScope(fixture.scope.getId(), ServiceSessionScope.class));
  }

  @Test
  void absentOriginDoesNotInventAnOriginOrChangeLegacyCacheLookup() {
    var fixture = fixture("runtime-a", true);
    assertNotNull(fixture.manager.getScope(
        fixture.authorization, ContextScope.class, fixture.scope.getId(), null));
    var session = fixture("runtime-a", false);
    assertNotNull(session.manager.getOrCreateSessionScope(
        session.scope.getId(), session.authorization, session.user, null));
    assertEquals("runtime-a", fixture.scope.getHostServiceId());
  }

  @Test
  void explicitOriginCannotClaimAnUnscopedCachedSessionOrContext() {
    for (boolean context : new boolean[] {false, true}) {
      for (String unknown : new String[] {null, "", " "}) {
        var fixture = fixture(unknown, context);
        var expectedClass = context ? ContextScope.class : SessionScope.class;
        assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getScope(
            fixture.authorization, expectedClass,
            fixture.scope.getId(), "runtime-a"));
        if (!context) {
          assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getOrCreateSessionScope(
              fixture.scope.getId(), fixture.authorization, fixture.user, "runtime-a"));
        }
        assertEquals(unknown, fixture.scope.getHostServiceId());
      }
    }
  }

  @Test
  void blankExplicitOriginFailsEvenWhenTheCachedHostIsAlsoBlank() {
    var fixture = fixture("", false);
    assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getScope(
        fixture.authorization, SessionScope.class, fixture.scope.getId(), ""));
    assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getOrCreateSessionScope(
        fixture.scope.getId(), fixture.authorization, fixture.user, " "));
  }

  @Test
  void userScopeCacheIsNotAHostedRuntimeSession() {
    var fixture = fixture("runtime-a", false);
    assertNotNull(fixture.manager.getScope(
        fixture.authorization, UserScope.class, "origin.user", "runtime-b"));
  }

  @Test
  void matchingOriginDoesNotBypassContextAcl() {
    var fixture = fixture("runtime-a", true, "different.owner");
    assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getScope(
        fixture.authorization, ContextScope.class, fixture.scope.getId(), "runtime-a"));
  }

  @Test
  void hostVerifiedContextCannotReuseParentSessionFromAnotherRuntime() {
    var fixture = fixture("runtime-a", false);
    var otherRuntime = mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    var status = mock(KlabService.ServiceStatus.class);
    when(otherRuntime.serviceId()).thenReturn("runtime-b");
    when(otherRuntime.status()).thenReturn(status);
    when(status.isOperational()).thenReturn(true);
    fixture.user.addService(otherRuntime);
    var configuration = DigitalTwin.Configuration.builder()
        .id("same-session.other-context").name("runtime-b context")
        .owner("origin.user").accessRights(ResourcePrivileges.create("origin.user"))
        .serviceId("runtime-b").build();
    when(otherRuntime.getConfiguration(eq(configuration.getId()), any())).thenReturn(configuration);
    assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getScope(
        fixture.authorization, ContextScope.class, configuration.getId(), "runtime-b"));
    assertEquals("runtime-a", fixture.scope.getHostServiceId());
    assertFalse(fixture.scope.getData().containsKey(ScopeManager.PERSISTED_SESSION_OWNER));
    assertNull(fixture.manager.getScope(configuration.getId(), ServiceContextScope.class));
    verify(fixture.ownerService, never()).declareContextScope(any(), any(), any());
  }

  private Fixture fixture(String runtimeId, boolean context) {
    return fixture(runtimeId, context, "origin.user");
  }

  private Fixture fixture(String runtimeId, boolean context, String contextOwner) {
    var ownerService = mock(KlabService.class);
    when(ownerService.serviceId()).thenReturn("resolver-id");
    var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn("origin.user");
    when(identity.getData()).thenReturn(Parameters.create());
    when(identity.getGroups()).thenReturn(Set.of());
    var user = new ServiceUserScope(identity, ownerService);
    user.setId("origin.user");
    var session = new ServiceSessionScope(user);
    session.setId("same-session");
    session.setHostServiceId(runtimeId);
    ServiceUserScope scope = session;
    if (context) {
      var configuration = DigitalTwin.Configuration.builder()
          .id("same-session.same-context").name("same wire ID in two origins")
          .owner(contextOwner).accessRights(ResourcePrivileges.create(contextOwner))
          .serviceId(runtimeId).build();
      scope = new ServiceContextScope(session, configuration, identity);
      scope.setId(configuration.getId());
      scope.setHostServiceId(runtimeId);
    }
    var manager = new ScopeManager(ownerService);
    manager.registerScope(user);
    manager.registerScope(scope);
    var authorization = new EngineAuthorization(null, "origin.user", "fixture-token", Map.of(), List.of(), List.of());
    return new Fixture(manager, authorization, user, scope, ownerService);
  }

  private record Fixture(ScopeManager manager, EngineAuthorization authorization,
                         ServiceUserScope user, ServiceUserScope scope, KlabService ownerService) {}
}
