package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.digitaltwin.ObservationGraph;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

public class ObservationGraphImpl implements ObservationGraph {

    private final GraphDatabase database;

    public ObservationGraphImpl(GraphDatabase database, ContextScope scope) {
        this.database = database.contextualize(scope);
    }
}
