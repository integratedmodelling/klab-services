package org.integratedmodelling.klab.utilities;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.eclipse.jgit.transport.*;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.junit.jupiter.api.Test;

class GitCredentialProviderTest {
  @Test void refusesCrossHostPushUrlsRedirectsAndProtocolDowngrades() throws Exception {
    var credentials = new ExternalAuthenticationCredentials();
    credentials.setEndpoint("https://github.com/org");
    credentials.setParameters(Map.of("username", "git", "password", "private-token"));
    var provider = Utils.Git.getCredentialsProvider(credentials);
    var username = new CredentialItem.Username(); var password = new CredentialItem.Password();
    assertTrue(provider.get(new URIish("https://github.com/org/repo.git"), username, password));
    assertEquals("git", username.getValue());
    for (String endpoint : new String[]{ "https://evil.example/org/repo.git", "http://github.com/org/repo.git", "https://github.com/other/repo.git" }) {
      var rejected = new CredentialItem.Password();
      assertThrows(SecurityException.class, () -> provider.get(new URIish(endpoint), rejected));
      assertNull(rejected.getValue());
    }
  }
  @Test void blocksAnActualRedirectAfterHttpAuthentication(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
    var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    var authorizedRequests = new java.util.concurrent.atomic.AtomicInteger();
    var escapedRequests = new java.util.concurrent.atomic.AtomicInteger();
    server.createContext("/org/", exchange -> {
      if (exchange.getRequestHeaders().getFirst("Authorization") == null) {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=repository");
        exchange.sendResponseHeaders(401, -1);
      } else {
        authorizedRequests.incrementAndGet();
        exchange.getResponseHeaders().set("Location", "/other/repo.git/info/refs?service=git-upload-pack");
        exchange.sendResponseHeaders(302, -1);
      }
      exchange.close();
    });
    server.createContext("/other/", exchange -> {
      escapedRequests.incrementAndGet(); exchange.sendResponseHeaders(404, -1); exchange.close();
    });
    server.start();
    try {
      var caller = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.Scope.class);
      var service = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.base.BaseService.class);
      var vault = new org.integratedmodelling.common.authentication.CredentialVault(root, root.resolve("key"), scope -> scope == caller);
      org.mockito.Mockito.when(service.credentialVault()).thenReturn(vault);
      var c = new ExternalAuthenticationCredentials(); c.setName("Redirect fixture"); c.setScheme("basic");
      c.setParameters(Map.of("username", "git", "password", "private-token"));
      String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/org";
      String id = vault.save(endpoint, c, caller).key();
      assertThrows(RuntimeException.class, () -> Utils.Git.withCredentials(service, caller, id,
          () -> Utils.Git.clone(endpoint + "/repo.git", root.resolve("checkout").toFile(), false, caller)));
      assertTrue(authorizedRequests.get() > 0, "The fixture must reach authenticated HTTP before redirecting");
      assertEquals(0, escapedRequests.get(), "No request may escape the credential endpoint");
    } finally { server.stop(0); }
  }

}
