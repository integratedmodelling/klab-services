package org.integratedmodelling.klab.api.actors;

import java.io.PrintStream;
import java.io.Serial;
import java.io.Serializable;
import java.net.URL;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * The runtime peer of a k.Actors agent. The agent can be connected to a {@link
 * org.integratedmodelling.klab.api.scope.UserScope} (user agent with configurable behavior
 * instantiated on authentication), a {@link SessionScope} (applications, scripts or test cases,
 * each running within a session that is private to the agent), a {@link ContextScope} (for DT-wide
 * automation) or an {@link org.integratedmodelling.klab.api.knowledge.observation.Observation}
 * (autonomous agents within a {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}.
 *
 * <p>Both client and service-side agents implement this interface.
 */
public interface RuntimeAgent {

  enum State {
    READY,
    RUNNING,
    STOPPED,
    FAILED
  }

  /**
   * Reserved custom-message discriminators used by interactive agent consoles. The enum names are
   * the exact constants carried by {@link CustomMessage}.
   */
  enum ConsoleMessageType {
    CONSOLE_ATTACH,
    CONSOLE_DETACH,
    STDIN,
    STDOUT,
    STDERR;

    public Constant constant() {
      return Constant.create(name());
    }
  }

  /**
   * Serializable lifecycle snapshot exchanged between all peers of an agent handle.
   *
   * @param agentUrn runtime-wide agent identity
   * @param state current lifecycle state
   * @param viable whether the agent can still be used
   * @param detail optional status or failure detail
   * @param timestamp sender-side snapshot timestamp
   * @param observationId represented observation ID, or {@link Observation#UNASSIGNED_ID}
   * @param startedAt first-start timestamp, or {@code -1}
   * @param lastActivityAt latest message or reactor activity timestamp, or {@code -1}
   */
  final class Status implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private String agentUrn;
    private State state;
    private boolean viable;
    private String detail;
    private long timestamp;
    private long observationId = Observation.UNASSIGNED_ID;
    private long startedAt = -1;
    private long lastActivityAt = -1;

    public Status() {}

    public Status(String agentUrn, State state, boolean viable, String detail, long timestamp) {
      this(agentUrn, state, viable, detail, timestamp, Observation.UNASSIGNED_ID, -1, -1);
    }

    public Status(
        String agentUrn,
        State state,
        boolean viable,
        String detail,
        long timestamp,
        long observationId,
        long startedAt,
        long lastActivityAt) {
      this.agentUrn = agentUrn;
      this.state = state;
      this.viable = viable;
      this.detail = detail;
      this.timestamp = timestamp;
      this.observationId = observationId;
      this.startedAt = startedAt;
      this.lastActivityAt = lastActivityAt;
    }

    public String agentUrn() {
      return agentUrn;
    }

    public State state() {
      return state;
    }

    public boolean viable() {
      return viable;
    }

    public String detail() {
      return detail;
    }

    public long timestamp() {
      return timestamp;
    }

    public long observationId() {
      return observationId;
    }

    public long startedAt() {
      return startedAt;
    }

    public long lastActivityAt() {
      return lastActivityAt;
    }

    public void setAgentUrn(String agentUrn) {
      this.agentUrn = agentUrn;
    }

    public void setState(State state) {
      this.state = state;
    }

    public void setViable(boolean viable) {
      this.viable = viable;
    }

    public void setDetail(String detail) {
      this.detail = detail;
    }

    public void setTimestamp(long timestamp) {
      this.timestamp = timestamp;
    }

    public void setObservationId(long observationId) {
      this.observationId = observationId;
    }

    public void setStartedAt(long startedAt) {
      this.startedAt = startedAt;
    }

    public void setLastActivityAt(long lastActivityAt) {
      this.lastActivityAt = lastActivityAt;
    }
  }

  /**
   * Payload for {@link Message.MessageType#CustomAgentMessage}. k.Actors code uses the constant as
   * the message discriminator instead of extending the Java message-type enum.
   */
  final class CustomMessage implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private Constant type;
    private Object payload;
    private String payloadClass;
    private String requestId;
    private String inResponseTo;
    private String failure;

    public CustomMessage() {}

    public CustomMessage(Constant type, Serializable payload) {
      if (type == null || type.getValue() == null || type.getValue().isBlank()) {
        throw new IllegalArgumentException("A custom agent message requires a constant type");
      }
      this.type = type;
      this.payload = payload;
      this.payloadClass = payload == null ? null : payload.getClass().getName();
    }

    public CustomMessage(CustomMessage other) {
      this.type = other.type;
      this.payload = other.payload;
      this.payloadClass = other.payloadClass;
      this.requestId = other.requestId;
      this.inResponseTo = other.inResponseTo;
      this.failure = other.failure;
    }

    public Constant type() {
      return type;
    }

    public Serializable payload() {
      return payload instanceof Serializable serializable ? serializable : null;
    }

    public String payloadClass() {
      return payloadClass;
    }

    /** Unique request identifier used by {@code ask}; null for ordinary messages. */
    public String requestId() {
      return requestId;
    }

    /** Request identifier completed by this response; null for requests and ordinary messages. */
    public String inResponseTo() {
      return inResponseTo;
    }

    /** Optional remote failure detail for an exceptionally completed request. */
    public String failure() {
      return failure;
    }

    public void setType(Constant type) {
      this.type = type;
    }

    public void setPayload(Serializable payload) {
      this.payload = payload;
    }

    public void setPayloadClass(String payloadClass) {
      this.payloadClass = payloadClass;
    }

    public void setRequestId(String requestId) {
      this.requestId = requestId;
    }

    public void setInResponseTo(String inResponseTo) {
      this.inResponseTo = inResponseTo;
    }

    public void setFailure(String failure) {
      this.failure = failure;
    }
  }

  /**
   * Passed to {@link
   * org.integratedmodelling.klab.api.services.RuntimeService#createAgent(KActorsBehavior, String,
   * java.util.Collection<CompilationOptions>, UserScope)}
   */
  enum CompilationOptions {
    /** Include the generated Java code in service-side handles that support it. */
    INCLUDE_JAVA_CODE,
    /** Stop after source generation and validation; do not compile or instantiate the class. */
    DO_NOT_COMPILE_JAVA,
    /** Do not bind the agent to any observation even if one is present in the scope. */
    DO_NOT_BIND_OBSERVATION,
    /**
     * Do not bind the agent to the session or the scope in any way - just create an independent
     * agent
     */
    DO_NOT_BIND_SESSION,
    /** Compile with debugging enabled. Currently unimplemented. */
    COMPILE_FOR_DEBUGGING
  }

  /**
   * Agent scope, containing the local state for the agent and its actions, and methods to implement
   * agent function in Java @{@link
   * org.integratedmodelling.klab.api.services.runtime.extension.Verb} implementations, which use
   * the scope to interact with the agent.
   */
  interface Scope extends Parameters<String> {

    /**
     * Called exactly once immediately before the owning agent begins execution. Specialized scopes
     * may override this to install runtime facilities needed by their agent.
     */
    default void setup() {}

    /**
     * Called exactly once when the owning agent terminates or is explicitly stopped. Specialized
     * scopes may override this to release facilities installed by {@link #setup()}.
     */
    default void dispose() {}

    /**
     * A SessionScope is always defined during an agent's lifetime. The scope may be a ContextScope
     * when the agent is part of a digital twin.
     *
     * @return the current session scope. Never null.
     */
    SessionScope getSession();

    /**
     * A ContextScope is only defined during an agent's lifetime if the agent is part of an active
     * digital twin.
     *
     * @return the current context scope, or null if not in a digital twin.
     */
    ContextScope getContext();

    /**
     * The agent that runs in this scope.
     *
     * @return
     */
    RuntimeAgent getAgent();

      org.integratedmodelling.klab.api.scope.Scope getScope();

      /**
     * Call this to fire an event to any subscribers, leaving the agent's code running. Can be
     * called multiple times during the lifetime of the agent, which must be tagged as an emitter to
     * work correctly.
     *
     * @param firedObject
     */
    void doFire(Object firedObject);

    /**
     * Calling return will stop the actor's code execution and return the passed object to any
     * subscribed listener.
     *
     * @param returnedObject
     */
    void doReturn(Object returnedObject);

    /**
     * If the agent is capable of writing to a console, this will return a PrintStream.
     *
     * @return a PrintStream for output or a no-op PrintStream (ideally logging) if the agent is not
     *     capable of writing to a console.
     */
    PrintStream getPrintWriter();

    /**
     * Action code that runs in the background must check this to know when to exit.
     *
     * @return true if the agent is done, false otherwise.
     */
    boolean isDone();

    /**
     * The actor code may call this to signal that it's done. If an exception or an error
     * notification is passed, the actor will be stopped exceptionally and should report
     * accordingly.
     */
    void done(Object... conditions);
  }

  /**
   * The actor's URL identifies it uniquely within the runtime it's part of and enables REST and
   * HTTP access to the accessible state of the actor.
   *
   * @return
   */
  URL getURL();

  /**
   * Return the observation represented or monitored by this agent.
   *
   * @return the service-side observation, or {@code null} for an unbound agent
   */
  Observation getObservation();

  /**
   * Return the scope selected by the runtime when this agent was created.
   *
   * <p>For scripts, applications, and test cases this is the dedicated session owned by the agent.
   * For user behaviors it is the root user scope owning the request. For tasks and ordinary
   * behaviors it is the user, session, or context scope under which the creation request was made.
   *
   * @return the service-side creation scope, or {@code null} when unavailable
   */
  org.integratedmodelling.klab.api.scope.Scope getCreationScope();

  /**
   * @return the epoch-millisecond timestamp at which this agent first started, or {@code -1}.
   */
  long getStartedAt();

  /**
   * @return the epoch-millisecond timestamp of the latest message or reactor activity, or {@code
   *     -1}
   */
  long getLastActivityAt();

  /**
   * Send an agent-communication message to a local or remote agent peer identified by URN.
   *
   * @return true when the message was accepted by the local event bus/transport
   */
  boolean send(String recipientAgentUrn, Message message);

  /**
   * Send a message through the most specific scope that hosts this agent.
   *
   * @return true if an instrumented hosting scope accepted the message
   */
  boolean sendToScope(Message message);

  /**
   * Send text to all console peers currently attached to this agent. Implementations return false
   * when no console is attached or messaging is unavailable, allowing extensions to fall back to a
   * local writer.
   *
   * @param type {@link ConsoleMessageType#STDOUT} or {@link ConsoleMessageType#STDERR}
   * @param text text chunk, including any desired line terminator
   */
  boolean sendToConsole(ConsoleMessageType type, String text);

  /**
   * An Actor can send a message to this actor using this. If a response is expected, the sender can
   * add a message consumer which may be called or not.
   *
   * @param message
   * @param sender
   * @param responseConsumer pass null if no response is expected
   */
  void send(Message message, RuntimeAgent sender, Consumer<Message> responseConsumer);
}
