package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.junit.jupiter.api.Test;

class ExportSchemaTransportTest {
  @Test void schemaMediaTypesTravelAsQueryParameters() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var request = new AtomicReference<String>();
    server.createContext("/", exchange -> {
      request.set(URLDecoder.decode(exchange.getRequestURI().toString(), StandardCharsets.UTF_8));
      byte[] body = "{\"empty\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var resources = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(resources, http);
      assertNotNull(resources.resolve("export-schema:image/png", KnowledgeClass.INFORMATION, null));
      assertEquals("/resolveExportSchema?mediaType=image/png", request.get());
      resources.resolve("import-schema:image/tiff;application=geotiff", KnowledgeClass.INFORMATION, null);
      assertEquals("/resolveImportSchema?mediaType=image/tiff;application=geotiff", request.get());
    } finally { server.stop(0); }
  }
}
