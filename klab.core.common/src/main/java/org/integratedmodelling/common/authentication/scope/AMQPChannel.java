package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.*;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.Federation;
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

  /**
   * Creates a new AMQPChannel with the specified federation and queue.
   *
   * @param federation the federation containing the broker URI
   * @param queue the queue name
   */
  public AMQPChannel(Federation federation, String queue, Consumer<Message> messageConsumer) {
    this.brokerUri = federation.getBroker();
    this.queue = federation.getId() + "." + queue;
    this.exchangeName = federation.getId() + "." + queue + ".exchange";
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
      this.props =
          new AMQP.BasicProperties.Builder()
              .headers(Map.of("channelId", channel.hashCode()))
              .deliveryMode(2) // persistent
              .contentType("text/plain")
              .build();

      // Declare a fanout exchange
      channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true);

      connected = true;
      // Create a unique queue for this consumer
      consumerQueue = channel.queueDeclare().getQueue();

      // Bind the queue to the exchange
      channel.queueBind(consumerQueue, exchangeName, queue);

      DeliverCallback deliverCallback =
          (consumerTag, delivery) -> {
            try {
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
   * Sends a message to the exchange.
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
      channel.basicPublish(
          exchangeName, "", null, Utils.Json.asString(message).getBytes(StandardCharsets.UTF_8));
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
