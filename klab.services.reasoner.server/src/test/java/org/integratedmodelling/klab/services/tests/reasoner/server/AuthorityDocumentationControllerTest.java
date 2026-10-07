package org.integratedmodelling.klab.services.tests.reasoner.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.controllers.AuthorityDocumentationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

class AuthorityDocumentationControllerTest {
  @TempDir Path directory;
  private ReasonerService service;
  private EngineAuthorization authorization;
  private ContextScope scope;
  private MockMvc mvc;

  @BeforeEach void setup() {
    service = mock(ReasonerService.class);
    var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new AuthorityDocumentationController();
    ReflectionTestUtils.setField(controller, "reasoner", server);
    scope = mock(ContextScope.class);
    authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(scope);
    mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test void publishesRemoteUrlsAndStreamsLocalMarkdownWithReservedCharacters() throws Exception {
    Path file = directory.resolve("taxon.md");
    String markdown = "# Taxon\n\ncafé & metadata";
    Files.writeString(file, markdown, StandardCharsets.UTF_8);
    String id = "A+B &/é";
    URL remote = URI.create("https://example.org/image.jpg").toURL();
    when(service.getAuthorityDocumentation("TAXA.SPECIES", id, scope))
        .thenReturn(Map.of("text/markdown", file.toUri().toURL(), "image/jpeg", remote));
    var response = mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION)
        .principal(authorization).param("authority", "TAXA.SPECIES").param("identity", id))
        .andExpect(status().isOk()).andReturn().getResponse();
    var metadata = new ObjectMapper().readTree(response.getContentAsString());
    assertEquals(remote.toExternalForm(), metadata.path("image/jpeg").asText());
    URI content = URI.create(metadata.path("text/markdown").asText());
    assertEquals("http", content.getScheme());
    assertEquals(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT, content.getPath());
    assertFalse(response.getContentAsString().contains("file:"));
    mvc.perform(get(content).principal(authorization))
        .andExpect(status().isOk()).andExpect(content().contentType("text/markdown"))
        .andExpect(content().bytes(markdown.getBytes(StandardCharsets.UTF_8)))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Cache-Control", "no-store"));
    verify(service, times(2)).getAuthorityDocumentation("TAXA.SPECIES", id, scope);
  }

  @Test void bothRoutesRequireAuthorizationAndNeverAcceptAnArbitraryFile() throws Exception {
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION)
        .param("authority", "TAXA").param("identity", "A1"))
        .andExpect(status().isForbidden());
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT)
        .param("authority", "TAXA").param("identity", "A1").param("mediaType", "text/markdown"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
    Path secret = directory.resolve("secret.txt"); Files.writeString(secret, "secret");
    when(service.getAuthorityDocumentation("TAXA", "A1", scope)).thenReturn(Map.of());
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT).principal(authorization)
        .param("authority", "TAXA").param("identity", "A1").param("mediaType", "text/markdown")
        .param("url", secret.toUri().toString()))
        .andExpect(status().isNotFound());
  }

  @Test void redirectsExternalContentAndReportsUnavailableResources() throws Exception {
    when(service.getAuthorityDocumentation("TAXA", "A1", scope))
        .thenReturn(Map.of("image/jpeg", URI.create("https://example.org/image.jpg").toURL(),
            "text/markdown", directory.resolve("missing.md").toUri().toURL()));
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT).principal(authorization)
        .param("authority", "TAXA").param("identity", "A1").param("mediaType", "image/jpeg"))
        .andExpect(status().isFound()).andExpect(header().string("Location", "https://example.org/image.jpg"));
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT).principal(authorization)
        .param("authority", "TAXA").param("identity", "A1").param("mediaType", "text/markdown"))
        .andExpect(status().isNotFound());
    when(service.getAuthorityDocumentation("TAXA", "unknown", scope))
        .thenThrow(new NoSuchElementException("Identity unavailable"));
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION).principal(authorization)
        .param("authority", "TAXA").param("identity", "unknown"))
        .andExpect(status().isNotFound());
    when(service.getAuthorityDocumentation("TAXA", "bad", scope)).thenThrow(new IllegalArgumentException());
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION).principal(authorization)
        .param("authority", "TAXA").param("identity", "bad"))
        .andExpect(status().isBadRequest());
  }

  @Test void respectsContextPathAndRejectsUnsafeProviderProtocols() throws Exception {
    when(service.getAuthorityDocumentation("TAXA", "A1", scope))
        .thenReturn(Map.of("text/markdown", directory.resolve("taxon.md").toUri().toURL()));
    var response = mvc.perform(get("/reasoner" + ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION)
        .contextPath("/reasoner").principal(authorization).param("authority", "TAXA").param("identity", "A1"))
        .andExpect(status().isOk()).andReturn().getResponse();
    assertTrue(new ObjectMapper().readTree(response.getContentAsString()).path("text/markdown").asText()
        .startsWith("http://localhost/reasoner" + ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT));
    when(service.getAuthorityDocumentation("TAXA", "A1", scope))
        .thenReturn(Map.of("text/markdown", URI.create("file://other-host/share/taxon.md").toURL()));
    mvc.perform(get(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION).principal(authorization)
        .param("authority", "TAXA").param("identity", "A1"))
        .andExpect(status().isBadGateway());
  }
}
