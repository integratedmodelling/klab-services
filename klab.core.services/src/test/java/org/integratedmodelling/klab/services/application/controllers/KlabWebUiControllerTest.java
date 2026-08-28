package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.junit.jupiter.api.Test;

class KlabWebUiControllerTest {

  @Test
  void forwardsHtmlRootAndReturnsTheInstanceConfiguration() {
    var instance = mock(ServiceNetworkedInstance.class);
    var configuration = WebUiConfiguration.builder(KlabService.Type.RESOURCES).build();
    when(instance.webUiConfiguration()).thenReturn(configuration);

    var controller = new KlabWebUiController(instance);

    assertEquals("forward:/index.html", controller.dashboard());
    assertEquals(configuration, controller.configuration());
  }
}
