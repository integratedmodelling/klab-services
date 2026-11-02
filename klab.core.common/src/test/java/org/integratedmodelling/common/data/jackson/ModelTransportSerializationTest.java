package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.junit.jupiter.api.Test;

/** Transport-only regression: no parser admission changes or expression evaluation. */
class ModelTransportSerializationTest {
  static class CountingOutput extends ByteArrayOutputStream {
    int flushes;
    @Override public void flush() throws IOException { flushes++; }
  }

  private KimNamespaceImpl namespace() {
    var namespace = new KimNamespaceImpl(); namespace.setUrn("transport.fixture");
    for (int i = 0; i < 20; i++) {
      var model = new KimModelImpl(); model.setUrn("transport.fixture.model" + i);
      namespace.getStatements().add(model);
    }
    return namespace;
  }

  @Test void nestedModelFieldsDoNotFlushTheTransportPerField() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var output = new CountingOutput();
    mapper.writeValue(output, namespace());
    assertTrue(output.flushes <= 1, "Only the outer mapper write may flush the response");
    var restored = mapper.readValue(output.toByteArray(), KimNamespace.class);
    assertEquals("transport.fixture", restored.getUrn());
    assertEquals(20, restored.getStatements().size());
    for (int i = 0; i < 20; i++) assertEquals("transport.fixture.model" + i, restored.getStatements().get(i).getUrn());
  }

  @Test void callerControlledStreamingDoesNotFlushOrCloseBeforeTheDocumentFinishes() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var output = new CountingOutput();
    var generator = mapper.getFactory().createGenerator(output);
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    generator.writeStartArray();
    mapper.writer().without(com.fasterxml.jackson.databind.SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
        .writeValue(generator, namespace());
    assertEquals(0, output.flushes, "Nested serialization must respect the outer writer's flush policy");
    generator.writeEndArray(); generator.close();
    assertEquals(20, mapper.readTree(output.toByteArray()).get(0).get("statements").size());
  }

  @Test void boundedTransportDoesNotAbortOnNestedFlushAmplification() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var output = new CountingOutput() {
      @Override public void flush() throws IOException {
        super.flush();
        if (flushes > 1) throw new IOException("controlled transport abort on unexpected nested flush");
      }
    };
    mapper.writeValue(output, namespace());
    assertEquals(20, mapper.readTree(output.toByteArray()).get("statements").size());
  }

  @Test void actualOutputFailuresRemainFailuresRatherThanTruncatedSuccess() {
    var mapper = JacksonConfiguration.newObjectMapper();
    var output = new OutputStream() {
      @Override public void write(int value) throws IOException {
        throw new IOException("controlled write failure");
      }
    };
    var failure = assertThrows(IOException.class, () -> mapper.writeValue(output, namespace()));
    assertTrue(failure.getMessage().contains("controlled write failure"));
  }

  @Test void nativeModelResponseCompletesAcrossLoopbackWithBoundedFlushes() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var flushes = new AtomicInteger();
    var failure = new AtomicReference<Throwable>();
    var server = com.sun.net.httpserver.HttpServer.create(
        new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    server.createContext("/model-transport-fixture", exchange -> {
      try (exchange) {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        mapper.writeValue(new FilterOutputStream(exchange.getResponseBody()) {
          @Override public void flush() throws IOException {
            if (flushes.incrementAndGet() > 1) throw new IOException("controlled nested-flush transport abort");
            super.flush();
          }
        }, namespace());
      } catch (Throwable error) { failure.set(error); }
    });
    server.start();
    try (var client = HttpClient.newBuilder().proxy(new java.net.ProxySelector() {
      @Override public java.util.List<java.net.Proxy> select(URI uri) { return java.util.List.of(java.net.Proxy.NO_PROXY); }
      @Override public void connectFailed(URI uri, java.net.SocketAddress address, IOException error) {}
    }).connectTimeout(Duration.ofSeconds(2)).build()) {
      var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
          + server.getAddress().getPort() + "/model-transport-fixture"))
          .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, response.statusCode());
      assertEquals(20, mapper.readValue(response.body(), KimNamespace.class).getStatements().size());
      assertTrue(flushes.get() <= 1);
      assertNull(failure.get());
    } finally { server.stop(0); }
  }
}
