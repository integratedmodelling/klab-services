package org.integratedmodelling.resources.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.application.security.*;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.workflow.ReviewNotificationWorker;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewNotificationAdminControllerTest {
  EngineAuthorization principal(boolean authenticated, Role role) {
    var auth = new EngineAuthorization("fixture", "operator", "fixture-token", Map.of(), List.of(), List.of(role));
    auth.setAuthenticated(authenticated); auth.setScope(mock(UserScope.class)); return auth;
  }
  @Test void onlyAuthenticatedServiceAdministratorCanReadBoundedPrivateHealth() throws Exception {
    var service = mock(ResourcesProvider.class); var server = mock(ResourcesServer.class); when(server.klabService()).thenReturn(service);
    var controller = new ReviewNotificationAdminController(); ReflectionTestUtils.setField(controller, "resourcesServer", server);
    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new org.integratedmodelling.klab.services.application.controllers.KlabErrorHandler()).build();
    String path = "/api/v1/admin/review-notifications";
    mvc.perform(get(path)).andExpect(status().isForbidden());
    mvc.perform(get(path).principal(() -> "forged-admin")).andExpect(status().isForbidden());
    mvc.perform(get(path).principal(principal(false, Role.ROLE_ADMINISTRATOR))).andExpect(status().isForbidden());
    mvc.perform(get(path).principal(principal(true, Role.ROLE_USER))).andExpect(status().isForbidden());
    verifyNoInteractions(service);
    var admin = principal(true, Role.ROLE_ADMINISTRATOR);
    mvc.perform(get(path).param("limit", "101").principal(admin)).andExpect(status().isBadRequest());
    when(service.reviewNotificationHealth(eq(50), any())).thenReturn(new ReviewNotificationWorker.Health(
        "REPLAY_ONLY", false, java.time.Instant.parse("2026-11-06T18:00:00Z"),
        java.time.Instant.parse("2026-11-06T18:00:01Z"), "DELIVERY_DISABLED", 0, Map.of(), List.of()));
    var response = mvc.perform(get(path).principal(admin)).andExpect(status().isOk()).andReturn().getResponse();
    assertEquals("private, no-store", response.getHeader("Cache-Control"));
    assertTrue(response.getContentAsString().contains("DELIVERY_DISABLED"));
    assertFalse(response.getContentAsString().contains("fixture-token"));
    verify(service).reviewNotificationHealth(50, (UserScope) admin.getScope());
  }
}
