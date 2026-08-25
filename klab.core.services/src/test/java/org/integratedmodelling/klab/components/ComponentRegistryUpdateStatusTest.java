package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.extension.MavenComponentCache;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

  @Test
  void dependencyRefreshRequiresANewerInstalledSourceComponent() {
    var dependency = descriptor(Extensions.ComponentImportType.DEPENDENCY, 1000L, 1000L);
    var merelyAdvertisedUpdate =
        descriptor(Extensions.ComponentImportType.MAVEN, 1000L, 2000L)
            .withUpdateStatus(Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE, 2000L);
    var installedUpdate = descriptor(Extensions.ComponentImportType.MAVEN, 2000L, 2000L);

    assertFalse(
        ComponentRegistry.isInstalledDependencyUpdateAvailable(
            dependency, merelyAdvertisedUpdate));
    assertTrue(
        ComponentRegistry.isInstalledDependencyUpdateAvailable(dependency, installedUpdate));
  }

  @Test
  void pendingDependencyUpdateIsAppliedWithoutASourceService(@TempDir Path repository)
      throws Exception {
    var pluginDirectory = Files.createDirectory(repository.resolve("plugins"));
    var pendingDirectory = Files.createDirectory(repository.resolve("pending-updates"));
    var archive = pendingDirectory.resolve("update.jar");
    Files.writeString(archive, "updated component");
    var marker = pendingDirectory.resolve("update.properties");
    var properties = new Properties();
    properties.setProperty("archive", archive.getFileName().toString());
    properties.setProperty("component", "test.component");
    properties.setProperty("version", "1.0.0");
    properties.setProperty("sourceService", "resources-service");
    properties.setProperty("sourceTimestamp", "2000");
    properties.setProperty("targetArchive", "test-component.jar");
    try (var output = Files.newOutputStream(marker)) {
      properties.store(output, null);
    }

    var applied =
        ComponentRegistry.applyPendingDependencyUpdates(pluginDirectory.toFile());
    var installed = pluginDirectory.resolve("test-component.jar");
    var update =
        applied.get(installed.toAbsolutePath().normalize().toString());

    assertEquals("updated component", Files.readString(installed));
    assertNotNull(update);
    assertEquals("test.component", update.componentId());
    assertEquals("resources-service", update.sourceServiceId());
    assertEquals(2000L, update.sourceTimestamp());
  }

  @Test
  void checkingAndUpdatingMavenSnapshotsAreSeparateOperations() {
    var cache = mock(MavenComponentCache.class);
    var service = mock(BaseService.class);
    when(service.serviceId()).thenReturn("resources-service");
    var component =
        descriptor(Extensions.ComponentImportType.MAVEN, 1000L, 2000L)
            .withUpdateStatus(Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE, 2000L);
    var availability =
        new MavenComponentCache.Availability(
            MavenComponentCache.Status.NEEDS_UPDATE_FROM_LOCAL_REPOSITORY, 2000L);
    when(cache.getAvailabilityInfo(
            "org.example", "test", "1.0.0-SNAPSHOT", "component", "kar"))
        .thenReturn(availability);
    var registry = new ComponentRegistry(service, null, cache, List.of(component));

    var report = registry.checkForUpdates();

    assertEquals(1, report.getNotifications().size());
    assertTrue(report.getNotifications().getFirst().getMessage().contains("Update available"));
    verify(cache, never())
        .synchronizeArtifact(
            "org.example", "test", "1.0.0-SNAPSHOT", "component", "kar");

    registry.updateMavenSnapshotComponents();

    verify(cache)
        .synchronizeArtifact(
            "org.example", "test", "1.0.0-SNAPSHOT", "component", "kar");

    clearInvocations(cache);
    registry.updateComponent("test.component", null);

    verify(cache)
        .synchronizeArtifact(
            "org.example", "test", "1.0.0-SNAPSHOT", "component", "kar");
  }

  private Extensions.ComponentDescriptor descriptor(
      Extensions.ComponentImportType importType, long timestamp, long latestTimestamp) {
    return new Extensions.ComponentDescriptor(
        "test.component",
        null,
        "Test component",
        null,
        null,
        importType == Extensions.ComponentImportType.MAVEN
            ? "org.example:test:1.0.0-SNAPSHOT"
            : null,
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
