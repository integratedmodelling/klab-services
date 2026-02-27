package org.example.localhostneo4j.neo4j;

import org.example.localhostneo4j.config.Neo4jServerProperties;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@Component
public class EmbeddedNeo4jManager {

    private final Neo4jServerProperties properties;
    private final Neo4j neo4j;

    public EmbeddedNeo4jManager(Neo4jServerProperties properties) throws IOException {
        this.properties = properties;

        Files.createDirectories(properties.getDataDirectory());
        Files.createDirectories(properties.getPluginsDirectory());

        var builder = Neo4jBuilders.newInProcessBuilder()
                .withConfig("server.directories.data", properties.getDataDirectory().toAbsolutePath().toString())
                .withConfig("server.directories.plugins", properties.getPluginsDirectory().toAbsolutePath().toString())
                .withConfig("dbms.security.auth_enabled", "false")
                .withConfig("server.default_listen_address", properties.getHost())
                .withConfig("server.default_advertised_address", properties.getHost())
                .withConfig("server.bolt.listen_address", properties.getHost() + ":" + properties.getBoltPort())
                .withConfig("server.http.listen_address", properties.getHost() + ":" + properties.getHttpPort());

        for (Map.Entry<String, String> entry : properties.getConfig().entrySet()) {
            builder = builder.withConfig(entry.getKey(), entry.getValue());
        }

        this.neo4j = builder.build();
    }

    public String boltUri() {
        return neo4j.boltURI().toString();
    }

    public String httpUri() {
        return neo4j.httpURI().toString();
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
