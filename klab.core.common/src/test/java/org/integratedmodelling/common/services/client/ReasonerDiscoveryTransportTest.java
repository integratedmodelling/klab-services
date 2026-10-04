package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.utils.Utils;
import org.junit.jupiter.api.Test;

class ReasonerDiscoveryTransportTest {
  @Test void discoveryErrorsCannotMasqueradeAsAnEmptyCandidateList() throws Exception {
    var status = new AtomicInteger(200);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/reasoner/api/v1/resolving", exchange -> {
      exchange.getRequestBody().readAllBytes();
      var body = (status.get() == 200 ? "[]"
          : "{\"detail\":\"Unresolved semantic modifier INHERENT\"}")
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/reasoner", null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(client, http);
      var concept = new ConceptImpl();
      concept.setUrn("earth:PhysicalEnvironment of each earth:Region");
      assertTrue(client.resolving(concept).isEmpty());
      status.set(500);
      var failure = assertThrows(Utils.Http.Client.RequestFailure.class, () -> client.resolving(concept));
      assertEquals(500, failure.getStatus());
    } finally {
      server.stop(0);
    }
  }
}
