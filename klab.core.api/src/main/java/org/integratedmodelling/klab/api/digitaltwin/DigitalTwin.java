package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * The digital twin is a graph model composed of observations and all their history. Each
 * {@link org.integratedmodelling.klab.api.scope.ContextScope} points to a digital twin and contains the
 * methods to access it. Digital twins can be built from pairing others in a federated fashion.
 */
public interface DigitalTwin {

    enum Relationship {
        Parent,
        Affects,
        Connects,
    }

    /**
     * Return the storage for all "datacube" content.
     *
     * @return
     */
    StateStorage stateStorage();

    /**
     * Return a view of the graph that only addresses observations.
     *
     * @return
     */
    ObservationGraph observationGraph();

    /**
     * Return a view of the graph that only addresses the dataflow.
     *
     * @return
     */
    DataflowGraph dataflowGraph();

    /**
     * Return the view of the graph that represents provenance.
     *
     * @return
     */
    ProvenanceGraph provenanceGraph();

    /**
     * Dispose of all storage and data, either in memory only or also on any attached storage. Whether the
     * disposal is permanent depends on the graph database used and its configuration.
     */
    void dispose();

    /**
     * Ingest and resolve an observation created externally. Ensure that the {@link Observation#getId()}
     * returns a proper key value before this is called.
     * <p>
     * Submitting a resolved observation that does not belong or unresolved related will throw a
     * {@link org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException}.
     *
     * @param observation  cannot be null, must be unresolved if the relationship is parent or null
     * @param related      may be null but if not, must be already submitted to the DT
     * @param relationship the relationship of the new observation to the second, must be non-null if related
     *                     isn't
     */
    void submit(Observation observation, Observation related, Relationship relationship);

    /**
     * Assemble the passed parameters into an unresolved Observation, to be passed to
     * {@link #submit(Observation, Observation, Relationship)} for resolution and insertion in the graph.
     *
     * @param array
     * @return
     */
    static Observation createObservation(Object... array) {
        return null;
    }


}
