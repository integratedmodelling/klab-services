package org.integratedmodelling.klab.services.resolver.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ResolverControllerScopeTest {
  @Test
  void unavailableContextProducesRecoveryDiagnosticBeforeResolution() {
    var authorization = mock(EngineAuthorization.class);
    var controller = new ResolverController();
    var error = assertThrows(ResponseStatusException.class,
        () -> controller.resolveObservation(new ResolutionRequest(), authorization));
    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    assertTrue(error.getReason().contains("Reconnect"));
  }

  @Test
  void validContextIsPreservedWithoutFabricatingAScope() {
    var authorization = mock(EngineAuthorization.class);
    var scope = mock(ContextScope.class);
    when(authorization.getScope(ContextScope.class)).thenReturn(scope);
    assertSame(scope, ResolverController.requireContext(authorization));
  }
}
