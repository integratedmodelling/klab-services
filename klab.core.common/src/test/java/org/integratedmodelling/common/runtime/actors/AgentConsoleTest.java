package org.integratedmodelling.common.runtime.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class AgentConsoleTest {

  @Test
  void consoleAttachesSendsInputAndDetaches() {
    var agent = new RecordingAgent();
    var console = new AgentConsole(agent);

    console.sendLine("status");
    console.close();

    assertEquals(
        List.of(
            RuntimeAgent.ConsoleMessageType.CONSOLE_ATTACH,
            RuntimeAgent.ConsoleMessageType.STDIN,
            RuntimeAgent.ConsoleMessageType.CONSOLE_DETACH),
        agent.messages.stream()
            .map(RuntimeAgent.CustomMessage::type)
            .map(constant -> RuntimeAgent.ConsoleMessageType.valueOf(constant.getValue()))
            .toList());
    assertEquals("status", agent.messages.get(1).payload());
    assertThrows(IllegalStateException.class, () -> console.sendLine("again"));
  }

  @Test
  void consoleReceivesOutputFromClientHandleSubscription() throws Exception {
    var agent = new AgentImpl();
    agent.setUrn("test:agent:console");
    agent.setViable(true);
    var console = new AgentConsole(agent);
    var output = new ArrayList<AgentConsole.Output>();

    try (var ignored = console.onOutput(output::add)) {
      agent.receiveMessage(
          Message.create(
              "test:agent:console",
              Message.MessageClass.AgentCommunication,
              Message.MessageType.CustomAgentMessage,
              new RuntimeAgent.CustomMessage(
                  RuntimeAgent.ConsoleMessageType.STDOUT.constant(), "ready\n")));
    } finally {
      console.close();
    }

    assertEquals(
        List.of(
            new AgentConsole.Output(RuntimeAgent.ConsoleMessageType.STDOUT, "ready\n")),
        output);
  }

  private static final class RecordingAgent implements Agent {

    private final List<RuntimeAgent.CustomMessage> messages = new ArrayList<>();

    @Override
    public String getUrn() {
      return "test:agent:recording";
    }

    @Override
    public String getBehaviorUrn() {
      return "test.console";
    }

    @Override
    public String getName() {
      return "recording";
    }

    @Override
    public boolean isViable() {
      return true;
    }

    @Override
    public boolean isAlive() {
      return true;
    }

    @Override
    public boolean start(Object... arguments) {
      return true;
    }

    @Override
    public boolean stop() {
      return true;
    }

    @Override
    public List<Notification> getNotifications() {
      return List.of();
    }

    @Override
    public <T extends Serializable> void tell(T message) {
      messages.add((RuntimeAgent.CustomMessage) message);
    }

    @Override
    public <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
        T message, Class<? extends R> responseClass) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
  }
}
