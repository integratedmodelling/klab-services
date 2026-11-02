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

class ContextActivityNotificationsTest {
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
}
