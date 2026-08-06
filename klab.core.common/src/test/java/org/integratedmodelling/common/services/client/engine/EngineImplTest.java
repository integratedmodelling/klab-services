package org.integratedmodelling.common.services.client.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.junit.jupiter.api.Test;

class EngineImplTest {

  @Test
  void roundTripsPhysicalDistributionIdentity() {
    var tag = Stack.Tag.of(Version.create("1.2.3-alpha1"), "develop", "202608061030", true, false);

    var restored =
        EngineImpl.deserializeDistributionTag(EngineImpl.serializeDistributionTag(tag));

    assertEquals(tag.version(), restored.version());
    assertEquals(tag.release(), restored.release());
    assertEquals(tag.build(), restored.build());
  }

  @Test
  void roundTripsHeadAndNullableSegments() {
    var tag = Stack.Tag.of(Version.HEAD, null, "source", true, false);

    var restored =
        EngineImpl.deserializeDistributionTag(EngineImpl.serializeDistributionTag(tag));

    assertEquals(Version.HEAD, restored.version());
    assertNull(restored.release());
    assertEquals(tag.build(), restored.build());
  }

  @Test
  void rejectsInvalidPersistedTags() {
    assertNull(EngineImpl.deserializeDistributionTag(""));
    assertNull(EngineImpl.deserializeDistributionTag("not-a-tag"));
    assertNull(EngineImpl.deserializeDistributionTag("1.2.3|invalid*base64|also-invalid"));
  }
}
