package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.data.GraphDatabase;

import java.net.URL;

public class GraphDatabaseNeo4jClient implements GraphDatabase {
    @Override
    public boolean canDistribute() {
        return false;
    }

    @Override
    public GraphDatabase merge(URL remoteDigitalTwinURL) {
        return null;
    }
}
