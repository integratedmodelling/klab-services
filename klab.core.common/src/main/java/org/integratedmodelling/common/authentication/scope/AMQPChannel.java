package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.*;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * A channel that connects to an AMQP broker and provides methods to send and receive messages. This
 * class is used for communication between instances of the same class with the same brokerUri and
 * queue. Uses a fanout exchange to implement a publish-subscribe pattern where all subscribers
 * receive all messages.
 */
public class AMQPChannel {

  private final String brokerUri;
  private final String queue;
  private final String exchangeName;
  private final org.integratedmodelling.klab.api.services.runtime.Channel klabChannel;
  private ConnectionFactory connectionFactory;
  private Connection connection;
  private Channel channel;
  private boolean connected = false;
  private final Consumer<Message> messageConsumer;
  private String consumerQueue;
  private AMQP.BasicProperties props;
  private boolean online = false;
  private String channelTag;
  private Collection<Message.Queue> queues;
  private boolean federationWide = false;

  /**
   * Creates a new AMQPChannel with the specified federation and queue.
   *
   * @param federation the federation containing the broker URI
   * @param queue the queue name
   */
  public AMQPChannel(
      Federation federation,
      String queue,
      org.integratedmodelling.klab.api.services.runtime.Channel channel,
      Consumer<Message> messageConsumer) {
    this.brokerUri = federation.getBroker();
    this.queue = queue;
    this.exchangeName = federation.getId() + ".exchange";
    this.messageConsumer = messageConsumer;
    this.online = connect();
    this.federationWide = queue.equals(federation.getId());
    this.klabChannel = channel;
  }

  public void filter(Collection<Message.Queue> queues) {
    this.queues = queues;
  }

  /**
   * Connects to the AMQP broker and creates a channel.
   *
   * @return true if the connection was successful, false otherwise
   */
  private boolean connect() {
    try {
      // Create connection factory
      connectionFactory = new ConnectionFactory();
      connectionFactory.setUri(brokerUri);

      // Create connection and channel
      connection = connectionFactory.newConnection();
      channel = connection.createChannel();
      channelTag = channel.hashCode() + "";
      this.props =
          new AMQP.BasicProperties.Builder()
              .headers(Map.of("channelId", channelTag))
              .deliveryMode(2) // persistent
              .contentType("text/plain")
              .build();

      // Declare a fanout exchange
      // FIXME either switch to a direct exchange or refactor to have just one fanout exchange per
      //  user scope
      channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true);

      connected = true;
      // Create a unique queue for this consumer
      consumerQueue = channel.queueDeclare().getQueue();

      // Bind the queue to the exchange
      channel.queueBind(consumerQueue, exchangeName, queue);

      if (messageConsumer != null) {

        DeliverCallback deliverCallback =
            (consumerTag, delivery) -> {
              try {

                // filter messages from self. TODO this may need configuration
                Map<String, Object> headers = delivery.getProperties().getHeaders();
                if (headers != null && headers.containsKey("channelId")) {
                  if (channelTag.equals(headers.get("channelId").toString())) {
                    return;
                  }
                }

                // Parse the message from JSON
                Message message =
                    Utils.Json.parseObject(
                        new String(delivery.getBody(), StandardCharsets.UTF_8), Message.class);

                // filter queue and identity route if needed
                if (queues != null && !queues.isEmpty() && !queues.contains(message.getQueue())) {
                  return;
                }

                if (klabChannel != null) {
                  // filter identity route. FIXME use direct, so that AMQP does the job and not me,
                  //  or leave fanout with one exchange per user scope. Early experiments were
                  //  painful.
                  if (!klabChannel.getDispatchId().equals(message.getDispatchId())) {
                    return;
                  }
                } else if (!federationWide && !message.getDispatchId().equals(queue)) {
                  return;
                }

                messageConsumer.accept(message);
              } catch (Exception e) {
                Logging.INSTANCE.error(
                    "Error processing received message: "
                        + e.getMessage()
                        + "\n"
                        + new String(delivery.getBody(), StandardCharsets.UTF_8)
                        + "\nqueue="
                        + queue);
              }
            };

        // Start consuming messages from the unique queue
        channel.basicConsume(consumerQueue, true, deliverCallback, consumerTag -> {});
      }

      return true;

    } catch (Exception e) {
      Logging.INSTANCE.error(
          "Error connecting to AMQP broker: "
              + e.getMessage()
              + "\n"
              + brokerUri
              + "\nqueue="
              + queue);
      return false;
    }
  }

  /**
   * Send the message with its own queue filters.
   *
   * @param scope
   * @param message
   */
  public void send(Scope scope, Object... message) {
    post(Message.create(scope, message));
  }

  /**
   * Sends any message to the exchange.
   *
   * @param message the message to send
   */
  public void post(Message message) {
    if (!connected && !connect()) {
      Logging.INSTANCE.error("Cannot post message: not connected to broker");
      return;
    }

    try {
      // Convert message to JSON and send to exchange
      // START: Method using routingKey parameter
      channel.basicPublish(
          exchangeName,
          queue,
          props,
          Utils.Json.asString(message).getBytes(StandardCharsets.UTF_8));
      // END: Method using routingKey parameter
    } catch (IOException e) {
      Logging.INSTANCE.error("Error posting message to exchange: " + e.getMessage());
    }
  }

  public boolean isOnline() {
    return online;
  }

  /** Closes the connection to the AMQP broker. */
  public void close() {
    if (channel != null) {
      try {
        if (consumerQueue != null) {
          channel.queueDelete(consumerQueue);
        }
        channel.close();
      } catch (IOException | TimeoutException e) {
        Logging.INSTANCE.error("Error closing channel: " + e.getMessage());
      }
    }

    if (connection != null) {
      try {
        connection.close();
      } catch (IOException e) {
        Logging.INSTANCE.error("Error closing connection: " + e.getMessage());
      }
    }

    connected = false;
  }
}
