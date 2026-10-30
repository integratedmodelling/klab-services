package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.file.*;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminLogControllerTest {
  @TempDir Path root;
  EngineAuthorization admin(boolean allowed) {
    var principal=mock(EngineAuthorization.class);var scope=mock(ServiceUserScope.class);
    when(principal.isAuthenticated()).thenReturn(true);when(principal.getScope()).thenReturn(scope);
    when(scope.isAuthorized(CRUDOperation.ADMINISTER)).thenReturn(allowed);
    var user=mock(UserIdentity.class);when(scope.getUser()).thenReturn(user);when(user.getId()).thenReturn("private-admin-identity");
    return principal;
  }
  @Test void resolvesSpringPropertyAndNeverAllowsCallerPaths() throws Exception {
    var file=root.resolve("reasoner.log");Files.writeString(file,
        "[2026-10-23 13:31:46.123] - 12 ERROR [main] --- org.integratedmodelling.klab.services.reasoner.internal.AuthorityBootstrap: AUTHORITY_BOOTSTRAP_FAILURE phase=PRODUCE\n");
    var env=new MockEnvironment().withProperty("service.logs",root.toString())
        .withProperty("logging.file.name","${service.logs}/reasoner.log");
    var mvc=MockMvcBuilders.standaloneSetup(new AdminLogController(env)).setControllerAdvice(new KlabErrorHandler()).build();
    var response=mvc.perform(post(ServicesAPI.ADMIN.LOGS_QUERY).principal(admin(true))
        .contentType("application/json").content("{\"streamId\":\"service\",\"limit\":10}"))
        .andReturn().getResponse();
    assertEquals(200,response.getStatus());assertTrue(response.getHeader("Cache-Control").contains("no-store"));
    assertTrue(response.getHeader("Cache-Control").contains("private"));
    assertTrue(response.getContentAsString().contains("PRODUCE"));
    assertFalse(response.getContentAsString().contains(root.toString()));
    var traversal=mvc.perform(post(ServicesAPI.ADMIN.LOGS_QUERY).principal(admin(true))
        .contentType("application/json").content("{\"streamId\":\"../../secret\"}"))
        .andReturn().getResponse();assertEquals(400,traversal.getStatus());
  }
  @Test void deniesMissingAndNonAdminBeforeInspectingFileConfiguration() throws Exception {
    var env=mock(org.springframework.core.env.Environment.class);
    var mvc=MockMvcBuilders.standaloneSetup(new AdminLogController(env)).setControllerAdvice(new KlabErrorHandler()).build();
    assertEquals(403,mvc.perform(get(ServicesAPI.ADMIN.LOGS_CAPABILITIES)).andReturn().getResponse().getStatus());
    assertEquals(403,mvc.perform(get(ServicesAPI.ADMIN.LOGS_CAPABILITIES).principal(admin(false))).andReturn().getResponse().getStatus());
    verifyNoInteractions(env);
  }
  @Test void rejectsLiteralTildeWithoutLeakingConfiguration() throws Exception {
    var mvc=MockMvcBuilders.standaloneSetup(new AdminLogController(new MockEnvironment()
        .withProperty("logging.file.name","~/secret/service.log")))
        .setControllerAdvice(new KlabErrorHandler()).build();
    var response=mvc.perform(get(ServicesAPI.ADMIN.LOGS_CAPABILITIES).principal(admin(true))).andReturn().getResponse();
    assertEquals(503,response.getStatus());assertFalse(response.getContentAsString().contains("secret"));
  }
  @Test void malformedJsonNeverEchoesCredentialsOrReadsConfiguration() throws Exception {
    var env=mock(org.springframework.core.env.Environment.class);
    var mvc=MockMvcBuilders.standaloneSetup(new AdminLogController(env)).setControllerAdvice(new KlabErrorHandler()).build();
    var response=mvc.perform(post(ServicesAPI.ADMIN.LOGS_QUERY).principal(admin(true))
        .contentType("application/json").content("{\"streamId\":\"service\",\"password\":\"secret\", broken"))
        .andReturn().getResponse();
    assertEquals(400,response.getStatus());assertFalse(response.getContentAsString().contains("secret"));
    assertFalse(response.getContentAsString().contains("password"));verifyNoInteractions(env);
  }
}
