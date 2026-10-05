package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.junit.jupiter.api.Test;

class ProjectDeletionTransportTest {
  @Test
  void deletionReturnsChangesAndPreservesErrors() throws Exception {
    var path = new AtomicReference<String>();
    var query = new AtomicReference<String>();
    var method = new AtomicReference<String>();
    var status = new AtomicInteger(200);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      path.set(exchange.getRequestURI().getRawPath());
      query.set(exchange.getRequestURI().getQuery());
      method.set(exchange.getRequestMethod());
      byte[] body = ("[{\"workspace\":\"local\",\"namespaces\":[{"
          + "\"operation\":\"DELETE\",\"resourceUrn\":\"test.document\","
          + "\"projectUrn\":\"test.project\",\"knowledgeClass\":\"NAMESPACE\"}]}]")
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort() + "/resources", null)) {
      var client = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(client, http);
      for (String name : new String[] {"concepts", "observables"}) {
        var cache = ResourcesClient.class.getDeclaredField(name);
        cache.setAccessible(true);
        cache.set(client, mock(com.google.common.cache.LoadingCache.class));
      }
      var result = client.delete("test.project/test.document", KlabAsset.KnowledgeClass.NAMESPACE, null);
      assertEquals("DELETE", method.get());
      assertEquals("/resources/api/v1/delete/NAMESPACE", path.get());
      assertEquals("urn=test.project/test.document", query.get());
      assertEquals(1, result.size());
      assertEquals(CRUDOperation.DELETE, result.getFirst().getNamespaces().iterator().next().getOperation());
      for (int code : new int[] {400, 403, 500}) {
        status.set(code);
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> client.delete("test.project/test.document", KlabAsset.KnowledgeClass.NAMESPACE, null));
        assertEquals(code, failure.getStatus());
      }
    } finally { server.stop(0); }
  }
}
