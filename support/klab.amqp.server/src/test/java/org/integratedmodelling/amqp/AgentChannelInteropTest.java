package org.integratedmodelling.amqp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.SystemLauncherListener;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.SystemConfig;
import org.integratedmodelling.common.authentication.scope.AMQPChannel;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Actual isolated broker + native serializer/channel peer, NOT a managed Runtime agent. */
class AgentChannelInteropTest {
  @TempDir Path directory;

  @Test @Timeout(90)
  void pythonAndNativePeerExchangeLifecycleCommandsWithOwnedCleanupAndWrongPrincipalDenial() throws Exception {
    String python = System.getProperty("agent.python");
    Assumptions.assumeTrue(python != null, "Set -Dagent.python to an events-extra Python; absent is SKIPPED, not accepted");
    int port;
    try (var reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
      port = reservation.getLocalPort();
    }
    String federationId = "test-federation", urn = "test.agent.status";
    String exchange = "klab.agent." + UUID.nameUUIDFromBytes((federationId + '\0' + urn).getBytes(StandardCharsets.UTF_8));
    var profile = ScopedBrokerProfileTest.profile(port);
    var definition = ScopedBrokerProfileTest.decode(ScopedBrokerProfileTest.compile(profile));
    // New TEST-ONLY profile. The production context-reader ACL compiler is unchanged.
    var acl = new StringBuilder("ACL DENY ALL ACCESS MANAGEMENT\n");
    for (var context : profile.withArray("contexts")) {
      String host = context.get("id").asText();
      for (String role : List.of("reader", "publisher")) {
        String name = context.get(role).get("username").asText();
        String boundary = " virtualhost_name=\"" + host + "\"\n";
        acl.append("ACL ALLOW ").append(name).append(" ACCESS VIRTUALHOST name=\"").append(host).append("\"\n");
        for (String op : List.of("CREATE", "CONSUME", "DELETE")) {
          acl.append("ACL ALLOW ").append(name).append(' ').append(op)
              .append(" QUEUE exclusive=true autodelete=true durable=false").append(boundary);
        }
        // Native creator owns this exact exchange's final auto-deletion. The
        // client is still denied DELETE/CREATE EXCHANGE; production ACLs unchanged.
        for (String op : role.equals("publisher") ? List.of("CREATE", "PUBLISH", "BIND", "DELETE") : List.of("PUBLISH", "BIND")) {
          acl.append("ACL ALLOW ").append(name).append(' ').append(op).append(" EXCHANGE name=\"")
              .append(exchange).append('"').append(boundary);
        }
      }
    }
    acl.append("ACL DENY ALL ALL ALL\n");
    ((ObjectNode) definition.at("/accesscontrolproviders/0")).put("path", "data:text/plain;base64,"
        + Base64.getEncoder().encodeToString(acl.toString().getBytes(StandardCharsets.UTF_8)));
    String location = "data:application/json;base64," + Base64.getEncoder().encodeToString(
        ScopedBrokerProfileTest.JSON.writeValueAsBytes(definition));
    var container = new AtomicReference<SystemConfig<?>>();
    var launcher = new SystemLauncher(new SystemLauncherListener.DefaultSystemLauncherListener() {
      @Override public void onContainerResolve(SystemConfig<?> value) { container.set(value); }
    });
    String previous = System.getProperty("QPID_WORK");
    System.setProperty("QPID_WORK", directory.resolve("broker-work").toString());
    AMQPChannel nativePeer = null;
    Process process = null;
    var startCount = new AtomicInteger(); var stopCount = new AtomicInteger();
    try {
      launcher.startup(Map.of("type", "Memory", "initialConfigurationLocation", location));
      var nativeIdentity = profile.at("/contexts/0/publisher");
      var federation = new Federation(federationId, "amqp://" + nativeIdentity.get("username").asText()
          + ":" + nativeIdentity.get("password").asText() + "@127.0.0.1:" + port + "/s.a");
      var parent = (MessagingChannel) Proxy.newProxyInstance(getClass().getClassLoader(),
          new Class<?>[] {MessagingChannel.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getFederation" -> federation;
            case "isConnected" -> true;
            case "getDispatchId" -> urn;
            default -> null;
          });
      var current = new AtomicReference<>(RuntimeAgent.State.READY);
      AMQPChannel[] peer = new AMQPChannel[1];
      nativePeer = peer[0] = AMQPChannel.forAgent(federation, urn, parent, message -> {
        Message.MessageType reply;
        switch (message.getMessageType()) {
          case AgentStartRequested -> { startCount.incrementAndGet(); current.set(RuntimeAgent.State.RUNNING); reply = Message.MessageType.AgentStarted; }
          case AgentStopRequested -> { stopCount.incrementAndGet(); current.set(RuntimeAgent.State.STOPPED); reply = Message.MessageType.AgentStopped; }
          case AgentStatusRequested -> reply = Message.MessageType.AgentStatusChanged;
          default -> { return; }
        }
        peer[0].post(Message.create(urn, Message.MessageClass.AgentCommunication, reply,
            new RuntimeAgent.Status(urn, current.get(), current.get() != RuntimeAgent.State.STOPPED,
                null, System.currentTimeMillis())));
      });
      assertTrue(nativePeer.isOnline(), "Native agent wire peer must really connect");
      var input = ScopedBrokerProfileTest.JSON.createObjectNode();
      input.put("fixture", "OWNED_NATIVE_WIRE_NOT_MANAGED_AGENT");
      input.put("agent_urn", urn); input.put("federation_id", federationId);
      input.put("broker_url", "amqp://127.0.0.1:" + port + "/s.a");
      input.put("other_vhost_url", "amqp://127.0.0.1:" + port + "/s.b");
      input.put("username", profile.at("/contexts/0/reader/username").asText());
      input.put("password", profile.at("/contexts/0/reader/password").asText());
      Path root = Path.of("../..").toAbsolutePath().normalize();
      Path output = directory.resolve("python-result.jsonl");
      process = new ProcessBuilder(python, root.resolve("python-client/tools/agent_channel_interop.py").toString())
          .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
      try (var stdin = process.getOutputStream()) { stdin.write(ScopedBrokerProfileTest.JSON.writeValueAsBytes(input)); }
      assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Owned Python wire probe must be bounded");
      String result = Files.readString(output);
      assertEquals(0, process.exitValue(), result);
      var decoded = ScopedBrokerProfileTest.JSON.readTree(result);
      assertEquals(List.of("READY", "RUNNING", "STOPPED"), ScopedBrokerProfileTest.JSON.convertValue(decoded.get("reports"), List.class));
      assertTrue(decoded.get("wrong_vhost_denied").asBoolean());
      assertTrue(decoded.get("invalid_password_denied").asBoolean());
      assertTrue(decoded.get("local_cleanup").asBoolean());
      assertFalse(decoded.get("managed_agent_execution").asBoolean());
      assertEquals("UNCONFIRMED_OPT_IN", decoded.get("publication").asText());
      assertEquals(1, startCount.get()); assertEquals(1, stopCount.get());
      var reader = profile.at("/contexts/0/reader");
      var deniedConnection = ScopedBrokerAuthorizationTest.connect(port, "s.a",
          reader.get("username").asText(), reader.get("password").asText());
      try {
        var deniedChannel = deniedConnection.createChannel();
        var denial = assertThrows(Exception.class, () -> deniedChannel.exchangeDelete(exchange));
        assertTrue(ScopedBrokerAuthorizationTest.denied(denial), "Client must not gain exchange-deletion authority");
      } finally {
        if (deniedConnection.isOpen()) deniedConnection.close();
      }
      nativePeer.close(); nativePeer = null;
      Broker<?> broker = container.get().getChildren(Broker.class).iterator().next();
      for (var node : broker.getVirtualHostNodes()) {
        assertTrue(node.getVirtualHost().getChildren(org.apache.qpid.server.model.Queue.class).isEmpty(),
            "Independent broker inventory must show no owned Python/native queues");
      }
      ((ObjectNode) decoded).put("independently_verified_owned_queues_remaining", 0);
      ((ObjectNode) decoded).put("client_exchange_delete_denied", true);
      Files.writeString(Path.of("target/agent-channel-interop-result.json"),
          ScopedBrokerProfileTest.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(decoded));
    } finally {
      if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
      if (nativePeer != null) nativePeer.close();
      try { launcher.shutdown(); }
      finally { if (previous == null) System.clearProperty("QPID_WORK"); else System.setProperty("QPID_WORK", previous); }
    }
  }
}
