package org.integratedmodelling.klab.api.digitaltwin;

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
}
