package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class KlabWebUiControllerTest {

  @Test
  void forwardsHtmlRootAndReturnsTheInstanceConfiguration() {
    var instance = mock(ServiceNetworkedInstance.class);
    var configuration = WebUiConfiguration.builder(KlabService.Type.RESOURCES).build();
    when(instance.webUiConfiguration()).thenReturn(configuration);

    var controller = new KlabWebUiController(instance);

    assertEquals("forward:/index.html", controller.dashboard());
    assertEquals("forward:/index.html", controller.fullPageComponent());
    assertEquals(configuration, controller.configuration());
  }

  @Test
  void servesADeclaredInstalledComponentModuleAsJavascript() {
    var instance = mock(ServiceNetworkedInstance.class);
    var service = mock(BaseService.class);
    var registry = mock(ComponentRegistry.class);
    var source = "export default {}".getBytes(StandardCharsets.UTF_8);
    when(instance.klabService()).thenReturn(service);
    when(service.getComponentRegistry()).thenReturn(registry);
    when(registry.getWebUiResource("test.component", "1.0.0", "catalog.js"))
        .thenReturn(Optional.of(new ComponentRegistry.WebUiResource(source, "catalog.js")));

    var response =
        new KlabWebUiController(instance)
            .componentModule("test.component", "1.0.0", "/catalog.js");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(MediaType.valueOf("text/javascript"), response.getHeaders().getContentType());
    assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    assertArrayEquals(source, response.getBody());
  }
}
