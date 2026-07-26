package org.integratedmodelling.common.runtime.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
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

  @Test
  void consoleRetainsOutputReplayedDuringAttachmentUntilListenerIsInstalled() throws Exception {
    var agent = new ReplayingAgent();
    var console = new AgentConsole(agent);
    var output = new ArrayList<AgentConsole.Output>();

    try (var ignored = console.onOutput(output::add)) {
      assertEquals(
          List.of(
              new AgentConsole.Output(RuntimeAgent.ConsoleMessageType.STDOUT, "startup\n")),
          output);
    } finally {
      console.close();
    }
  }

  @Test
  void clientHandleRetainsObservationAndActivityFromStatusMessages() {
    var agent = new AgentImpl();
    agent.setUrn("test:agent:status");
    agent.setViable(true);

    agent.receiveMessage(
        Message.create(
            "test:agent:status",
            Message.MessageClass.AgentCommunication,
            Message.MessageType.AgentStatusChanged,
            new RuntimeAgent.Status(
                "test:agent:status",
                RuntimeAgent.State.RUNNING,
                true,
                null,
                1_050L,
                42L,
                1_000L,
                1_040L)));

    assertEquals(42L, agent.getObservationId());
    assertEquals(1_000L, agent.getStartedAt());
    assertEquals(1_040L, agent.getLastActivityAt());
    assertEquals(true, agent.isAlive());
  }

  @Test
  void extendedAgentStatusSurvivesJacksonRoundTrip() throws Exception {
    var status =
        new RuntimeAgent.Status(
            "test:agent:status",
            RuntimeAgent.State.RUNNING,
            true,
            null,
            1_050L,
            42L,
            1_000L,
            1_040L);
    var mapper = JacksonConfiguration.newObjectMapper();

    var restored =
        mapper.readValue(mapper.writeValueAsString(status), RuntimeAgent.Status.class);

    assertEquals(42L, restored.observationId());
    assertEquals(1_000L, restored.startedAt());
    assertEquals(1_040L, restored.lastActivityAt());
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

  private static final class ReplayingAgent extends AgentImpl {

    private ReplayingAgent() {
      setUrn("test:agent:replay");
      setViable(true);
    }

    @Override
    public <T extends Serializable> void tell(T message) {
      if (message instanceof RuntimeAgent.CustomMessage custom
          && RuntimeAgent.ConsoleMessageType.CONSOLE_ATTACH
              .name()
              .equals(custom.type().getValue())) {
        receiveMessage(
            Message.create(
                getUrn(),
                Message.MessageClass.AgentCommunication,
                Message.MessageType.CustomAgentMessage,
                new RuntimeAgent.CustomMessage(
                    RuntimeAgent.ConsoleMessageType.STDOUT.constant(), "startup\n")));
      }
    }
  }
}
