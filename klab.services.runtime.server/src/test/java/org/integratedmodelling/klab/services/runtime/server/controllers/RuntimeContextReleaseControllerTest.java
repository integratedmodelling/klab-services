package org.integratedmodelling.klab.services.runtime.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.junit.jupiter.api.Test;

/** The existing authenticated HTTP route must use the canonical product lifecycle. */
class RuntimeContextReleaseControllerTest {
  private record Fixture(RuntimeServerController controller, RuntimeService runtime,
      EngineAuthorization authorization, ContextScope requestScope) {}
  private Fixture fixture() throws Exception {
    var runtime = mock(RuntimeService.class); var server = mock(RuntimeServer.class);
    when(server.klabService()).thenReturn(runtime);
    var controller = new RuntimeServerController();
    var field = RuntimeServerController.class.getDeclaredField("runtimeService");
    field.setAccessible(true); field.set(controller, server);
    var authorization = mock(EngineAuthorization.class); var scope = mock(ContextScope.class);
    when(authorization.getScope(ContextScope.class)).thenReturn(scope);
    return new Fixture(controller, runtime, authorization, scope);
  }

  @Test void authorizedRouteDelegatesToTheRegisteredRootLifecycle() throws Exception {
    var f = fixture(); when(f.runtime.releaseContext(f.requestScope)).thenReturn(true);
    assertTrue(f.controller.closeContext(f.authorization));
    verify(f.runtime).releaseContext(f.requestScope); verify(f.requestScope, never()).close();
  }
  @Test void productRefusalIsNotReportedAsSuccessfulRelease() throws Exception {
    var f = fixture(); when(f.runtime.releaseContext(f.requestScope)).thenReturn(false);
    assertFalse(f.controller.closeContext(f.authorization));
    verify(f.runtime).releaseContext(f.requestScope); verify(f.requestScope, never()).close();
  }
  @Test void missingAuthorizedContextDoesNotCloseAnything() throws Exception {
    var f = fixture(); when(f.authorization.getScope(ContextScope.class)).thenReturn(null);
    assertFalse(f.controller.closeContext(f.authorization));
    verifyNoInteractions(f.runtime, f.requestScope);
  }
  @Test void absentOrForeignPrincipalDoesNotReachProductLifecycle() throws Exception {
    var f = fixture(); assertFalse(f.controller.closeContext(null));
    assertFalse(f.controller.closeContext(() -> "foreign"));
    verifyNoInteractions(f.runtime, f.requestScope);
  }
  @Test void productFailureDoesNotFallBackToClosingTheRequestCopy() throws Exception {
    var f = fixture(); var error = new IllegalStateException("controlled lifecycle failure");
    when(f.runtime.releaseContext(f.requestScope)).thenThrow(error);
    assertSame(error, assertThrows(IllegalStateException.class, () -> f.controller.closeContext(f.authorization)));
    verify(f.requestScope, never()).close();
  }
  @Test void publicRouteRemainsTheExistingReleaseEndpoint() throws Exception {
    var mapping = RuntimeServerController.class.getMethod("closeContext", java.security.Principal.class)
        .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
    assertArrayEquals(new String[] {org.integratedmodelling.klab.api.ServicesAPI.RELEASE_CONTEXT}, mapping.value());
  }
}
