package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.objects.ObservationExportRequest;
import org.junit.jupiter.api.Test;

class ObservationViewExportTransportTest {
  @Test
  void exportsPortableViewRatherThanLookingUpItsSourceUrn() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var body = new AtomicReference<String>();
    var method = new AtomicReference<String>();
    var accept = new AtomicReference<String>();
    var memberRequest = new AtomicReference<String>();
    server.createContext(ServicesAPI.EXPORT_OBSERVATION, exchange -> {
      method.set(exchange.getRequestMethod());
      accept.set(exchange.getRequestHeaders().getFirst("Accept"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = new byte[] {0, 1, (byte) 255};
      exchange.sendResponseHeaders(200, bytes.length);
      try (var out = exchange.getResponseBody()) { out.write(bytes); }
    });
    server.createContext(ServicesAPI.OBSERVATION_MEMBERS, exchange -> {
      memberRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      var member = new ObservationImpl(); member.setId(42);
      byte[] bytes = JacksonConfiguration.newObjectMapper().writeValueAsBytes(List.of(member));
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var out = exchange.getResponseBody()) { out.write(bytes); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(RuntimeClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(client, http);
      var view = new ObservationImpl();
      view.setId(Observation.QUERY_ID);
      view.setUrn("test:source");
      view.setGeometry(Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
      view.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(42L));
      view.mergeAnnotations(List.of(Annotation.of("colormap", "palette", "terrain")), 2);
      try (var stream = client.exportObservation(view, "image/png",
          Parameters.create("viewportX", 123), null)) {
        assertArrayEquals(new byte[] {0, 1, (byte) 255}, stream.readAllBytes());
      }
      assertEquals("POST", method.get());
      assertEquals("image/png", accept.get());
      var request = JacksonConfiguration.newObjectMapper()
          .readValue(body.get(), ObservationExportRequest.class);
      assertEquals(0, request.getObservation().getId());
      assertEquals("test:source", request.getObservation().getUrn());
      assertEquals(view.getGeometry().encode(), request.getObservation().getGeometry().encode());
      assertEquals("terrain", request.getObservation().getAnnotations().iterator().next().get("palette"));
      assertEquals(123, ((Number) request.getParameters().get("viewportX")).intValue());
      assertEquals(42, client.getMembers(view, null).getFirst().getId());
      var memberView = JacksonConfiguration.newObjectMapper()
          .readValue(memberRequest.get(), Observation.class);
      assertEquals(0, memberView.getId());
      assertEquals(view.getGeometry().encode(), memberView.getGeometry().encode());
    } finally { server.stop(0); }
  }
}
