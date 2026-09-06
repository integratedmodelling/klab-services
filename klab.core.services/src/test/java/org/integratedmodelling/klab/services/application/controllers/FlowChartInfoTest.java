package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.flowchart.FlowChartService;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class FlowChartInfoTest {
  @Test
  void baseInfoResolvesTypedSourceAndPreservesAuthorizationFailures() throws Exception {
    var service = mock(BaseService.class, CALLS_REAL_METHODS);
    var field = BaseService.class.getDeclaredField("flowCharts"); field.setAccessible(true);
    field.set(service, new FlowChartService());
    var scope = mock(UserScope.class);
    var workflow = new WorkflowImpl(); workflow.setId("review"); workflow.setName("Review");
    doReturn(workflow).when(service).info("review", KnowledgeClass.WORKFLOW, Workflow.class, scope);
    assertNotNull(service.info("review", KnowledgeClass.WORKFLOW, FlowChart.class, scope));
    assertNotNull(service.info("review", KnowledgeClass.WORKFLOW, BufferedImage.class, scope));
    doThrow(new KlabAuthorizationException("Not visible")).when(service)
        .info("review", KnowledgeClass.WORKFLOW, Workflow.class, scope);
    assertThrows(KlabAuthorizationException.class,
        () -> service.info("review", KnowledgeClass.WORKFLOW, BufferedImage.class, scope));
  }

  @Test
  void imageEndpointReturnsPng404AndRejectsMissingScope() throws Exception {
    var service = mock(BaseService.class);
    var instance = mock(ServiceNetworkedInstance.class);
    when(instance.klabService()).thenReturn(service);
    var scope = mock(UserScope.class);
    var principal = mock(EngineAuthorization.class); when(principal.getScope()).thenReturn(scope);
    var controller = new KlabServiceController(); controller.instance = instance;
    when(service.info("review", KnowledgeClass.WORKFLOW, BufferedImage.class, scope))
        .thenReturn(new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB));
    var response = controller.infoImage("review", KnowledgeClass.WORKFLOW, principal);
    assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
    assertEquals("private, no-store", response.getHeaders().getCacheControl());
    assertEquals(20, ImageIO.read(new ByteArrayInputStream(response.getBody())).getWidth());
    assertEquals(HttpStatus.NOT_FOUND,
        controller.infoImage("missing", KnowledgeClass.WORKFLOW, principal).getStatusCode());
    assertThrows(KlabAuthorizationException.class,
        () -> controller.infoImage("review", KnowledgeClass.WORKFLOW, null));
  }
}
