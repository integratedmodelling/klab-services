package org.integratedmodelling.common.runtime.actors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * Client-side console adapter for a runtime agent.
 *
 * <p>The agent handle must already be connected to messaging. Construction attaches the console;
 * {@link #sendLine(String)} sends {@code STDIN}; and output listeners receive {@code STDOUT} and
 * {@code STDERR} chunks. Closing the console detaches it without disconnecting or stopping the
 * agent handle.
 */
public final class AgentConsole implements AutoCloseable {

  /** One output chunk received from the remote agent. */
  public record Output(RuntimeAgent.ConsoleMessageType stream, String text) {}

  private final Agent agent;
  private final CopyOnWriteArrayList<Consumer<Output>> outputListeners =
      new CopyOnWriteArrayList<>();
  private final AutoCloseable messageSubscription;
  private volatile boolean closed;

  public AgentConsole(Agent agent) {
    this.agent = Objects.requireNonNull(agent, "agent");
    this.messageSubscription =
        agent instanceof AgentImpl implementation
            ? implementation.addMessageListener(this::receive)
            : () -> {};
    send(RuntimeAgent.ConsoleMessageType.CONSOLE_ATTACH, null);
  }

  public Agent getAgent() {
    return agent;
  }

  /**
   * Register a thread-safe output listener. UI implementations should marshal callbacks onto their
   * UI thread.
   */
  public AutoCloseable onOutput(Consumer<Output> listener) {
    Objects.requireNonNull(listener, "listener");
    outputListeners.add(listener);
    return () -> outputListeners.remove(listener);
  }

  /** Send one logical input line. No line terminator is added to the payload. */
  public void sendLine(String line) {
    ensureOpen();
    send(RuntimeAgent.ConsoleMessageType.STDIN, line == null ? "" : line);
  }

  /**
   * Run a blocking terminal loop until EOF. Remote output is written to the supplied stdout and
   * stderr streams while UTF-8 input lines are forwarded to the agent.
   */
  public void run(InputStream input, PrintStream stdout, PrintStream stderr) throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(stdout, "stdout");
    Objects.requireNonNull(stderr, "stderr");
    try (var subscription =
            onOutput(
                output -> {
                  PrintStream target =
                      output.stream() == RuntimeAgent.ConsoleMessageType.STDERR ? stderr : stdout;
                  target.print(output.text());
                  target.flush();
                });
        var reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while (!closed && (line = reader.readLine()) != null) {
        sendLine(line);
      }
    } catch (RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IOException("Cannot close agent-console output subscription", failure);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    send(RuntimeAgent.ConsoleMessageType.CONSOLE_DETACH, null);
    closed = true;
    try {
      messageSubscription.close();
    } catch (Exception ignored) {
      // Closing a local listener must never prevent console teardown.
    }
    outputListeners.clear();
  }

  private void receive(Message message) {
    if (closed || message.getMessageType() != Message.MessageType.CustomAgentMessage) {
      return;
    }
    var custom = message.getPayload(RuntimeAgent.CustomMessage.class);
    if (custom == null || custom.type() == null) {
      return;
    }
    RuntimeAgent.ConsoleMessageType stream;
    try {
      stream = RuntimeAgent.ConsoleMessageType.valueOf(custom.type().getValue());
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (stream != RuntimeAgent.ConsoleMessageType.STDOUT
        && stream != RuntimeAgent.ConsoleMessageType.STDERR) {
      return;
    }
    String text = custom.payload() == null ? "" : String.valueOf(custom.payload());
    var output = new Output(stream, text);
    outputListeners.forEach(listener -> listener.accept(output));
  }

  private void send(RuntimeAgent.ConsoleMessageType type, String text) {
    agent.tell(new RuntimeAgent.CustomMessage(type.constant(), text));
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("The agent console is closed");
    }
  }
}
