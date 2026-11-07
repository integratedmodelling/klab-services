package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.junit.jupiter.api.Test;

class ResourcesInitializationTransportTest {
  private ResourcesClient client(Utils.Http.Client http) throws Exception {
    var client = mock(ResourcesClient.class, CALLS_REAL_METHODS);
    var field = BaseServiceClient.class.getDeclaredField("client");
    field.setAccessible(true); field.set(client, http); return client;
  }

  private Flow.InitializationRequest request() {
    var state = Flow.State.create(); state.setSchemaId("bootstrap"); state.setAssetUrn("test.ontology");
    state.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY); state.setTitle("Named bootstrap");
    var request = Flow.InitializationRequest.create(); request.setInitialState(state); return request;
  }

  @Test void definiteValidationRejectionsRemainDistinctFromUncertainServerFailuresWithoutAutomaticRetry() throws Exception {
    var status = new AtomicInteger(400); var calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.RESOURCES.FLOW_INITIALIZATION, exchange -> {
      exchange.getRequestBody().readAllBytes(); calls.incrementAndGet();
      var body = "{\"detail\":\"private server payload\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
      exchange.sendResponseHeaders(status.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var resources = client(http);
      for (int expected : new int[]{400, 422, 500}) {
        status.set(expected); int before = calls.get();
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> resources.initializeFlow("ontology-review-pilot", request(), null));
        assertEquals(expected, failure.getStatus());
        assertFalse(failure.getMessage().contains("private server payload"));
        assertEquals(before + 1, calls.get(), "Creation must never be automatically repeated after an error");
      }
    } finally { server.stop(0); }
  }

  @Test void anUndecodableSuccessfulCreationHasUnknownOutcomeRatherThanValidationStatus() throws Exception {
    var calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.RESOURCES.FLOW_INITIALIZATION, exchange -> {
      exchange.getRequestBody().readAllBytes(); calls.incrementAndGet();
      var body = "{broken JSON".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var resources = client(http);
      var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> resources.initializeFlow("ontology-review-pilot", request(), null));
      assertEquals(0, failure.getStatus()); assertEquals(1, calls.get());
      server.stop(0);
      var disconnected = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> resources.initializeFlow("ontology-review-pilot", request(), null));
      assertEquals(0, disconnected.getStatus()); assertEquals(1, calls.get());
    } finally { server.stop(0); }
  }

  @Test void successfulFlowUsesOrdinaryResourceResponseSizesNotTheDiagnosticLimit() throws Exception {
    var body = ("{\"id\":\"new-flow\",\"metadata\":{\"context\":\"" + "x".repeat(1_100_000) + "\"}}")
        .getBytes(StandardCharsets.UTF_8);
    assertTrue(body.length > 1024 * 1024);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.RESOURCES.FLOW_INITIALIZATION, exchange -> {
      exchange.getRequestBody().readAllBytes(); exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var flow = client(http).initializeFlow("ontology-review-pilot", request(), null);
      assertEquals("new-flow", flow.getId()); assertEquals(1_100_000, flow.getMetadata().get("context").toString().length());
    } finally { server.stop(0); }
  }
}
