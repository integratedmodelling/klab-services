package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.kactors.messages.Observe;

public interface DigitalTwin {


    StateStorage stateStorage();

    ObservationGraph observationGraph();

    DataflowGraph dataflowGraph();

    ProvenanceGraph provenanceGraph();

    /**
     * Dispose of all storage and data, either in memory only or also on any attached storage.
     *
     * @param removePersistentData if true, clear all databases and storage
     */
    void dispose(boolean removePersistentData);

    /**
     * Ingest and resolve an observation created externally. Ensure that the {@link Observation#getId()}
     * returns a proper key value before this is called.
     *
     * @param observation
     */
    void startResolution(Observation observation);
}
