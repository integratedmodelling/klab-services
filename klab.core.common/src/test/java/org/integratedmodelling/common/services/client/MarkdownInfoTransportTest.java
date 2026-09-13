package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.junit.jupiter.api.Test;

class MarkdownInfoTransportTest {
  @Test void requestsMarkdownWithAnEncodedObservableUrnAndReturnsPlainUtf8() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var request = new AtomicReference<String>();
    var accept = new AtomicReference<String>();
    server.createContext("/", exchange -> {
      request.set(exchange.getRequestURI().toASCIIString());
      accept.set(exchange.getRequestHeaders().getFirst("Accept"));
      byte[] body = "# Árbol\n\n- metadata: café".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "text/markdown;charset=UTF-8");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var resources = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(resources, http);
      String urn = "audit:Speed in m/s";
      assertEquals("# Árbol\n\n- metadata: café", resources.info(urn, KnowledgeClass.OBSERVABLE, String.class, null));
      assertEquals("text/markdown", accept.get());
      assertTrue(request.get().contains("m%2Fs"));
      assertEquals("/api/v1/info/OBSERVABLE/" + urn,
          URLDecoder.decode(request.get(), StandardCharsets.UTF_8));
      assertFalse(request.get().contains("infoClass"));
    } finally { server.stop(0); }
  }
}
