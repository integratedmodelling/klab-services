package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.services.configuration.RuntimeConfiguration;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigurationLoaderTest {
  @TempDir Path directory;

  @Test
  void validConfigurationRetainsIdentityBackendAndFileBytes() throws Exception {
    var text = "serviceId: fixed-runtime\ngraphDatabaseUrl: bolt://127.0.0.1:17687\n";
    var file = Files.writeString(directory.resolve("runtime.yaml"), text);
    var configuration = RuntimeConfigurationLoader.load(file.toFile());
    assertEquals("fixed-runtime", configuration.getServiceId());
    assertEquals("bolt://127.0.0.1:17687", configuration.getGraphDatabaseUrl());
    assertEquals(text, Files.readString(file));
  }

  @Test
  void unknownFieldFailsWithoutDeletingOrReplacingConfiguration() throws Exception {
    assertPreservedFailure("serviceId: fixed-runtime\ngraphDatabaseUrl: bolt://127.0.0.1:17687\nservicePath: runtime\n");
  }

  @Test
  void malformedYamlFailsWithoutDeletingOrReplacingConfiguration() throws Exception {
    assertPreservedFailure("serviceId: [unterminated\n");
  }

  @Test
  void existingEmptyConfigurationFailsWithoutBeingReplaced() throws Exception {
    assertPreservedFailure("");
  }

  @Test
  void nullConfigurationFailsWithoutGeneratingAnotherIdentity() throws Exception {
    assertPreservedFailure("null\n");
  }

  @Test
  void missingIdentityFailsWithoutGeneratingAnotherIdentity() throws Exception {
    assertPreservedFailure("graphDatabaseUrl: bolt://127.0.0.1:17687\n");
  }

  @Test
  void nullBackendFailsWithoutSelectingTheDefaultBackend() throws Exception {
    assertPreservedFailure("serviceId: fixed-runtime\ngraphDatabaseUrl: null\n");
  }

  @Test
  void omittedBackendFailsWithoutSelectingTheDefaultBackend() throws Exception {
    assertPreservedFailure("serviceId: fixed-runtime\n");
  }

  @Test
  void blankBackendFailsWithoutSelectingTheDefaultBackend() throws Exception {
    assertPreservedFailure("serviceId: fixed-runtime\ngraphDatabaseUrl: ' '\n");
  }

  @Test
  void malformedBackendTypeFailsWithoutSelectingTheDefaultBackend() throws Exception {
    assertPreservedFailure("serviceId: fixed-runtime\ngraphDatabaseUrl: [one, two]\n");
  }

  @Test
  void existingConfigurationMismatchFailsAdmissionWithoutChangingBytes() throws Exception {
    var text = "serviceId: fixed-runtime\ngraphDatabaseUrl: bolt://127.0.0.1:17687\n";
    var file = Files.writeString(directory.resolve("runtime.yaml"), text);
    assertThrows(IllegalStateException.class, () -> RuntimeConfigurationLoader.loadForStartup(
        file.toFile(), false, "bolt://other-host:17687"));
    assertEquals(text, Files.readString(file));
  }

  @Test
  void missingFileCreatesDefaultsOnlyWithoutAnIncompatibleExpectation() throws Exception {
    var file = directory.resolve("runtime.yaml");
    assertThrows(IllegalStateException.class, () -> RuntimeConfigurationLoader.loadForStartup(
        file.toFile(), false, "bolt://127.0.0.1:17687"));
    assertFalse(Files.exists(file));
    var configuration = RuntimeConfigurationLoader.loadForStartup(file.toFile(), false, null);
    assertNotNull(configuration.getServiceId());
    assertEquals(new RuntimeConfiguration().getGraphDatabaseUrl(), configuration.getGraphDatabaseUrl());
    assertEquals(configuration.getServiceId(), RuntimeConfigurationLoader.load(file.toFile()).getServiceId());
  }

  @Test
  void explicitCleanRejectsIncompatibleExpectationBeforeOverwritingExistingFile() throws Exception {
    var text = "serviceId: fixed-runtime\ngraphDatabaseUrl: bolt://127.0.0.1:17687\n";
    var file = Files.writeString(directory.resolve("runtime.yaml"), text);
    assertThrows(IllegalStateException.class, () -> RuntimeConfigurationLoader.loadForStartup(
        file.toFile(), true, "bolt://127.0.0.1:17687"));
    assertEquals(text, Files.readString(file));
  }

  @Test
  void explicitCleanWithoutExpectationDeliberatelyReplacesExistingConfiguration() throws Exception {
    var file = Files.writeString(directory.resolve("runtime.yaml"), "invalid existing config");
    var configuration = RuntimeConfigurationLoader.loadForStartup(file.toFile(), true, null);
    assertNotNull(configuration.getServiceId());
    assertEquals(new RuntimeConfiguration().getGraphDatabaseUrl(), configuration.getGraphDatabaseUrl());
    assertEquals(configuration.getServiceId(), RuntimeConfigurationLoader.load(file.toFile()).getServiceId());
  }

  @Test
  void missingFileAcceptsExpectationMatchingExplicitFirstRunDefaults() {
    var configuration = RuntimeConfigurationLoader.loadForStartup(
        directory.resolve("runtime.yaml").toFile(), false, new RuntimeConfiguration().getGraphDatabaseUrl());
    assertNotNull(configuration.getServiceId());
  }

  @Test
  void actualRuntimeConstructorRejectsMismatchBeforeAnyServiceLifecycleWork() throws Exception {
    var folder = Files.createDirectories(directory.resolve("services/runtime"));
    var text = "serviceId: fixed-runtime\ngraphDatabaseUrl: bolt://127.0.0.1:17687\n";
    var file = Files.writeString(folder.resolve("runtime.yaml"), text);
    var options = mock(ServiceStartupOptions.class);
    when(options.getDataDir()).thenReturn(directory.toFile());
    when(options.getServiceType()).thenReturn(KlabService.Type.RUNTIME);
    var scope = mock(ServiceScope.class);
    var previous = System.getProperty(RuntimeConfigurationLoader.REQUIRED_GRAPH_DATABASE_URL);
    System.setProperty(RuntimeConfigurationLoader.REQUIRED_GRAPH_DATABASE_URL, "bolt://other-host:17687");
    try {
      assertThrows(IllegalStateException.class, () -> new RuntimeService(scope, options));
      verifyNoInteractions(scope);
      // The superclass, component registry, main graph, readiness and maintenance never start.
      verify(options, never()).getServiceHostUrl();
      assertEquals(text, Files.readString(file));
      try (var files = Files.list(folder)) {
        assertEquals(1, files.count());
      }
    } finally {
      if (previous == null) System.clearProperty(RuntimeConfigurationLoader.REQUIRED_GRAPH_DATABASE_URL);
      else System.setProperty(RuntimeConfigurationLoader.REQUIRED_GRAPH_DATABASE_URL, previous);
    }
  }

  @Test
  void matchingBackendIsPassedToConnectionFactory() {
    var configuration = configuration("bolt://127.0.0.1:17687");
    var connected = RuntimeConfigurationLoader.openGraph(configuration,
        "bolt://127.0.0.1:17687", url -> "connected:" + url);
    assertEquals("connected:bolt://127.0.0.1:17687", connected);
  }

  @Test
  void mismatchedBackendNeverInvokesConnectionFactory() {
    var used = new AtomicBoolean();
    assertThrows(IllegalStateException.class, () -> RuntimeConfigurationLoader.openGraph(
        configuration("bolt://0.0.0.0:7687"), "bolt://127.0.0.1:17687", url -> {
          used.set(true);
          return new Object();
        }));
    assertFalse(used.get());
  }

  @Test
  void blankBackendExpectationNeverInvokesConnectionFactory() {
    var used = new AtomicBoolean();
    assertThrows(IllegalStateException.class, () -> RuntimeConfigurationLoader.openGraph(
        configuration("bolt://127.0.0.1:17687"), " ", url -> {
          used.set(true);
          return new Object();
        }));
    assertFalse(used.get());
  }

  @Test
  void noExpectationPreservesOrdinaryExplicitBackendConfiguration() {
    assertEquals("bolt://test-host:17687", RuntimeConfigurationLoader.openGraph(
        configuration("bolt://test-host:17687"), null, url -> url));
  }

  private void assertPreservedFailure(String text) throws Exception {
    var file = Files.writeString(directory.resolve("runtime.yaml"), text);
    var used = new AtomicBoolean();
    assertThrows(IllegalStateException.class, () -> RuntimeConfigurationLoader.openGraph(
        RuntimeConfigurationLoader.load(file.toFile()), null, url -> {
          used.set(true);
          return new Object();
        }));
    assertFalse(used.get());
    assertTrue(Files.exists(file));
    assertEquals(text, Files.readString(file));
    try (var files = Files.list(directory)) {
      assertEquals(1, files.count());
    }
  }

  private RuntimeConfiguration configuration(String url) {
    var configuration = new RuntimeConfiguration();
    configuration.setServiceId("fixed-runtime");
    configuration.setGraphDatabaseUrl(url);
    return configuration;
  }
}
