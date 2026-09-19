package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpDownloadTest {
  @Test void mediaTypeSurvivesQueryDecodingAndErrorsNeverBecomeDownloadedGeoJson() throws Exception {
    var query = new AtomicReference<String>();
    var status = new AtomicInteger(200);
    var body = new AtomicReference<>("{\"type\":\"FeatureCollection\",\"features\":[]}");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
      byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status.get(), bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      client.get("/resolveExportSchema", Map.class, "mediaType", "application/geo+json");
      assertEquals("mediaType=application/geo+json", query.get());
      var file = client.accepting(List.of("application/geo+json"))
          .download("/export", "mediaType", "application/geo+json");
      try { assertEquals(body.get(), Files.readString(file.toPath())); }
      finally { Files.deleteIfExists(file.toPath()); }
      assertEquals("mediaType=application/geo+json", query.get());
      for (int code : new int[] {400, 403, 500}) {
        status.set(code);
        body.set("{\"body\":{\"detail\":\"No export schema is available\\nstack trace\"}}");
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> client.download("/export", "mediaType", "application/geo+json"));
        assertEquals(code, failure.getStatus());
        assertTrue(failure.getMessage().contains("No export schema is available"));
        assertFalse(failure.getMessage().contains("stack trace"));
      }
    } finally { server.stop(0); }
  }
}
