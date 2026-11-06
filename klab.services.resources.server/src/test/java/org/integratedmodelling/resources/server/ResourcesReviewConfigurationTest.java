package org.integratedmodelling.resources.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.junit.jupiter.api.Test;

class ResourcesReviewConfigurationTest {
  @Test void reviewStageUsesTheAuthenticatedResourcesFullPageRoute() {
    var server = mock(ResourcesServer.class, CALLS_REAL_METHODS);
    var builder = WebUiConfiguration.builder(KlabService.Type.RESOURCES);
    server.configureWebUi(builder);
    var page = builder.build().pages().stream().filter(p -> p.name().equals("review")).findFirst().orElseThrow();
    assertTrue(page.requiresAuthentication());
    assertEquals("resources-review", page.component());
  }
}
