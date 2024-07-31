package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.scope.ContextScope;

public class DigitalTwinImpl implements DigitalTwin {

    GraphDatabase graphDatabase;

    public DigitalTwinImpl(ContextScope scope, GraphDatabase database) {
        this.graphDatabase = database;
    }

    @Override
    public GraphStorage graphStorage() {
        return null;
    }

    @Override
    public StateStorage stateStorage() {
        return null;
    }

    @Override
    public ObservationGraph observationGraph() {
        return null;
    }

    @Override
    public DataflowGraph dataflowGraph() {
        return null;
    }

    @Override
    public ProvenanceGraph provenanceGraph() {
        return null;
    }
}
