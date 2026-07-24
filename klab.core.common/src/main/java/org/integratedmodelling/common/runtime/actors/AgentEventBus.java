package org.integratedmodelling.common.runtime.actors;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.integratedmodelling.common.authentication.scope.AMQPChannel;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

/**
 * Process-wide registry and AMQP transport for runtime-agent messages.
 *
 * <p>Each agent URN maps to one fanout exchange in its federation and one transport per JVM.
 * Multiple local handles subscribe to that transport without creating additional broker consumers.
 * Publications are dispatched locally and posted to AMQP; the AMQP channel suppresses its own
 * broker echo, so a message is delivered once to each local subscriber.
 *
 * <p>No transport state is stored in serializable {@code Agent} handles. A handle uses its URN and
 * object identity to subscribe or unsubscribe here after deserialization.
 */
public enum AgentEventBus {
  INSTANCE;

  private record TransportKey(String federationId, String broker, String agentUrn) {

    private TransportKey(Federation federation, String agentUrn) {
      this(federation.getId(), federation.getBroker(), agentUrn);
    }
  }

  private record Subscription(Object owner, Consumer<Message> consumer) {}

  private final class Transport {

    private final TransportKey key;
    private final MessagingChannel parentChannel;
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AMQPChannel amqp;
    private final boolean receivesMessages;

    private Transport(
        TransportKey key, MessagingChannel parentChannel, boolean receivesMessages) {
      this.key = key;
      this.parentChannel = parentChannel;
      this.receivesMessages = receivesMessages;
      this.amqp =
          AMQPChannel.forAgent(
              parentChannel.getFederation(),
              key.agentUrn(),
              parentChannel,
              receivesMessages ? this::receiveRemote : null);
    }

    private void receiveRemote(Message message) {
      if (isAgentMessage(message)) {
        deliverLocal(message);
      } else {
        Logging.INSTANCE.warn(
            "Ignoring non-agent message received on agent exchange " + key.agentUrn());
      }
    }

    private void deliverLocal(Message message) {
      for (var subscription : subscriptions) {
        try {
          subscription.consumer().accept(message);
        } catch (Throwable failure) {
          Logging.INSTANCE.error(
              "Error dispatching agent message for " + key.agentUrn(), failure);
        }
      }
    }

    private void subscribe(Object owner, Consumer<Message> consumer) {
      subscriptions.removeIf(subscription -> subscription.owner() == owner);
      subscriptions.add(new Subscription(owner, consumer));
    }

    private boolean unsubscribe(Object owner) {
      return subscriptions.removeIf(subscription -> subscription.owner() == owner);
    }

    private boolean hasSubscribers() {
      return !subscriptions.isEmpty();
    }

    private boolean isOnline() {
      return amqp.isOnline();
    }

    private boolean receivesMessages() {
      return receivesMessages;
    }

    private void close() {
      amqp.close();
    }
  }

  private final ConcurrentMap<TransportKey, Transport> transports = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Class<? extends Serializable>> payloadTypes =
      new ConcurrentHashMap<>();

  /**
   * Allow a concrete serializable class to be reconstructed from a custom agent-message payload.
   * Components defining message DTOs should call this on both communicating runtimes when they are
   * loaded. The explicit registry prevents an untrusted wire-level class name from becoming an
   * unrestricted class-loading request.
   */
  public void registerPayloadType(Class<? extends Serializable> payloadType) {
    if (payloadType != null
        && !payloadType.isInterface()
        && !java.lang.reflect.Modifier.isAbstract(payloadType.getModifiers())) {
      payloadTypes.put(payloadType.getName(), payloadType);
    }
  }

  public Class<? extends Serializable> resolvePayloadType(String className) {
    return className == null ? null : payloadTypes.get(className);
  }

  /**
   * Subscribe a local peer to the exchange identified by an agent URN.
   *
   * @return true if the parent scope was connected and the agent transport was established
   */
  public boolean subscribe(
      String agentUrn,
      Object owner,
      MessagingChannel parentChannel,
      Consumer<Message> consumer) {
    if (agentUrn == null
        || agentUrn.isBlank()
        || owner == null
        || consumer == null
        || !canMessage(parentChannel)) {
      return false;
    }
    var key = new TransportKey(parentChannel.getFederation(), agentUrn);
    var transport =
        transports.compute(
            key,
            (ignored, existing) -> {
              if (existing == null) {
                return new Transport(key, parentChannel, true);
              }
              if (!existing.receivesMessages()) {
                existing.close();
                return new Transport(key, parentChannel, true);
              }
              return existing;
            });
    if (!transport.isOnline()) {
      transports.remove(key, transport);
      transport.close();
      return false;
    }
    transport.subscribe(owner, consumer);
    return true;
  }

  /**
   * Remove all subscriptions belonging to {@code owner} for the supplied agent URN.
   *
   * @return true when at least one subscription was removed
   */
  public boolean unsubscribe(String agentUrn, Object owner) {
    if (agentUrn == null || owner == null) {
      return false;
    }
    boolean removed = false;
    for (var entry : transports.entrySet()) {
      if (entry.getKey().agentUrn().equals(agentUrn)) {
        var transport = entry.getValue();
        removed |= transport.unsubscribe(owner);
        if (!transport.hasSubscribers() && transports.remove(entry.getKey(), transport)) {
          transport.close();
        }
      }
    }
    return removed;
  }

  /**
   * Publish to the remote peer(s) and local subscribers of the same agent handle.
   */
  public boolean publish(String agentUrn, Object... messageArguments) {
    return publish(agentUrn, agentUrn, messageArguments);
  }

  /**
   * Publish an agent message from {@code senderUrn} to {@code recipientUrn}. The sender's
   * federation selects the destination transport, enabling agent-to-agent communication without a
   * shared all-agent queue.
   */
  public boolean publish(
      String senderUrn, String recipientUrn, Object... messageArguments) {
    if (senderUrn == null || recipientUrn == null) {
      return false;
    }
    var source = findTransport(senderUrn);
    if (source == null || !source.isOnline()) {
      return false;
    }
    var targetKey = new TransportKey(source.parentChannel.getFederation(), recipientUrn);
    var target =
        transports.computeIfAbsent(
            targetKey, ignored -> new Transport(targetKey, source.parentChannel, false));
    if (!target.isOnline()) {
      transports.remove(targetKey, target);
      target.close();
      return false;
    }
    Message message = agentMessage(senderUrn, messageArguments);
    target.deliverLocal(message);
    target.amqp.post(message);
    return true;
  }

  public boolean isSubscribed(String agentUrn, Object owner) {
    if (agentUrn == null || owner == null) {
      return false;
    }
    for (var entry : transports.entrySet()) {
      if (entry.getKey().agentUrn().equals(agentUrn)
          && entry.getValue().subscriptions.stream()
              .anyMatch(subscription -> subscription.owner() == owner)) {
        return true;
      }
    }
    return false;
  }

  private Transport findTransport(String agentUrn) {
    return transports.entrySet().stream()
        .filter(entry -> entry.getKey().agentUrn().equals(agentUrn))
        .map(java.util.Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private Message agentMessage(String senderUrn, Object... arguments) {
    if (arguments != null
        && arguments.length == 1
        && arguments[0] instanceof Message message) {
      requireSupportedAgentMessage(message);
      registerPayload(message);
      return Objects.requireNonNull(
          Message.create(
              senderUrn,
              message.getMessageClass(),
              message.getMessageType(),
              message.getPayload(Object.class)));
    }
    Object[] supplied = arguments == null ? new Object[0] : arguments;
    Object[] completed = Arrays.copyOf(supplied, supplied.length + 1);
    System.arraycopy(completed, 0, completed, 1, supplied.length);
    completed[0] = Message.MessageClass.AgentCommunication;
    var message = Objects.requireNonNull(Message.create(senderUrn, completed));
    requireSupportedAgentMessage(message);
    registerPayload(message);
    return message;
  }

  @SuppressWarnings("unchecked")
  private void registerPayload(Message message) {
    if (message.getMessageType() != Message.MessageType.CustomAgentMessage) {
      return;
    }
    var custom = message.getPayload(RuntimeAgent.CustomMessage.class);
    if (custom != null && custom.payload() != null) {
      registerPayloadType((Class<? extends Serializable>) custom.payload().getClass());
    }
  }

  private boolean isAgentMessage(Message message) {
    return message != null
        && message.getMessageClass() == Message.MessageClass.AgentCommunication;
  }

  private void requireSupportedAgentMessage(Message message) {
    if (!isAgentMessage(message)) {
      throw new IllegalArgumentException(
          "Messages sent through the agent event bus must use AgentCommunication");
    }
    if (message.getMessageType() == null
        || Arrays.stream(Message.MessageClass.AgentCommunication.messageTypes)
            .noneMatch(type -> type == message.getMessageType())) {
      throw new IllegalArgumentException(
          "Unsupported agent message type " + message.getMessageType());
    }
  }

  private boolean canMessage(MessagingChannel channel) {
    return channel != null
        && channel.isConnected()
        && channel.getFederation() != null
        && channel.getFederation().getBroker() != null
        && !channel.getFederation().getBroker().isBlank();
  }
}
