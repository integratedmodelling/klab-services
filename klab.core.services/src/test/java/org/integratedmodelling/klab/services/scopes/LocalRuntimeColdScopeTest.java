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
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.junit.jupiter.api.Test;

/** Local host restoration without remote advertisements; not persistence integration by itself. */
class LocalRuntimeColdScopeTest {

  @Test
  void hostingRuntimeResolvesItsOwnOriginWithoutUserServiceAdvertisements() {
    var fixture = fixture();
    assertSame(fixture.runtime, fixture.manager.findOriginatingRuntime(fixture.user, "runtime-self"));
  }

  @Test
  void coldOwnerContextCanUseHostAuthorizedConfigurationWithoutReAdvertising() {
    var fixture = fixture();
    var descriptor = DigitalTwin.Configuration.builder().id("session.private")
        .name("persisted private context").owner("cold.owner")
        .accessRights(ResourcePrivileges.create("cold.owner")).serviceId("runtime-self").build();
    when(fixture.runtime.getConfiguration(eq(descriptor.getId()), any())).thenReturn(descriptor);
    var context = fixture.manager.getScope(fixture.authorization, ContextScope.class,
        descriptor.getId(), "runtime-self");
    assertNotNull(context);
    assertEquals("cold.owner", context.getUser().getUsername());
    assertEquals("cold.owner", context.getConfiguration().getOwner());
    assertEquals("runtime-self", ((ServiceContextScope) context).getHostServiceId());
    verify(fixture.runtime).getConfiguration(eq(descriptor.getId()),
        argThat(requester -> "cold.owner".equals(requester.getUser().getUsername())));
    verify(fixture.runtime).declareContextScope(any(), any(),
        argThat(requester -> "cold.owner".equals(requester.getUser().getUsername())));
  }

  @Test
  void wrongExplicitRuntimeDoesNotSelectTheLocalHost() {
    var fixture = fixture();
    assertNull(fixture.manager.getScope(fixture.authorization, ContextScope.class,
        "session.private", "runtime-other"));
    verify(fixture.runtime, never()).getConfiguration(any(), any());
    verify(fixture.runtime, never()).declareContextScope(any(), any(), any());
  }

  @Test
  void missingOriginStillCannotRestoreAContext() {
    var fixture = fixture();
    assertNull(fixture.manager.getScope(fixture.authorization, ContextScope.class,
        "session.private", null));
    verify(fixture.runtime, never()).getConfiguration(any(), any());
  }

  @Test
  void localAuthorizedLookupDenialStillPropagatesWithoutDeclaration() {
    var fixture = fixture();
    when(fixture.runtime.getConfiguration(eq("session.private"), any()))
        .thenThrow(new KlabAuthorizationException("private context denied"));
    assertThrows(KlabAuthorizationException.class, () -> fixture.manager.getScope(
        fixture.authorization, ContextScope.class, "session.private", "runtime-self"));
    verify(fixture.runtime, never()).declareSessionScope(any(), any(), any());
    verify(fixture.runtime, never()).declareContextScope(any(), any(), any());
    assertNull(fixture.manager.getScope("session", ServiceSessionScope.class));
  }

  @Test
  void missingPersistedContextCannotCreateAScope() {
    var fixture = fixture();
    assertNull(fixture.manager.getScope(fixture.authorization, ContextScope.class,
        "session.missing", "runtime-self"));
    verify(fixture.runtime, never()).declareSessionScope(any(), any(), any());
    verify(fixture.runtime, never()).declareContextScope(any(), any(), any());
  }

  @Test
  void lookupCannotSubstituteADifferentContextId() {
    var fixture = fixture();
    when(fixture.runtime.getConfiguration(eq("session.private"), any())).thenReturn(
        DigitalTwin.Configuration.builder().id("session.other").owner("cold.owner").build());
    assertNull(fixture.manager.getScope(fixture.authorization, ContextScope.class,
        "session.private", "runtime-self"));
    verify(fixture.runtime, never()).declareSessionScope(any(), any(), any());
    verify(fixture.runtime, never()).declareContextScope(any(), any(), any());
  }

  @Test
  void selfOriginDoesNotUseAnAdvertisedClientInsteadOfTheAuthorizedLocalLookup() {
    var fixture = fixture();
    var advertised = mock(RuntimeService.class);
    when(advertised.serviceId()).thenReturn("runtime-self");
    fixture.user.addService(advertised);
    assertSame(fixture.runtime, fixture.manager.findOriginatingRuntime(fixture.user, "runtime-self"));
    verify(advertised, never()).getConfiguration(any(), any());
  }

  @Test
  void coldRegisteredRootRetainsItsExactLocalHostForDisposal() {
    var fixture = fixture();
    var descriptor = DigitalTwin.Configuration.builder().id("session.private")
        .name("persisted private context").owner("cold.owner")
        .accessRights(ResourcePrivileges.create("cold.owner")).serviceId("runtime-self").build();
    when(fixture.runtime.getConfiguration(eq(descriptor.getId()), any())).thenReturn(descriptor);
    var twin = mock(DigitalTwin.class);
    doAnswer(call -> {
      var root = (ServiceContextScope) call.getArgument(0);
      root.setDigitalTwin(twin);
      fixture.manager.registerScope(root);
      return descriptor;
    }).when(fixture.runtime).declareContextScope(any(), any(), any());
    assertNotNull(fixture.manager.getScope(fixture.authorization, ContextScope.class,
        descriptor.getId(), "runtime-self"));
    var root = fixture.manager.getScope(descriptor.getId(), ServiceContextScope.class);
    assertNotNull(root);
    assertDoesNotThrow(root::close);
    verify(twin).dispose();
    assertSame(fixture.runtime, root.getService(RuntimeService.class));
  }

  private Fixture fixture() {
    var runtime = mock(RuntimeService.class);
    when(runtime.serviceId()).thenReturn("runtime-self");
    var status = mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);
    when(status.isOperational()).thenReturn(true);
    when(runtime.status()).thenReturn(status);
    var identity = mock(UserIdentity.class);
    when(identity.getUsername()).thenReturn("cold.owner");
    when(identity.getData()).thenReturn(Parameters.create());
    when(identity.getGroups()).thenReturn(Set.of());
    var user = new ServiceUserScope(identity, runtime);
    user.setId("cold.owner");
    var manager = new ScopeManager(runtime);
    manager.registerScope(user);
    var authorization = new EngineAuthorization(null, "cold.owner", "fixture-token", Map.of(), List.of(), List.of());
    return new Fixture(runtime, manager, user, authorization);
  }

  private record Fixture(RuntimeService runtime, ScopeManager manager, ServiceUserScope user,
                         EngineAuthorization authorization) {}
}
