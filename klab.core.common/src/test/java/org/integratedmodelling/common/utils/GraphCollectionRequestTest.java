package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.junit.jupiter.api.Test;

class GraphCollectionRequestTest {
  @Test void relationshipFilterParsesWireNames() {
    assertEquals(List.of(GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.HAS_MEMBER),
        Utils.Data.parseList("HAS_CHILD, HAS_MEMBER", GraphModel.Relationship.class));
    assertThrows(IllegalArgumentException.class,
        () -> Utils.Data.parseList("HAS_UNKNOWN", GraphModel.Relationship.class));
  }

  @Test void failedAdjacencyMustNotBecomeAnEmptyGraph() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/links", exchange -> {
      var body = "{\"detail\":\"Invalid relationship filter\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(500, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.createContext("/empty", exchange -> {
      var body = "[]".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> client.getCollectionOrThrow("/links", Object.class));
      assertEquals(500, failure.getStatus());
      assertTrue(failure.getMessage().contains("Invalid relationship filter"));
      assertTrue(client.getCollectionOrThrow("/empty", Object.class).isEmpty());
    } finally { server.stop(0); }
  }
}
