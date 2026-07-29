package org.integratedmodelling.common.runtime.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.authentication.scope.AMQPChannel;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.junit.jupiter.api.Test;

class AgentEventBusTest {

  @Test
  void correlatedAskCompletesAndMissingResponseTimesOut() throws Exception {
    var channel = mock(MessagingChannel.class);
    var amqp = mock(AMQPChannel.class);
    var federation = new Federation("test-federation", "amqp://test");
    when(channel.isConnected()).thenReturn(true);
    when(channel.getFederation()).thenReturn(federation);
    when(amqp.isOnline()).thenReturn(true);
    Object senderOwner = new Object();
    Object recipientOwner = new Object();

    try (var mocked = mockStatic(AMQPChannel.class)) {
      mocked
          .when(() -> AMQPChannel.forAgent(any(), anyString(), any(), any()))
          .thenReturn(amqp);
      AgentEventBus.INSTANCE.subscribe("agent:sender", senderOwner, channel, ignored -> {});
      AgentEventBus.INSTANCE.subscribe(
          "agent:recipient",
          recipientOwner,
          channel,
          message -> {
            var request = message.getPayload(RuntimeAgent.CustomMessage.class);
            if (request != null && request.requestId() != null) {
              var response =
                  new RuntimeAgent.CustomMessage(request.type(), "response:" + request.payload());
              response.setInResponseTo(request.requestId());
              AgentEventBus.INSTANCE.publish(
                  "agent:recipient",
                  "agent:sender",
                  Message.MessageType.CustomAgentMessage,
                  response);
            }
          });

      var response =
          AgentEventBus.INSTANCE
              .ask(
                  "agent:sender",
                  "agent:recipient",
                  new RuntimeAgent.CustomMessage(Constant.create("QUESTION"), "payload"),
                  String.class,
                  Duration.ofSeconds(1))
              .get(1, TimeUnit.SECONDS);

      assertEquals("response:payload", response);

      AgentEventBus.INSTANCE.unsubscribe("agent:recipient", recipientOwner);
      var timeout =
          AgentEventBus.INSTANCE.ask(
              "agent:sender",
              "agent:recipient",
              new RuntimeAgent.CustomMessage(Constant.create("QUESTION"), "ignored"),
              String.class,
              Duration.ofMillis(20));
      assertThrows(ExecutionException.class, () -> timeout.get(1, TimeUnit.SECONDS));
    } finally {
      AgentEventBus.INSTANCE.unsubscribe("agent:sender", senderOwner);
      AgentEventBus.INSTANCE.unsubscribe("agent:recipient", recipientOwner);
    }
  }
}
