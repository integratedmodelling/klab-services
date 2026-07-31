package org.integratedmodelling.common.authentication.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.identities.Federation;
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
}
