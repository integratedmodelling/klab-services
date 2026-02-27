package org.integratedmodelling.klab.support.graphdb.neo4j;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class Neo4jLifecycle {

    private final EmbeddedNeo4jManager neo4jManager;

    public Neo4jLifecycle(EmbeddedNeo4jManager neo4jManager) {
        this.neo4jManager = neo4jManager;
    }

    @PreDestroy
    public void close() {
        neo4jManager.shutdown();
    }
}
