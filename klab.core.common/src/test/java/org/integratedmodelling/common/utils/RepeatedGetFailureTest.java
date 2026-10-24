package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class RepeatedGetFailureTest {
  @Test void voidGetsAlsoCheckFailureStatusAndResetAfterSuccess() throws Exception {
    try (var endpoint = new Endpoint(); var client = Utils.Http.getClient(endpoint.url(), endpoint.scope)) {
      assertNull(client.get("/poll", Void.class));
      assertNull(client.withScope(endpoint.scope).get("/poll", Void.class));
      assertEquals(1, errors(endpoint.scope));
      endpoint.status.set(200);
      assertNull(client.get("/poll", Void.class));
      endpoint.status.set(502);
      assertNull(client.get("/poll", Void.class));
      assertEquals(2, errors(endpoint.scope));
    }
  }

  @Test void repeatedGatewayFailuresReportOnceAcrossScopedCopiesButKeepPolling() throws Exception {
    try (var endpoint = new Endpoint(); var client = Utils.Http.getClient(endpoint.url(), endpoint.scope)) {
      for (int poll = 0; poll < 8; poll++) assertNull(client.withScope(endpoint.scope).get("/components", Map.class));
      assertEquals(8, endpoint.requests.get());
      assertEquals(1, errors(endpoint.scope));
      endpoint.status.set(503);
      assertNull(client.withScope(endpoint.scope).get("/components", Map.class));
      assertEquals(2, errors(endpoint.scope));
      endpoint.status.set(200);
      assertEquals(1, ((Number) client.withScope(endpoint.scope).get("/components", Map.class).get("value")).intValue());
      endpoint.status.set(502);
      assertNull(client.withScope(endpoint.scope).get("/components", Map.class));
      assertEquals(3, errors(endpoint.scope));
    }
  }

  @Test void endpointsAndCredentialsHaveIndependentFailureEpisodes() throws Exception {
    try (var endpoint = new Endpoint(); var client = Utils.Http.getClient(endpoint.url(), endpoint.scope)) {
      var first = mock(Identity.class); when(first.getId()).thenReturn("first-test-principal");
      var second = mock(Identity.class); when(second.getId()).thenReturn("second-test-principal");
      assertNull(client.withIdentity(first).get("/first", Map.class));
      assertNull(client.withIdentity(first).get("/first", Map.class));
      assertNull(client.withIdentity(first).get("/second", Map.class));
      assertNull(client.withIdentity(second).get("/first", Map.class));
      assertEquals(3, errors(endpoint.scope));
      endpoint.status.set(200);
      assertNotNull(client.withIdentity(first).get("/second", Map.class));
      endpoint.status.set(502);
      assertNull(client.withIdentity(first).get("/first", Map.class));
      assertEquals(3, errors(endpoint.scope), "Success on another endpoint must not reset this outage");
    }
  }

  @Test void malformedSuccessBodiesDoNotResetARepeatedDecodingFailure() throws Exception {
    try (var endpoint = new Endpoint(); var client = Utils.Http.getClient(endpoint.url(), endpoint.scope)) {
      endpoint.status.set(200); endpoint.body = "not JSON";
      assertNull(client.get("/components", Map.class));
      assertNull(client.withScope(endpoint.scope).get("/components", Map.class));
      assertEquals(1, errors(endpoint.scope));
      endpoint.body = "{\"value\":1}";
      assertNotNull(client.get("/components", Map.class));
      endpoint.body = "not JSON";
      assertNull(client.get("/components", Map.class));
      assertEquals(2, errors(endpoint.scope));
    }
  }

  @Test void concurrentScopedPollsShareTheFirstFailureReport() throws Exception {
    try (var endpoint = new Endpoint(); var client = Utils.Http.getClient(endpoint.url(), endpoint.scope);
         var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = new java.util.ArrayList<Future<?>>();
      for (int poll = 0; poll < 12; poll++) results.add(workers.submit(() ->
          assertNull(client.withScope(endpoint.scope).get("/components", Map.class))));
      for (var result : results) result.get(10, TimeUnit.SECONDS);
      assertEquals(12, endpoint.requests.get()); assertEquals(1, errors(endpoint.scope));
    }
  }

  @Test void silentFailureDoesNotConsumeTheFirstNormalReport() throws Exception {
    try (var endpoint = new Endpoint(); var client = Utils.Http.getClient(endpoint.url(), endpoint.scope)) {
      assertNull(client.get("/components", Map.class, Notification.Mode.Silent));
      assertNull(client.get("/components", Map.class));
      assertNull(client.withScope(endpoint.scope).get("/components", Map.class));
      verify(endpoint.scope, times(1)).error(any(), any(), eq(Notification.Mode.Normal));
    }
  }

  private static long errors(Scope scope) {
    return mockingDetails(scope).getInvocations().stream().filter(invocation -> invocation.getMethod().getName().equals("error")).count();
  }

  private static final class Endpoint implements AutoCloseable {
    final AtomicInteger status = new AtomicInteger(502), requests = new AtomicInteger();
    final Scope scope = mock(Scope.class);
    final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    volatile String body = "{\"value\":1}";
    Endpoint() throws Exception {
      server.createContext("/", exchange -> {
        requests.incrementAndGet(); byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status.get(), response.length);
        try (var output = exchange.getResponseBody()) { output.write(response); }
      });
      server.start();
    }
    String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public void close() { server.stop(0); }
  }
}
