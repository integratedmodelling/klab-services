package org.integratedmodelling.common.authentication.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.junit.jupiter.api.Test;

class AMQPChannelTest {

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
