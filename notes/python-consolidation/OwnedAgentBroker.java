import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.qpid.server.SystemLauncher;
import com.fasterxml.jackson.databind.ObjectMapper;

/** TEST-only genuine broker bootstrap. No fake lifecycle peer or HTTP shutdown listener. */
public class OwnedAgentBroker {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Private profile file required");
    var profile = new ObjectMapper().readTree(Files.readAllBytes(Path.of(args[0])));
    int port = profile.path("port").asInt();
    String user = profile.path("username").asText(), password = profile.path("password").asText();
    String host = profile.path("vhost").asText();
    if (port < 1024 || port > 65535 || port == 20937 || port == 20938
        || !user.matches("owned-[a-z0-9]+") || !host.matches("owned[.]agent")
        || !password.matches("[A-Za-z0-9_-]{32,128}"))
      throw new IllegalArgumentException("Invalid private TEST broker profile");
    var json = new ObjectMapper();
    String acl = "ACL DENY ALL ACCESS MANAGEMENT\nACL ALLOW " + user
        + " ACCESS VIRTUALHOST name=\"" + host + "\"\nACL ALLOW " + user
        + " ALL ALL virtualhost_name=\"" + host + "\"\nACL DENY ALL ALL ALL\n";
    var virtualHost = Map.of("name", host, "type", "Memory", "connectionThreadPoolSize", 4,
        "numberOfSelectors", 1, "context", Map.of("qpid.max_message_size", 1048576,
            "queue.defaultMaximumQueueDepthMessages", 128,
            "queue.defaultMaximumQueueDepthBytes", 16777216, "queue.defaultOverflowPolicy", "REJECT"));
    var definition = Map.of("name", "Owned managed agent acceptance", "modelVersion", "9.0",
        "authenticationproviders", java.util.List.of(Map.of("name", "owned", "type", "Plain",
            "secureOnlyMechanisms", java.util.List.of(), "users", java.util.List.of(
                Map.of("name", user, "type", "managed", "password", password)))),
        "accesscontrolproviders", java.util.List.of(Map.of("name", "owned-policy", "type", "AclFile",
            "path", data("text/plain", acl))),
        "ports", java.util.List.of(Map.of("name", "owned-amqp", "port", port,
            "bindingAddress", "127.0.0.1", "protocols", java.util.List.of("AMQP_0_9_1"),
            "authenticationProvider", "owned", "virtualhostaliases", java.util.List.of(
                Map.of("name", "exact-name", "type", "nameAlias")))),
        "virtualhostnodes", java.util.List.of(Map.of("name", host, "type", "Memory",
            "defaultVirtualHostNode", false, "virtualHostInitialConfiguration", json.writeValueAsString(virtualHost))));
    var launcher = new SystemLauncher();
    Runtime.getRuntime().addShutdownHook(new Thread(launcher::shutdown));
    launcher.startup(Map.of("type", "Memory", "initialConfigurationLocation",
        data("application/json", json.writeValueAsString(definition))));
    System.out.println("Owned broker started; no managed lifecycle evidence inferred");
    new CountDownLatch(1).await();
  }
  static String data(String type, String text) {
    return "data:" + type + ";base64," + java.util.Base64.getEncoder().encodeToString(
        text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
