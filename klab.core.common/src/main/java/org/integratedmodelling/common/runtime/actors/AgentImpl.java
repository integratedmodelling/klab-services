package org.integratedmodelling.common.runtime.actors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** The client-side Agent incarnates the service-side agent in the runtime. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentImpl implements Agent {

  private List<Notification> notifications = new ArrayList<>();
  private boolean alive;
  private String behaviorUrn;
  private String urn;
  private boolean viable;
  private String javaCode;
  private String name;
  private String scopeId;
  private List<String> handledMessageClasses = List.of();
  private long observationId = Observation.UNASSIGNED_ID;
  private long startedAt = -1;
  private long lastActivityAt = -1;
  private boolean startDeferred;
  private boolean messagingConnected;
  private transient String localSenderUrn;
  private transient String localSenderName;
  private transient String localResponseTo;
  private transient MessagingChannel messagingChannel;
  private transient AutoCloseable ownedMessagingPeer;
  private transient CopyOnWriteArrayList<Consumer<Message>> messageListeners =
      new CopyOnWriteArrayList<>();
  private transient CopyOnWriteArrayList<Consumer<Message>> sentMessageListeners =
      new CopyOnWriteArrayList<>();

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public String getBehaviorUrn() {
    return this.behaviorUrn;
  }

  @Override
  public boolean isViable() {
    return viable;
  }

  @Override
  public boolean isAlive() {
    return this.alive;
  }

  @Override
  public boolean start(Object... arguments) {
    if (!viable || urn == null || arguments != null && arguments.length > 0) {
      if (arguments != null && arguments.length > 0) {
        notifications.add(
            Notification.warning(
                "Remote initialization arguments are not yet supported by agent messaging"));
      }
      return false;
    }
    // Mark the optimistic state before publishing so a terminal status received during a very
    // short run cannot be overwritten after publish() returns.
    alive = true;
    boolean accepted = publish(Message.MessageType.AgentStartRequested);
    if (!accepted) {
      alive = false;
    }
    return accepted;
  }

  public void setViable(boolean viable) {
    this.viable = viable;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * non-API: if requested and compilation has succeeded, the Java code of the agent for inspection
   * and debugging
   *
   * @return
   */
  public String getJavaCode() {
    return javaCode;
  }

  /**
   * non-API: the ID of the observation the agent is bound to, if any. Can be only an AGENT if the
   * behavior is a BEHAVIOR; can be any observation if a TASK
   *
   * @return
   */
  public long getObservationId() {
    return observationId;
  }

  public void setObservationId(long observationId) {
    this.observationId = observationId;
  }

  public long getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(long startedAt) {
    this.startedAt = startedAt;
  }

  public long getLastActivityAt() {
    return lastActivityAt;
  }

  public void setLastActivityAt(long lastActivityAt) {
    this.lastActivityAt = lastActivityAt;
  }

  /** True when the service confirms that this single-use instance is waiting for {@link #start}. */
  public boolean isStartDeferred() {
    return startDeferred;
  }

  public void setStartDeferred(boolean startDeferred) {
    this.startDeferred = startDeferred;
  }

  /** True when the service-side runtime confirms that its agent transport is connected. */
  public boolean isMessagingConnected() {
    return messagingConnected;
  }

  public void setMessagingConnected(boolean messagingConnected) {
    this.messagingConnected = messagingConnected;
  }

  /**
   * non-API: the ID of the scope the agent belongs to, if any
   *
   * @return
   */
  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  public void setJavaCode(String javaCode) {
    this.javaCode = javaCode;
  }

  @Override
  public boolean stop() {
    return urn != null && publish(Message.MessageType.AgentStopRequested);
  }

  @Override
  public List<Notification> getNotifications() {
    return this.notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  public void setAlive(boolean alive) {
    this.alive = alive;
  }

  public void setBehaviorUrn(String behaviorUrn) {
    this.behaviorUrn = behaviorUrn;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  @Override
  public List<String> getHandledMessageClasses() {
    return handledMessageClasses;
  }

  public void setHandledMessageClasses(List<String> handledMessageClasses) {
    this.handledMessageClasses =
        handledMessageClasses == null ? List.of() : List.copyOf(handledMessageClasses);
  }

  @Override
  public <T extends Serializable> void tell(T message) {
    if (urn == null || message == null) {
      return;
    }
    if (message instanceof Message agentMessage) {
      publish(agentMessage);
      return;
    }
    RuntimeAgent.CustomMessage customMessage =
        message instanceof RuntimeAgent.CustomMessage custom
            ? new RuntimeAgent.CustomMessage(custom)
            : message instanceof Constant constant
                ? new RuntimeAgent.CustomMessage(constant, null)
                : new RuntimeAgent.CustomMessage(Constant.create("message"), message);
    if (localResponseTo != null && customMessage.inResponseTo() == null) {
      customMessage.setInResponseTo(localResponseTo);
    }
    stampSenderName(customMessage);
    publish(Message.MessageType.CustomAgentMessage, customMessage);
  }

  @Override
  public <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
      T message, Class<? extends R> responseClass) {
    return ask(message, responseClass, null);
  }

  @Override
  public <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
      T message, Class<? extends R> responseClass, Duration timeout) {
    if (urn == null || message == null || responseClass == null || messagingChannel == null) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("A connected recipient, request, and response class are required"));
    }
    RuntimeAgent.CustomMessage request =
        message instanceof RuntimeAgent.CustomMessage custom
            ? new RuntimeAgent.CustomMessage(custom)
            : message instanceof Constant constant
                ? new RuntimeAgent.CustomMessage(constant, null)
                : new RuntimeAgent.CustomMessage(Constant.create("message"), message);
    stampSenderName(request);
    return AgentEventBus.INSTANCE.ask(
        messagingChannel,
        this,
        messageSenderUrn(),
        urn,
        request,
        responseClass,
        timeout,
        true);
  }

  /**
   * Attach this deserialized handle to its AMQP peer. Transport state remains centralized in
   * {@link AgentEventBus} and is not serialized with the bean.
   */
  public boolean connect(MessagingChannel channel) {
    if (urn == null || !AgentEventBus.INSTANCE.subscribe(urn, this, channel, this::receiveMessage)) {
      notifications.add(
          Notification.info(
              "Agent messaging is disabled because its creating scope is not connected"));
      return false;
    }
    this.messagingChannel = channel;
    AgentEventBus.INSTANCE.publish(
        channel, this, urn, urn, Message.MessageType.AgentStatusRequested);
    return true;
  }

  /** Attach a transport peer whose local resources belong exclusively to this handle. */
  public boolean connectOwned(MessagingChannel channel, AutoCloseable ownedPeer) {
    this.ownedMessagingPeer = ownedPeer;
    if (!connect(channel)) {
      if (this.ownedMessagingPeer == ownedPeer) {
        this.ownedMessagingPeer = null;
        closeOwnedPeer(ownedPeer);
      }
      return false;
    }
    return true;
  }

  public void disconnect() {
    if (urn != null) {
      AgentEventBus.INSTANCE.unsubscribe(urn, this);
    }
    messagingChannel = null;
    var ownedPeer = ownedMessagingPeer;
    ownedMessagingPeer = null;
    closeOwnedPeer(ownedPeer);
  }

  private void closeOwnedPeer(AutoCloseable ownedPeer) {
    if (ownedPeer != null) {
      try {
        ownedPeer.close();
      } catch (Exception failure) {
        notifications.add(
            Notification.warning("Cannot close the agent messaging peer: " + failure.getMessage()));
      }
    }
  }

  /**
   * Observe messages received by this client-side handle without creating another AMQP consumer.
   * Listeners are runtime-only and are never serialized with the handle.
   *
   * @return a subscription that removes the listener when closed
   */
  public AutoCloseable addMessageListener(Consumer<Message> listener) {
    if (listener == null) {
      return () -> {};
    }
    listeners().add(listener);
    return () -> listeners().remove(listener);
  }

  /**
   * Observe messages successfully sent by this client-side handle. Listeners are runtime-only and
   * are never serialized with the handle.
   *
   * @return a subscription that removes the listener when closed
   */
  public AutoCloseable addSentMessageListener(Consumer<Message> listener) {
    if (listener == null) {
      return () -> {};
    }
    sentListeners().add(listener);
    return () -> sentListeners().remove(listener);
  }

  /** Runtime-only origin used by sender handles injected into {@code @handle} actions. */
  @JsonIgnore
  public void setLocalSenderUrn(String localSenderUrn) {
    this.localSenderUrn = localSenderUrn;
  }

  /** Runtime-only display identity paired with {@link #setLocalSenderUrn(String)}. */
  @JsonIgnore
  public void setLocalSenderName(String localSenderName) {
    this.localSenderName = localSenderName;
  }

  /** Runtime-only correlation installed on a sender handle injected into a request handler. */
  @JsonIgnore
  public void setLocalResponseTo(String localResponseTo) {
    this.localResponseTo = localResponseTo;
  }

  /** Runtime-only transport context used by reply handles created inside a service agent. */
  @JsonIgnore
  public void setLocalMessagingChannel(MessagingChannel messagingChannel) {
    this.messagingChannel = messagingChannel;
  }

  private String messageSenderUrn() {
    return localSenderUrn == null ? urn : localSenderUrn;
  }

  private void stampSenderName(RuntimeAgent.CustomMessage message) {
    if (message != null && (message.senderName() == null || message.senderName().isBlank())) {
      message.setSenderName(localSenderName == null ? name : localSenderName);
    }
  }

  private boolean publish(Object... messageArguments) {
    if (messagingChannel == null) {
      return false;
    }
    String senderUrn = messageSenderUrn();
    Message message = createSentMessage(senderUrn, messageArguments);
    boolean published =
        AgentEventBus.INSTANCE.publish(messagingChannel, this, senderUrn, urn, message);
    if (published) {
      markActivity();
      for (var listener : sentListeners()) {
        try {
          listener.accept(message);
        } catch (Throwable failure) {
          notifications.add(
              Notification.warning("Agent sent-message listener failed: " + failure.getMessage()));
        }
      }
    }
    return published;
  }

  private Message createSentMessage(String senderUrn, Object... messageArguments) {
    if (messageArguments != null
        && messageArguments.length == 1
        && messageArguments[0] instanceof Message message) {
      if (message.getMessageType() == Message.MessageType.CustomAgentMessage) {
        stampSenderName(message.getPayload(RuntimeAgent.CustomMessage.class));
      }
      return Message.create(
          senderUrn,
          message.getMessageClass(),
          message.getMessageType(),
          message.getPayload(Object.class));
    }
    Object[] supplied = messageArguments == null ? new Object[0] : messageArguments;
    for (Object argument : supplied) {
      if (argument instanceof RuntimeAgent.CustomMessage customMessage) {
        stampSenderName(customMessage);
      }
    }
    Object[] completed = new Object[supplied.length + 1];
    completed[0] = Message.MessageClass.AgentCommunication;
    System.arraycopy(supplied, 0, completed, 1, supplied.length);
    return Message.create(senderUrn, completed);
  }

  void receiveMessage(Message message) {
    if (message == null
        || message.getMessageClass() != Message.MessageClass.AgentCommunication) {
      return;
    }
    if (message.getMessageType() == Message.MessageType.CustomAgentMessage) {
      markActivity();
    }
    boolean terminal = false;
    switch (message.getMessageType()) {
      case AgentStarted -> applyStatus(message, true);
      case AgentStopped -> {
        applyStatus(message, false);
        terminal = true;
      }
      case AgentStatusChanged -> applyStatus(message, null);
      case AgentFailed -> {
        applyStatus(message, false);
        viable = false;
        terminal = true;
        var status = message.getPayload(RuntimeAgent.Status.class);
        notifications.add(
            Notification.error(
                status == null || status.detail() == null
                    ? "The remote agent failed"
                    : status.detail()));
      }
      default -> {
        // Requests and custom events are handled by runtime peers or application subscribers.
      }
    }
    for (var listener : listeners()) {
      try {
        listener.accept(message);
      } catch (Throwable failure) {
        notifications.add(
            Notification.warning("Agent message listener failed: " + failure.getMessage()));
      }
    }
    if (terminal) {
      disconnect();
    }
  }

  private CopyOnWriteArrayList<Consumer<Message>> listeners() {
    if (messageListeners == null) {
      messageListeners = new CopyOnWriteArrayList<>();
    }
    return messageListeners;
  }

  private CopyOnWriteArrayList<Consumer<Message>> sentListeners() {
    if (sentMessageListeners == null) {
      sentMessageListeners = new CopyOnWriteArrayList<>();
    }
    return sentMessageListeners;
  }

  private void applyStatus(Message message, Boolean aliveDefault) {
    var status = message.getPayload(RuntimeAgent.Status.class);
    if (status == null) {
      if (aliveDefault != null) {
        this.alive = aliveDefault;
      }
      return;
    }
    this.alive = status.state() == RuntimeAgent.State.RUNNING;
    this.viable = status.viable();
    this.observationId = status.observationId();
    this.startedAt = status.startedAt();
    this.lastActivityAt = Math.max(this.lastActivityAt, status.lastActivityAt());
  }

  public String toString() {
    String label =
        name == null || name.isBlank()
            ? urn == null || urn.isBlank() ? "agent" : urn
            : name;
    return Character.toString(0x1F464) + " " + label;
  }


  private void markActivity() {
    this.lastActivityAt = Math.max(this.lastActivityAt, System.currentTimeMillis());
  }
}
