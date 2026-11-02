package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;

class ContextActivityNotificationsTest {
  @TempDir Path directory;
  static final ObjectMapper JSON = new ObjectMapper();
  static ObjectNode profile() {
    var root = JSON.createObjectNode(); root.put("runtimeId", "runtime");
    var route = root.putArray("contexts").addObject();
    route.put("context", "s.a"); route.put("owner", "alice"); route.put("host", "127.0.0.1");
    route.put("port", 18197); route.put("tls", false);
    route.put("username", "publisher-a"); route.put("password", "fixture-only-random-password-123456789");
    return root;
  }
  static byte[] encoded(ObjectNode profile) throws Exception { return JSON.writeValueAsBytes(profile); }
  static Message message(String context, String runtime, Message.MessageType type) {
    var activity = new ActivityImpl(); activity.setServiceId(runtime); activity.setUrn("genuine-source-urn");
    activity.setStart(12); activity.setEnd(34); activity.setOutcome(Activity.Outcome.SUCCESS);
    activity.setDescription("private description intentionally not exported");
    return Message.create(context, Message.MessageClass.DigitalTwin, type, activity);
  }

  @Test void explicitIdentityOriginAndSenderOnlySnapshot() throws Exception {
    var connection = mock(Connection.class); var channel = mock(Channel.class);
    when(connection.createChannel()).thenReturn(channel);
    var opened = new CountDownLatch(1); var delivered = new CountDownLatch(1);
    var factory = new AtomicReference<ConnectionFactory>(); var body = new AtomicReference<byte[]>();
    doAnswer(call -> { body.set(call.getArgument(3)); delivered.countDown(); return null; }).when(channel)
        .basicPublish(eq("s.a"), eq(""), any(AMQP.BasicProperties.class), any(byte[].class));
    try (var notifications = ContextActivityNotifications.parse("runtime", encoded(profile()), configured -> {
      factory.set(configured); opened.countDown(); return connection;
    })) {
      assertNull(notifications.forContext("s.a", "bob")); assertNull(notifications.forContext("s.b", "alice"));
      var sink = notifications.forContext("s.a", "alice"); assertSame(sink, notifications.forContext("s.a", "alice"));
      assertTrue(opened.await(3, TimeUnit.SECONDS));
      var original = message("s.a", "runtime", Message.MessageType.ActivityFinished);
      sink.accept(original); assertTrue(delivered.await(3, TimeUnit.SECONDS));
      assertEquals("127.0.0.1", factory.get().getHost()); assertEquals("s.a", factory.get().getVirtualHost());
      assertEquals("publisher-a", factory.get().getUsername());
      assertFalse(factory.get().isAutomaticRecoveryEnabled()); assertFalse(factory.get().isTopologyRecoveryEnabled());
      var envelope = JSON.readTree(body.get());
      assertEquals(original.getId(), envelope.get("id").asLong());
      assertEquals(original.getTimestamp(), envelope.get("timestamp").asLong());
      assertEquals("s.a", envelope.get("dispatchId").asText());
      assertEquals("runtime", envelope.at("/payload/serviceId").asText());
      assertEquals("SUCCESS", envelope.at("/payload/outcome").asText());
      assertFalse(new String(body.get(), StandardCharsets.UTF_8).contains("private description"));
      verify(channel).exchangeDeclare("s.a", BuiltinExchangeType.FANOUT, false, false, null);
      verify(channel, never()).queueDeclare(); verify(channel, never()).exchangeDelete(anyString());
    }
    verify(connection).abort(1000);
  }

  @Test void terminalConnectFailureNeverRetriesAndNeverEscapesToJob() throws Exception {
    var attempts = new AtomicInteger(); var failed = new CountDownLatch(1);
    try (var notifications = ContextActivityNotifications.parse("runtime", encoded(profile()), factory -> {
      attempts.incrementAndGet(); failed.countDown(); throw new java.io.IOException("private broker credentials");
    })) {
      var sink = notifications.forContext("s.a", "alice"); assertTrue(failed.await(3, TimeUnit.SECONDS));
      for (int i = 0; i < 100; i++) sink.accept(message("s.a", "runtime", Message.MessageType.ActivityStarted));
      sink.close(); assertEquals(1, attempts.get());
    }
  }

  @Test void wrongOriginUnsupportedTypesAndBoundedQueueCannotBecomePublications() throws Exception {
    var connecting = new CountDownLatch(1); var release = new CountDownLatch(1);
    var connection = mock(Connection.class); var channel = mock(Channel.class); when(connection.createChannel()).thenReturn(channel);
    var attempts = new AtomicInteger();
    try (var notifications = ContextActivityNotifications.parse("runtime", encoded(profile()), factory -> {
      attempts.incrementAndGet(); connecting.countDown(); release.await(3, TimeUnit.SECONDS); return connection;
    })) {
      var sink = notifications.forContext("s.a", "alice"); assertTrue(connecting.await(3, TimeUnit.SECONDS));
      assertDoesNotThrow(() -> sink.accept(null));
      sink.accept(message("s.b", "runtime", Message.MessageType.ActivityStarted));
      sink.accept(message("s.a", "wrong-runtime", Message.MessageType.ActivityStarted));
      sink.accept(message("s.a", "runtime", Message.MessageType.ContextClosed));
      // While the only worker is held, enqueue more than the finite128-slot budget.
      for (int i = 0; i < 200; i++) sink.accept(message("s.a", "runtime", Message.MessageType.ActivityStarted));
      sink.close(); release.countDown();
      assertEquals(1, attempts.get()); verify(channel, never()).basicPublish(anyString(), anyString(), any(), any(byte[].class));
    } finally { release.countDown(); }
  }

  @ParameterizedTest @ValueSource(strings = {"null", "{}", "[]", "{", " ", "{\"runtimeId\":\"runtime\",\"contexts\":[]}",
      "{\"runtimeId\":\"runtime\",\"runtimeId\":\"other\",\"contexts\":[]}"})
  void malformedProfilesFailClosedWithSanitizedDiagnostics(String input) {
    var error = assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", input.getBytes(StandardCharsets.UTF_8)));
    assertEquals("Invalid private Runtime activity-notification profile", error.getMessage()); assertNull(error.getCause());
  }

  @ParameterizedTest @ValueSource(strings = {"context", "owner", "host", "port", "tls", "username", "password"})
  void missingFieldsFailClosed(String field) throws Exception {
    var profile = profile(); ((ObjectNode) profile.at("/contexts/0")).remove(field);
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(profile)));
  }

  @ParameterizedTest @ValueSource(strings = {"guest", "ALL", "anonymous", "bad name", "a*", "a\nACL"})
  void reservedOrInjectedPrincipalsAreRejected(String username) throws Exception {
    var profile = profile(); ((ObjectNode) profile.at("/contexts/0")).put("username", username);
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(profile)));
  }

  @Test void runtimeOriginPlaintextNetworkAndDuplicateOrOversizedProfilesRejected() throws Exception {
    var input = encoded(profile());
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("other-runtime", input));
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", new byte[65537]));
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", null));
    var profile = profile(); ((ObjectNode) profile.at("/contexts/0")).put("host", "remote.example");
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(profile)));
    var duplicate = profile(); duplicate.withArray("contexts").add(duplicate.at("/contexts/0").deepCopy());
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(duplicate)));
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", (new String(input, StandardCharsets.UTF_8) + " {}").getBytes(StandardCharsets.UTF_8)));
  }

  @Test void noConfiguredProfileCreatesNoRouteAndInvalidPrivatePathsFailClosed() throws Exception {
    var previous = System.getProperty(ContextActivityNotifications.PROPERTY);
    try {
      System.clearProperty(ContextActivityNotifications.PROPERTY);
      try (var disabled = ContextActivityNotifications.configured("runtime")) {
        assertNull(disabled.forContext("s.a", "alice"));
      }
      var file = directory.resolve("private-profile.json"); Files.write(file, encoded(profile()));
      System.setProperty(ContextActivityNotifications.PROPERTY, file.toString());
      try (var configured = ContextActivityNotifications.configured("runtime")) {
        assertNull(configured.forContext("s.a", "wrong-owner")); // No socket or worker for an unapproved owner.
      }
      System.setProperty(ContextActivityNotifications.PROPERTY, directory.resolve("private-missing").toString());
      var error = assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.configured("runtime"));
      assertFalse(error.getMessage().contains("private-missing")); assertNull(error.getCause());
      System.setProperty(ContextActivityNotifications.PROPERTY, directory.toString());
      assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.configured("runtime"));
    } finally {
      if (previous == null) System.clearProperty(ContextActivityNotifications.PROPERTY);
      else System.setProperty(ContextActivityNotifications.PROPERTY, previous);
    }
  }

  @ParameterizedTest @ValueSource(strings = {
      "context=invalid", "context=s.a.extra", "owner=", "host=amqp://host", "password=short",
      "port=0", "port=65536", "port=1.25", "port=9999999999999", "tls=1", "extra=field"
  })
  void routeMutationsRemainExplicitlyBounded(String mutation) throws Exception {
    var profile = profile(); var route = (ObjectNode) profile.at("/contexts/0");
    var parts = mutation.split("=", -1);
    if (parts[0].equals("port")) route.set("port", JSON.readTree(parts[1]));
    else if (parts[0].equals("tls")) route.put("tls", 1);
    else route.put(parts[0], parts[1]);
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(profile)));
  }

  @Test void closeDuringInitialConnectAbortsLateConnectionAndCannotReopenTheRoute() throws Exception {
    var connecting = new CountDownLatch(1); var release = new CountDownLatch(1); var aborted = new CountDownLatch(1);
    var connection = mock(Connection.class);
    doAnswer(call -> { aborted.countDown(); return null; }).when(connection).abort(1000);
    try (var notifications = ContextActivityNotifications.parse("runtime", encoded(profile()), factory -> {
      connecting.countDown();
      // Model an opener that ignores interruption but still returns within a finite budget.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (release.getCount() != 0 && System.nanoTime() < deadline) {
        try { release.await(10, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
      }
      return connection;
    })) {
      var sink = notifications.forContext("s.a", "alice"); assertTrue(connecting.await(3, TimeUnit.SECONDS));
      notifications.closeContext("s.a"); notifications.closeContext("s.missing");
      release.countDown(); assertTrue(aborted.await(3, TimeUnit.SECONDS));
      assertSame(sink, notifications.forContext("s.a", "alice")); // Terminal; never silently recreate.
      sink.accept(message("s.a", "runtime", Message.MessageType.ActivityStarted));
      verify(connection, never()).createChannel();
      notifications.close(); assertNull(notifications.forContext("s.a", "alice"));
    } finally { release.countDown(); }
  }

  @Test void additionalProfileBudgetsAndDuplicateIdentitiesFailClosed() throws Exception {
    for (var runtime : new String[] {null, ""})
      assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse(runtime, encoded(profile())));
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", new byte[0]));
    var objectContexts = profile(); objectContexts.set("contexts", JSON.createObjectNode());
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(objectContexts)));
    var tooMany = profile(); for (int i = 0; i < 16; i++) tooMany.withArray("contexts").add(profile().at("/contexts/0"));
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(tooMany)));
    for (var pair : new String[][] {{"context", "s." + "a".repeat(254)}, {"owner", "a".repeat(256)}, {"owner", "a\n"},
        {"password", "a".repeat(257)}, {"password", "a".repeat(24) + " "}, {"password", "a".repeat(24) + "\u00e9"}}) {
      var changed = profile(); ((ObjectNode) changed.at("/contexts/0")).put(pair[0], pair[1]);
      assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(changed)));
    }
    for (var collision : new String[] {"username", "password", "context"}) {
      var duplicate = profile(); var extra = duplicate.at("/contexts/0").deepCopy();
      ((ObjectNode) extra).put("context", "s.b"); ((ObjectNode) extra).put("username", "publisher-b");
      ((ObjectNode) extra).put("password", "another-fixture-secret-123456789");
      ((ObjectNode) extra).set(collision, duplicate.at("/contexts/0/" + collision));
      duplicate.withArray("contexts").add(extra);
      assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(duplicate)));
    }
    var sameSizedUnknown = profile(); sameSizedUnknown.remove("runtimeId"); sameSizedUnknown.put("unknown", "runtime");
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(sameSizedUnknown)));
    var numeric = profile(); ((ObjectNode) numeric.at("/contexts/0")).put("owner", 123);
    assertThrows(IllegalArgumentException.class, () -> ContextActivityNotifications.parse("runtime", encoded(numeric)));
  }

  @Test void tlsFactoryIsVerifiedAndIncompleteOrOversizedHintsAreNotPublished() throws Exception {
    var profile = profile(); ((ObjectNode) profile.at("/contexts/0")).put("tls", true);
    ((ObjectNode) profile.at("/contexts/0")).put("host", "broker.example");
    var connection = mock(Connection.class); var channel = mock(Channel.class); when(connection.createChannel()).thenReturn(channel);
    var initialized = new CountDownLatch(1); var delivered = new CountDownLatch(2); var factory = new AtomicReference<ConnectionFactory>();
    doAnswer(call -> { initialized.countDown(); return null; }).when(channel).exchangeDeclare(anyString(), any(BuiltinExchangeType.class), anyBoolean(), anyBoolean(), anyMapOrNull());
    doAnswer(call -> { delivered.countDown(); return null; }).when(channel).basicPublish(anyString(), anyString(), any(), any(byte[].class));
    try (var notifications = ContextActivityNotifications.parse("runtime", encoded(profile), configured -> { factory.set(configured); return connection; })) {
      var sink = notifications.forContext("s.a", "alice"); assertTrue(initialized.await(3, TimeUnit.SECONDS)); assertTrue(factory.get().isSSL());
      var wrongClass = mock(Message.class); when(wrongClass.getMessageClass()).thenReturn(Message.MessageClass.SessionLifecycle); sink.accept(wrongClass);
      var noDispatch = mock(Message.class); when(noDispatch.getMessageClass()).thenReturn(Message.MessageClass.DigitalTwin);
      when(noDispatch.getMessageType()).thenReturn(Message.MessageType.ActivityStarted); sink.accept(noDispatch);
      var noPayload = mock(Message.class); when(noPayload.getMessageClass()).thenReturn(Message.MessageClass.DigitalTwin);
      when(noPayload.getMessageType()).thenReturn(Message.MessageType.ActivityStarted); when(noPayload.getDispatchId()).thenReturn("s.a"); sink.accept(noPayload);
      var oversized = message("s.a", "runtime", Message.MessageType.ActivityStarted);
      oversized.getPayload(ActivityImpl.class).setUrn("x".repeat(1025)); sink.accept(oversized);
      var nested = message("s.a.child", "runtime", Message.MessageType.ActivityStarted); nested.getPayload(ActivityImpl.class).setUrn(null); sink.accept(nested);
      sink.accept(message("s.a#1", "runtime", Message.MessageType.ActivityFinished));
      assertTrue(delivered.await(3, TimeUnit.SECONDS));
      verify(channel, times(2)).basicPublish(anyString(), anyString(), any(), any(byte[].class));
    }
    var longRuntime = "runtime".repeat(1400); var largeProfile = profile(); largeProfile.put("runtimeId", longRuntime);
    var initializedLarge = new CountDownLatch(1);
    var largeConnection = mock(Connection.class); var largeChannel = mock(Channel.class); when(largeConnection.createChannel()).thenReturn(largeChannel);
    doAnswer(call -> { initializedLarge.countDown(); return null; }).when(largeChannel).exchangeDeclare(anyString(), any(BuiltinExchangeType.class), anyBoolean(), anyBoolean(), anyMapOrNull());
    try (var notifications = ContextActivityNotifications.parse(longRuntime, encoded(largeProfile), configured -> largeConnection)) {
      var sink = notifications.forContext("s.a", "alice"); assertTrue(initializedLarge.await(3, TimeUnit.SECONDS));
      sink.accept(message("s.a", longRuntime, Message.MessageType.ActivityStarted)); sink.close();
      verify(largeChannel, never()).basicPublish(anyString(), anyString(), any(), any(byte[].class));
    }
  }

  // Nullable argument is part of the exact exchangeDeclare overload used by the product.
  private static java.util.Map<String, Object> anyMapOrNull() { return nullable(java.util.Map.class); }

  @Test void defensiveInternalGuardsAndAlreadyTerminalWorkerRemainFailClosed() throws Exception {
    var fields = ContextActivityNotifications.class.getDeclaredMethod("fields", com.fasterxml.jackson.databind.JsonNode.class, String[].class);
    fields.setAccessible(true);
    var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
        () -> fields.invoke(null, null, new String[] {"field"}));
    assertInstanceOf(IllegalArgumentException.class, error.getCause());
    var text = ContextActivityNotifications.class.getDeclaredMethod("text", com.fasterxml.jackson.databind.JsonNode.class, String.class);
    text.setAccessible(true);
    error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> text.invoke(null, JSON.createObjectNode(), "field"));
    assertInstanceOf(IllegalArgumentException.class, error.getCause());
    try (var notifications = ContextActivityNotifications.parse("runtime", encoded(profile()), factory -> { throw new java.io.IOException(); })) {
      var sink = notifications.forContext("s.a", "alice"); sink.close();
      var publish = sink.getClass().getDeclaredMethod("publish", byte[].class); publish.setAccessible(true);
      assertDoesNotThrow(() -> publish.invoke(sink, new byte[] {1}));
    }
  }
}
