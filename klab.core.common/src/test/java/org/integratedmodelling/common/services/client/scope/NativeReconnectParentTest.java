package org.integratedmodelling.common.services.client.scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.junit.jupiter.api.Test;

/** Local HTTP transport regression; not remote membership or private-parent authority proof. */
class NativeReconnectParentTest {

  @Test
  void reconnectUsesTheAuthorizedDescriptorParentNotTheCallersDefaultSession() throws Exception {
    try (var fixture = new Fixture("reconnect-parent-baseline")) {
      var unrelated = new ClientSessionScope(fixture.user, "caller default", fixture.runtime)
          .withId("caller-default");
      doReturn(unrelated).when(fixture.user).getUserSession(fixture.runtime);
      var context = (ClientContextScope) fixture.runtime.connectContext(fixture.descriptor, fixture.user);
      try {
        assertNotNull(context);
        assertEquals("actual-parent", ((ClientSessionScope) context.getParentScope()).getId());
        assertEquals("reader", context.getUser().getUsername());
        assertEquals("owner", context.getConfiguration().getOwner());
        verify(fixture.user, never()).getUserSession(any());
        verify(fixture.runtime, never()).declareSessionScope(any(), any(), any());
      } finally {
        if (context != null) {
          var parent = (ClientSessionScope) context.getParentScope();
          context.closePeer(); parent.closePeer();
        }
        unrelated.closePeer();
      }
    }
  }

  @Test
  void reconnectDoesNotNeedDefaultSessionCreationCapability() throws Exception {
    try (var fixture = new Fixture("reconnect-parent-no-default")) {
      doThrow(new AssertionError("Reconnection must not request or create a default session"))
          .when(fixture.user).getUserSession(any());
      var context = (ClientContextScope) fixture.runtime.connectContext(fixture.descriptor, fixture.user);
      try {
        assertNotNull(context);
        assertEquals("actual-parent", ((ClientSessionScope) context.getParentScope()).getId());
      } finally {
        if (context != null) {
          var parent = (ClientSessionScope) context.getParentScope();
          context.closePeer(); parent.closePeer();
        }
      }
    }
  }

  @Test
  void reconnectReusesAnExistingExactParentPeer() throws Exception {
    try (var fixture = new Fixture("reconnect-parent-existing")) {
      var parent = new ClientSessionScope(fixture.user, "existing", fixture.runtime).withId("actual-parent");
      ClientScopeManager.INSTANCE.register(parent);
      ClientContextScope context = null;
      try {
        context = (ClientContextScope) fixture.runtime.connectContext(fixture.descriptor, fixture.user);
        assertSame(parent, context.getParentScope());
        assertSame(context, fixture.runtime.connectContext(fixture.descriptor, fixture.user));
        assertEquals(List.of("POST /api/v1/connect"), fixture.requests);
        verify(fixture.user, never()).getUserSession(any());
      } finally { if (context != null) context.closePeer(); parent.closePeer(); }
    }
  }

  @Test
  void unrelatedDefaultPeerIsNotReboundOrEvicted() throws Exception {
    try (var fixture = new Fixture("reconnect-parent-unrelated")) {
      var unrelated = new ClientSessionScope(fixture.user, "default", fixture.runtime).withId("caller-default");
      ClientScopeManager.INSTANCE.register(unrelated);
      ClientContextScope context = null;
      try {
        context = (ClientContextScope) fixture.runtime.connectContext(fixture.descriptor, fixture.user);
        assertEquals("actual-parent", ((ClientSessionScope) context.getParentScope()).getId());
        assertSame(unrelated, ClientScopeManager.INSTANCE.getScope(
            fixture.runtime.serviceId(), "caller-default", "reader", ClientSessionScope.class));
        assertEquals("caller-default", unrelated.getId());
        verify(fixture.runtime, never()).declareSessionScope(any(), any(), any());
      } finally {
        if (context != null) {
          var parent = (ClientSessionScope) context.getParentScope();
          context.closePeer(); parent.closePeer();
        }
        unrelated.closePeer();
      }
    }
  }

  @Test void responseCannotChangeTheRequestedContext() throws Exception {
    rejectChangedDescriptor("reconnect-parent-wrong-context", "other-parent.shared", "reconnect-parent-wrong-context");
  }

  @Test void responseCannotChangeTheRuntime() throws Exception {
    rejectChangedDescriptor("reconnect-parent-wrong-runtime", "actual-parent.shared", "different-runtime");
  }

  @Test void responseMustIdentifyItsRuntime() throws Exception {
    rejectChangedDescriptor("reconnect-parent-missing-runtime", "actual-parent.shared", null);
  }

  private void rejectChangedDescriptor(String runtimeId, String id, String returnedRuntime) throws Exception {
    try (var fixture = new Fixture(runtimeId)) {
      fixture.response.set(DigitalTwin.Configuration.builder().id(id).name("changed")
          .serviceId(returnedRuntime).serverUrl(fixture.runtime.getUrl()).build());
      assertNull(fixture.runtime.connectContext(fixture.descriptor, fixture.user));
      assertNull(ClientScopeManager.INSTANCE.getScope(runtimeId, "actual-parent", "reader", ClientSessionScope.class));
      verify(fixture.user, never()).getUserSession(any());
      verify(fixture.runtime, never()).declareSessionScope(any(), any(), any());
    }
  }

  @Test
  void sameParentAndRuntimeDoNotAliasDifferentCallers() throws Exception {
    try (var first = new Fixture("reconnect-parent-principals", "actual-parent.shared", "reader");
         var second = new Fixture("reconnect-parent-principals", "actual-parent.shared", "collaborator")) {
      var a = (ClientContextScope) first.runtime.connectContext(first.descriptor, first.user);
      var b = (ClientContextScope) second.runtime.connectContext(second.descriptor, second.user);
      try {
        assertNotSame(a, b); assertNotSame(a.getParentScope(), b.getParentScope());
        assertEquals("reader", a.getUser().getUsername());
        assertEquals("collaborator", b.getUser().getUsername());
        assertEquals("owner", b.getConfiguration().getOwner());
      } finally { closeLocal(a); closeLocal(b); }
    }
  }

  @Test
  void sameParentAndCallerDoNotAliasDifferentRuntimes() throws Exception {
    try (var first = new Fixture("reconnect-parent-runtime-a");
         var second = new Fixture("reconnect-parent-runtime-b")) {
      var a = (ClientContextScope) first.runtime.connectContext(first.descriptor, first.user);
      var b = (ClientContextScope) second.runtime.connectContext(second.descriptor, second.user);
      try {
        assertNotSame(a.getParentScope(), b.getParentScope());
        assertEquals(first.runtime.serviceId(), ((ClientSessionScope) a.getParentScope()).getHostServiceId());
        assertEquals(second.runtime.serviceId(), ((ClientSessionScope) b.getParentScope()).getHostServiceId());
      } finally { closeLocal(a); closeLocal(b); }
    }
  }

  @Test
  void concurrentIndependentClientsPublishOneExactLocalParent() throws Exception {
    try (var first = new Fixture("reconnect-parent-concurrent", "actual-parent.first", "reader");
         var second = new Fixture("reconnect-parent-concurrent", "actual-parent.second", "reader");
         var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var ready = new CountDownLatch(2);
      Runnable overlap = () -> {
        ready.countDown();
        try { assertTrue(ready.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
      };
      first.beforeReply = overlap; second.beforeReply = overlap;
      var aFuture = executor.submit(() -> (ClientContextScope) first.runtime.connectContext(first.descriptor, first.user));
      var bFuture = executor.submit(() -> (ClientContextScope) second.runtime.connectContext(second.descriptor, second.user));
      var a = aFuture.get(10, TimeUnit.SECONDS); var b = bFuture.get(10, TimeUnit.SECONDS);
      try {
        assertNotSame(a, b); assertSame(a.getParentScope(), b.getParentScope());
        assertEquals("actual-parent", ((ClientSessionScope) a.getParentScope()).getId());
        assertEquals(1, first.requests.size()); assertEquals(1, second.requests.size());
        verify(first.user, never()).getUserSession(any());
        verify(second.user, never()).getUserSession(any());
      } finally { closeLocal(a); closeLocal(b); }
    }
  }

  private void closeLocal(ClientContextScope context) {
    if (context != null) {
      var parent = (ClientSessionScope) context.getParentScope();
      context.closePeer(); parent.closePeer();
    }
  }

  private static class Fixture implements AutoCloseable {
    final com.sun.net.httpserver.HttpServer server;
    final Utils.Http.Client http;
    final RuntimeClient runtime;
    final ClientUserScope user;
    final DigitalTwin.Configuration descriptor;
    final AtomicReference<DigitalTwin.Configuration> response;
    final List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
    volatile Runnable beforeReply = () -> {};

    Fixture(String runtimeId) throws Exception {
      this(runtimeId, "actual-parent.shared", "reader");
    }

    Fixture(String runtimeId, String contextId, String username) throws Exception {
      runtime = mock(RuntimeClient.class);
      when(runtime.serviceId()).thenReturn(runtimeId);
      when(runtime.getUrl()).thenReturn(new URL("http://127.0.0.1:8283/runtime"));
      doCallRealMethod().when(runtime).connectContext(any(), any());
      var identity = mock(UserIdentity.class);
      when(identity.getId()).thenReturn("fixture-" + username + "-credential");
      when(identity.getUsername()).thenReturn(username);
      var data = Parameters.<String>create();
      data.put(UserIdentity.FEDERATION_DATA_PROPERTY, new Federation("reader.group", null));
      when(identity.getData()).thenReturn(data);
      var engine = mock(EngineImpl.class, RETURNS_DEEP_STUBS);
      when(engine.getSettings().get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class)).thenReturn(true);
      user = spy(new ClientUserScope(identity, engine));
      descriptor = DigitalTwin.Configuration.builder().id(contextId)
          .name("remote context").owner("owner").persistence(Persistence.EXPLICIT_ACTION)
          .serviceId(runtimeId).serverUrl(runtime.getUrl()).build();
      response = new AtomicReference<>(descriptor);
      server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        exchange.getRequestBody().readAllBytes();
        beforeReply.run();
        var bytes = Utils.Json.asString(response.get()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
      });
      server.start();
      http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true); field.set(runtime, http);
    }

    @Override public void close() throws Exception {
      http.close(); server.stop(0);
    }
  }
}
