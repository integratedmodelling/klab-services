package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class FederatedIdentityTransportTest {
  private ContextScope scope(String username, String token) {
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn(username);
    when(user.getId()).thenReturn(token);
    var data = Parameters.<String>create();
    data.put(UserIdentity.FEDERATION_DATA_PROPERTY, new Federation("test.federation", null));
    when(user.getData()).thenReturn(data);
    var scope = mock(ContextScope.class);
    when(scope.getId()).thenReturn("test_federation.context");
    when(scope.getUser()).thenReturn(user);
    when(scope.getHostServiceId()).thenReturn("runtime");
    return scope;
  }

  @Test void reusedClientPropagatesEachActorsCredentialAndClearsPreviousFocus() throws Exception {
    var received = new AtomicReference<Map<String, String>>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      var headers = new HashMap<String, String>();
      for (var key : new String[]{"Authorization", ServicesAPI.USERNAME_HEADER,
          ServicesAPI.FEDERATION_HEADER, ServicesAPI.SCOPE_HEADER, ServicesAPI.TRANSACTION_ID_HEADER}) {
        headers.put(key, exchange.getRequestHeaders().getFirst(key));
      }
      received.set(headers);
      var body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var alice = scope("alice", "alice-credential");
      var bob = scope("bob", "bob-credential");
      when(alice.getTransactionId()).thenReturn("alice-transaction");
      var scoped = http.withScope(alice);
      assertEquals("ok", scoped.get("/probe", String.class));
      assertEquals("alice-credential", received.get().get("Authorization"));
      assertEquals("alice", received.get().get(ServicesAPI.USERNAME_HEADER));
      assertEquals("alice-transaction", received.get().get(ServicesAPI.TRANSACTION_ID_HEADER));
      assertEquals("ok", scoped.withScope(bob).get("/probe", String.class));
      assertEquals("bob-credential", received.get().get("Authorization"));
      assertEquals("bob", received.get().get(ServicesAPI.USERNAME_HEADER));
      assertEquals("test.federation", received.get().get(ServicesAPI.FEDERATION_HEADER));
      assertNull(received.get().get(ServicesAPI.TRANSACTION_ID_HEADER));
      var anonymous = scope("anonymous", ServicesAPI.ANONYMOUS_TOKEN);
      when(anonymous.getUser().isAnonymous()).thenReturn(true);
      assertEquals("ok", scoped.withScope(anonymous).get("/probe", String.class));
      assertEquals(ServicesAPI.ANONYMOUS_TOKEN, received.get().get("Authorization"));
      assertNull(received.get().get(ServicesAPI.USERNAME_HEADER));
      assertNull(received.get().get(ServicesAPI.FEDERATION_HEADER));
      assertEquals("ok", scoped.withScope(scope("bob", null)).get("/probe", String.class));
      assertNull(received.get().get("Authorization"));
    } finally { server.stop(0); }
  }
}
