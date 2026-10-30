package org.integratedmodelling.klab.services.application.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.controllers.ApplicationUiController;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

class ApplicationUiWiringTest {
  @Test
  void sharedServiceScanWiresApplicationHttpAndWebSocketEndpoints() {
    try (var context = new GenericWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.getBeanFactory().registerSingleton("serviceInstance", mock(ServiceNetworkedInstance.class));
      context.getBeanFactory().registerSingleton("authentication", mock(ServiceAuthorizationManager.class));
      // Exercise the production scan boundaries without booting service discovery or security.
      var scanner = new ClassPathBeanDefinitionScanner(context, false);
      for (var type :
          new Class<?>[] {
            ApplicationUiController.class,
            ApplicationUiManager.class,
            ServiceUiApplicationLauncher.class,
            ApplicationUiWebSocket.class
          }) {
        scanner.addIncludeFilter(new AssignableTypeFilter(type));
      }
      scanner.scan(
          ServiceNetworkedInstance.class.getAnnotation(ComponentScan.class).basePackages());
      context.refresh();

      assertNotNull(context.getBean(ApplicationUiController.class));
      assertNotNull(context.getBean(ApplicationUiManager.class));
      assertInstanceOf(ServiceUiApplicationLauncher.class, context.getBean(UiApplicationLauncher.class));
      assertNotNull(context.getBean(ApplicationUiWebSocket.class));
      assertTrue(
          context.getBeansOfType(SimpleUrlHandlerMapping.class).values().stream()
              .anyMatch(mapping -> mapping.getUrlMap().containsKey("/ui-bus")));
    }
  }
}
