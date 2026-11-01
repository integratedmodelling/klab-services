package org.integratedmodelling.klab.services.runtime;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.services.configuration.RuntimeConfiguration;

/** Preserve a configured Runtime's identity and backend when startup configuration is invalid. */
final class RuntimeConfigurationLoader {
  static final String REQUIRED_GRAPH_DATABASE_URL = "klab.runtime.requiredGraphDatabaseUrl";

  private RuntimeConfigurationLoader() {}

  static RuntimeConfiguration load(File file) {
    try {
      var bytes = Files.readAllBytes(file.toPath());
      var fields = Utils.YAML.load(new ByteArrayInputStream(bytes), Map.class);
      if (fields == null || !(fields.get("graphDatabaseUrl") instanceof String backend)
          || backend.isBlank()) {
        throw new IllegalArgumentException("An explicit graph database URL is required");
      }
      var configuration = Utils.YAML.load(new ByteArrayInputStream(bytes), RuntimeConfiguration.class);
      if (configuration == null
          || configuration.getServiceId() == null || configuration.getServiceId().isBlank()
          || configuration.getGraphDatabaseUrl() == null || configuration.getGraphDatabaseUrl().isBlank()) {
        throw new IllegalArgumentException("Runtime identity and graph database URL are required");
      }
      return configuration;
    } catch (Exception failure) {
      throw new IllegalStateException(
          "Cannot load existing Runtime configuration; refusing to replace its identity or backend: " + file,
          failure);
    }
  }

  /** Admission happens in the Runtime constructor, before initialization can be scheduled. */
  static RuntimeConfiguration loadForStartup(File file, boolean clean, String requiredUrl) {
    boolean create = !file.exists() || clean;
    var configuration = create ? new RuntimeConfiguration() : load(file);
    requireBackend(configuration, requiredUrl);
    if (create) {
      configuration.setServiceId(UUID.randomUUID().toString());
      Utils.YAML.save(configuration, file);
    }
    return configuration;
  }

  private static void requireBackend(RuntimeConfiguration configuration, String requiredUrl) {
    if (requiredUrl != null
        && (requiredUrl.isBlank() || !requiredUrl.equals(configuration.getGraphDatabaseUrl()))) {
      throw new IllegalStateException("Runtime backend does not match the required endpoint; refusing connection");
    }
  }

  /** Optional exact backend binding, checked before invoking the connection factory. */
  static <T> T openGraph(
      RuntimeConfiguration configuration, String requiredUrl, Function<String, T> connectionFactory) {
    requireBackend(configuration, requiredUrl);
    return connectionFactory.apply(configuration.getGraphDatabaseUrl());
  }
}
