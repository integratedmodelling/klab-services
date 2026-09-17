package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

class ProjectSettingsTransportTest {
  @Test void projectCoordinatesStayInBodyAndHttpFailuresAreNotEmptyResults() throws Exception {
    var path = new AtomicReference<String>(); var payload = new AtomicReference<String>();
    var status = new AtomicInteger(200);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      path.set(exchange.getRequestURI().getRawPath());
      payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort() + "/resources", null)) {
      var client = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var project = new ProjectImpl(); project.setUrn("local/imod");
      project.getSettings().getMetadata().put("klab.user.observer", "agency:Volitional society:HumanIndividual");
      assertTrue(client.submit(project, ResourcesService.SubmissionMode.REPLACE, null).isEmpty());
      assertEquals("/resources/api/v1/submit/PROJECT/REPLACE/imod", path.get());
      var sent = Utils.Json.parseObject(payload.get(), ProjectImpl.class);
      assertEquals("local/imod", sent.getUrn()); assertNull(sent.getManifest());
      assertEquals("agency:Volitional society:HumanIndividual", sent.getSettings().getMetadata().get("klab.user.observer"));
      for (int code : new int[]{400, 403, 500}) {
        status.set(code);
        var failure = assertThrows(Utils.Http.Client.RequestFailure.class,
            () -> client.submit(project, ResourcesService.SubmissionMode.REPLACE, null));
        assertEquals(code, failure.getStatus()); assertTrue(failure.getMessage().contains("HTTP " + code));
      }
    } finally { server.stop(0); }
  }
}
