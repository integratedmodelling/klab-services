package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
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

  private Channel channel_;
  private boolean sender;
  private boolean receiver;
  private ConnectionFactory connectionFactory = null;
  private Connection connection = null;
  private final Map<Message.Queue, String> queueNames = new HashMap<>();
  private boolean connected;

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
    copyMessagingSetup(other);
  }

  protected void copyMessagingSetup(MessagingChannelImpl parent) {
    this.channel_ = parent.channel_;
    this.sender = parent.sender;
    this.receiver = parent.receiver;
    this.connectionFactory = parent.connectionFactory;
    this.connection = parent.connection;
    this.connected = parent.connected;
    this.queueNames.putAll(parent.queueNames);
    copyListeners(parent);
  }

  @Override
  public Message send(Object... args) {
    if (this.sender) {
      // dispatch to queue if the queue is there
      var message = Message.create(this, args);
      var queue = queueNames.get(message.getQueue());
      if (queue != null) {
        try {
          getChannel(message.getQueue())
              .basicPublish(
                  "", queue, null, Utils.Json.asString(message).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
          error(e);
        }
      }
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
    if (this.channel_ != null) {
      try {
        for (var queue : queueNames.values()) {
          this.channel_.queueDelete(queue);
        }
        queueNames.clear();
      } catch (Exception e) {
        error("Error closing messaging channel", e);
      }
    }
  }

  protected Channel getOrCreateChannel(Message.Queue queue) {

    //        if (!queueNames.containsKey(queue)) {

    /*
    Looks like just one channel is enough - so one connection factory, one
    connection, one channel. Maybe the whole API could be simpler. Maybe channels are synchronizing? In
     all cases we now can have a queue name w/o a channel so we would need to keep a hash of channels
     and dispose
    properly.
     */
    if (this.channel_ == null) {
      var holder = findParent((p) -> p.channel_ != null);
      if (holder != null) {
        return holder.channel_;
      }
      try {
        this.channel_ = this.connection.createChannel();
      } catch (IOException e) {
        // just return null
      }
    }

    return this.channel_;
  }

  protected Channel getChannel(Message.Queue queue) {
    return getOrCreateChannel(queue);
  }

  /*
   * Channels are not hierarchically organized but most of their derivatives are. If this is a scope,
   * this methods finds the first parent that meets the passed condition.
   *
   * @param filter
   * @return
   */
  private MessagingChannelImpl findParent(Predicate<MessagingChannelImpl> filter) {

    if (filter.test(this)) {
      return this;
    }

    if (this instanceof Scope scope
        && scope.getParentScope() instanceof MessagingChannelImpl parent) {
      return parent.findParent(filter);
    }

    return null;
  }

  /**
   * Establishes a messaging setup by connecting to the specified broker and initializing the
   * provided message queues for communication. If the broker URL is null or an error occurs during
   * connection, an empty collection of queues is returned.
   *
   * @param ownId the identifier for the current entity to configure messaging for
   * @param brokerUrl the URL of the message broker to connect to
   * @param queues the collection of {@link Message.Queue} instances to initialize
   * @return a collection of {@link Message.Queue} that were successfully set up and ready for use
   */
  public Collection<Message.Queue> setupMessaging(
      String ownId, String brokerUrl, Collection<Message.Queue> queues) {

    if (brokerUrl == null) {
      return EnumSet.noneOf(Message.Queue.class);
    }

    try {
      this.connectionFactory = new ConnectionFactory();
      this.connectionFactory.setUri(brokerUrl);
      this.connection = this.connectionFactory.newConnection();
      return setupQueues(ownId, queues);
    } catch (Throwable t) {
      error("Error connecting to broker: no messaging available", t);
      return EnumSet.noneOf(Message.Queue.class);
    }
  }

  /**
   * Sets up the queues for messaging by declaring them with the broker and configuring message
   * consumers if applicable. This method ensures the queues are connected and ready for either
   * sending or receiving messages based on the channel configuration.
   *
   * @param baseQueueName the prefix to use for queue names when declaring queues
   * @param queues the collection of {@link Message.Queue} instances to be set up
   * @return a collection of {@link Message.Queue} instances that were successfully set up and
   *     connected
   */
  public Collection<Message.Queue> setupQueues(
      String baseQueueName, Collection<Message.Queue> queues) {

    Connection conn = this.connection;
    if (conn == null) {
      var holder = findParent((s) -> s.connection != null);
      if (holder != null) {
        conn = holder.connection;
      }
    }

    Set<Message.Queue> ret = EnumSet.noneOf(Message.Queue.class);
    if (conn != null) {
      for (var queue : queues) {
        try {
          String queueId = baseQueueName + "." + queue.name().toLowerCase();
          var result = getOrCreateChannel(queue)
              .queueDeclare(queueId, true, false, true /* TODO LINK TO SCOPE
                     PERSISTENCE */, Map.of());
          if (result.getQueue() == null) {
            error("Queue " + queueId + " could not be declared");
            continue;
          }
          this.queueNames.put(queue, queueId);
          ret.add(queue);
        } catch (Throwable e) {
          // just don't add the queue, THis includes channel_ == null.
        }
      }

      if (this.receiver) {
        Set<Message.Queue> toRemove = EnumSet.noneOf(Message.Queue.class);
        // setup consumers. The service-side scopes only receive messages from paired DTs.
        for (var queue : ret) {
          String queueId = baseQueueName + "." + queue.name().toLowerCase();
          try {

            DeliverCallback deliverCallback =
                (consumerTag, delivery) -> {
                  var message =
                      Utils.Json.parseObject(
                          new String(delivery.getBody(), StandardCharsets.UTF_8), Message.class);

                  Logging.INSTANCE.info("ZIO PERA " + message);

                  // if there is a consumer installed for this queue, run it
                  var consumers = queueConsumers.get(baseQueueName);
                  if (consumers != null && consumers.containsKey(queueId)) {
                    for (var consumer : consumers.get(queueId)) {
                      consumer.accept(message);
                      // TODO the consumer may call reply() on the message and if that was done,
                      //  we could reply with the message ID as long as the channel is also a
                      //  sender.
                      //  reply() would take all the parameters of Message.create() and would
                      //  automatically
                      //  install the requesting message ID.
                    }
                  }

                  switch (queue) {
                    case Events -> {
                      event(message);
                    }
                    case Errors -> {
                      error(message);
                    }
                    case Warnings -> {
                      warn(message);
                    }
                    case Info -> {
                      info(message);
                    }
                    case Debug -> {
                      debug(message);
                    }
                    case Clock -> {
                      // TODO
                    }
                    case Status -> {
                      // TODO
                    }
                    case UI -> {
                      ui(message);
                    }
                    case None -> {}
                  }
                };
            getChannel(queue)
                .basicConsume(
                    queueId,
                    true,
                    deliverCallback,
                    consumerTag -> {
                      queueNames.remove(queue);
                    });
          } catch (IOException e) {
            this.queueNames.remove(queue);
            error(e);
            toRemove.add(queue);
          }
        }

        if (!toRemove.isEmpty()) {
          ret.removeAll(toRemove);
        }
      }

      if (connectionFactory != null) {
        info(
            this.getClass().getCanonicalName()
                + " scope connected to queues "
                + ret
                + " through broker "
                + connectionFactory.getHost()
                + (receiver ? " (R)" : "")
                + (sender ? " (T)" : ""));

        if (!ret.isEmpty()) {
          configureQueueConsumers(ret);
        }
      }

      return ret;
    } else {
      error("No connection to broker");
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
    return connection != null && connection.isOpen();
  }

  /**
   * Do-nothing implementation, override to install consumers when needed.
   *
   * @param availableQueues
   */
  protected void configureQueueConsumers(Set<Message.Queue> availableQueues) {}

  @Override
  public boolean isConnected() {
    return connected;
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
