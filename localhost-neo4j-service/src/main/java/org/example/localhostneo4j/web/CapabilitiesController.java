package org.example.localhostneo4j.web;

import org.example.localhostneo4j.neo4j.EmbeddedNeo4jManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CapabilitiesController {

    private final EmbeddedNeo4jManager neo4jManager;

    public CapabilitiesController(EmbeddedNeo4jManager neo4jManager) {
        this.neo4jManager = neo4jManager;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of(
                "service", "localhost-neo4j-service",
                "neo4j", Map.of(
                        "boltUrl", neo4jManager.boltUri(),
                        "httpUrl", neo4jManager.httpUri(),
                        "pluginsDirectory", neo4jManager.pluginsDirectory(),
                        "dataDirectory", neo4jManager.dataDirectory()
                )
        );
    }
}
