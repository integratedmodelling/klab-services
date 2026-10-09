package org.integratedmodelling.common.services.client.scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URL;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class ClientScopeCreationTest {
  private ClientUserScope user() {
    var identity = mock(UserIdentity.class);
    when(identity.getId()).thenReturn("scope-test");
    when(identity.getUsername()).thenReturn("scope-test");
    when(identity.getData()).thenReturn(Parameters.create());
    var engine = mock(EngineImpl.class, RETURNS_DEEP_STUBS);
    when(engine.getSettings().get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class)).thenReturn(true);
    return spy(new ClientUserScope(identity, engine));
  }

  private RuntimeClient runtime(String id) throws Exception {
    var runtime = mock(RuntimeClient.class);
    when(runtime.serviceId()).thenReturn(id);
    when(runtime.getUrl()).thenReturn(new URL("http://localhost:8283"));
    when(runtime.declareSessionScope(any(), any(), isNull())).thenReturn("scope-test");
    return runtime;
  }

  @Test void failedCreationPreservesReasonAndNeverReturnsAnUninitializedScope() throws Exception {
    var user = user();
    var runtime = runtime("failed-runtime");
    var session = new ClientSessionScope(user, "test", runtime).withId("scope-test");
    var notification = Notification.error("Database unavailable");
    when(runtime.declareContextScope(any(), any(), any()))
        .thenReturn(DigitalTwin.Configuration.empty(notification));
    var failure = assertThrows(KlabResourceAccessException.class,
        () -> session.createContext(DigitalTwin.Configuration.builder().name("test").build()));
    assertTrue(failure.getMessage().contains("Database unavailable"));
    verify(user).send(notification);
  }

  @Test void creationAdoptsDescriptorBeforeRegistrationAndChildrenShareOneTwin() throws Exception {
    var user = user();
    var runtime = runtime("creation-runtime");
    var session = new ClientSessionScope(user, "test", runtime).withId("scope-test");
    var requested = DigitalTwin.Configuration.builder().name("requested").build();
    var descriptor = DigitalTwin.Configuration.builder().id("scope-test.created")
        .name("canonical").owner("scope-test").description("description")
        .persistence(Persistence.EXPLICIT_ACTION).serviceId(runtime.serviceId())
        .serverUrl(runtime.getUrl()).build();
    when(runtime.declareContextScope(any(), any(), any())).thenReturn(descriptor);
    var context = (ClientContextScope) session.createContext(requested);
    try {
      assertEquals(context.getId(), context.getConfiguration().getId());
      assertEquals("http://localhost:8283/dt/scope-test.created", context.getUrl().toString());
      assertEquals("canonical", context.getName());
      assertEquals("scope-test", context.getConfiguration().getOwner());
      assertEquals("description", context.getConfiguration().getDescription());
      assertEquals(Persistence.EXPLICIT_ACTION, context.getConfiguration().getPersistence());
      assertNull(requested.getUrl());
      assertSame(context, ClientScopeManager.INSTANCE.getScope(runtime.serviceId(), context.getId(), ClientContextScope.class));
      var twin = context.getDigitalTwin();
      ClientScopeManager.INSTANCE.register(context);
      assertSame(twin, context.getDigitalTwin());
      assertSame(twin, context.within(null).getDigitalTwin());
    } finally { context.closePeer(); }
  }

  @Test void missingContextIdAndFailedSessionDeclarationAreRejected() throws Exception {
    var user = user();
    var runtime = runtime("invalid-runtime");
    var session = new ClientSessionScope(user, "test", runtime).withId("scope-test");
    when(runtime.declareContextScope(any(), any(), any()))
        .thenReturn(DigitalTwin.Configuration.builder().name("missing ID").build());
    assertThrows(KlabResourceAccessException.class,
        () -> session.createContext(DigitalTwin.Configuration.builder().name("test").build()));
    when(runtime.declareSessionScope(any(), any(), isNull())).thenReturn(null);
    assertThrows(KlabResourceAccessException.class, () -> user.getUserSession(runtime));
    assertNull(ClientScopeManager.INSTANCE.getScope(runtime.serviceId(), "scope-test", ClientSessionScope.class));
  }

  @Test void serviceDescriptorMergeRetainsOwnershipAndReportsRequestedDifferences() {
    var requested = DigitalTwin.Configuration.builder().name("requested")
        .persistence(Persistence.ONE_OFF).build();
    var canonical = DigitalTwin.Configuration.builder().name("canonical").owner("owner")
        .serviceId("runtime").persistence(Persistence.EXPLICIT_ACTION).build();
    requested.defineFromExisting(canonical);
    assertEquals("owner", requested.getOwner());
    assertEquals("runtime", requested.getServiceId());
    assertEquals("canonical", requested.getName());
    assertEquals(Persistence.EXPLICIT_ACTION, requested.getPersistence());
    assertEquals(2, requested.getNotifications().size());
  }

  @Test void sameUserGetsIndependentSessionsOnDifferentRuntimes() throws Exception {
    var user = user();
    var first = runtime("first-runtime");
    var second = runtime("second-runtime");
    var a = (ClientSessionScope) user.getUserSession(first);
    var b = (ClientSessionScope) user.getUserSession(second);
    try {
      assertNotSame(a, b);
      assertSame(first, a.getService(RuntimeService.class));
      assertSame(second, b.getService(RuntimeService.class));
      assertSame(a, user.getUserSession(first));
      assertSame(b, user.getUserSession(second));
    } finally { a.closePeer(); b.closePeer(); }
  }

  @Test void reconnectRegistersOnePeerAndSubsequentConnectionsDoNotRepeatHttp() throws Exception {
    var user = user();
    var runtime = runtime("reconnect-runtime");
    var session = new ClientSessionScope(user, "test", runtime).withId("scope-test");
    doReturn(session).when(user).getUserSession(runtime);
    doCallRealMethod().when(runtime).connectContext(any(), any());
    var descriptor = DigitalTwin.Configuration.builder().id("scope-test.reconnected")
        .name("reconnected").persistence(Persistence.EXPLICIT_ACTION)
        .serviceId(runtime.serviceId()).serverUrl(runtime.getUrl()).build();
    var requests = new java.util.concurrent.atomic.AtomicInteger();
    var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      var body = org.integratedmodelling.common.utils.Utils.Json.asString(descriptor)
          .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    ClientContextScope context = null;
    try (var http = org.integratedmodelling.common.utils.Utils.Http.getClient(
        "http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var field = org.integratedmodelling.common.services.client.BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(runtime, http);
      context = (ClientContextScope) runtime.connectContext(descriptor, user);
      assertNotNull(context);
      assertSame(context, runtime.connectContext(descriptor, user));
      assertSame(context, ClientScopeManager.INSTANCE.getScope(runtime.serviceId(), descriptor.getId(), ClientContextScope.class));
      assertEquals(1, requests.get());
      assertEquals(descriptor.getId(), context.getConfiguration().getId());
    } finally {
      if (context != null) context.closePeer();
      server.stop(0);
    }
  }
}
