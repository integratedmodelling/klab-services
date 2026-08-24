package org.integratedmodelling.klab.api.services.runtime.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            0L,
            null,
            null,
            0L);

    assertNotNull(descriptor.actors());
    assertTrue(descriptor.actors().isEmpty());
    assertEquals(Extensions.ComponentImportType.FILE, descriptor.importType());
    assertEquals(Extensions.ComponentUpdateStatus.UNKNOWN, descriptor.updateStatus());
    assertTrue(descriptor.isUpdateable());
  }

  @Test
  void stableMavenComponentIsNotUpdateableButSnapshotIs() {
    var stable = component("org.example:test:1.0.0");
    var snapshot = component("org.example:test:1.1.0-SNAPSHOT");

    assertFalse(stable.isUpdateable());
    assertEquals(Extensions.ComponentUpdateStatus.NOT_UPDATEABLE, stable.updateStatus());
    assertTrue(snapshot.isUpdateable());
    assertEquals(Extensions.ComponentUpdateStatus.UNKNOWN, snapshot.updateStatus());

    var refreshed =
        snapshot.withUpdateStatus(Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE, 1234L);
    assertEquals(Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE, refreshed.updateStatus());
    assertEquals(1234L, refreshed.latestVersionTimestamp());
    assertEquals(snapshot, refreshed);
  }

  private Extensions.ComponentDescriptor component(String mavenCoordinates) {
    return new Extensions.ComponentDescriptor(
        "test.component",
        null,
        "Test component",
        null,
        null,
        mavenCoordinates,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        1000L,
        Extensions.ComponentImportType.MAVEN,
        null,
        0L);
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
