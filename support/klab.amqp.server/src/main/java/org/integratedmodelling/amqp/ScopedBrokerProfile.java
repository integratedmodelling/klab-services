package org.integratedmodelling.amqp;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

/** Opt-in, loopback-only notification broker provisioning; never an HTTP authorization adapter. */
final class ScopedBrokerProfile {
    static final String PROPERTY = "klab.amqp.scopedProfile";
    static final int MAX_BYTES = 65536;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ScopedBrokerProfile() {}

    static String load(Path file) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw invalid();
            try (var input = Files.newInputStream(file)) {
                return compile(input.readNBytes(MAX_BYTES + 1));
            }
        } catch (IOException failure) {
            throw invalid(); // Never include private paths, malformed JSON, or credentials in diagnostics.
        }
    }

    static String compile(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_BYTES) throw invalid();
        try {
            var root = JSON.readTree(encoded);
            fields(root, "port", "contexts");
            var portNode = root.get("port");
            if (!portNode.isIntegralNumber() || !portNode.canConvertToInt()
                    || portNode.intValue() < 1024 || portNode.intValue() > 65535) throw invalid();
            var contexts = root.get("contexts");
            if (!contexts.isArray() || contexts.isEmpty() || contexts.size() > 16) throw invalid();
            var users = new ArrayList<Map<String, Object>>();
            var nodes = new ArrayList<Map<String, Object>>();
            var ids = new HashSet<String>();
            var names = new HashSet<String>();
            var passwords = new HashSet<String>();
            var acl = new StringBuilder("ACL DENY ALL ACCESS MANAGEMENT\n");
            for (var context : contexts) {
                fields(context, "id", "reader", "publisher");
                var id = value(context.get("id"));
                if (id.length() > 255 || !id.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+") || !ids.add(id)) throw invalid();
                for (var role : List.of("reader", "publisher")) {
                    var identity = context.get(role);
                    fields(identity, "username", "password");
                    var name = value(identity.get("username"));
                    var password = value(identity.get("password"));
                    if (!name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                            || Set.of("all", "guest", "anonymous").contains(name.toLowerCase(Locale.ROOT))
                            || !names.add(name) || password.length() < 24 || password.length() > 256
                            || password.codePoints().anyMatch(c -> c < 33 || c >= 127 && c <= 159 || c >= 0xD800 && c <= 0xDFFF)
                            || !passwords.add(password)) throw invalid();
                    users.add(Map.of("name", name, "type", "managed", "password", password));
                    acl.append("ACL ALLOW ").append(name).append(" ACCESS VIRTUALHOST name=\"").append(id).append("\"\n");
                    var boundary = " virtualhost_name=\"" + id + "\"\n";
                    if (role.equals("reader")) {
                        for (var operation : List.of("CREATE", "CONSUME", "DELETE")) {
                            acl.append("ACL ALLOW ").append(name).append(' ').append(operation)
                                    .append(" QUEUE exclusive=true autodelete=true durable=false").append(boundary);
                        }
                        // Defense in depth only: isolation is enforced by the per-context virtual host.
                        acl.append("ACL ALLOW ").append(name).append(" BIND EXCHANGE name=\"").append(id).append('"').append(boundary);
                    } else {
                        for (var operation : List.of("CREATE", "PUBLISH")) {
                            acl.append("ACL ALLOW ").append(name).append(' ').append(operation)
                                    .append(" EXCHANGE name=\"").append(id).append('"').append(boundary);
                        }
                    }
                }
                var host = Map.of("name", id, "type", "Memory", "connectionThreadPoolSize", 4, "numberOfSelectors", 1,
                        "context", Map.of("qpid.max_message_size", 1048576,
                                "queue.defaultMaximumQueueDepthMessages", 128,
                                "queue.defaultMaximumQueueDepthBytes", 16777216,
                                "queue.defaultOverflowPolicy", "REJECT"));
                nodes.add(Map.of("name", id, "type", "Memory", "defaultVirtualHostNode", false,
                        "virtualHostInitialConfiguration", JSON.writeValueAsString(host)));
            }
            acl.append("ACL DENY ALL ALL ALL\n");
            var broker = Map.of("name", "Scoped loopback notifications", "modelVersion", "9.0",
                    "authenticationproviders", List.of(Map.of("name", "scoped", "type", "Plain",
                            "secureOnlyMechanisms", List.of(), "users", users)),
                    "accesscontrolproviders", List.of(Map.of("name", "scoped-policy", "type", "AclFile",
                            "path", data("text/plain", acl.toString()))),
                    "ports", List.of(Map.of("name", "scoped-amqp", "port", portNode.intValue(), "bindingAddress", "127.0.0.1",
                            "protocols", List.of("AMQP_0_9_1"), "authenticationProvider", "scoped",
                            "virtualhostaliases", List.of(Map.of("name", "exact-name", "type", "nameAlias")))),
                    "virtualhostnodes", nodes);
            return data("application/json", JSON.writeValueAsString(broker));
        } catch (IOException failure) {
            throw invalid();
        }
    }

    private static String data(String type, String value) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void fields(JsonNode object, String... expected) {
        if (object == null || !object.isObject() || object.size() != expected.length) throw invalid();
        for (var name : expected) if (!object.has(name)) throw invalid();
    }

    private static String value(JsonNode node) {
        if (node == null || !node.isTextual() || node.textValue().isEmpty()) throw invalid();
        return node.textValue();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid scoped broker profile; require bounded explicit contexts and distinct broker accounts");
    }
}
