package org.integratedmodelling.klab.support.graphdb.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.neo4j")
public class Neo4jServerProperties {

    @NotBlank
    private String host = "127.0.0.1";

    private int boltPort = 7687;

    private int httpPort = 7474;

    @NotNull
    private Path dataDirectory = Path.of("./var/neo4j/data");

    @NotNull
    private Path pluginsDirectory = Path.of("./var/neo4j/plugins");

    private Map<String, String> config = new LinkedHashMap<>();

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
