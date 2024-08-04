package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.digitaltwin.ProvenanceGraph;
import org.integratedmodelling.klab.api.scope.ContextScope;

public class ProvenanceGraphImpl implements ProvenanceGraph {

    private final GraphDatabase database;

    public ProvenanceGraphImpl(GraphDatabase database, ContextScope contextScope) {
        this.database = database;
    }
}
