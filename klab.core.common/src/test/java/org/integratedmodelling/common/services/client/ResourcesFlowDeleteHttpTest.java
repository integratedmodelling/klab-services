package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.junit.jupiter.api.Test;

/** Exercises ResourcesClient.deleteFlow through a real HTTP exchange, not a mocked transport. */
class ResourcesFlowDeleteHttpTest {
  private static final String FLOW_ID = "review-flow-1";
  private static final String FLOW_PATH = ServicesAPI.RESOURCES.FLOW.replace("{flowId}", FLOW_ID);

  @Test
  void trueAndFalseBodiesReachCallerWithOriginalPathAndScopedHeaders() throws Exception {
    try (var fixture = new Fixture()) {
      assertTrue(fixture.client.deleteFlow(FLOW_ID, fixture.scope));
      assertEquals("DELETE", fixture.method.get());
      assertEquals(FLOW_PATH, fixture.path.get());
      var headers = fixture.headers.get();
      assertEquals(List.of("scope-secret"), headers.get("Authorization"));
      assertEquals(List.of("service-secret"), headers.get("Authentication"));
      assertEquals(List.of("session-id"), headers.get(ServicesAPI.SCOPE_HEADER));
      assertEquals(List.of("host-service"), headers.get(ServicesAPI.SERVICE_ID_HEADER));
      assertEquals(List.of("application/json"), headers.get("Accept"));
      assertEquals(List.of("unchanged"), headers.get("X-Test-Header"));
      fixture.body.set(" \n false\t\r");
      assertFalse(fixture.client.deleteFlow(FLOW_ID, fixture.scope));
      fixture.body.set(" \n true\t\r");
      assertTrue(fixture.client.deleteFlow(FLOW_ID, fixture.scope));
      assertEquals(3, fixture.calls.get());
    }
  }

  @Test
  void preservesRejectionStatusesWithoutLeakingBodiesOrCredentials() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.body.set("{\"detail\":\"private-response-secret\"}");
      for (int status : new int[] {403, 409, 500, 499}) {
        fixture.status.set(status);
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope));
        assertEquals(status, failure.getStatus());
        assertRedacted(failure);
      }
      assertEquals(4, fixture.calls.get());
    }
  }

  @Test
  void malformedMissingAndNonBooleanBodiesAreUnknownNotSuccess() throws Exception {
    try (var fixture = new Fixture()) {
      for (String body : List.of("", "null", "\"true\"", "1", "{}", "TRUE", "true false",
          "\ftrue", "not-json private-response-secret", "true" + " ".repeat(1025))) {
        fixture.body.set(body);
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope), body);
        assertEquals(200, failure.getStatus());
        assertTrue(failure.getMessage().contains("outcome unknown"));
        assertRedacted(failure);
      }
      fixture.status.set(204);
      var missing = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope));
      assertEquals(204, missing.getStatus());
      assertEquals(11, fixture.calls.get());
    }
  }

  @Test
  void oversizedResponseHeadersFailClosedWithoutLeakingOrRepeatingDelete() throws Exception {
    try (var fixture = new Fixture()) {
      for (boolean longLine : List.of(true, false)) {
        fixture.behavior.set(exchange -> {
          if (longLine) {
            exchange.getResponseHeaders().set("X-Oversized", "private-header-secret" + "x".repeat(8192));
          } else {
            for (int i = 0; i < 65; i++)
              exchange.getResponseHeaders().set("X-Header-" + i, "private-header-secret");
          }
          fixture.respond(exchange);
        });
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope));
        assertEquals(0, failure.getStatus());
        assertTrue(failure.getMessage().contains("outcome unknown"));
        assertRedacted(failure);
      }
      assertEquals(2, fixture.calls.get());
    }
  }
  @Test
  void stoppedServerReportsTransportFailureInsteadOfSuccess() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.server.stop(0);
      var failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
          () -> assertThrows(Utils.Http.Client.RequestFailure.class,
              () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope)));
      assertEquals(0, failure.getStatus());
      assertTrue(failure.getMessage().contains("outcome unknown"));
      assertRedacted(failure);
      assertEquals(0, fixture.calls.get());
    }
  }

  @Test
  void droppedResponseAfterReceivingDeleteIsNeverRetried() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.behavior.set(exchange -> exchange.close());
      var failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
          () -> assertThrows(Utils.Http.Client.RequestFailure.class,
              () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope)));
      assertEquals(0, failure.getStatus());
      assertTrue(failure.getMessage().contains("outcome unknown"));
      assertRedacted(failure);
      assertEquals(1, fixture.calls.get(), "The server received DELETE before dropping its response");
    }
  }

  @Test
  void redirectsAndAuthenticationChallengesNeverReplayDelete() throws Exception {
    try (var fixture = new Fixture()) {
      var redirected = new AtomicInteger();
      fixture.server.createContext("/redirected", exchange -> {
        redirected.incrementAndGet();
        exchange.close();
      });
      fixture.behavior.set(exchange -> {
        exchange.getResponseHeaders().set("Location", "/redirected");
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"test\"");
        exchange.sendResponseHeaders(fixture.status.get(), -1);
        exchange.close();
      });
      for (int status : new int[] {301, 302, 307, 308, 401, 407}) {
        fixture.status.set(status);
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope));
        assertEquals(status, failure.getStatus());
      }
      assertEquals(6, fixture.calls.get());
      assertEquals(0, redirected.get());
    }
  }

  @Test
  void configuredTimeoutSurvivesScopeCopiesAndBoundsWholeResponse() throws Exception {
    try (var fixture = new Fixture()) {
      var release = new CountDownLatch(1);
      fixture.behavior.set(exchange -> {
        exchange.sendResponseHeaders(200, 0);
        try (var output = exchange.getResponseBody()) {
          // A continuous trickle must not turn the one-second request deadline into an idle timeout.
          while (!release.await(100, TimeUnit.MILLISECONDS)) {
            output.write(' ');
            output.flush();
          }
        } catch (IOException cancelled) {
          // Expected when the client aborts the response at its deadline.
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      });
      try {
        var failure = assertTimeoutPreemptively(Duration.ofSeconds(3),
            () -> assertThrows(Utils.Http.Client.RequestFailure.class,
                () -> fixture.client.deleteFlow(FLOW_ID, fixture.scope)));
        assertEquals(200, failure.getStatus());
        assertRedacted(failure);
        assertEquals(1, fixture.calls.get());
      } finally {
        release.countDown();
      }
    }
  }

  private static void assertRedacted(Throwable failure) {
    var stack = new StringWriter();
    failure.printStackTrace(new PrintWriter(stack));
    assertFalse(stack.toString().contains("secret"));
    assertFalse(stack.toString().contains(FLOW_ID));
  }

  @FunctionalInterface
  private interface ResponseBehavior {
    void respond(HttpExchange exchange) throws IOException;
  }

  private static final class Fixture implements AutoCloseable {
    final HttpServer server;
    final Utils.Http.Client http;
    final ResourcesClient client;
    final SessionScope scope = mock(SessionScope.class);
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicReference<String> body = new AtomicReference<>("true");
    final AtomicInteger calls = new AtomicInteger();
    final AtomicReference<String> method = new AtomicReference<>();
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<Map<String, List<String>>> headers = new AtomicReference<>();
    final AtomicReference<ResponseBehavior> behavior = new AtomicReference<>(this::respond);

    Fixture() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        calls.incrementAndGet();
        method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().toString());
        // Headers preserves HTTP's case-insensitive lookup.
        headers.set(exchange.getRequestHeaders());
        exchange.getRequestBody().readAllBytes();
        behavior.get().respond(exchange);
      });
      server.start();
      http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)
          .withTimeout(1).withAuthentication("service-secret")
          .withHeader("X-Test-Header", "unchanged").accepting(List.of("application/json"));
      http.setAuthorization("base-secret");
      var user = mock(UserIdentity.class);
      when(user.getId()).thenReturn("scope-secret");
      when(user.isAnonymous()).thenReturn(true);
      when(scope.getUser()).thenReturn(user);
      when(scope.getId()).thenReturn("session-id");
      when(scope.getHostServiceId()).thenReturn("host-service");
      client = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(client, http);
    }

    private void respond(HttpExchange exchange) throws IOException {
      var bytes = body.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      if (bytes.length == 0 || status.get() == 204) {
        exchange.sendResponseHeaders(status.get(), -1);
        exchange.close();
      } else {
        exchange.sendResponseHeaders(status.get(), bytes.length);
        try (var output = exchange.getResponseBody()) {
          output.write(bytes);
        }
      }
    }

    @Override
    public void close() throws Exception {
      server.stop(0);
      http.close();
    }
  }
}
