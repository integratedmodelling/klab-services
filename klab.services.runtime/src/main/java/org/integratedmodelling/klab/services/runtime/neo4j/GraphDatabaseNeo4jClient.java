package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

import java.net.URL;

public class GraphDatabaseNeo4jClient implements GraphDatabase {
    @Override
    public GraphDatabase contextualize(ContextScope scope) {
        return null;
    }

    @Override
    public boolean canDistribute() {
        return false;
    }

    @Override
    public GraphDatabase merge(URL remoteDigitalTwinURL) {
        return null;
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public long add(Observation observation) {
        return 0;
    }

    @Override
    public long link(Observation source, Observation destination, DigitalTwin.Relationship linkType, Metadata linkMetadata) {
        return 0;
    }


    @Override
    public long add(Actuator actuator, Actuator parent) {
        return Observation.UNASSIGNED_ID;
    }

    @Override
    public long add(Provenance.Node node, Provenance.Node parent) {
        return Observation.UNASSIGNED_ID;
    }

    @Override
    public void shutdown() {

    }


}
