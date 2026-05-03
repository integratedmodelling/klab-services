package org.integratedmodelling.klab.support.graphdb.neo4j;

import org.integratedmodelling.klab.support.graphdb.config.Neo4jServerProperties;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Component
public class EmbeddedNeo4jManager {

  private final Neo4jServerProperties properties;
  private final Neo4j neo4j;

  public EmbeddedNeo4jManager(Neo4jServerProperties properties) throws IOException {
    this.properties = properties;

    /*
     * TODO consult the plugins directory w.r.t. the distribution and copy any newer or absent plugin
     *  to the plugins directory.
     */
    var pluginsDir = Paths.get("").resolve("plugins");
    var logger = LoggerFactory.getLogger(EmbeddedNeo4jManager.class);

    Files.createDirectories(properties.getDataDirectory());
    Files.createDirectories(properties.getPluginsDirectory());

    if (pluginsDir.toFile().exists()) {
      logger.info("Copying plugins from {}", pluginsDir);
      for (var file : pluginsDir.toFile().listFiles()) {
        // TODO improve - check if file is a plugin, copy recursively if needed, check hash if
        //  existing
        Files.copy(
            file.toPath(),
            properties.getPluginsDirectory().resolve(file.getName()),
            StandardCopyOption.REPLACE_EXISTING);
      }
    }

    var builder =
        Neo4jBuilders.newInProcessBuilder()
            .withConfig(
                GraphDatabaseSettings.data_directory,
                properties.getDataDirectory().toAbsolutePath())
            .withConfig(
                GraphDatabaseSettings.plugin_dir, properties.getPluginsDirectory().toAbsolutePath())
            .withConfig(GraphDatabaseSettings.auth_enabled, false)
            .withConfig(BoltConnector.enabled, true)
            // add spatial procedures access
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("spatial.*"))
            .withConfig(
                BoltConnector.listen_address,
                new SocketAddress(properties.getHost(), properties.getBoltPort()))
            .withConfig(HttpConnector.enabled, false);

    this.neo4j = builder.build();
  }

  public String boltUri() {
    return neo4j.boltURI().toString();
  }

  public String httpUri() {
    return "HTTP connector disabled";
  }

  public String pluginsDirectory() {
    return properties.getPluginsDirectory().toAbsolutePath().toString();
  }

  public String dataDirectory() {
    return properties.getDataDirectory().toAbsolutePath().toString();
  }

  public void shutdown() {
    neo4j.close();
  }
}
