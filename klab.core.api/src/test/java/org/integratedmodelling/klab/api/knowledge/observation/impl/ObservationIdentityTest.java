package org.integratedmodelling.klab.api.knowledge.observation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.junit.jupiter.api.Test;

class ObservationIdentityTest {

  @Test
  void namedSubstantialUsesIdentityNameAcrossTransport() {
    var observation = new ObservationImpl();
    observation.setUrn("context-1:individuals:test.tanzania:ruaha");

    assertEquals("ruaha", observation.getName());
    assertEquals("ruaha", Observation.forTransport(observation).getName());

    observation.setName("Ruaha National Park");
    assertEquals("Ruaha National Park", observation.getName());
  }

  @Test
  void onlyAnonymousObservationUrnsAreInterpretedAsNumericIds() {
    var anonymous = ObservationImpl.idFromUrn("context-1", "context-1.713");

    assertTrue(anonymous.isPresent());
    assertEquals(713L, anonymous.getAsLong());
    assertFalse(
        ObservationImpl.idFromUrn(
                "context-1", "context-1:individuals:test.tanzania:ruaha")
            .isPresent());
    assertFalse(ObservationImpl.idFromUrn("context-1", "context-1.not-an-id").isPresent());
  }

  @Test
  void catalogUrnIsIdempotentForPersistedNamedObservations() {
    assertEquals(
        "context-1:individuals:test.tanzania:ruaha",
        ObservationImpl.catalogUrn(
            "context-1", "context-1:individuals:test.tanzania:ruaha"));
  }
}
