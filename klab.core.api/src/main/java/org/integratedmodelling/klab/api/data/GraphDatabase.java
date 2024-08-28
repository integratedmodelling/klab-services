package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

import java.net.URL;

/**
 * A graph database instrumented for k.LAB operation. Must be able to store and connect knowledge such as
 * observations, actuators and provenance nodes. Implementations must use this interface only, to implement
 * persistent or non-persistent, distributed or local digital twin operation according to configuration.
 */
public interface GraphDatabase {

    /**
     * The database should only be used in a contextualized form, which will establish any possible long-lived
     * connection so that performance is optimal. Implementations should throw an exception when
     * contextualization has not happened.
     * <p>
     * If the database serves multiple contexts, the contextualization operation should also build or load a
     * main context node, to which all root observations will be linked, and the context-specific dataflow and
     * provenance roots..
     *
     * @param scope
     * @return
     */
    GraphDatabase contextualize(ContextScope scope);

    /**
     * If true, the database can create a new database by merging with the URL of another digital twin,
     * enabling federated DTs.
     *
     * @return true if distribution of DTs is enabled
     */
    boolean canDistribute();

    /**
     * Build a federated graph resulting from merging with the URL pointing to a remote digital twin.
     *
     * @param remoteDigitalTwinURL
     * @return the federated database
     */
    GraphDatabase merge(URL remoteDigitalTwinURL);

    /**
     * Checked after initialization.
     *
     * @return true if DB can be used.
     */
    boolean isOnline();

    /**
     * Add a new observation to the graph. The return value <em>must</em> become the ID of the observation.
     *
     * @param observation the new observation, whose {@link Observation#getId()} <em>must</em> return
     *                    {@link Observation#UNASSIGNED_ID} before the call.
     * @param parent      null for top-level observations, or another previously registered observation whose
     *                    {@link Observation#getId()} methods <em>must</em> return a valid ID.
     * @return the ID for the new observation, which must be manually added to the passed peer.
     */
    long add(Observation observation);

    long link(Observation source, Observation destination, DigitalTwin.Relationship linkType, Metadata linkMetadata);

    long add(Actuator actuator, Actuator parent);

    long add(Provenance.Node node, Provenance.Node parent);

    void shutdown();
}
