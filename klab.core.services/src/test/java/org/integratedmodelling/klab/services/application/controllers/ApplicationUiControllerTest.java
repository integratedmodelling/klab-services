package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.ui.ApplicationUiManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApplicationUiControllerTest {
  @Test void localHeadersAreRetainedOnlyForVerifiedLoopbackRequests() {
    var manager = mock(ApplicationUiManager.class); var user = mock(EngineAuthorization.class);
    when(user.isLocal()).thenReturn(true);
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setScheme("http"); request.setServerName("localhost"); request.setServerPort(8094);
    request.setRequestURI("/runtime/api/v1/ui/instances/id/ticket");
    request.setRemoteAddr("127.0.0.1"); request.addHeader("Authorization", "local-token");
    request.addHeader(org.integratedmodelling.klab.api.ServicesAPI.SERVER_KEY_HEADER, "local-test-key");
    var controller = new ApplicationUiController(manager); controller.ticket("id", user, request);
    verify(manager).ticket("id", "http://localhost:8094", user, "local-token",
        java.util.Map.of(org.integratedmodelling.klab.api.ServicesAPI.SERVER_KEY_HEADER, "local-test-key"));
    request.setRemoteAddr("192.0.2.1"); controller.ticket("id", user, request);
    verify(manager).ticket("id", "http://localhost:8094", user, "local-token", java.util.Map.of());
  }

  @Test
  void uploadBoundsTheHttpStreamBeforePassingBytesToTheInstance() throws Exception {
    var manager = mock(ApplicationUiManager.class);
    var user = mock(EngineAuthorization.class);
    var controller = new ApplicationUiController(manager);
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setContent(new byte[4194305]);
    assertEquals(413, assertThrows(ResponseStatusException.class,
        () -> controller.upload("id", "node", "file.csv", "text/csv", user, request)).getStatusCode().value());
    verify(manager, never()).upload(anyString(), anyString(), anyString(), anyString(), any(), any());
    request.setContent(new byte[] {1, 2});
    controller.upload("id", "node", "file.csv", "text/csv", user, request);
    verify(manager).upload(eq("id"), eq("node"), eq("file.csv"), eq("text/csv"), org.mockito.AdditionalMatchers.aryEq(new byte[] {1, 2}), same(user));
  }

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
    verify(manager).ticket("id", "https://runtime.test", user, "webui_presented", java.util.Map.of());
    when(request.getHeader("Origin")).thenReturn("https://attacker.test");
    assertThrows(
        ResponseStatusException.class,
        () -> new ApplicationUiController(manager).ticket("id", user, request));
  }
}
