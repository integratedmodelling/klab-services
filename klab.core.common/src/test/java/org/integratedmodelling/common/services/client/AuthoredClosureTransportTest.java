package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.junit.jupiter.api.Test;

class AuthoredClosureTransportTest {
  @Test void unsupportedAndFailedRequestsStayDistinctFromSuccessfulEmptyClosure() throws Exception {
    var status = new AtomicInteger(404);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.REASONER.AUTHORED_CLOSURE, exchange -> {
      assertEquals("POST", exchange.getRequestMethod());
      var body = (status.get() == 200 ? "[]" : "{\"detail\":\"Unavailable authored vocabulary\"}")
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), body.length);
      try (var out = exchange.getResponseBody()) { out.write(body); }
    }); server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var target = new ConceptImpl(); target.setUrn("audit:Category");
      for (int code : new int[] {404, 500}) {
        status.set(code);
        var error = assertThrows(Utils.Http.Client.RequestFailure.class, () -> client.authoredClosure(target));
        assertEquals(code, error.getStatus());
      }
      status.set(200); assertTrue(client.authoredClosure(target).isEmpty());
    } finally { server.stop(0); }
  }
}
