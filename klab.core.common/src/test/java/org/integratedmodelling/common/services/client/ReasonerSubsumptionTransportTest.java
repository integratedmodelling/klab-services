package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.junit.jupiter.api.Test;

class ReasonerSubsumptionTransportTest {
  @Test void cachesTrueAndFalseButRetriesFailedAndEmptyResponses() throws Exception {
    var status = new AtomicInteger(500);
    var calls = new AtomicInteger();
    var body = new java.util.concurrent.atomic.AtomicReference<>("failure");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.REASONER.SUBSUMES, exchange -> {
      exchange.getRequestBody().readAllBytes();
      calls.incrementAndGet();
      var bytes = body.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true); field.set(client, http);
      field = ReasonerClient.class.getDeclaredField("subsumption");
      field.setAccessible(true); field.set(client, Caffeine.newBuilder().build());
      var child = new ConceptImpl(); child.setUrn("earth:StreamJunction");
      var parent = new ConceptImpl(); parent.setUrn("earth:Location");
      assertTrue(assertThrows(KlabServiceAccessException.class, () -> client.is(child, parent))
          .getMessage().contains("500"));
      status.set(200); body.set("");
      assertTrue(assertThrows(KlabServiceAccessException.class, () -> client.is(child, parent))
          .getMessage().contains("empty response"));
      body.set("true");
      assertTrue(client.is(child, parent));
      assertTrue(client.is(child, parent));
      assertEquals(3, calls.get());
      body.set("false");
      assertFalse(client.is(parent, child));
      assertFalse(client.is(parent, child));
      assertEquals(4, calls.get());
    } finally { server.stop(0); }
  }
}
