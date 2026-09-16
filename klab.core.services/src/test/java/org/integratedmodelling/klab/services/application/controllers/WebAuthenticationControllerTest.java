package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.HubWebAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WebAuthenticationControllerTest {
  @Test void exchangesBearerAndNeverCachesTheCredential() {
    var bridge = mock(HubWebAuthentication.class);
    var instance = mock(ServiceNetworkedInstance.class);
    var login = new HubWebAuthentication.Login("webui_test", "alice", 1234);
    when(bridge.exchange("keycloak", instance)).thenReturn(login);
    var response = new WebAuthenticationController(bridge, instance).login("Bearer keycloak");
    assertEquals(200, response.getStatusCode().value());
    assertEquals("no-store", response.getHeaders().getCacheControl());
    assertSame(login, response.getBody());
  }

  @Test void conveysActionableRejectionAndSupportsLogout() {
    var bridge = mock(HubWebAuthentication.class);
    var instance = mock(ServiceNetworkedInstance.class);
    when(bridge.exchange(null, instance)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first."));
    var controller = new WebAuthenticationController(bridge, instance);
    var response = controller.login("raw-token");
    assertEquals(401, response.getStatusCode().value());
    assertEquals("no-store", response.getHeaders().getCacheControl());
    assertEquals(java.util.Map.of("message", "Sign in first."), response.getBody());
    assertEquals(204, controller.logout("Bearer webui_test").getStatusCode().value());
    verify(bridge).revoke("webui_test");
  }
}
