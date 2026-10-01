package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.junit.jupiter.api.Test;

class ReasonerAuthorityDocumentationTest {
  @Test void readsHttpMetadataAndPreservesQueryIdsWithoutHidingFailures() throws Exception {
    var status = new AtomicInteger(200);
    var query = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION, exchange -> {
      query.set(exchange.getRequestURI().getRawQuery());
      byte[] body = "{\"text/markdown\":\"https://reasoner.example.org/document.md\"}"
          .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true); field.set(client, http);
      var metadata = client.getAuthorityDocumentation("TAXA.SPECIES", "A+B &/é", null);
      assertEquals("https://reasoner.example.org/document.md", metadata.get("text/markdown").toExternalForm());
      assertTrue(query.get().contains("authority=TAXA.SPECIES"));
      assertTrue(query.get().contains("%2B"));
      assertTrue(java.net.URLDecoder.decode(query.get(), java.nio.charset.StandardCharsets.UTF_8)
          .contains("identity=A+B &/é"));
      assertThrows(UnsupportedOperationException.class, metadata::clear);
      status.set(502);
      assertThrows(KlabServiceAccessException.class,
          () -> client.getAuthorityDocumentation("TAXA", "A1", null));
    } finally { server.stop(0); }
  }
}
