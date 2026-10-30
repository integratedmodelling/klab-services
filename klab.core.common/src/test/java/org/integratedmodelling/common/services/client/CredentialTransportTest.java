package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.junit.jupiter.api.Test;

class CredentialTransportTest {
  @Test void deletionPreservesFalseAndAdministrationFailuresArePropagated() throws Exception {
    var status = new AtomicInteger(200); var body = new AtomicReference<>("false");
    var requestBody = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), response.length);
      try (var output = exchange.getResponseBody()) { output.write(response); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      assertFalse(client.removeCredentials("absent", null));
      body.set("true"); assertTrue(client.removeCredentials("existing", null));
      var credentials = new ExternalAuthenticationCredentials(); credentials.setName("Git");
      credentials.setParameters(Map.of("username", "git", "password", "private-fixture-password"));
      for (int code : new int[]{400, 403, 500}) {
        status.set(code); body.set("{\"detail\":\"refused\"}");
        assertThrows(RuntimeException.class, () -> client.getCredentialInfo(null));
        assertThrows(RuntimeException.class, () -> client.getCredentialSchemas(null));
        assertThrows(RuntimeException.class, () -> client.removeCredentials("existing", null));
        var failure = assertThrows(RuntimeException.class, () -> client.addCredentials("https://github.com", credentials, null));
        assertFalse(failure.getMessage().contains("private-fixture-password"));
        assertTrue(requestBody.get().contains("private-fixture-password"));
      }
    } finally { server.stop(0); }
  }
}
