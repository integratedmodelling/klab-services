package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.ui.ApplicationUiManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApplicationUiControllerTest {
  @Test
  void ticketBindsPresentedServiceCredentialRatherThanPrincipalUpstreamToken() {
    var manager = mock(ApplicationUiManager.class);
    var user = mock(EngineAuthorization.class);
    when(user.getToken()).thenReturn("upstream-hub-jwt");
    var request = mock(HttpServletRequest.class);
    when(request.getRequestURL())
        .thenReturn(new StringBuffer("https://runtime.test/runtime/api/v1/ui/instances/id/ticket"));
    when(request.getHeader("Origin")).thenReturn("https://runtime.test");
    when(request.getHeader("Authorization")).thenReturn("Bearer webui_presented");
    new ApplicationUiController(manager).ticket("id", user, request);
    verify(manager).ticket("id", "https://runtime.test", user, "webui_presented");
    when(request.getHeader("Origin")).thenReturn("https://attacker.test");
    assertThrows(
        ResponseStatusException.class,
        () -> new ApplicationUiController(manager).ticket("id", user, request));
  }
}
