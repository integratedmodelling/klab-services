package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class MarkdownInfoTest {
  @Test void markdownUsesUserScopeUtf8AndNotFoundAndJsonProjectionRemainsAvailable() throws Exception {
    var service = mock(BaseService.class);
    var instance = mock(ServiceNetworkedInstance.class);
    doReturn(service).when(instance).klabService();
    var controller = new KlabServiceController();
    controller.instance = instance;
    var user = mock(UserScope.class);
    var principal = mock(EngineAuthorization.class);
    when(principal.getScope()).thenReturn(user);
    when(service.info("audit:Tree", KnowledgeClass.OBSERVABLE, String.class, user)).thenReturn("# Árbol\n");
    var response = controller.infoMarkdown("audit:Tree", KnowledgeClass.OBSERVABLE, principal);
    assertEquals(200, response.getStatusCode().value());
    assertEquals("text/markdown;charset=UTF-8", response.getHeaders().getContentType().toString());
    assertEquals("# Árbol\n", response.getBody());
    assertEquals(404, controller.infoMarkdown("audit:Missing", KnowledgeClass.OBSERVABLE, principal).getStatusCode().value());
    assertThrows(KlabAuthorizationException.class, () -> controller.infoMarkdown("audit:Tree", KnowledgeClass.OBSERVABLE, null));
    when(service.info("audit:Tree", KnowledgeClass.OBSERVABLE, Integer.class, user)).thenReturn(42);
    assertEquals(42, (Integer) controller.info("audit:Tree", KnowledgeClass.OBSERVABLE, "java.lang.Integer", principal));
    var mapping = KlabServiceController.class.getMethod("infoMarkdown", String.class, KnowledgeClass.class,
        java.security.Principal.class).getAnnotation(GetMapping.class);
    assertArrayEquals(new String[]{"text/markdown"}, mapping.produces());
    assertEquals(0, mapping.params().length); // Accept alone selects this representation.
  }
}
