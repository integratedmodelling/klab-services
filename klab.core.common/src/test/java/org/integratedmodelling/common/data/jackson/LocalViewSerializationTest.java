package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.LocalView;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.junit.jupiter.api.Test;

class LocalViewSerializationTest {
  public interface DeclaredValue { String getName(); }

  @Test void aDeclaredInterfaceSerializerCannotBypassTheLocalViewMarker() {
    class View implements DeclaredValue, LocalView {
      public String getName() { throw new AssertionError("Guard must run before reading properties"); }
    }
    var mapper = JacksonConfiguration.newObjectMapper();
    var failure = assertThrows(JsonMappingException.class,
        () -> mapper.writerFor(DeclaredValue.class).writeValueAsString(new View()));
    assertTrue(JacksonConfiguration.isLocalViewSerializationFailure(failure));
    DeclaredValue portable = () -> "portable";
    assertEquals("{\"name\":\"portable\"}", assertDoesNotThrow(
        () -> mapper.writerFor(DeclaredValue.class).writeValueAsString(portable)));
  }

  private Concept proxy(boolean marked) {
    var concept = new ConceptImpl(); concept.setUrn("earth:StreamJunction");
    return (Concept) Proxy.newProxyInstance(getClass().getClassLoader(),
        marked ? new Class<?>[] {Concept.class, LocalView.class} : new Class<?>[] {Concept.class},
        (proxy, method, arguments) -> method.invoke(concept, arguments));
  }

  @Test void rejectsMarkedAndUnmarkedDomainProxiesWithAnActionableDiagnostic() {
    for (boolean marked : List.of(false, true)) {
      var mapper = JacksonConfiguration.newObjectMapper();
      var value = proxy(marked);
      for (var writer : List.of(mapper.writer(), mapper.writerFor(Concept.class))) {
        var failure = assertThrows(JsonMappingException.class, () -> writer.writeValueAsString(value));
        assertTrue(JacksonConfiguration.isLocalViewSerializationFailure(failure));
        assertTrue(failure.getMessage().contains("Concept"));
        assertTrue(failure.getMessage().contains("explicit transport representation"));
        assertFalse(failure.getMessage().contains("Proxy.h"));
      }
    }
  }

  @Test void reportsTheNestedPayloadPathAndRetainsTheDiagnosticThroughRedaction() {
    var mapper = JacksonConfiguration.newObjectMapper();
    var failure = assertThrows(JsonMappingException.class,
        () -> mapper.writeValueAsString(Map.of("endpoints", List.of(proxy(true)))));
    assertTrue(failure.getPathReference().contains("endpoints"));
    assertTrue(failure.getPathReference().contains("[0]"));
    assertTrue(Logging.safeFailure(failure).getMessage().contains("LocalViewSerializationException"));
    var wrapped = assertThrows(IllegalArgumentException.class,
        () -> Utils.Json.asString(Map.of("endpoints", List.of(proxy(true)))));
    assertTrue(JacksonConfiguration.isLocalViewSerializationFailure(wrapped));
  }

  @Test void markerAlsoGuardsValuesOutsideTheReflectiveDomainSerializer() {
    var mapper = JacksonConfiguration.newObjectMapper();
    var view = new LocalView() {};
    var failure = assertThrows(JsonMappingException.class,
        () -> mapper.writeValueAsString(List.of(view)));
    assertTrue(JacksonConfiguration.isLocalViewSerializationFailure(failure));
  }

  @Test void evenBestEffortPostMustPropagateLocalPayloadErrorsWithoutSendingARequest() throws Exception {
    var requests = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
    });
    server.start();
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var payload = Map.of("endpoints", List.of(proxy(true)));
      var failure = assertThrows(KlabServiceAccessException.class,
          () -> client.post("/relationships", payload, Boolean.class));
      assertTrue(JacksonConfiguration.isLocalViewSerializationFailure(failure));
      assertThrows(KlabServiceAccessException.class,
          () -> client.postQuietly("/relationships", payload, Boolean.class));
      assertThrows(KlabServiceAccessException.class,
          () -> client.put("/relationships", payload, Boolean.class));
      assertThrows(KlabServiceAccessException.class,
          () -> client.postCollection("/relationships", payload, Boolean.class));
      assertThrows(KlabServiceAccessException.class,
          () -> client.putCollection("/relationships", payload, Boolean.class));
      var async = assertThrows(java.util.concurrent.CompletionException.class,
          () -> client.postAsync("/relationships", payload, Boolean.class).join());
      assertTrue(JacksonConfiguration.isLocalViewSerializationFailure(async));
      assertEquals(0, requests.get());
    } finally { server.stop(0); }
  }
}
