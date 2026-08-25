package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.authentication.CustomProperty;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.junit.jupiter.api.Test;

class ScopeManagerTest {

  @Test
  void assignsReadOnlyPermissionsByDefault() {
    var scope = createAuthenticatedUserScope(false, List.of());

    assertEquals(Set.of(CRUDOperation.READ), scope.getPermissions());
    assertFalse(scope.isAuthorized(CRUDOperation.ADMINISTER));
  }

  @Test
  void assignsFullPermissionsToLocalAndAdministrativeUsers() {
    assertEquals(
        Set.of(CRUDOperation.values()), createAuthenticatedUserScope(true, List.of()).getPermissions());
    assertEquals(
        Set.of(CRUDOperation.values()),
        createAuthenticatedUserScope(false, List.of(Role.ROLE_ADMINISTRATOR)).getPermissions());
    assertTrue(
        createAuthenticatedUserScope(false, List.of(Role.ROLE_SYSTEM))
            .isAuthorized(CRUDOperation.ADMINISTER));
  }

  @Test
  void reconstructsCanonicalSessionForDefaultLocalFederation() {
    var fixture = fixture(true);

    var session =
        fixture.manager.getScope(
            fixture.authorization,
            SessionScope.class,
            "test_user",
            fixture.runtime.serviceId());

    assertNotNull(session);
    assertEquals("test_user", session.getId());
    verify(fixture.ownerService).declareSessionScope(session, fixture.userScope, null);
  }

  @Test
  void reconstructsAgentSessionOnlyAfterRuntimeVerifiesItsContext() {
    var fixture = fixture(true);
    var contextId = "test_user_agent-1.context-id";
    var configuration =
        DigitalTwin.Configuration.builder()
            .id(contextId)
            .name("agent context")
            .owner("test.user")
            .accessRights(ResourcePrivileges.create("test.user"))
            .serviceId(fixture.runtime.serviceId())
            .build();
    when(fixture.runtime.getConfiguration(contextId, fixture.userScope))
        .thenReturn(configuration);

    var context =
        fixture.manager.getScope(
            fixture.authorization,
            ContextScope.class,
            contextId,
            fixture.runtime.serviceId());

    assertNotNull(context);
    assertEquals(contextId, context.getId());
    var parentSession = (SessionScope) context.getParentScope();
    assertEquals("test_user_agent-1", parentSession.getId());
    verify(fixture.runtime).getConfiguration(contextId, fixture.userScope);
    verify(fixture.ownerService)
        .declareSessionScope(parentSession, fixture.userScope, null);
    verify(fixture.ownerService)
        .declareContextScope(context, parentSession, fixture.userScope);
  }

  @Test
  void rejectsUnverifiedAgentSessionHeader() {
    var fixture = fixture(true);

    var session =
        fixture.manager.getScope(
            fixture.authorization,
            SessionScope.class,
            "test_user_agent-1",
            fixture.runtime.serviceId());

    assertNull(session);
    verify(fixture.ownerService, never())
        .declareSessionScope(any(), any(), any());
  }

  private Fixture fixture(boolean localFederation) {
    var user = mock(UserIdentity.class);
    var ownerService = mock(KlabService.class);
    var runtime = mock(RuntimeService.class);
    var runtimeStatus = mock(KlabService.ServiceStatus.class);
    var authorization =
        new EngineAuthorization(null, "test.user", "token", java.util.Map.of(), List.of(), List.of());

    when(user.getUsername()).thenReturn("test.user");
    when(user.getData()).thenReturn(Parameters.create());
    var groups = localFederation ? Set.of(localFederationGroup()) : Set.<Group>of();
    when(user.getGroups()).thenReturn(groups);
    when(ownerService.serviceId()).thenReturn("resolver-id");
    when(runtime.serviceId()).thenReturn("runtime-id");
    when(runtime.status()).thenReturn(runtimeStatus);
    when(runtimeStatus.isOperational()).thenReturn(true);
    var manager = new ScopeManager(ownerService);
    var userScope = new ServiceUserScope(user, ownerService);
    userScope.setId("test.user");
    userScope.addService(runtime);
    manager.registerScope(userScope);

    return new Fixture(manager, authorization, userScope, ownerService, runtime);
  }

  private ServiceUserScope createAuthenticatedUserScope(boolean local, List<Role> roles) {
    var ownerService = mock(KlabService.class);
    var serviceScope = mock(ServiceScope.class);
    when(ownerService.serviceId()).thenReturn("service-id");
    when(ownerService.serviceName()).thenReturn("test-service");
    when(ownerService.serviceScope()).thenReturn(serviceScope);
    when(serviceScope.getIdentity()).thenReturn(null);
    var authorization =
        new EngineAuthorization(null, "permission.user", "token", java.util.Map.of(), List.of(), roles);
    authorization.setLocal(local);
    return new ScopeManager(ownerService).getOrCreateUserScope(authorization);
  }

  private Group localFederationGroup() {
    var group = mock(Group.class);
    var federationProperty = new CustomProperty();
    federationProperty.setKey("federation.id");
    federationProperty.setValue("true");
    when(group.getName()).thenReturn(Federation.LOCAL_FEDERATION_ID);
    when(group.getCustomProperties()).thenReturn(List.of(federationProperty));
    return group;
  }

  private record Fixture(
      ScopeManager manager,
      EngineAuthorization authorization,
      ServiceUserScope userScope,
      KlabService ownerService,
      RuntimeService runtime) {}
}
