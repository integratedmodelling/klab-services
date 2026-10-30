package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.Test;

class ProjectLockTransportTest {
  @Test void lockAndUnlockUseTheSameRequestCredentialAsDocumentWrites() throws Exception {
    var credential = new AtomicReference<String>();
    var actor = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      credential.set(exchange.getRequestHeaders().getFirst("Authorization"));
      actor.set(exchange.getRequestHeaders().getFirst(ServicesAPI.USERNAME_HEADER));
      var body = "true".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var defaultIdentity = mock(UserIdentity.class); when(defaultIdentity.getId()).thenReturn("client-default");
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http.withIdentity(defaultIdentity));
      var user = mock(UserIdentity.class);
      when(user.getId()).thenReturn("editor-credential"); when(user.getUsername()).thenReturn("alice");
      var data = Parameters.<String>create(); data.put(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.local());
      when(user.getData()).thenReturn(data);
      var scope = mock(UserScope.class); when(scope.getUser()).thenReturn(user);
      assertTrue(client.lockProject("imod", scope));
      assertEquals("editor-credential", credential.get()); assertEquals("alice", actor.get());
      assertTrue(client.unlockProject("imod", scope));
      assertEquals("editor-credential", credential.get()); assertEquals("alice", actor.get());
    } finally { server.stop(0); }
  }
}
