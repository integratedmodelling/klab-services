package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.junit.jupiter.api.Test;

/** Real ResourcesClient, production JSON mapping and HTTP, invoked through ResourcesService. */
class ResourcesStageDetailsHttpTest {
  private static final String FLOW = "review-flow";
  private static final String STATE = "bootstrap-stage";
  private static final String PATH = ServicesAPI.RESOURCES.FLOW_STATE
      .replace("{flowId}", FLOW).replace("{stateId}", STATE) + "/details";
  private static final Flow.StateDetailsUpdate UPDATE =
      new Flow.StateDetailsUpdate(7, "Named stage", "Editorial scope");

  @Test void directAndMergedServicesSaveTheRevisionCheckedDtoThroughProductionSerialization() throws Exception {
    try (var fixture = new Fixture()) {
      for (ResourcesService resources : List.of(fixture.client, fixture.merger)) {
        var result = resources.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope);
        assertEquals(FLOW, result.getId());
        assertEquals(8, result.getRevision());
        assertEquals(UPDATE.title(), result.getStates().get(STATE).getTitle());
        assertEquals(UPDATE.description(), result.getStates().get(STATE).getDescription());
        assertEquals("POST", fixture.method.get());
        assertEquals(PATH, fixture.path.get());
        assertEquals(UPDATE, JacksonConfiguration.newObjectMapper()
            .readValue(fixture.request.get(), Flow.StateDetailsUpdate.class));
        var json = JacksonConfiguration.newObjectMapper().readTree(fixture.request.get());
        assertEquals(3, json.size(), "Do not round-trip protected stage data");
        assertEquals(List.of("scope-secret"), fixture.headers.get().get("Authorization"));
        assertEquals(List.of("service-secret"), fixture.headers.get().get("Authentication"));
        assertEquals(List.of("session-id"), fixture.headers.get().get(ServicesAPI.SCOPE_HEADER));
        assertEquals(List.of("host-service"), fixture.headers.get().get(ServicesAPI.SERVICE_ID_HEADER));
      }
      assertEquals(2, fixture.calls.get());
      verify(fixture.secondary, never()).updateFlowStateDetails(any(), any(), any(), any());
    }
  }

  @Test void conflictPermissionValidationAndServerFailuresPreserveStatusWithoutFailover() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.body.set("{\"detail\":\"private-response-secret\"}");
      for (int status : new int[] {400, 401, 403, 404, 409, 422, 500, 503}) {
        fixture.status.set(status);
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> fixture.merger.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope));
        assertEquals(status, failure.getStatus());
        assertFalse(failure.getMessage().contains("secret"));
      }
      assertEquals(8, fixture.calls.get(), "Each explicit save gets exactly one HTTP request");
      verify(fixture.secondary, never()).updateFlowStateDetails(any(), any(), any(), any());
    }
  }

  @Test void emptyMalformedWrongAndUnchangedResponsesNeverClaimThatDetailsWereSaved() throws Exception {
    try (var fixture = new Fixture()) {
      for (String body : List.of("", "null", "{}", "{broken", "[]", "true",
          Utils.Json.asString(flow("another-flow", 8)), Utils.Json.asString(flow(FLOW, 7)))) {
        fixture.body.set(body);
        assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> fixture.client.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope), body);
      }
      var missingStage = flow(FLOW, 8);
      missingStage.getStates().clear();
      fixture.body.set(Utils.Json.asString(missingStage));
      assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> fixture.client.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope));
      fixture.status.set(204);
      assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> fixture.client.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope));
      assertEquals(10, fixture.calls.get());
    }
  }

  @Test void aDroppedMutationResponseIsUnknownAndNeverRetried() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.behavior.set(HttpExchange::close);
      var failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
          () -> assertThrows(Utils.Http.Client.RequestFailure.class,
              () -> fixture.merger.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope)));
      assertEquals(0, failure.getStatus());
      assertEquals(1, fixture.calls.get());
      verify(fixture.secondary, never()).updateFlowStateDetails(any(), any(), any(), any());
    }
  }

  @Test void redirectsNeverReplayTheSaveAtAnotherEndpoint() throws Exception {
    try (var fixture = new Fixture()) {
      var redirected = new AtomicInteger();
      fixture.server.createContext("/redirected", exchange -> {
        redirected.incrementAndGet(); exchange.close();
      });
      fixture.behavior.set(exchange -> {
        exchange.getResponseHeaders().set("Location", "/redirected");
        exchange.sendResponseHeaders(fixture.status.get(), -1); exchange.close();
      });
      for (int status : new int[] {301, 302, 307, 308}) {
        fixture.status.set(status);
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> fixture.client.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope));
        assertEquals(status, failure.getStatus());
      }
      assertEquals(4, fixture.calls.get()); assertEquals(0, redirected.get());
    }
  }

  @Test void disconnectedServerFailsInsteadOfReturningNull() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.server.stop(0);
      var failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
          () -> assertThrows(Utils.Http.Client.RequestFailure.class,
              () -> fixture.client.updateFlowStateDetails(FLOW, STATE, UPDATE, fixture.scope)));
      assertEquals(0, failure.getStatus()); assertEquals(0, fixture.calls.get());
    }
  }

  private static Flow flow(String id, long revision) {
    var flow = Flow.create(); flow.setId(id); flow.setRevision(revision);
    var stage = Flow.State.create(); stage.setId(STATE); stage.setSchemaId("bootstrap");
    stage.setTitle(UPDATE.title()); stage.setDescription(UPDATE.description());
    flow.getStates().put(STATE, stage); flow.getCurrentStateIds().add(STATE);
    return flow;
  }

  @FunctionalInterface private interface ResponseBehavior {
    void respond(HttpExchange exchange) throws IOException;
  }

  private static final class Fixture implements AutoCloseable {
    final HttpServer server;
    final Utils.Http.Client http;
    final ResourcesService client;
    final ResourcesService merger;
    final ResourcesService secondary = mock(ResourcesService.class);
    final SessionScope scope = mock(SessionScope.class);
    final AtomicInteger calls = new AtomicInteger();
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicReference<String> body = new AtomicReference<>(Utils.Json.asString(flow(FLOW, 8)));
    final AtomicReference<String> method = new AtomicReference<>();
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> request = new AtomicReference<>();
    final AtomicReference<Map<String, List<String>>> headers = new AtomicReference<>();
    final AtomicReference<ResponseBehavior> behavior = new AtomicReference<>(this::respond);

    Fixture() throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        calls.incrementAndGet(); method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().toString()); headers.set(exchange.getRequestHeaders());
        request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        behavior.get().respond(exchange);
      });
      server.start();
      http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)
          .withTimeout(1).withAuthentication("service-secret");
      var user = mock(UserIdentity.class);
      when(user.getId()).thenReturn("scope-secret"); when(user.isAnonymous()).thenReturn(true);
      when(scope.getIdentity()).thenReturn(user); when(scope.getUser()).thenReturn(user);
      when(scope.getId()).thenReturn("session-id"); when(scope.getHostServiceId()).thenReturn("host-service");
      var monitor = mock(ServiceClientCatalog.ClientMonitor.class);
      when(monitor.getClient()).thenReturn(http); when(monitor.getType()).thenReturn(KlabService.Type.RESOURCES);
      when(monitor.getServiceId()).thenReturn("primary");
      client = new ResourcesClient(monitor, scope, null);
      when(secondary.serviceId()).thenReturn("secondary");
      doReturn(List.of(client, secondary)).when(scope).getServices(ResourcesService.class);
      merger = new ResourcesMerger(scope);
    }

    private void respond(HttpExchange exchange) throws IOException {
      byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      if (bytes.length == 0 || status.get() == 204) {
        exchange.sendResponseHeaders(status.get(), -1); exchange.close();
      } else {
        exchange.sendResponseHeaders(status.get(), bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
      }
    }

    @Override public void close() throws Exception { server.stop(0); http.close(); }
  }
}
