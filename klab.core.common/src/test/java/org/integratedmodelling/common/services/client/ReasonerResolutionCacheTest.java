package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.junit.jupiter.api.Test;

class ReasonerResolutionCacheTest {
  @Test void serverFailureIsNotConvertedToNothing() throws Exception {
    var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "{\"detail\":\"Semantic compilation failed: tableau unavailable\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(500, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = org.integratedmodelling.common.utils.Utils.Http.getClient(
        "http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true); field.set(client, http);
      for (boolean caches : new boolean[] {false, true}) {
        set(client, "useCaches", caches);
        set(client, "concepts", Caffeine.newBuilder().build());
        set(client, "observables", Caffeine.newBuilder().build());
        for (int attempt = 0; attempt < 2; attempt++) {
          assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,
              () -> client.resolveConcept("earth:Freshwater earth:Region"));
          assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,
              () -> client.resolveObservable("earth:Freshwater earth:Region"));
        }
      }
    } finally { server.stop(0); }
  }
  @Test void failedResolutionsAreRetriedButSuccessfulResolutionsAreCached() throws Exception {
    var previous = org.integratedmodelling.klab.api.Klab.INSTANCE.getConfiguration();
    try {
    org.integratedmodelling.klab.api.Klab.INSTANCE.setConfiguration(
        new org.integratedmodelling.common.configuration.CommonConfiguration());
    var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
    set(client, "useCaches", true);
    set(client, "concepts", Caffeine.newBuilder().build());
    set(client, "observables", Caffeine.newBuilder().build());
    String expression = "each earth:StreamConnection linking earth:StreamJunction to earth:StreamJunction";
    var concept = new ConceptImpl();
    concept.setUrn(expression);
    concept.getType().add(SemanticType.RELATIONSHIP);
    var observable = new ObservableImpl();
    observable.setSemantics(concept); observable.setUrn(expression);
    var rejectedConcept = Concept.nothing();
    var rejectedObservable = Observable.nothing(null);
    doReturn(null, rejectedConcept, concept).when(client).resolveConceptInternal(expression);
    doReturn(null, rejectedObservable, observable).when(client).resolveObservableInternal(expression);
    assertTrue(client.resolveConcept(expression).is(SemanticType.NOTHING));
    assertSame(rejectedConcept, client.resolveConcept(expression));
    assertSame(concept, client.resolveConcept(expression));
    assertSame(concept, client.resolveConcept(expression));
    assertTrue(client.resolveObservable(expression).is(SemanticType.NOTHING));
    assertSame(rejectedObservable, client.resolveObservable(expression));
    assertSame(observable, client.resolveObservable(expression));
    assertSame(observable, client.resolveObservable(expression));
    verify(client, times(3)).resolveConceptInternal(expression);
    verify(client, times(3)).resolveObservableInternal(expression);
    } finally { org.integratedmodelling.klab.api.Klab.INSTANCE.setConfiguration(previous); }
  }

  private static void set(Object target, String name, Object value) throws Exception {
    var field = ReasonerClient.class.getDeclaredField(name);
    field.setAccessible(true); field.set(target, value);
  }
}
