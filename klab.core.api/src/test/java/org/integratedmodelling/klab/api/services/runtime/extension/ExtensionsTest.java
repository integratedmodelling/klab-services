package org.integratedmodelling.klab.api.services.runtime.extension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExtensionsTest {

  @Test
  void componentDescriptorNormalizesMissingActorField() {
    var descriptor =
        new Extensions.ComponentDescriptor(
            "test.component",
            null,
            "Test component",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0L);

    assertNotNull(descriptor.actors());
    assertTrue(descriptor.actors().isEmpty());
  }

  @Test
  void libraryDescriptorNormalizesMissingActorField() {
    var descriptor =
        new Extensions.LibraryDescriptor(
            "test.library", "Test library", null, null, null, null, null);

    assertNotNull(descriptor.actors());
    assertTrue(descriptor.actors().isEmpty());
  }
}
