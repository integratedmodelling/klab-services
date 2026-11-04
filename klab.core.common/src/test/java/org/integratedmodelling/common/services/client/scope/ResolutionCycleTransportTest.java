package org.integratedmodelling.common.services.client.scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.junit.jupiter.api.Test;

class ResolutionCycleTransportTest {
  private static ResolutionCycleBinding binding(String context) {
    return new ResolutionCycleBinding(
        1,
        "cycle",
        context,
        "runtime",
        true,
        new SemanticCycleBinding(
            SemanticCycleBinding.VERSION,
            "reasoner",
            "session",
            7,
            "semantic-content-v2:" + "a".repeat(64),
            "lease"));
  }

  private static UserIdentity user() {
    var user = mock(UserIdentity.class);
    when(user.getId()).thenReturn("user");
    when(user.getUsername()).thenReturn("tester");
    when(user.getData()).thenReturn(Parameters.create());
    return user;
  }

  @Test
  void copiedClientScopesKeepFrozenBindingWithoutMutatingTheRoot() throws Exception {
    var runtime = mock(RuntimeClient.class);
    when(runtime.serviceId()).thenReturn("runtime");
    when(runtime.getUrl()).thenReturn(java.net.URI.create("http://127.0.0.1:8094/runtime").toURL());
    var parent =
        new ClientSessionScope(new ClientUserScope(user(), null), "session", runtime)
            .withId("session");
    var scope =
        new ClientContextScope(
            parent,
            runtime,
            DigitalTwin.Configuration.builder().id("context").name("Twin").build());
    scope.setId("context");
    var binding = binding("context");
    var frozen = scope.withResolutionCycleBinding(binding);
    assertEquals(binding, frozen.withObserver(null).getResolutionCycleBinding());
    assertNull(scope.getResolutionCycleBinding());
    assertThrows(
        IllegalArgumentException.class, () -> scope.withResolutionCycleBinding(binding("other")));
  }

  @Test
  void ordinaryAndConsequenceRequestsTransportTheSameBindingAndReuseClearsIt() throws Exception {
    var received = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          received.set(exchange.getRequestHeaders().getFirst(ServicesAPI.RESOLUTION_CYCLE_HEADER));
          var body = "ok".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
    try (var http =
        Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var actor = user();
      var scope = mock(ContextScope.class);
      when(scope.getId()).thenReturn("context");
      when(scope.getUser()).thenReturn(actor);
      when(scope.getHostServiceId()).thenReturn("runtime");
      when(scope.getResolutionCycleBinding()).thenReturn(binding("context"));
      var client = http.withScope(scope);
      assertEquals("ok", client.get("/probe", String.class));
      assertEquals(
          binding("context"),
          Utils.Json.parseObject(
              new String(Base64.getDecoder().decode(received.get()), StandardCharsets.UTF_8),
              ResolutionCycleBinding.class));
      var cause =
          new org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause(
              1,
              "context",
              1,
              2,
              0,
              null,
              null,
              "decision",
              org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind.CHANGE_PROCESS);
      when(scope.getConsequenceCause()).thenReturn(cause);
      assertEquals("ok", client.withScope(scope).get("/probe", String.class));
      assertNotNull(received.get());
      when(scope.getResolutionCycleBinding()).thenReturn(null);
      when(scope.getConsequenceCause()).thenReturn(null);
      assertEquals("ok", client.withScope(scope).get("/probe", String.class));
      assertNull(received.get());
    } finally {
      server.stop(0);
    }
  }
}
