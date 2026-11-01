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
