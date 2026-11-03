package org.integratedmodelling.amqp;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.SystemLauncherListener;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.SystemConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Actual isolated Qpid sockets and ACLs; not a Runtime or scientific acceptance test. */
class ScopedBrokerAuthorizationTest {
    @TempDir Path directory;

    static Connection connect(int port, String host, String name, String password) throws Exception {
        var factory = new ConnectionFactory();
        factory.setHost("127.0.0.1"); factory.setPort(port); factory.setVirtualHost(host);
        factory.setUsername(name); factory.setPassword(password);
        factory.setAutomaticRecoveryEnabled(false); factory.setTopologyRecoveryEnabled(false);
        factory.setConnectionTimeout(5000); factory.setHandshakeTimeout(5000);
        return factory.newConnection();
    }

    static boolean denied(Throwable failure) {
        for (int i = 0; failure != null && i < 8; i++, failure = failure.getCause()) {
            if (failure instanceof ShutdownSignalException signal) {
                if (signal.getReason() instanceof AMQP.Connection.Close close) return close.getReplyCode() == 403;
                if (signal.getReason() instanceof AMQP.Channel.Close close) return close.getReplyCode() == 403;
            }
        }
        return false;
    }

    @Test @Timeout(60)
    void contextsHaveIndependentReadPublishAndConnectionAuthorityAndOwnedQueueCleanup() throws Exception {
        int port;
        try (var reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { port = reservation.getLocalPort(); }
        ObjectNode profile = ScopedBrokerProfileTest.profile(port);
        var container = new java.util.concurrent.atomic.AtomicReference<SystemConfig<?>>();
        var launcher = new SystemLauncher(new SystemLauncherListener.DefaultSystemLauncherListener() {
            @Override public void onContainerResolve(SystemConfig<?> value) { container.set(value); }
        });
        var connections = new ArrayList<Connection>();
        var previous = System.getProperty("QPID_WORK");
        System.setProperty("QPID_WORK", directory.resolve("private-work").toString());
        try {
            launcher.startup(Map.of("type", "Memory", "initialConfigurationLocation", ScopedBrokerProfileTest.compile(profile)));
            for (var context : profile.withArray("contexts")) {
                var id = context.get("id").asText();
                var publisher = context.get("publisher"); var reader = context.get("reader");
                var producer = connect(port, id, publisher.get("username").asText(), publisher.get("password").asText()); connections.add(producer);
                var outgoing = producer.createChannel(); outgoing.exchangeDeclare(id, "fanout", false); outgoing.confirmSelect();
                var consumer = connect(port, id, reader.get("username").asText(), reader.get("password").asText()); connections.add(consumer);
                var incoming = consumer.createChannel();
                incoming.exchangeDeclarePassive(id);
                var queue = incoming.queueDeclare("", false, true, true, Map.of("qpid.max_count", 2, "qpid.policy_type", "reject")).getQueue();
                incoming.queueBind(queue, id, "");
                var body = ("owned-notification-" + id).getBytes(StandardCharsets.UTF_8);
                outgoing.basicPublish(id, "", null, body); outgoing.waitForConfirmsOrDie(5000);
                GetResponse message = null;
                for (int i = 0; i < 40 && message == null; i++) {
                    message = incoming.basicGet(queue, false);
                    if (message == null) Thread.sleep(50);
                }
                assertNotNull(message, "Expected independently authorized own-context hint");
                assertArrayEquals(body, message.getBody()); incoming.basicAck(message.getEnvelope().getDeliveryTag(), false);
                incoming.queueDelete(queue); // Confirm only this explicitly owned queue's removal.
            }
            for (int i = 0; i < 2; i++) {
                var context = profile.withArray("contexts").get(i);
                var other = profile.withArray("contexts").get(1 - i).get("id").asText();
                for (var role : List.of("reader", "publisher")) {
                    var identity = context.get(role);
                    var failure = assertThrows(Exception.class, () -> {
                        var unexpected = connect(port, other, identity.get("username").asText(), identity.get("password").asText());
                        connections.add(unexpected);
                    });
                    assertTrue(denied(failure), "Cross-context failure must be broker permission denial, not an arbitrary connection error");
                }
            }
            var reader = profile.at("/contexts/0/reader");
            var forbidden = connect(port, "s.a", reader.get("username").asText(), reader.get("password").asText()); connections.add(forbidden);
            var channel = forbidden.createChannel(); channel.confirmSelect();
            var publish = assertThrows(Exception.class, () -> {
                channel.basicPublish("s.a", "", null, "forbidden".getBytes(StandardCharsets.UTF_8));
                channel.waitForConfirmsOrDie(5000);
            });
            assertTrue(denied(publish), "Reader publication must be denied by the broker");
            // Inspect independently from the former receiving channels: no owned queues remain.
            Broker<?> broker = container.get().getChildren(Broker.class).iterator().next();
            assertEquals(2, broker.getVirtualHostNodes().size());
            for (var node : broker.getVirtualHostNodes()) {
                assertTrue(node.getVirtualHost().getChildren(org.apache.qpid.server.model.Queue.class).isEmpty());
                assertNotNull(node.getVirtualHost().getChildByName(org.apache.qpid.server.model.Exchange.class, node.getName()));
            }
        } finally {
            for (var connection : connections) if (connection.isOpen()) try { connection.close(); } catch (Exception ignored) {}
            try { launcher.shutdown(); }
            finally {
                if (previous == null) System.clearProperty("QPID_WORK"); else System.setProperty("QPID_WORK", previous);
            }
        }
    }
}
