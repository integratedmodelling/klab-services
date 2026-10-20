package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.junit.jupiter.api.Test;

class ReasonerDistanceRevisionTransportTest {
  @Test void olderServerCannotAnswerRevisionBoundRequestThroughItsLegacyScalarRoute() throws Exception {
    var legacyCalls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/reasoner/api/v1/distance", exchange -> {
      exchange.getRequestBody().readAllBytes();
      boolean legacyPath = exchange.getRequestURI().getPath().equals("/reasoner/api/v1/distance");
      if (legacyPath) legacyCalls.incrementAndGet();
      var body = (legacyPath ? "0" : "{}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(legacyPath ? 200 : 404, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort() + "/reasoner", null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var concept = new ConceptImpl(); concept.setUrn("w:Value");
      var error = assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,
          () -> client.semanticDistance(concept, concept, null, 42));
      assertTrue(error.getMessage().contains("HTTP 404"));
      assertEquals(0, legacyCalls.get(), "Missing validated route must not fall back to legacy arithmetic");
      server.createContext("/reasoner/api/v1/distance/validated", exchange -> {
        exchange.getRequestBody().readAllBytes();
        var body = new byte[0];
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
      });
      var missing = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> client.semanticDistance(concept, concept, null, 42));
      assertTrue(missing.getMessage().contains("no score"), "A missing scalar must not become an unboxing NPE");
      assertEquals(0, legacyCalls.get());
    } finally { server.stop(0); }
  }

  @Test void unsupportedRevisionAwareReasonerCannotFallBackToUnversionedArithmetic() {
    var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class, CALLS_REAL_METHODS);
    var a = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    var b = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    assertTrue(reasoner.semanticDistance(a, b, null, 42) < 0);
    verify(reasoner, never()).semanticDistance(a, b, null);
  }

  @Test void observerHierarchyUsesSameGenerationAndRejectsIfGenerationChangesAfterMetric() throws Exception {
    var staleObserver = new AtomicBoolean(false); var queries = new ArrayList<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/reasoner/api/v1/distance/validated", exchange -> {
      exchange.getRequestBody().readAllBytes();
      var query = exchange.getRequestURI().getRawQuery();
      synchronized (queries) { queries.add(query); }
      boolean asserted = query != null && query.contains("asserted=true");
      int score = asserted ? staleObserver.get() ? -1 : 2 : 6;
      var body = Integer.toString(score).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort() + "/reasoner", null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var concept = new ConceptImpl(); concept.setUrn("w:Value");
      var modelObserver = new ConceptImpl(); modelObserver.setUrn("w:Observer");
      var requestObserver = new ConceptImpl(); requestObserver.setUrn("w:SpecialObserver");
      var output = mock(Observable.class); var request = mock(Observable.class);
      when(output.asConcept()).thenReturn(concept); when(request.asConcept()).thenReturn(concept);
      when(output.getObserverSemantics()).thenReturn(modelObserver);
      when(request.getObserverSemantics()).thenReturn(requestObserver);
      assertEquals(8, client.semanticDistance(output, request, null, 42));
      staleObserver.set(true);
      assertTrue(client.semanticDistance(output, request, null, 42) < 0,
          "A valid metric cannot mask stale observer hierarchy admission");
      assertEquals(4, queries.size());
      assertTrue(queries.stream().allMatch(q -> q != null && q.contains("knowledgeRevision=42")), queries.toString());
      assertEquals(2, queries.stream().filter(q -> q.contains("asserted=true")).count());
    } finally { server.stop(0); }
  }

  @Test void expectedRevisionTravelsOnEveryRequestAndObservableRepresentationStillContributes() throws Exception {
    var score = new AtomicInteger(6); var queries = new ArrayList<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/reasoner/api/v1/distance/validated", exchange -> {
      exchange.getRequestBody().readAllBytes();
      synchronized (queries) { queries.add(exchange.getRequestURI().getRawQuery()); }
      var body = Integer.toString(score.get()).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort() + "/reasoner", null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var concept = new ConceptImpl(); concept.setUrn("w:Value");
      var observer = new ConceptImpl(); observer.setUrn("w:Observer");
      var output = mock(Observable.class); var request = mock(Observable.class);
      when(output.asConcept()).thenReturn(concept); when(request.asConcept()).thenReturn(concept);
      when(request.getObserverSemantics()).thenReturn(observer);
      assertEquals(7, client.semanticDistance(output, request, null, 42), "The local observer representation adds one");
      score.set(-50);
      assertEquals(-50, client.semanticDistance(output, request, null, 42), "A same-key retry must reach the server, not reuse an earlier score");
      assertTrue(client.semanticDistance(output, request, null, -1) < 0);
      assertEquals(2, queries.size());
      assertTrue(queries.stream().allMatch(q -> q != null && q.contains("knowledgeRevision=42")), queries.toString());
    } finally { server.stop(0); }
  }
}
