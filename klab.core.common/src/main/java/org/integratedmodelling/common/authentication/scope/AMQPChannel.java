package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.*;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

  String brokerUri;
  String queue;
  String exchangeName;
  private ConnectionFactory connectionFactory;
  private Connection connection;
  private Channel channel;
  private boolean connected = false;
  private Consumer<Message> messageConsumer;
  private String consumerQueue;
  private AMQP.BasicProperties props;
  private boolean online = false;
  private String channelTag;

  /**
   * Creates a new AMQPChannel with the specified federation and queue.
   *
   * @param federation the federation containing the broker URI
   * @param queue the queue name
   */
  public AMQPChannel(Federation federation, String queue, Consumer<Message> messageConsumer) {
    this.brokerUri = federation.getBroker();
    this.queue = queue;
    this.exchangeName = federation.getId() + ".exchange";
    this.messageConsumer = messageConsumer;
    this.online = connect();
  }

  /**
   * Creates a new AMQPChannel with the specified federation and queue.
   *
   * @param federation the federation containing the broker URI
   * @param queue the queue name
   */
  public AMQPChannel(Federation federation, Scope scope, Consumer<Message> messageConsumer) {
    this.brokerUri = federation.getBroker();
//    this.queue = scope.getId();
    this.exchangeName = federation.getId() + ".exchange";
    this.messageConsumer = messageConsumer;
    this.online = connect();
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
      channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true);

      connected = true;
      // Create a unique queue for this consumer
      consumerQueue = channel.queueDeclare().getQueue();

      // Bind the queue to the exchange
      // START: Method using routingKey parameter
      channel.queueBind(consumerQueue, exchangeName, queue);
      // END: Method using routingKey parameter

      DeliverCallback deliverCallback =
          (consumerTag, delivery) -> {
            try {

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

              // Call the consumer with the received message
              if (messageConsumer != null) {
                messageConsumer.accept(message);
              }
            } catch (Exception e) {
              Logging.INSTANCE.error("Error processing received message: " + e.getMessage());
            }
          };

      // Start consuming messages from the unique queue
      channel.basicConsume(consumerQueue, true, deliverCallback, consumerTag -> {});

      return true;

    } catch (Exception e) {
      Logging.INSTANCE.error("Error connecting to AMQP broker: " + e.getMessage());
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
