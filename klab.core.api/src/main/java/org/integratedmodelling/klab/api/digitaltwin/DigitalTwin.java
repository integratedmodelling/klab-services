package org.integratedmodelling.klab.api.digitaltwin;

public interface DigitalTwin {


    StateStorage stateStorage();

    ObservationGraph observationGraph();

    DataflowGraph dataflowGraph();

    ProvenanceGraph provenanceGraph();


}
