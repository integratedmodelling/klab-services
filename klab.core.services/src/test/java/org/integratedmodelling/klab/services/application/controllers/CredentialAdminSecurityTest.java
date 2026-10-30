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

class CredentialAdminSecurityTest {
  @TempDir Path root;
  @Configuration @EnableWebMvc
  @Import({ServiceSecurityConfiguration.class,KlabAdminController.class,KlabErrorHandler.class})
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

  @Test void realSecurityChainProtectsEveryCredentialRouteAndAdminCrudNeverReturnsSecrets() throws Exception {
    try (var context = new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.register(Config.class); context.refresh();
      var manager = context.getBean(ServiceAuthorizationManager.class);
      var reader = authorization(false); var admin = authorization(true);
      when(manager.validateToken(eq("reader-token"), anyMap())).thenReturn(reader);
      when(manager.validateToken(eq("admin-token"), anyMap())).thenReturn(admin);
      var service = mock(org.integratedmodelling.klab.services.base.BaseService.class);
      var vault = new org.integratedmodelling.common.authentication.CredentialVault(root, root.resolve("key"),
          scope -> scope == admin.getScope());
      when(context.getBean(ServiceNetworkedInstance.class).klabService()).thenReturn(service);
      when(service.getCredentialInfo(any())).thenAnswer(call -> vault.list(call.getArgument(0)));
      when(service.getCredentialSchemas(any())).thenAnswer(call -> vault.schemas(call.getArgument(0)));
      when(service.addCredentials(any(), any(), any())).thenAnswer(call -> vault.save(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
      when(service.removeCredentials(any(), any())).thenAnswer(call -> vault.delete(call.getArgument(0), call.getArgument(1)));
      var mvc = MockMvcBuilders.webAppContextSetup(context)
          .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
      for (var request : List.of(get("/credentials"), get("/credentials/schemata"),
          get("/checkCredentials").param("host", "https://github.com").param("scheme", "basic"),
          post("/credentials").contentType("application/json").content("{}"), delete("/credentials").param("id", "x"))) {
        assertEquals(403, mvc.perform(request.header("Authorization", "Bearer reader-token")).andReturn().getResponse().getStatus());
      }
      assertEquals(401, mvc.perform(get("/credentials").header("Authorization", "Bearer webui_invalid")).andReturn().getResponse().getStatus());
      String request = """
          {"host":"https://github.com/org","credentials":{"name":"GitHub","scheme":"basic",
          "parameters":{"username":"git","password":"private-fixture-secret"},"privileges":{"allowedUsers":["alice"]}}}
          """;
      var created = mvc.perform(post("/credentials").header("Authorization", "Bearer admin-token")
          .contentType("application/json").content(request)).andReturn().getResponse();
      assertEquals(200, created.getStatus(), created.getContentAsString());
      assertFalse(created.getContentAsString().contains("private-fixture-secret"));
      var json = new com.fasterxml.jackson.databind.ObjectMapper();
      String id = json.readTree(created.getContentAsString()).get("key").asText();
      var listed = mvc.perform(get("/credentials").header("Authorization", "Bearer admin-token")).andReturn().getResponse();
      assertEquals(200, listed.getStatus()); assertFalse(listed.getContentAsString().contains("private-fixture-secret"));
      var edited = mvc.perform(post("/credentials").header("Authorization", "Bearer admin-token").contentType("application/json")
          .content("{\"host\":\"https://github.com/org\",\"credentials\":{\"id\":\"" + id + "\",\"name\":\"Renamed\",\"scheme\":\"basic\",\"parameters\":{}}}"))
          .andReturn().getResponse();
      assertEquals(200, edited.getStatus(), edited.getContentAsString());
      assertEquals("private-fixture-secret", vault.resolve(id, "https://github.com/org/repo", admin.getScope()).getParameters().get("password"));
      assertEquals(Set.of("alice"), vault.list(admin.getScope()).getFirst().privileges().getAllowedUsers());
      var removed = mvc.perform(delete("/credentials").param("id", id).header("Authorization", "Bearer admin-token")).andReturn().getResponse();
      assertEquals(200, removed.getStatus()); assertEquals("true", removed.getContentAsString());
      assertTrue(vault.list(admin.getScope()).isEmpty());
    }
  }
}
