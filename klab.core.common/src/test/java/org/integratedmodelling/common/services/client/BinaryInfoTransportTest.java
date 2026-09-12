package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.junit.jupiter.api.Test;

class BinaryInfoTransportTest {
  @Test
  void preservesBytesAuthenticationAndStatus() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var authorization = new AtomicReference<String>();
    var accept = new AtomicReference<String>();
    var scopeHeader = new AtomicReference<String>();
    byte[] content = {(byte) 137, 80, 78, 71, 0, (byte) 255};
    server.createContext("/image", exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      accept.set(exchange.getRequestHeaders().getFirst("Accept"));
      scopeHeader.set(exchange.getRequestHeaders().getFirst("X-Test-Scope"));
      exchange.getResponseHeaders().set("Content-Type", "image/png");
      exchange.sendResponseHeaders(200, content.length);
      try (var output = exchange.getResponseBody()) { output.write(content); }
    });
    server.createContext("/missing", exchange -> { exchange.sendResponseHeaders(404, -1); exchange.close(); });
    server.createContext("/denied", exchange -> { exchange.sendResponseHeaders(403, -1); exchange.close(); });
    server.start();
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      client.setAuthorization("Bearer test");
      client.setHeader("X-Test-Scope", "scope");
      var images = client.accepting(List.of("image/png"));
      assertArrayEquals(content, images.getBytes("/image"));
      assertEquals("Bearer test", authorization.get());
      assertEquals("image/png", accept.get());
      assertEquals("scope", scopeHeader.get());
      assertArrayEquals(content, images.postRequired("/image", "{\"root\":{}}", byte[].class));
      assertEquals("Bearer test", authorization.get());
      assertEquals("image/png", accept.get());
      assertEquals("scope", scopeHeader.get());
      assertNull(images.getBytes("/missing"));
      assertThrows(KlabServiceAccessException.class, () -> images.getBytes("/denied"));
    } finally { server.stop(0); }
  }
}
