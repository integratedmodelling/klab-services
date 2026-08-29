package org.integratedmodelling.klab.support.graphdb.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.support.graphdb.neo4j.EmbeddedNeo4jManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(
    name = "Graph database",
    description = "Embedded graph database status and connection information")
public class CapabilitiesController {

    private final EmbeddedNeo4jManager neo4jManager;

    public CapabilitiesController(EmbeddedNeo4jManager neo4jManager) {
        this.neo4jManager = neo4jManager;
    }

    @Operation(
            summary = "Get graph database capabilities",
            description = "Return Neo4j endpoints and local storage locations")
    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of(
                "service", "klab.support.graphdb",
                "neo4j", Map.of(
                        "boltUrl", neo4jManager.boltUri(),
                        "httpUrl", neo4jManager.httpUri(),
                        "pluginsDirectory", neo4jManager.pluginsDirectory(),
                        "dataDirectory", neo4jManager.dataDirectory()
                )
        );
    }
}
