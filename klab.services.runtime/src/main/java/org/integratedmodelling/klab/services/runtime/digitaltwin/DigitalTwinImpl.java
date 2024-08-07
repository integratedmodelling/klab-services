package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.runtime.storage.StateStorageImpl;

public class DigitalTwinImpl implements DigitalTwin {

    GraphDatabase graphDatabase;
    StateStorage stateStorage;
    ObservationGraph observationGraph;
    DataflowGraph dataflowGraph;
    ProvenanceGraph provenanceGraph;

    public DigitalTwinImpl(ContextScope scope, GraphDatabase database) {
        this.graphDatabase = database.contextualize(scope);
        this.observationGraph = new ObservationGraphImpl(database, scope);
        this.dataflowGraph = new DataflowGraphImpl(database, scope);
        this.provenanceGraph = new ProvenanceGraphImpl(database, scope);
        this.stateStorage = new StateStorageImpl(scope);
    }

    @Override
    public StateStorage stateStorage() {
        return this.stateStorage;
    }

    @Override
    public ObservationGraph observationGraph() {
        return this.observationGraph;
    }

    @Override
    public DataflowGraph dataflowGraph() {
        return this.dataflowGraph;
    }

    @Override
    public ProvenanceGraph provenanceGraph() {
        return this.provenanceGraph;
    }

    @Override
    public void dispose() {
        // TODO. Persistence depends on the database passed at initialization.
    }

    @Override
    public void startResolution(Observation observation) {

    }
}
