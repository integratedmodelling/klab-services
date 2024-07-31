package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

/**
 * The graph storage is contextualized to a {@link DigitalTwin} and is unique in it. The
 * {@link ObservationGraph}, {@link DataflowGraph} and {@link ProvenanceGraph} in the digital twin are views
 * on the graph.
 */
public interface GraphStorage {

    /**
     * @param observation any observation, including relationships
     * @param parent      may be null
     * @return
     */
    long add(Observation observation, Observation parent);

    long add(Actuator actuator, Actuator parent);

    long add(Provenance.Node node, Provenance.Node parent);

}
