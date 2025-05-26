package org.integratedmodelling.cli.utils;

import com.rabbitmq.client.*;
import java.io.PrintWriter;
import java.util.*;
import org.integratedmodelling.cli.KlabCLI;
import org.integratedmodelling.common.authentication.scope.AMQPChannel;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import picocli.CommandLine;

@CommandLine.Command(
    name = "message",
    mixinStandardHelpOptions = true,
    version = Version.CURRENT,
    description = {"Commands to test the messaging system.", ""},
    subcommands = {Message.Connect.class, Message.Send.class, Message.Delete.class})
public class Message {

  //  private static Channel channel_;
  //  private static ConnectionFactory connectionFactory = null;
  //  private static Connection connection = null;
  //  private static final Map<
  //          String, List<Consumer<org.integratedmodelling.klab.api.services.runtime.Message>>>
  //      queueConsumers = new HashMap<>();
  private static Map<String, AMQPChannel> channelMap = new HashMap<>();
  private static Federation federation;
  //  private static String consumerTag;

  private static final String EXCHANGE_NAME = "klab-exchange";

  private static Federation federation() {

    if (federation == null) {
      var runtime = KlabCLI.INSTANCE.modeler().user().getService(RuntimeService.class);
      federation =
          KlabCLI.INSTANCE
              .modeler()
              .user()
              .getIdentity()
              .getData()
              .get(UserIdentity.FEDERATION_DATA_PROPERTY, Federation.class);

      if (federation == null && Utils.URLs.isLocalHost(runtime.getUrl())) {
        federation = Federation.local();
      }

      if (federation == null) {
        throw new KlabInternalErrorException("no broker info available");
      }
    }

    return federation;
  }

  //  private static Channel channel() {
  //    if (channel_ == null) {
  //      try {
  //        connectionFactory = new ConnectionFactory();
  //        connectionFactory.setUri(federation().getBroker());
  //        connectionFactory.setAutomaticRecoveryEnabled(true);
  //        connection = connectionFactory.newConnection();
  //        channel_ = connection.createChannel();
  //        channel_.basicQos(1);
  //        channel_.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, true);
  //
  //      } catch (Throwable t) {
  //        throw new KlabInternalErrorException(t);
  //      }
  //    }
  //    return channel_;
  //  }

  @CommandLine.Command(
      name = "connect",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Connect to a queue on a host, using the federation host as a default."})
  public static class Connect implements Runnable {

    @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Parameters String queue;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      channelMap.computeIfAbsent(
          queue,
          q -> {
            out.println(
                "Connecting to queue " + queue + " through broker " + federation().getBroker());
            return new AMQPChannel(federation(), queue);
          });

      out.println(" connected to queue " + queue + " through broker " + federation().getBroker());
    }
  }

  @CommandLine.Command(
      name = "send",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Send a message to a queue."})
  public static class Send implements Runnable {

    @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Parameters List<String> message;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      if (message.size() < 2) {
        err.println("Not enough parameters for send command");
        return;
      }

      var queue = message.getFirst();
      var text = Utils.Strings.join(message.subList(1, message.size()), " ");

      // TODO define message type and payload based on options
      // For now we just send an info notification with the text as the payload
      var msg =
          org.integratedmodelling.klab.api.services.runtime.Message.create(
              KlabCLI.INSTANCE.user(), Notification.info(text));

      var channel =
          channelMap.computeIfAbsent(
              queue,
              q -> {
                out.println(
                    "Connecting to queue " + queue + " through broker " + federation().getBroker());
                var ret = new AMQPChannel(federation(), queue);
                ret.consume(message1 -> out.println("[" + queue + "]: " + message1));
                return ret;
              });

      channel.post(msg);

      //      try {
      //        AMQP.BasicProperties props =
      //            new AMQP.BasicProperties.Builder()
      //                //                .headers(Map.of("channelId", channel().hashCode()))
      //                .deliveryMode(2) // persistent
      //                .contentType("text/plain")
      //                .build();
      //
      //        channel()
      //            .basicPublish(
      //                EXCHANGE_NAME,
      //                queue,
      //                props,
      //                Utils.Json.asString(msg).getBytes(StandardCharsets.UTF_8));
      //      } catch (IOException e) {
      //        throw new RuntimeException(e);
      //      }
    }
  }

  @CommandLine.Command(
      name = "delete",
      mixinStandardHelpOptions = true,
      version = Version.CURRENT,
      description = {"Delete a specified queue."})
  public static class Delete implements Runnable {

    @CommandLine.Spec CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Parameters(description = "Name of the queue to delete")
    String queue;

    @CommandLine.Option(
        names = {"-f", "--force"},
        description = "Force delete even if queue is not empty")
    boolean force = false;

    @Override
    public void run() {

      PrintWriter out = commandSpec.commandLine().getOut();
      PrintWriter err = commandSpec.commandLine().getErr();

      var channel = channelMap.get(queue);
      if (channel != null) {
        channel.close();
        out.println("Queue '" + queue + "' successfully deleted");
      }

      //      try {
      //        // Queue deletion with parameters:
      //        // queue - the queue to delete
      //        // ifUnused - true to only delete if queue has no consumers
      //        // ifEmpty - true to only delete if queue has no messages
      //        channel().queueDelete(queue, !force, !force);
      //        queueConsumers.remove(queue);
      //
      //        out.println("Queue '" + queue + "' successfully deleted");
      //
      //      } catch (IOException e) {
      //        if (e.getCause() instanceof com.rabbitmq.client.ShutdownSignalException) {
      //          err.println("Queue '" + queue + "' does not exist");
      //        } else {
      //          err.println("Failed to delete queue '" + queue + "': " + e.getMessage());
      //          if (!force) {
      //            err.println("Try using --force if the queue is not empty or has consumers");
      //          }
      //        }
      //      }
    }
  }
}
