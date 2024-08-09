package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public interface DataflowGraph {

    /**
     * Internalize the dataflow and all the conditions of resolution
     *
     * @param dataflow
     */
    void submit(Dataflow<Observation> dataflow);
}
