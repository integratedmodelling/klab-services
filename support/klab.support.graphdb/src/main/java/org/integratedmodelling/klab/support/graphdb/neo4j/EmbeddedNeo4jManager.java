package org.integratedmodelling.klab.support.graphdb.neo4j;

import org.integratedmodelling.klab.support.graphdb.config.Neo4jServerProperties;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Component
public class EmbeddedNeo4jManager {

    private final Neo4jServerProperties properties;
    private final Neo4j neo4j;

    public EmbeddedNeo4jManager(Neo4jServerProperties properties) throws IOException {
        this.properties = properties;

        Files.createDirectories(properties.getDataDirectory());
        Files.createDirectories(properties.getPluginsDirectory());

        var builder = Neo4jBuilders.newInProcessBuilder()
                .withConfig(GraphDatabaseSettings.data_directory, properties.getDataDirectory().toAbsolutePath())
                .withConfig(GraphDatabaseSettings.plugin_dir, properties.getPluginsDirectory().toAbsolutePath())
                .withConfig(GraphDatabaseSettings.auth_enabled, false)
                .withConfig(BoltConnector.enabled, true)
                .withConfig(BoltConnector.listen_address, new SocketAddress(properties.getHost(), properties.getBoltPort()))
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
