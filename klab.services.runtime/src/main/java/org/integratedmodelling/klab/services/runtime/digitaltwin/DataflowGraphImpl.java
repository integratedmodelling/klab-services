package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.digitaltwin.DataflowGraph;
import org.integratedmodelling.klab.api.scope.ContextScope;

public class DataflowGraphImpl implements DataflowGraph {

    private final GraphDatabase database;

    public DataflowGraphImpl(GraphDatabase database, ContextScope contextScope) {
        this.database = database;
    }
}
