package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
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
 * A channel that connects to an AMQP broker and provides methods to send and receive messages.
 * This class is used for communication between instances of the same class with the same brokerUri and queue.
 */
public class AMQPChannel {

  String brokerUri;
  String queue;
  private ConnectionFactory connectionFactory;
  private Connection connection;
  private Channel channel;
  private boolean connected = false;
  private Consumer<Message> messageConsumer;

  /**
   * Creates a new AMQPChannel with the specified federation and queue.
   * 
   * @param federation the federation containing the broker URI
   * @param queue the queue name
   */
  public AMQPChannel(Federation federation, String queue) {
    this.brokerUri = federation.getBroker();
    this.queue = federation.getId() + "." + queue;
    connect();
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

      // Declare a persistent queue
      channel.queueDeclare(queue, true, false, false, Map.of());

      connected = true;
      return true;
    } catch (Exception e) {
      Logging.INSTANCE.error("Error connecting to AMQP broker: " + e.getMessage());
      return false;
    }
  }

  /**
   * Sends a message to the queue.
   * 
   * @param message the message to send
   */
  public void post(Message message) {
    if (!connected && !connect()) {
      Logging.INSTANCE.error("Cannot post message: not connected to broker");
      return;
    }

    try {
      // Convert message to JSON and send to queue
      channel.basicPublish("", queue, null, Utils.Json.asString(message).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      Logging.INSTANCE.error("Error posting message to queue: " + e.getMessage());
    }
  }

  /**
   * Sets up a consumer to receive messages from the queue.
   * 
   * @param consumer the consumer to call when a message is received
   */
  public void consume(Consumer<Message> consumer) {
    if (!connected && !connect()) {
      Logging.INSTANCE.error("Cannot consume messages: not connected to broker");
      return;
    }

    this.messageConsumer = consumer;

    try {
      // Set up a delivery callback to process incoming messages
      DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        try {
          // Parse the message from JSON
          Message message = Utils.Json.parseObject(
              new String(delivery.getBody(), StandardCharsets.UTF_8), Message.class);

          // Call the consumer with the received message
          if (messageConsumer != null) {
            messageConsumer.accept(message);
          }
        } catch (Exception e) {
          Logging.INSTANCE.error("Error processing received message: " + e.getMessage());
        }
      };

      // Start consuming messages from the queue
      channel.basicConsume(queue, true, deliverCallback, consumerTag -> {});
    } catch (IOException e) {
      Logging.INSTANCE.error("Error setting up message consumer: " + e.getMessage());
    }
  }

  /**
   * Closes the connection to the AMQP broker.
   */
  public void close() {
    if (channel != null) {
      try {
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
