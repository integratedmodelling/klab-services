package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.*;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminLogSecurityTest {
  @TempDir Path root;
  @Configuration @EnableWebMvc
  @Import({ServiceSecurityConfiguration.class,AdminLogController.class,KlabErrorHandler.class})
  static class Config {
    @Bean ServiceAuthorizationManager manager() { return mock(ServiceAuthorizationManager.class); }
    @Bean HubWebAuthentication webAuthentication() { return mock(HubWebAuthentication.class); }
    @Bean org.springframework.boot.availability.ApplicationAvailability applicationAvailability() {
      return mock(org.springframework.boot.availability.ApplicationAvailability.class);
    }
    @Bean ServiceNetworkedInstance<?> instance() { return mock(ServiceNetworkedInstance.class); }
    @Bean AuthenticationProvider authenticationProvider() {
      var provider=mock(AuthenticationProvider.class);when(provider.supports(any())).thenReturn(true);return provider;
    }
  }
  EngineAuthorization authorization(boolean admin) {
    var authorization=new EngineAuthorization("test-hub",admin?"admin":"reader","fixture-token",Map.of(),List.of(),
        List.of(admin?Role.ROLE_ADMINISTRATOR:Role.ROLE_USER));
    authorization.setAuthenticated(true);
    var scope=mock(ServiceUserScope.class);authorization.setScope(scope);
    when(scope.isAuthorized(CRUDOperation.ADMINISTER)).thenReturn(admin);
    var identity=mock(UserIdentity.class);when(identity.getId()).thenReturn("fixture-private-identity");
    when(scope.getUser()).thenReturn(identity);return authorization;
  }
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @Test void productionSecurityChainRejectsInvalidTokenAndReaderButPermitsAdmin() throws Exception {
    var file=root.resolve("runtime.log");Files.writeString(file,"");
    try(var context=new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("logging.file.name",file.toString())));
      context.register(Config.class);context.refresh();
      var manager=context.getBean(ServiceAuthorizationManager.class);
      var readerAuthorization=authorization(false);
      var adminAuthorization=authorization(true);
      when(manager.validateToken(eq("webui_invalid"),anyMap())).thenReturn(null);
      when(manager.validateToken(eq("reader-token"),anyMap())).thenReturn(readerAuthorization);
      when(manager.validateToken(eq("admin-token"),anyMap())).thenReturn(adminAuthorization);
      var mvc=MockMvcBuilders.webAppContextSetup(context)
          .addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
      assertEquals(401,mvc.perform(get(ServicesAPI.ADMIN.LOGS_CAPABILITIES)
          .header("Authorization","Bearer webui_invalid")).andReturn().getResponse().getStatus());
      assertEquals(403,mvc.perform(get(ServicesAPI.ADMIN.LOGS_CAPABILITIES)
          .header("Authorization","Bearer reader-token")).andReturn().getResponse().getStatus());
      var allowed=mvc.perform(get(ServicesAPI.ADMIN.LOGS_CAPABILITIES)
          .header("Authorization","Bearer admin-token")).andReturn().getResponse();
      assertEquals(200,allowed.getStatus());assertFalse(allowed.getContentAsString().contains("fixture-private"));
      verify(manager).validateToken(eq("admin-token"),anyMap());
    }
  }
}
