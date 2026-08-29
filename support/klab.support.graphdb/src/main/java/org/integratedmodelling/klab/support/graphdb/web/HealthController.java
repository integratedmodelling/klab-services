package org.integratedmodelling.klab.support.graphdb.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@Tag(
    name = "Graph database",
    description = "Embedded graph database status and connection information")
public class HealthController {

    @Operation(
            summary = "Get graph database health",
            description = "Return liveness status and the current server timestamp")
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
