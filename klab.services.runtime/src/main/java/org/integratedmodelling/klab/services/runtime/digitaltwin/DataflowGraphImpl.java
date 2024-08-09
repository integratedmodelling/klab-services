package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.digitaltwin.DataflowGraph;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowGraphImpl implements DataflowGraph {

    private final GraphDatabase database;

    public DataflowGraphImpl(GraphDatabase database, ContextScope contextScope) {
        this.database = database;
    }

    @Override
    public void submit(Dataflow<Observation> dataflow) {
        // TODO internalize the dataflow
    }
}
