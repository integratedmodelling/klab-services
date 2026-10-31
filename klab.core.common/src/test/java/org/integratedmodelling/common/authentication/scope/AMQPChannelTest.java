package org.integratedmodelling.common.authentication.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.junit.jupiter.api.Test;

class AMQPChannelTest {

  @Test
  void absentBrokersNeverConstructAConnectionFactory() {
    try (var factories = mockConstruction(com.rabbitmq.client.ConnectionFactory.class)) {
      for (var federation : java.util.Arrays.asList(
          null, new Federation("none", null), new Federation("blank", " "))) {
        var transport = new AMQPChannel(federation, "scope", mock(
            org.integratedmodelling.klab.api.services.runtime.Channel.class), null);
        assertFalse(transport.isOnline());
        var agent = AMQPChannel.forAgent(federation, "agent", mock(
            org.integratedmodelling.klab.api.services.runtime.Channel.class), null);
        assertFalse(agent.isOnline());
      }
      assertTrue(factories.constructed().isEmpty());
    }
  }

  @Test
  void absentBrokerMeansNoMessagingOrQueues() {
    var scope = mock(MessagingChannelImpl.class, CALLS_REAL_METHODS);
    try (var transports = mockConstruction(AMQPChannel.class)) {
      for (var federation : java.util.Arrays.asList(
          null, new Federation("none", null), new Federation("blank", " "))) {
        assertTrue(scope.setupMessaging(federation, "scope", java.util.List.of()).isEmpty());
        assertFalse(scope.hasMessaging());
      }
      assertTrue(transports.constructed().isEmpty());
    }
  }

  @Test
  void removingBrokerDisconnectsOnlyTheTransportCreatedByThisScope() {
    var scope = mock(MessagingChannelImpl.class, CALLS_REAL_METHODS);
    try (var transports = mockConstruction(AMQPChannel.class)) {
      scope.setupMessaging(new Federation("configured", "amqp://broker"), "scope", java.util.List.of());
      var transport = transports.constructed().getFirst();
      assertTrue(scope.setupMessaging(new Federation("none", null), "scope", java.util.List.of()).isEmpty());
      verify(transport).disconnect();
      verify(transport, never()).close();
      assertFalse(scope.hasMessaging());
    }
  }

  @Test
  void removingBrokerOnCopiesDoesNotDisconnectTheirParentsBorrowedTransport() {
    try (var transports = mockConstruction(AMQPChannel.class)) {
      var parent = new TestMessagingChannel();
      parent.setupMessaging(new Federation("configured", "amqp://broker"), "parent", java.util.List.of());
      var copied = new TestMessagingChannel(parent);
      var inherited = new TestMessagingChannel();
      inherited.copyMessagingSetup(parent);
      var transport = transports.constructed().getFirst();
      assertTrue(copied.setupMessaging(new Federation("none", null), "copy", java.util.List.of()).isEmpty());
      assertFalse(copied.hasMessaging());
      assertTrue(parent.hasMessaging());
      assertTrue(inherited.hasMessaging());
      assertTrue(inherited.setupMessaging(new Federation("none", null), "inherited", java.util.List.of()).isEmpty());
      assertTrue(parent.hasMessaging());
      verify(transport, never()).disconnect();
      verify(transport, never()).close();
      verify(transport, times(1)).filter(any());
    }
  }

  private static class TestMessagingChannel extends MessagingChannelImpl {
    TestMessagingChannel() {
      super(mock(org.integratedmodelling.klab.api.identities.Identity.class), true, true);
    }

    TestMessagingChannel(TestMessagingChannel parent) {
      super(parent);
    }

    @Override public String getId() { return "test"; }
    @Override public String getDispatchId() { return "test-dispatch"; }
  }

  @Test
  void failedBrokerSetupReleasesThePartiallyOpenedConnection() throws Exception {
    var connection = mock(com.rabbitmq.client.Connection.class);
    var channel = mock(com.rabbitmq.client.Channel.class);
    when(connection.createChannel()).thenReturn(channel);
    doThrow(new java.io.IOException("exchange declaration failed")).when(channel)
        .exchangeDeclare(eq("federation"), eq(com.rabbitmq.client.BuiltinExchangeType.FANOUT),
            eq(true), eq(false), isNull());
    try (var factories = mockConstruction(com.rabbitmq.client.ConnectionFactory.class,
        (factory, context) -> when(factory.newConnection()).thenReturn(connection))) {
      var transport = new AMQPChannel(new Federation("federation", "amqp://broker"),
          "federation", mock(org.integratedmodelling.klab.api.services.runtime.Channel.class), null);
      assertFalse(transport.isOnline());
      verify(channel).close();
      verify(connection).close();
    }
  }

  @Test
  void disconnectPreservesExchangeAndClosesConnectionAfterQueueFailure() throws Exception {
    var context = mock(ContextScope.class);
    var twin = mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);
    when(context.getDigitalTwin()).thenReturn(twin);
    var transport = new AMQPChannel(new Federation("test", "amqp://broker"), null, context, null);
    var channel = mock(com.rabbitmq.client.Channel.class);
    var connection = mock(com.rabbitmq.client.Connection.class);
    setField(transport, "amqpChannel", channel);
    setField(transport, "connection", connection);
    setField(transport, "consumerQueue", "queue");
    setField(transport, "online", true);
    doThrow(new java.io.IOException("gone")).when(channel).queueDelete("queue");
    transport.disconnect();
    verify(channel).close();
    verify(connection).close();
    verify(channel, never()).exchangeDelete(any());
    assertFalse(transport.isOnline());
    transport.disconnect();
    verify(connection, times(1)).close();
  }

  private void setField(Object target, String name, Object value) throws Exception {
    var field = AMQPChannel.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  void agentExchangeNamesAreStableBoundedAndFederationQualified() {
    var firstFederation = new Federation("first", "amqp://broker");
    var secondFederation = new Federation("second", "amqp://broker");
    String urn = "user:agent:runtime-incarnation:1";

    String first = AMQPChannel.agentExchangeId(firstFederation, urn);
    String repeated = AMQPChannel.agentExchangeId(firstFederation, urn);
    String second = AMQPChannel.agentExchangeId(secondFederation, urn);

    assertEquals(first, repeated);
    assertNotEquals(first, second);
    assertTrue(first.startsWith("klab.agent."));
    assertTrue(first.length() < 256);
  }

  @Test
  void explicitlyInstrumentedSessionsAndContextsUseTheirOwnExchange() {
    var federation = new Federation("local", "amqp://broker");

    assertEquals(
        "session-1",
        MessagingChannelImpl.scopeExchangeId(mock(SessionScope.class), federation, "session-1"));
    assertEquals(
        "context-1",
        MessagingChannelImpl.scopeExchangeId(mock(ContextScope.class), federation, "context-1"));
    assertEquals(
        "local", MessagingChannelImpl.scopeExchangeId(new Object(), federation, "ignored"));
  }
}
