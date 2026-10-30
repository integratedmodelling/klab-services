package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.junit.jupiter.api.Test;

class SemanticFingerprintTransportTest {
  private static final String CONTENT = "semantic-content-v2:" + "a".repeat(64);

  @Test void formatIsVersionedAndFailClosed() {
    assertTrue(SemanticContentFingerprint.valid(CONTENT));
    for (String bad : List.of("", "semantic-content-v3:" + "a".repeat(64),
        "semantic-content-v1:" + "A".repeat(64), "semantic-content-v1:" + "a".repeat(63)))
      assertFalse(SemanticContentFingerprint.valid(bad));
    assertFalse(SemanticContentFingerprint.valid(null));
    assertTrue(SemanticContentFingerprint.valid("semantic-content-v1:"+"a".repeat(64)));
    assertFalse(SemanticContentFingerprint.scoped("semantic-content-v1:"+"a".repeat(64)),"Legacy format is readable, not a scoped certificate");
  }

  @Test void capabilityAndTypedBindingKeepFingerprintAcrossJson() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var caps = new ReasonerCapabilitiesImpl(); caps.setSemanticContentFingerprint(CONTENT);
    assertEquals(CONTENT, mapper.readValue(mapper.writeValueAsBytes(caps),
        ReasonerCapabilitiesImpl.class).getSemanticContentFingerprint());
    var binding = new SingularContextBinding(1, "context:1", "test:Region", "t0(1)",
        "test:Elevation", "reasoner", 7, CONTENT);
    assertEquals(binding, mapper.readValue(mapper.writeValueAsBytes(binding), SingularContextBinding.class));
    var legacy = mapper.readValue(mapper.writeValueAsString(binding)
        .replace(",\"semanticFingerprint\":\"" + CONTENT + "\"", ""), SingularContextBinding.class);
    assertNull(legacy.semanticFingerprint(), "Legacy data must never inherit current capabilities");
  }

  @Test void viewVersionsAreReadableWithoutSilentlyUpgradingLegacyEvidence() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var legacy = view(1, null);
    assertNull(mapper.readValue(mapper.writeValueAsBytes(legacy), CollectiveSelectionView.class).semanticFingerprint());
    var current = view(2, CONTENT);
    assertEquals(current, mapper.readValue(mapper.writeValueAsBytes(current), CollectiveSelectionView.class));
    assertThrows(IllegalArgumentException.class, () -> view(4, CONTENT));
    assertThrows(IllegalArgumentException.class, () -> view(1, CONTENT));
    assertThrows(IllegalArgumentException.class, () -> view(2, "malformed"));
    assertNull(view(2, null).semanticFingerprint(), "Uncertified live-provider evidence stays uncertified");
  }

  private static CollectiveSelectionView view(int version, String fingerprint) {
    return new CollectiveSelectionView(version, "each test:Beech test:Tree", "each test:Tree",
        List.of("test:Beech"), List.of("test:Species"), "reasoner", 1, 0, null, "t0(1)",
        1, List.of(1L), List.of(), List.of(), "scope", null, null, fingerprint);
  }
}
