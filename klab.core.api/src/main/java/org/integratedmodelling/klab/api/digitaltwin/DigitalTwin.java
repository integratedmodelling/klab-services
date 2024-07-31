package org.integratedmodelling.klab.api.digitaltwin;

public interface DigitalTwin {

    GraphStorage graphStorage();

    StateStorage stateStorage();

    ObservationGraph observationGraph();

    DataflowGraph dataflowGraph();

    ProvenanceGraph provenanceGraph();


}
