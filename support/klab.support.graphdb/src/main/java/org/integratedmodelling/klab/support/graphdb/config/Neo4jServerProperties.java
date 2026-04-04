package org.integratedmodelling.klab.support.graphdb.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.neo4j")
public class Neo4jServerProperties {

  private static final Logger log = LoggerFactory.getLogger(Neo4jServerProperties.class);

  @NotBlank private String host = "127.0.0.1";

  private int boltPort = 7687;

  private int httpPort = 7474;

  @NotNull
  private Path dataDirectory =
      Path.of(System.getProperty("user.home") + "/.klab/services/graphdb/data");

  @NotNull
  private Path pluginsDirectory =
      Path.of(System.getProperty("user.home") + "/.klab/services/graphdb/plugins");

  private Map<String, String> config = new LinkedHashMap<>();

  public Neo4jServerProperties() {

    // Read the ~/.klab/engine.properties file if it exists and look for
    // `services.database_directory`; if found, override the defaultDataDirectory and the other
    // fields that depend on it. The setting is managed in the runtime.
    File engineProperties = new File(System.getProperty("user.home") + "/.klab/engine.properties");
    if (engineProperties.exists()) {
      try (var input = new FileInputStream(engineProperties)) {
        var properties = new java.util.Properties();
        properties.load(input);
        if (properties.containsKey("services.database_directory")) {
          Path defaultVarDirectory = Path.of(properties.getProperty("services.database_directory"));
          dataDirectory = Path.of(defaultVarDirectory + "/data");
          pluginsDirectory = Path.of(dataDirectory + "/plugins");
        }
      } catch (Exception e) {
        log.error("Error reading engine.properties", e);
      }
    }
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getBoltPort() {
    return boltPort;
  }

  public void setBoltPort(int boltPort) {
    this.boltPort = boltPort;
  }

  public int getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(int httpPort) {
    this.httpPort = httpPort;
  }

  public Path getDataDirectory() {
    return dataDirectory;
  }

  public void setDataDirectory(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
  }

  public Path getPluginsDirectory() {
    return pluginsDirectory;
  }

  public void setPluginsDirectory(Path pluginsDirectory) {
    this.pluginsDirectory = pluginsDirectory;
  }

  public Map<String, String> getConfig() {
    return config;
  }

  public void setConfig(Map<String, String> config) {
    this.config = config;
  }
}
