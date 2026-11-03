package org.integratedmodelling.common.authentication.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;
import org.junit.jupiter.api.Test;

/** Uses the actual AMQP serializer/exchange selector; no compiler or managed runtime. */
class AgentMessagingWireTest {
  @Test
  void productionAgentMessagesAndExchangeMatchPythonFixture() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var bundle = mapper.createObjectNode();
    String urn = "test.agent.status";
    var federation = new Federation("test-federation", "amqp://unused");
    bundle.put("federationId", federation.getId());
    bundle.put("agentUrn", urn);
    bundle.put("exchange", AMQPChannel.agentExchangeId(federation, urn));
    var messages = bundle.putArray("messages");
    int id = 0;
    for (var type : new Message.MessageType[] {
        Message.MessageType.AgentStartRequested, Message.MessageType.AgentStopRequested,
        Message.MessageType.AgentStatusRequested, Message.MessageType.AgentStarted,
        Message.MessageType.AgentStopped, Message.MessageType.AgentStatusChanged,
        Message.MessageType.AgentFailed}) {
      Message message;
      if (type == Message.MessageType.AgentStarted || type == Message.MessageType.AgentStopped
          || type == Message.MessageType.AgentStatusChanged || type == Message.MessageType.AgentFailed) {
        var state = type == Message.MessageType.AgentStopped ? RuntimeAgent.State.STOPPED
            : type == Message.MessageType.AgentFailed ? RuntimeAgent.State.FAILED : RuntimeAgent.State.RUNNING;
        var status = new RuntimeAgent.Status(urn, state, state == RuntimeAgent.State.RUNNING,
            null, 1760000000000L);
        message = Message.create(urn, Message.MessageClass.AgentCommunication, type, status);
      } else {
        message = Message.create(urn, Message.MessageClass.AgentCommunication, type);
      }
      ((MessageImpl) message).setId(++id);
      ((MessageImpl) message).setTimestamp(1760000000000L);
      // AMQPChannel.post uses this serializer, not a guessed Python schema.
      var tree = mapper.readTree(Utils.Json.asString(message));
      assertEquals(type, Utils.Json.parseObject(tree.toString(), Message.class).getMessageType());
      messages.add(tree);
    }
    Files.write(Path.of("target/agent-messaging-wire-actual.json"),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle));
    try (var fixture = getClass().getResourceAsStream("/agent-messaging-wire.json")) {
      assertEquals(mapper.readTree(fixture), bundle);
    }
  }
}
