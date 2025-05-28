package org.integratedmodelling.common.authentication.scope;

import java.util.*;
import java.util.function.Consumer;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

/**
 * Abstract base implementation of a messaging channel, providing functionality for sending and
 * receiving messages, managing connections, and setting up messaging queues on a compliant AMQP
 * 0.9.1 broker. This class extends {@link ChannelImpl} and implements the {@link MessagingChannel}
 * interface.
 *
 * <p>The class is designed to enable communication through messaging frameworks by establishing
 * connections, configuring message queues, and facilitating message dispatching to local handlers
 * or through message brokers.
 */
public abstract class MessagingChannelImpl extends ChannelImpl implements MessagingChannel {

  private boolean sender;
  private boolean receiver;
  private AMQPChannel amqpChannel = null;

  private static final Map<String, Map<String, List<Consumer<Message>>>> queueConsumers =
      new HashMap<>();

  public abstract String getId();

  public MessagingChannelImpl(Identity identity, boolean isSender, boolean isReceiver) {
    super(identity);
    this.sender = isSender;
    this.receiver = isReceiver;
  }

  protected MessagingChannelImpl(MessagingChannelImpl other) {
    super(other);
    this.amqpChannel = other.amqpChannel;
    this.sender = other.sender;
    this.receiver = other.receiver;
  }

  protected void copyMessagingSetup(MessagingChannelImpl parent) {
    this.amqpChannel = parent.amqpChannel;
    copyListeners(parent);
  }

  @Override
  public Message send(Object... args) {
    if (this.sender && this.amqpChannel != null && this.amqpChannel.isOnline()) {
      // dispatch to queue if the queue is there
      var message = Message.create(this, args);
      this.amqpChannel.post(message);
    }
    // this will dispatch to the local handlers
    return super.send(args);
  }

  public void installQueueConsumer(
      String scopeId, Message.Queue queue, Consumer<Message> consumer) {
    queueConsumers
        .computeIfAbsent(scopeId, k -> new HashMap<>())
        .computeIfAbsent(
            scopeId + "." + queue.name().toLowerCase(), q -> new ArrayList<Consumer<Message>>())
        .add(consumer);
  }

  public void installQueueConsumer(String queueId, Consumer<Message> consumer) {
    queueConsumers
        .computeIfAbsent(getId(), k -> new HashMap<>())
        .computeIfAbsent(queueId, q -> new ArrayList<Consumer<Message>>())
        .add(consumer);
  }

  protected void closeMessaging() {
    if (this.amqpChannel != null) {
      this.amqpChannel.close();
    }
  }

  /**
   * Sets up messaging by establishing an AMQP channel and configuring it for the specified queues.
   * This method ensures the messaging system is properly initialized and ready for operation.
   *
   * @param federation the {@link Federation} instance representing the messaging federation context
   * @param queues the collection of {@link Message.Queue} instances to be initialized and
   *     configured
   * @return a collection of {@link Message.Queue} instances that were successfully set up and
   *     connected
   */
  public Collection<Message.Queue> setupMessaging(
      Federation federation, String ownId, Collection<Message.Queue> queues) {

    this.amqpChannel =
        new AMQPChannel(
            federation,
            switch (this) {
              case ContextScope ignored1 -> ownId;
              case SessionScope ignored -> ownId;
              default -> federation.getId();
            },
            this.receiver ? this::messageHandler : null);

    return setupQueues(queues);
  }

  private void messageHandler(Message message) {

    Logging.INSTANCE.info("ZIO PERA " + message);

    // TODO listeners, resend to handlers in super, handle reply() in posted messages

    // if there is a consumer installed for this queue, run it
    //                      var consumers = queueConsumers.get(baseQueueName);
    //                      if (consumers != null && consumers.containsKey(queueId)) {
    //                        for (var consumer : consumers.get(queueId)) {
    //                          consumer.accept(message);
    //                          // TODO the consumer may call reply() on the message and if that was
    //     done,
    //                          //  we could reply with the message ID as long as the channel is
    // also a
    //                          //  sender.
    //                          //  reply() would take all the parameters of Message.create() and
    // would
    //                          //  automatically
    //                          //  install the requesting message ID.
    //                        }
    //                      }
  }

  /**
   * Sets up the queues for messaging by declaring them with the broker and configuring message
   * consumers if applicable. This method ensures the queues are connected and ready for either
   * sending or receiving messages based on the channel configuration.
   *
   * @param queues the collection of {@link Message.Queue} instances to be set up
   * @return a collection of {@link Message.Queue} instances that were successfully set up and
   *     connected
   */
  public Collection<Message.Queue> setupQueues(Collection<Message.Queue> queues) {

    //    Connection conn = this.connection;
    //    if (conn == null) {
    //      var holder = findParent((s) -> s.connection != null);
    //      if (holder != null) {
    //        conn = holder.connection;
    //      }
    //    }

    //    Set<Message.Queue> ret = EnumSet.noneOf(Message.Queue.class);
    //    if (conn != null) {
    //      for (var queue : queues) {
    //        try {
    //          String queueId = baseQueueName + "." + queue.name().toLowerCase();
    //          var result = getOrCreateChannel(queue)
    //              .queueDeclare(queueId, true, false, true /* TODO LINK TO SCOPE
    //                     PERSISTENCE */, Map.of());
    //          if (result.getQueue() == null) {
    //            error("Queue " + queueId + " could not be declared");
    //            continue;
    //          }
    //          this.queueNames.put(queue, queueId);
    //          ret.add(queue);
    //        } catch (Throwable e) {
    //          // just don't add the queue, THis includes channel_ == null.
    //        }
    //      }
    //
    //      if (this.receiver) {
    //        Set<Message.Queue> toRemove = EnumSet.noneOf(Message.Queue.class);
    //        // setup consumers. The service-side scopes only receive messages from paired DTs.
    //        for (var queue : ret) {
    //          String queueId = baseQueueName + "." + queue.name().toLowerCase();
    //          try {
    //
    //            DeliverCallback deliverCallback =
    //                (consumerTag, delivery) -> {
    //                  var message =
    //                      Utils.Json.parseObject(
    //                          new String(delivery.getBody(), StandardCharsets.UTF_8),
    // Message.class);
    //
    //                  Logging.INSTANCE.info("ZIO PERA " + message);
    //
    //                  // if there is a consumer installed for this queue, run it
    //                  var consumers = queueConsumers.get(baseQueueName);
    //                  if (consumers != null && consumers.containsKey(queueId)) {
    //                    for (var consumer : consumers.get(queueId)) {
    //                      consumer.accept(message);
    //                      // TODO the consumer may call reply() on the message and if that was
    // done,
    //                      //  we could reply with the message ID as long as the channel is also a
    //                      //  sender.
    //                      //  reply() would take all the parameters of Message.create() and would
    //                      //  automatically
    //                      //  install the requesting message ID.
    //                    }
    //                  }
    //
    //                  switch (queue) {
    //                    case Events -> {
    //                      event(message);
    //                    }
    //                    case Errors -> {
    //                      error(message);
    //                    }
    //                    case Warnings -> {
    //                      warn(message);
    //                    }
    //                    case Info -> {
    //                      info(message);
    //                    }
    //                    case Debug -> {
    //                      debug(message);
    //                    }
    //                    case Clock -> {
    //                      // TODO
    //                    }
    //                    case Status -> {
    //                      // TODO
    //                    }
    //                    case UI -> {
    //                      ui(message);
    //                    }
    //                    case None -> {}
    //                  }
    //                };
    //            getChannel(queue)
    //                .basicConsume(
    //                    queueId,
    //                    true,
    //                    deliverCallback,
    //                    consumerTag -> {
    //                      queueNames.remove(queue);
    //                    });
    //          } catch (IOException e) {
    //            this.queueNames.remove(queue);
    //            error(e);
    //            toRemove.add(queue);
    //          }
    //        }
    //
    //        if (!toRemove.isEmpty()) {
    //          ret.removeAll(toRemove);
    //        }
    //      }
    //
    //      if (connectionFactory != null) {
    //        info(
    //            this.getClass().getCanonicalName()
    //                + " scope connected to queues "
    //                + ret
    //                + " through broker "
    //                + connectionFactory.getHost()
    //                + (receiver ? " (R)" : "")
    //                + (sender ? " (T)" : ""));
    //
    //        if (!ret.isEmpty()) {
    //          configureQueueConsumers(ret);
    //        }
    //      }
    //
    //      return ret;
    //    } else {
    //      error("No connection to broker");
    //    }

    if (this.amqpChannel != null) {
      this.amqpChannel.filter(queues);
    }

    return EnumSet.noneOf(Message.Queue.class);
  }

  private boolean matchApplies(Message.Match match, Message message) {

    if (!match.getApplicableClasses().isEmpty()) {
      if (!match.getApplicableClasses().contains(message.getMessageClass())) {
        return false;
      }
    }
    if (!match.getApplicableTypes().isEmpty()) {
      if (!match.getApplicableTypes().contains(message.getMessageType())) {
        return false;
      }
    }

    if (match.getMessagePredicate() != null) {
      if (!match.getMessagePredicate().test(message)) {
        return false;
      }
    }

    if (match.getPayloadMatch() != null) {
      if (!match.getPayloadMatch().equals(message.getPayload(Object.class))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean hasMessaging() {
    return this.amqpChannel != null;
  }

  @Override
  public boolean isConnected() {
    return this.amqpChannel != null && this.amqpChannel.isOnline();
  }

  @Override
  public boolean isSender() {
    return sender;
  }

  @Override
  public boolean isReceiver() {
    return receiver;
  }
}
