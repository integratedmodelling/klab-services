package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.junit.jupiter.api.Test;

class DomainObjectMessageSerializationTest {

  @Test
  void customAgentMessageRetainsDomainObjectPayloadAndChildren() throws Exception {
    var assertion =
        DomainObject.create(
            DomainObject.TYPE, "assertion", DomainObject.URN, "actual == expected", "outcome", true);
    var test =
        DomainObject.create(
            DomainObject.TYPE, "test", DomainObject.URN, "checks_values", assertion);
    var message =
        new RuntimeAgent.CustomMessage(
            RuntimeAgent.TestMessageType.TEST_FINISHED.constant(), test);

    var mapper = JacksonConfiguration.newObjectMapper();
    var restored =
        mapper.readValue(mapper.writeValueAsString(message), RuntimeAgent.CustomMessage.class);

    var payload = assertInstanceOf(DomainObject.class, restored.payload());
    assertEquals(RuntimeAgent.TestMessageType.TEST_FINISHED.messageClass(), restored.type().getValue());
    assertEquals("checks_values", payload.urn());
    assertEquals("actual == expected", payload.getChildren().getFirst().urn());
  }

  @Test
  void agentCommunicationEnvelopeRetainsDomainObjectPayloadAndChildren() throws Exception {
    var assertion =
        DomainObject.create(
            DomainObject.TYPE, "assertion", DomainObject.URN, "actual == expected", "outcome", true);
    var test =
        DomainObject.create(
            DomainObject.TYPE, "test", DomainObject.URN, "checks_values", assertion);
    var custom =
        new RuntimeAgent.CustomMessage(RuntimeAgent.TestMessageType.TEST_FINISHED.constant(), test);
    var message =
        Message.create(
            "agent:test",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.CustomAgentMessage,
            custom);

    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(message), Message.class);
    var restoredCustom = restored.getPayload(RuntimeAgent.CustomMessage.class);

    var payload = assertInstanceOf(DomainObject.class, restoredCustom.payload());
    assertEquals("checks_values", payload.urn());
    assertEquals("actual == expected", payload.getChildren().getFirst().urn());
  }

  @Test
  void agentHandleRetainsDeferredStartAcknowledgement() throws Exception {
    var agent = new AgentImpl();
    agent.setUrn("runtime:test:1");
    agent.setStartDeferred(true);
    agent.setMessagingConnected(true);

    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(agent), AgentImpl.class);

    assertTrue(restored.isStartDeferred());
    assertTrue(restored.isMessagingConnected());
  }
}
