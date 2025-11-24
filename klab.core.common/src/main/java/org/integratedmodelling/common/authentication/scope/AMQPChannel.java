package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * A channel that connects to an AMQP broker and provides methods to send and receive messages. This
 * class is used for communication between instances of the same class with the same brokerUri and
 * queue. Uses a fanout exchange to implement a publish-subscribe pattern where all subscribers
 * receive all messages.
 *
 * <p>Re: persistence, the strategy is:
 *
 * <p>1. Each federation creates a persistent and a temporary exchange, except the LOCAL_FEDERATION
 * on the local broker, which only creates a temporary exchange.
 *
 * <p>2. Each session scope is a channel to the default federation's exchange - persistent for
 * non-local brokers, temporary otherwise.
 *
 * <p>3. Each context scope is a channel to an exchange whose persistence is linked to the digital
 * twin's configuration. If the latter is persistent, the exchange is also persistent.
 *
 * <p>Currently, there is one exchange per federation and all queues are created in it, which is
 * incompatible with these specs as the exchange and the queue's persistence must be consistent.
 * Must create an exchange per context scope. ALSO: check logic at close() - first, check if it's
 * called on delete action or close action. If delete, currently calling queueDelete
 * indiscriminately, which is wrong. If non-persistent, must close queue AND exchange. If
 * persistent, must close but not delete.
 */
public class AMQPChannel {

  private final String brokerUri;
  private final String exchangeId;
  private final org.integratedmodelling.klab.api.services.runtime.Channel klabChannel;
  private final Federation federation;
  private ConnectionFactory connectionFactory;
  private Connection connection;
  private Channel amqpChannel;
  private boolean connected = false;
  private final Consumer<Message> messageConsumer;
  private String consumerQueue;
  private AMQP.BasicProperties props;
  private boolean online = false;
  private String channelTag;
  private Collection<Message.Queue> queues;

  /**
   * Creates a new AMQPChannel with the specified federation and queue.
   *
   * @param federation the federation containing the broker URI
   * @param exchangeId the exchange name (scope ID for contexts, federation ID for user scopes. Null
   *     can be passed but will cause an inoperative channel)
   */
  public AMQPChannel(
      Federation federation,
      String exchangeId,
      org.integratedmodelling.klab.api.services.runtime.Channel channel,
      Consumer<Message> messageConsumer) {
    this.brokerUri = federation.getBroker();
    this.exchangeId = exchangeId;
    this.federation = federation;
    this.messageConsumer = messageConsumer;
    this.klabChannel = channel;
    if (exchangeId == null
        || channel == null
        || (klabChannel instanceof Scope scope && scope.getType() == Scope.Type.SESSION)) {
      this.online = false;
    } else {
      this.online = connect();
    }
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

    // initialize for the federation. If we are a context, refine later
    var persistence = Federation.LOCAL_FEDERATION_ID.equals(federation.getId()) ? 1 : 2;

    try {
      // Create connection factory
      connectionFactory = new ConnectionFactory();
      connectionFactory.setUri(brokerUri);

      if (klabChannel instanceof ContextScope contextScope) {
        var configuration = contextScope.getConfiguration();
        if (configuration != null
            && persistence == 2
            && !configuration.getPersistence().persistent) {
          persistence = 1;
        }
      }

      // Create connection and channel
      connection = connectionFactory.newConnection();
      amqpChannel = connection.createChannel();
      channelTag = amqpChannel.hashCode() + "";
      this.props =
          new AMQP.BasicProperties.Builder()
              .headers(Map.of("channelId", channelTag))
              .deliveryMode(persistence) // persistent
              .contentType("text/plain")
              .build();

      // Declare a fanout exchange
      amqpChannel.exchangeDeclare(exchangeId, BuiltinExchangeType.FANOUT, persistence == 2);

      connected = true;
      // Create a unique queue for this consumer
      consumerQueue = amqpChannel.queueDeclare().getQueue();

      // Bind the queue to the exchange
      // For FANOUT exchanges the routing key is ignored; use empty string for clarity
      amqpChannel.queueBind(consumerQueue, exchangeId, "");

      if (messageConsumer != null) {

        DeliverCallback deliverCallback =
            (consumerTag, delivery) -> {
              try {
                // Do not process our own messages (identified by channelId header)
                Map<String, Object> headers = delivery.getProperties() != null
                    ? delivery.getProperties().getHeaders()
                    : null;
                if (headers != null) {
                  Object senderId = headers.get("channelId");
                  if (senderId != null && channelTag.equals(String.valueOf(senderId))) {
                    return;
                  }
                }

                // Parse the message from JSON
                Message message =
                    Utils.Json.parseObject(
                        new String(delivery.getBody(), StandardCharsets.UTF_8), Message.class);

                // Filter by subscribed queues if provided
                if (queues != null && !queues.isEmpty() && !queues.contains(message.getQueue())) {
                  return;
                }

                // Broadcast semantics within the same exchange: no dispatchId filtering here
                messageConsumer.accept(message);
              } catch (Exception e) {
                Logging.INSTANCE.error(
                    "Error processing received message: "
                        + e.getMessage()
                        + "\n"
                        + new String(delivery.getBody(), StandardCharsets.UTF_8)
                        + "\nqueue="
                        + consumerQueue);
              }
            };

        // Start consuming messages from the unique queue
        amqpChannel.basicConsume(consumerQueue, true, deliverCallback, consumerTag -> {});
      }

      return true;

    } catch (Exception e) {
      Logging.INSTANCE.error(
          "Error connecting to AMQP broker: "
              + e.getMessage()
              + "\n"
              + brokerUri
              + "\nqueue="
              + consumerQueue);
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
      amqpChannel.basicPublish(
          exchangeId,
          "", // FANOUT exchange ignores routing key; use empty for clarity
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
    if (amqpChannel != null) {
      try {
        if (consumerQueue != null) {
          amqpChannel.queueDelete(consumerQueue);
        }
        amqpChannel.close();
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
