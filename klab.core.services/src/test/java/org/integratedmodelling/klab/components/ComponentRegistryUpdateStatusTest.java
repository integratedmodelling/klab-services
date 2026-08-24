package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class ComponentRegistryUpdateStatusTest {

  @Test
  void dependencyUsesTheHostingServiceTimestampAndStatus() {
    var dependency = descriptor(Extensions.ComponentImportType.DEPENDENCY, 1000L, 1000L);
    var updatedSource = descriptor(Extensions.ComponentImportType.FILE, 2000L, 2000L);

    var update = ComponentRegistry.applyDependencySourceStatus(dependency, updatedSource);

    assertEquals(Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE, update.updateStatus());
    assertEquals(2000L, update.latestVersionTimestamp());
  }

  @Test
  void dependencyPropagatesUnknownAndNonUpdateableSourceStates() {
    var dependency = descriptor(Extensions.ComponentImportType.DEPENDENCY, 1000L, 1000L);
    var unknownSource =
        descriptor(Extensions.ComponentImportType.MAVEN, 1000L, 0L)
            .withUpdateStatus(Extensions.ComponentUpdateStatus.UNKNOWN, 0L);
    var stableSource =
        descriptor(Extensions.ComponentImportType.MAVEN, 1000L, 0L)
            .withUpdateStatus(Extensions.ComponentUpdateStatus.NOT_UPDATEABLE, 0L);

    assertEquals(
        Extensions.ComponentUpdateStatus.UNKNOWN,
        ComponentRegistry.applyDependencySourceStatus(dependency, unknownSource).updateStatus());
    assertEquals(
        Extensions.ComponentUpdateStatus.NOT_UPDATEABLE,
        ComponentRegistry.applyDependencySourceStatus(dependency, stableSource).updateStatus());
  }

  private Extensions.ComponentDescriptor descriptor(
      Extensions.ComponentImportType importType, long timestamp, long latestTimestamp) {
    return new Extensions.ComponentDescriptor(
        "test.component",
        null,
        "Test component",
        null,
        null,
        importType == Extensions.ComponentImportType.MAVEN ? "org.example:test:1.0.0" : null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "resources-service",
        timestamp,
        importType,
        importType == Extensions.ComponentImportType.MAVEN
            ? Extensions.ComponentUpdateStatus.NOT_UPDATEABLE
            : Extensions.ComponentUpdateStatus.UP_TO_DATE,
        latestTimestamp);
  }
}
