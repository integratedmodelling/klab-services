package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.integratedmodelling.klab.api.provenance.Activity;
import org.junit.jupiter.api.Test;

class SemanticEvidenceTest {
  static final String A = "semantic-content-v1:" + "a".repeat(64);
  static final String B = "semantic-content-v1:" + "b".repeat(64);

  Activity activity(String fingerprint) {
    var result = new org.integratedmodelling.klab.api.provenance.impl.ActivityImpl();
    result.setType(Activity.Type.INSTANTIATION);
    result.getMetadata().put(RuntimeService.COLLECTIVE_REASONER, "reasoner");
    result.getMetadata().put(RuntimeService.COLLECTIVE_REVISION, 1L);
    result.getMetadata().put(RuntimeService.COLLECTIVE_FINGERPRINT, fingerprint);
    return result;
  }

  @Test void bareFingerprintNeverCertifiesDurableEvidenceWithoutCompleteExecutionWitness() {
    assertFalse(SemanticEvidence.matches(activity(A), "reasoner", 17L, A, null, false, null));
    assertFalse(SemanticEvidence.matches(activity(A), "reasoner", 1L, B, null, false, null));
    assertFalse(SemanticEvidence.matches(activity(A), "different-reasoner", 1L, A, null, false, null));
  }

  @Test void currentExecutionRetainsExactCounterAndContentFence() {
    assertTrue(SemanticEvidence.matches(activity(A), "reasoner", 1L, A, null, true, null));
    assertFalse(SemanticEvidence.matches(activity(A), "reasoner", 2L, A, null, true, null));
    assertFalse(SemanticEvidence.matches(activity(A), "reasoner", 1L, B, null, true, null));
  }

  @Test void legacyAndUnboundedProviderEvidenceCannotCertifyDurableDiscovery() {
    var legacy = activity(null);
    assertTrue(SemanticEvidence.matches(legacy, "reasoner", 1L, null, null, true, null));
    assertFalse(SemanticEvidence.matches(legacy, "reasoner", 1L, null, null, false, null));
    assertFalse(SemanticEvidence.matches(legacy, "reasoner", 1L, A, null, false, null));
    assertNull(legacy.getMetadata().get(RuntimeService.COLLECTIVE_FINGERPRINT));
  }

  @Test void malformedOrUnknownFingerprintVersionsCannotCertifyEvenWhenEqual() {
    for (var invalid : new String[] {"", "semantic-content-v1:a", "semantic-content-v3:" + "a".repeat(64),
        "semantic-content-v1:" + "A".repeat(64)})
      assertFalse(SemanticEvidence.matches(activity(invalid), "reasoner", 1L, invalid, null, false, null));
  }
}
