package org.integratedmodelling.common.runtime.actors;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.integratedmodelling.common.authentication.scope.AMQPChannel;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
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

  private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(30);

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

    private Transport(TransportKey key, MessagingChannel parentChannel, boolean receivesMessages) {
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
      deliverLocal(message, null);
    }

    private void deliverLocal(Message message, Object excludedOwner) {
      completeResponse(message);
      for (var subscription : subscriptions) {
        if (subscription.owner() == excludedOwner) {
          continue;
        }
        try {
          subscription.consumer().accept(message);
        } catch (Throwable failure) {
          Logging.INSTANCE.error("Error dispatching agent message for " + key.agentUrn(), failure);
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
  private final ConcurrentMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

  private record PendingRequest(
      Class<? extends Serializable> responseClass, CompletableFuture<Serializable> future) {}

  /** Read-only transport diagnostics for runtime inspection and tests. */
  public record TransportStatus(
      String federationId,
      String brokerIdentity,
      String agentUrn,
      boolean online,
      boolean receiving,
      int subscribers) {}

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
      String agentUrn, Object owner, MessagingChannel parentChannel, Consumer<Message> consumer) {
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

  /** Publish to the remote peer(s) and local subscribers of the same agent handle. */
  public boolean publish(String agentUrn, Object... messageArguments) {
    return publish(agentUrn, agentUrn, messageArguments);
  }

  /**
   * Publish an agent message from {@code senderUrn} to {@code recipientUrn}. The sender's
   * federation selects the destination transport, enabling agent-to-agent communication without a
   * shared all-agent queue.
   */
  public boolean publish(String senderUrn, String recipientUrn, Object... messageArguments) {
    return publish(null, null, senderUrn, recipientUrn, messageArguments);
  }

  /**
   * Publish using the exact federation of {@code sourceChannel}, excluding the publishing owner
   * from in-process loopback delivery.
   *
   * <p>The explicit channel is required by connected agent peers because an agent URN alone is not
   * sufficient to choose between transports connected to different federations or brokers.
   */
  public boolean publish(
      MessagingChannel sourceChannel,
      Object publisher,
      String senderUrn,
      String recipientUrn,
      Object... messageArguments) {
    if (senderUrn == null || recipientUrn == null) {
      return false;
    }
    var source =
        sourceChannel == null
            ? findTransport(senderUrn)
            : transports.get(new TransportKey(sourceChannel.getFederation(), senderUrn));
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
    target.deliverLocal(message, publisher);
    target.amqp.post(message);
    return true;
  }

  /**
   * Publish a correlated custom request and complete with the response payload. Correlation lives
   * in the portable custom-message envelope so it works identically for local and AMQP peers.
   */
  @SuppressWarnings("unchecked")
  public <R extends Serializable> CompletableFuture<R> ask(
      String senderUrn,
      String recipientUrn,
      RuntimeAgent.CustomMessage request,
      Class<? extends R> responseClass,
      Duration timeout) {
    return ask(null, null, senderUrn, recipientUrn, request, responseClass, timeout, true);
  }

  /**
   * Publish a correlated custom request, optionally without a response deadline.
   *
   * @param enforceTimeout false when the request should remain pending until a response or explicit
   *     cancellation
   */
  @SuppressWarnings("unchecked")
  public <R extends Serializable> CompletableFuture<R> ask(
      String senderUrn,
      String recipientUrn,
      RuntimeAgent.CustomMessage request,
      Class<? extends R> responseClass,
      Duration timeout,
      boolean enforceTimeout) {
    return ask(
        null,
        null,
        senderUrn,
        recipientUrn,
        request,
        responseClass,
        timeout,
        enforceTimeout);
  }

  /**
   * Send a correlated request through the exact transport owned by {@code sourceChannel}.
   */
  @SuppressWarnings("unchecked")
  public <R extends Serializable> CompletableFuture<R> ask(
      MessagingChannel sourceChannel,
      Object publisher,
      String senderUrn,
      String recipientUrn,
      RuntimeAgent.CustomMessage request,
      Class<? extends R> responseClass,
      Duration timeout,
      boolean enforceTimeout) {
    if (request == null || responseClass == null) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("An agent request and response class are required"));
    }
    String requestId = UUID.randomUUID().toString();
    var correlated = new RuntimeAgent.CustomMessage(request);
    correlated.setRequestId(requestId);
    correlated.setInResponseTo(null);
    correlated.setFailure(null);
    var future = new CompletableFuture<Serializable>();
    pendingRequests.put(
        requestId,
        new PendingRequest((Class<? extends Serializable>) responseClass, future));
    if (enforceTimeout) {
      Duration effectiveTimeout =
          timeout == null || timeout.isZero() || timeout.isNegative()
              ? DEFAULT_ASK_TIMEOUT
              : timeout;
      future.orTimeout(
          effectiveTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    future.whenComplete((ignored, failure) -> pendingRequests.remove(requestId));
    if (!publish(
        sourceChannel,
        publisher,
        senderUrn,
        recipientUrn,
        Message.MessageType.CustomAgentMessage,
        correlated)) {
      pendingRequests.remove(requestId);
      future.completeExceptionally(
          new IllegalStateException("Agent messaging is not connected for " + senderUrn));
    }
    return (CompletableFuture<R>) (CompletableFuture<?>) future;
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
    var matches =
        transports.entrySet().stream()
        .filter(entry -> entry.getKey().agentUrn().equals(agentUrn))
        .map(java.util.Map.Entry::getValue)
        .toList();
    if (matches.size() > 1) {
      Logging.INSTANCE.error(
          "Agent transport is ambiguous across federations; use a connected handle for "
              + agentUrn);
      return null;
    }
    return matches.isEmpty() ? null : matches.getFirst();
  }

  /** Snapshot all live agent transports without exposing their owners or consumers. */
  public List<TransportStatus> getTransportStatus() {
    return transports.entrySet().stream()
        .map(
            entry ->
                new TransportStatus(
                    entry.getKey().federationId(),
                    UUID.nameUUIDFromBytes(
                            Objects.toString(entry.getKey().broker(), "")
                                .getBytes(StandardCharsets.UTF_8))
                        .toString(),
                    entry.getKey().agentUrn(),
                    entry.getValue().isOnline(),
                    entry.getValue().receivesMessages(),
                    entry.getValue().subscriptions.size()))
        .sorted(
            java.util.Comparator.comparing(TransportStatus::federationId)
                .thenComparing(TransportStatus::agentUrn))
        .toList();
  }

  private Message agentMessage(String senderUrn, Object... arguments) {
    if (arguments != null && arguments.length == 1 && arguments[0] instanceof Message message) {
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

  private void completeResponse(Message message) {
    if (message == null || message.getMessageType() != Message.MessageType.CustomAgentMessage) {
      return;
    }
    var custom = message.getPayload(RuntimeAgent.CustomMessage.class);
    if (custom == null || custom.inResponseTo() == null) {
      return;
    }
    var pending = pendingRequests.remove(custom.inResponseTo());
    if (pending == null) {
      return;
    }
    if (custom.failure() != null) {
      pending.future().completeExceptionally(new IllegalStateException(custom.failure()));
      return;
    }
    Object payload = custom.payload();
    try {
      Serializable response;
      if (payload == null) {
        response = null;
      } else if (pending.responseClass().isInstance(payload)) {
        response = (Serializable) payload;
      } else {
        response =
            JacksonConfiguration.newObjectMapper()
                .convertValue(payload, pending.responseClass());
      }
      pending.future().complete(response);
    } catch (Throwable failure) {
      pending.future().completeExceptionally(failure);
    }
  }

  private boolean isAgentMessage(Message message) {
    return message != null && message.getMessageClass() == Message.MessageClass.AgentCommunication;
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
