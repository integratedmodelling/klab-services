package org.integratedmodelling.amqp;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScopedBrokerProfileTest {
    static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    static ObjectNode profile(int port) {
        var profile = JSON.createObjectNode(); profile.put("port", port);
        var contexts = profile.putArray("contexts");
        for (var id : List.of("s.a", "s.b")) {
            var context = contexts.addObject(); context.put("id", id);
            for (var role : List.of("reader", "publisher")) {
                var identity = context.putObject(role);
                identity.put("username", role + "-" + id.charAt(2));
                identity.put("password", UUID.randomUUID().toString());
            }
        }
        return profile;
    }

    static String compile(ObjectNode profile) throws Exception {
        return ScopedBrokerProfile.compile(JSON.writeValueAsBytes(profile));
    }

    static ObjectNode decode(String location) throws Exception {
        return (ObjectNode) JSON.readTree(Base64.getDecoder().decode(location.substring(location.indexOf(',') + 1)));
    }

    @Test void configurationIsLoopbackOnlyWithExactNamedHostsAndNoGuestOrManagementPort() throws Exception {
        var definition = decode(compile(profile(18197)));
        var port = definition.get("ports").get(0);
        assertEquals(1, definition.get("ports").size());
        assertEquals("127.0.0.1", port.get("bindingAddress").asText());
        assertEquals("AMQP_0_9_1", port.get("protocols").get(0).asText());
        assertEquals("nameAlias", port.get("virtualhostaliases").get(0).get("type").asText());
        assertFalse(definition.has("plugins"));
        assertEquals(4, definition.get("authenticationproviders").get(0).get("users").size());
        assertEquals(2, definition.get("virtualhostnodes").size());
        for (var host : definition.get("virtualhostnodes")) assertFalse(host.get("defaultVirtualHostNode").asBoolean());
        var aclLocation = definition.get("accesscontrolproviders").get(0).get("path").asText();
        var acl = new String(Base64.getDecoder().decode(aclLocation.substring(aclLocation.indexOf(',') + 1)), StandardCharsets.UTF_8);
        assertTrue(acl.startsWith("ACL DENY ALL ACCESS MANAGEMENT\n"));
        assertTrue(acl.endsWith("ACL DENY ALL ALL ALL\n"));
        assertFalse(acl.contains("guest"));
        assertFalse(acl.contains("ACL ALLOW reader-a PUBLISH"));
        assertTrue(acl.contains("ACL ALLOW reader-a ACCESS VIRTUALHOST name=\"s.a\""));
        assertTrue(acl.contains("ACL ALLOW publisher-b PUBLISH EXCHANGE name=\"s.b\" virtualhost_name=\"s.b\""));
    }

    @ParameterizedTest @ValueSource(strings = {
        "null", "[]", "{}", "{", "{\"port\":18197,\"port\":18198,\"contexts\":[]}",
        "{\"port\":18197,\"contexts\":[]} {}", "{\"port\":true,\"contexts\":[]}",
        "{\"port\":18197,\"contexts\":[]}", "{\"port\":18197,\"contexts\":{}}"
    })
    void malformedInputIsRejectedWithoutContentDiagnostics(String encoded) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> ScopedBrokerProfile.compile(encoded.getBytes(StandardCharsets.UTF_8)));
        assertFalse(error.getMessage().contains(encoded)); assertNull(error.getCause());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1023, 65536, -1})
    void unsafePortRejected(int port) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> compile(profile(port)));
    }

    @ParameterizedTest @ValueSource(strings = {"all", "ALL", "guest", "anonymous", "bad user", "a\nACL", "a*", "a\"", ""})
    void aclIdentitiesCannotInjectOrNameReservedPrincipals(String name) throws Exception {
        var profile = profile(18197); ((ObjectNode) profile.at("/contexts/0/reader")).put("username", name);
        assertThrows(IllegalArgumentException.class, () -> compile(profile));
    }

    @ParameterizedTest @ValueSource(strings = {"s", "s.a.extra", "s.a#1", "a.*", "a.b\n", "a.\"b", ""})
    void rootsMustBeSafeExactContextIds(String id) throws Exception {
        var profile = profile(18197); ((ObjectNode) profile.at("/contexts/0")).put("id", id);
        assertThrows(IllegalArgumentException.class, () -> compile(profile));
    }

    @Test void unknownFieldsMissingFieldsAndNontextSecretsFailClosed() throws Exception {
        var base = profile(18197);
        var invalid = base.deepCopy(); invalid.put("bindingAddress", "0.0.0.0");
        var extra = invalid; assertThrows(IllegalArgumentException.class, () -> compile(extra));
        invalid = base.deepCopy(); ((ObjectNode) invalid.at("/contexts/0")).remove("publisher");
        var missing = invalid; assertThrows(IllegalArgumentException.class, () -> compile(missing));
        invalid = base.deepCopy(); ((ObjectNode) invalid.at("/contexts/0/reader")).put("admin", true);
        var elevation = invalid; assertThrows(IllegalArgumentException.class, () -> compile(elevation));
        invalid = base.deepCopy(); ((ObjectNode) invalid.at("/contexts/0/reader")).put("password", 1234);
        var numeric = invalid; assertThrows(IllegalArgumentException.class, () -> compile(numeric));
    }

    @Test void duplicatesWeakOrSharedPasswordsAndProfileBudgetsFailClosed() throws Exception {
        var base = profile(18197);
        var duplicate = base.deepCopy(); ((ObjectNode) duplicate.at("/contexts/1")).put("id", "s.a");
        assertThrows(IllegalArgumentException.class, () -> compile(duplicate));
        var name = base.deepCopy(); ((ObjectNode) name.at("/contexts/1/reader")).put("username", "reader-a");
        assertThrows(IllegalArgumentException.class, () -> compile(name));
        for (var password : List.of("short", "x".repeat(257), "x".repeat(24) + "\n", "x".repeat(24) + "\uD800")) {
            var invalid = base.deepCopy(); ((ObjectNode) invalid.at("/contexts/0/reader")).put("password", password);
            assertThrows(IllegalArgumentException.class, () -> compile(invalid));
        }
        var shared = base.deepCopy(); ((ObjectNode) shared.at("/contexts/1/reader")).put("password", shared.at("/contexts/0/reader/password").asText());
        assertThrows(IllegalArgumentException.class, () -> compile(shared));
        assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.compile(null));
        assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.compile(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.compile(new byte[ScopedBrokerProfile.MAX_BYTES + 1]));
        var large = base.deepCopy(); for (int i = 0; i < 16; i++) large.withArray("contexts").add(base.at("/contexts/0"));
        assertThrows(IllegalArgumentException.class, () -> compile(large));
    }

    @Test void privateFileLoadIsBoundedAndDoesNotEchoPathOrSecret() throws Exception {
        var file = directory.resolve("private-profile.json"); Files.write(file, JSON.writeValueAsBytes(profile(18197)));
        assertNotNull(ScopedBrokerProfile.load(file));
        Files.write(file, new byte[ScopedBrokerProfile.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.load(file));
        var missing = assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.load(directory.resolve("private-missing-file")));
        assertFalse(missing.getMessage().contains("private-missing-file"));
        assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.load(directory));
    }

    @Test void numericLimitsNullFieldsAndTrailingTokensRemainInvalid() throws Exception {
        var port = profile(18197); port.put("port", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> compile(port));
        port.put("port", 18197.5);
        assertThrows(IllegalArgumentException.class, () -> compile(port));
        var nullId = profile(18197); ((ObjectNode) nullId.at("/contexts/0")).putNull("id");
        assertThrows(IllegalArgumentException.class, () -> compile(nullId));
        var noRole = profile(18197); ((ObjectNode) noRole.at("/contexts/0")).putNull("reader");
        assertThrows(IllegalArgumentException.class, () -> compile(noRole));
        var longId = profile(18197); ((ObjectNode) longId.at("/contexts/0")).put("id", "s." + "a".repeat(254));
        assertThrows(IllegalArgumentException.class, () -> compile(longId));
        var longName = profile(18197); ((ObjectNode) longName.at("/contexts/0/reader")).put("username", "a".repeat(65));
        assertThrows(IllegalArgumentException.class, () -> compile(longName));
        var secret = profile(18197); ((ObjectNode) secret.at("/contexts/0/reader")).put("password", "x".repeat(24) + "\u007f");
        assertThrows(IllegalArgumentException.class, () -> compile(secret));
        assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.compile(" ".getBytes(StandardCharsets.UTF_8)));
        var trailing = JSON.writeValueAsString(profile(18197)) + " {}";
        assertThrows(IllegalArgumentException.class, () -> ScopedBrokerProfile.compile(trailing.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void upperLimitsAndValidUnicodeSecretCompileWithoutChangingInputs() throws Exception {
        var profile = profile(65535);
        ((ObjectNode) profile.at("/contexts/0")).put("id", "s." + "a".repeat(253));
        ((ObjectNode) profile.at("/contexts/0/reader")).put("username", "a".repeat(64));
        ((ObjectNode) profile.at("/contexts/0/reader")).put("password", "a".repeat(254) + "\u00e9\ue000");
        var before = profile.deepCopy();
        assertNotNull(compile(profile)); assertEquals(before, profile);
    }
}
