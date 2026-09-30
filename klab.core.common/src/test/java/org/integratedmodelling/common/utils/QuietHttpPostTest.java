package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;

class QuietHttpPostTest {
  @Test void gatewayFailureStaysUnacknowledgedUntilTheRetrySucceeds() throws Exception {
    var attempts = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/notifyUserScope", exchange -> {
      boolean failed = attempts.incrementAndGet() == 1;
      byte[] body = (failed ? "<html><h1>502 Bad Gateway</h1></html>" : "true")
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", failed ? "text/html" : "application/json");
      exchange.sendResponseHeaders(failed ? 502 : 200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    var scope = mock(Scope.class);
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), scope)) {
      assertFalse(Boolean.TRUE.equals(client.postQuietly("/notifyUserScope", Map.of(), Boolean.class)));
      assertTrue(Boolean.TRUE.equals(client.postQuietly("/notifyUserScope", Map.of(), Boolean.class)));
      assertEquals(2, attempts.get());
      verifyNoInteractions(scope);
      server.stop(0);
      assertNull(client.postQuietly("/notifyUserScope", Map.of(), Boolean.class));
      verifyNoInteractions(scope);
    } finally { server.stop(0); }
  }

  @Test void requiredCommandsStillReportHttpStatusAndStructuredErrorDetails() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String text = exchange.getRequestURI().getPath().equals("/gateway")
          ? "<html>Bad Gateway</html>" : "{\"body\":{\"detail\":\"Invalid configuration\"}}";
      byte[] body = text.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(502, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var gateway = assertThrows(KlabServiceAccessException.class,
          () -> client.postRequired("/gateway", Map.of(), Boolean.class));
      assertEquals("POST /gateway failed with HTTP 502", gateway.getMessage());
      var structured = assertThrows(KlabServiceAccessException.class,
          () -> client.postRequired("/structured", Map.of(), Boolean.class));
      assertTrue(structured.getMessage().contains("HTTP 502: Invalid configuration"));
    } finally { server.stop(0); }
  }
}
