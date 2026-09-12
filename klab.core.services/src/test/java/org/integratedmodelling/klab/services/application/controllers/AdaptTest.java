package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.integratedmodelling.klab.api.documentation.WorkflowFlowChartAdapter;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.flowchart.FlowChartService;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class AdaptTest {
  private KlabServiceController controller() throws Exception {
    var service = mock(BaseService.class, CALLS_REAL_METHODS);
    var field = BaseService.class.getDeclaredField("flowCharts");
    field.setAccessible(true);
    field.set(service, new FlowChartService());
    var instance = mock(ServiceNetworkedInstance.class);
    when(instance.klabService()).thenReturn(service);
    var controller = new KlabServiceController();
    controller.instance = instance;
    return controller;
  }

  @Test
  void rendersPostedJsonAndNegotiatesSupportedRepresentation() throws Exception {
    var workflow = new WorkflowImpl();
    workflow.setId("review"); workflow.setName("Review");
    var source = org.integratedmodelling.common.utils.Utils.Json.asString(new WorkflowFlowChartAdapter().adapt(workflow));
    var response = controller().adapt(source, "image/svg+xml, image/png;q=0.8", null, null);
    assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
    assertEquals("private, no-store", response.getHeaders().getCacheControl());
    var image = ImageIO.read(new ByteArrayInputStream(response.getBody()));
    assertNotNull(image);
    assertTrue(image.getWidth() > 0);
    assertTrue(image.getHeight() > 0);
  }

  @Test
  void rejectsMalformedInputAndUnsupportedRepresentations() throws Exception {
    var controller = controller();
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> controller.adapt("{", "image/png", null, null)).getStatusCode());
    assertEquals(HttpStatus.NOT_ACCEPTABLE, assertThrows(ResponseStatusException.class,
        () -> controller.adapt("{}", "image/svg+xml", null, null)).getStatusCode());
    assertEquals(HttpStatus.NOT_ACCEPTABLE, assertThrows(ResponseStatusException.class,
        () -> controller.adapt("{}", "image/png;q=0", null, null)).getStatusCode());
  }
}
