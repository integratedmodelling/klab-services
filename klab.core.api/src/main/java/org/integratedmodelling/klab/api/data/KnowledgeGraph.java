package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

import java.net.URL;
import java.util.List;

/**
 * A persistent knowledge graph instrumented for k.LAB operation, hosting all the runtime assets managed
 * within a {@link DigitalTwin}. Must be able to store and connect knowledge such as observations, actuators
 * and provenance nodes, all implementing {@link RuntimeAsset}. Implementations must use this interface only,
 * to implement persistent or non-persistent, distributed or local digital twin operation according to
 * configuration.
 */
public interface KnowledgeGraph {

    interface Operation {

        /**
         * Run the operation as configured and return the ID of the last object created or modified, or
         * {@link Observation#UNASSIGNED_ID} if the operation failed or was wrongly defined.
         *
         * @return a valid ID or {@link Observation#UNASSIGNED_ID}
         */
        long run();

        /**
         * Add the passed runtime asset, which must not be part of the graph already.
         * <p>
         * Sets the current link target to the created object.
         *
         * @param observation
         * @return
         */
        Operation add(RuntimeAsset observation);

        /**
         * Create a new asset based on the passed parameters, which may include
         * {@link org.integratedmodelling.klab.api.knowledge.Observable},
         * {@link org.integratedmodelling.klab.api.geometry.Geometry} or {@link Metadata} for observations,
         * {@link Observation} itself (in which case it will behave like {@link #add(RuntimeAsset)}) or
         * predefined {@link RuntimeAsset}s such as provenance nodes or actuators.
         * <p>
         * Sets the current link target to the created object.
         *
         * @param parameters
         * @return
         */
        Operation create(Object... parameters);

        /**
         * Sets an existing asset as the target for future links or updates called on the operation. If
         * properties are passed that differ from the existing ones, an update is performed at {@link #run()}
         * and the appropriate provenance records are inserted in the graph.
         *
         * @param source
         * @return
         */
        Operation set(RuntimeAsset source, Object... properties);

        /**
         * Link the last asset referenced in the call chain to the passed asset, which must exist. Use the
         * additional parameters to specify the link, which should include a link type unless that can be
         * inferred unambiguously, and can include PODs for properties as needed. Creates an outgoing
         * connection from the current asset to the passed one.
         * <p>
         * When returning, the target is still set as before the link call, so that various link calls can be
         * chained.
         *
         * @param asset
         * @param linkData
         * @return
         */
        Operation link(RuntimeAsset assetFrom, RuntimeAsset assetTo, Object... linkData);

        /**
         * Link the passed asset directly to the root object of reference - provenance, context or dataflow.
         *
         * @param asset
         * @param linkData
         * @return
         */
        Operation rootLink(RuntimeAsset asset, Object... linkData);

    }


    /**
     * Create an operation to be run on the graph. If no parameters are passed, the first target must be set
     * manually on the returned operation. Otherwise the target will be set according to which parameters are
     * passed and their existence in the graph. No change is made to the graph until {@link Operation#run()}
     * is called.
     * <p>
     *
     * @param agent  the agent that will own the activity created
     * @param scope  the specific scope, whose observer and context will determine the links made
     * @param target any additional parameters. A string will be interpreted as the description of the
     *               activity generated.
     * @return an operation
     */
    Operation op(Agent agent, ContextScope scope, Object... target);


    /**
     * Returns the user agent asset from the provenance graph, which is created automatically when a
     * KnowledgeGraph is contextualized. If the graph was not contextualized, a
     * {@link org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException} is thrown.
     *
     * @return the user agent
     */
    Agent user();

    /**
     * Returns the k.LAB agent asset from the provenance graph, which is created automatically when a
     * KnowledgeGraph is contextualized. If the graph was not contextualized, a
     * {@link org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException} is thrown.
     *
     * @return the user agent
     */
    Agent klab();

    /**
     * Clear the knowledge graph - if contextualized, clear all the assets linked to the context, otherwise
     * everything.
     */
    void clear();

    /**
     * The graph should only be used in a contextualized form, which will establish any possible long-lived
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
    KnowledgeGraph contextualize(ContextScope scope);

    /**
     * Extract and return the asset with the specified ID from the graph, ensuring it is of the passed class.
     *
     * @param id
     * @param resultClass
     * @param <T>
     * @return
     */
    <T extends RuntimeAsset> T get(long id, Class<T> resultClass);

    /**
     * Extract and return all the assets linked to the passed one in the graph.
     *
     * @param source
     * @param linkType
     * @param <T>
     * @return
     */
    <T extends RuntimeAsset> List<T> get(RuntimeAsset source, DigitalTwin.Relationship linkType, Class<T> resultClass);

    //    /**
    //     * If true, the database can create a new database by merging with the URL of another digital twin,
    //     * enabling federated DTs.
    //     *
    //     * @return true if distribution of DTs is enabled
    //     */
    //    boolean canDistribute();

    /**
     * Build a federated graph resulting from merging with the URL pointing to a remote digital twin.
     *
     * @param remoteDigitalTwinURL
     * @return the federated database
     */
    KnowledgeGraph merge(URL remoteDigitalTwinURL);

    /**
     * Checked after initialization.
     *
     * @return true if DB can be used.
     */
    boolean isOnline();

    //    /**
    //     * Add a new observation to the graph, optionally linking to context (if other params are null) or
    //     * contextual observation. The return value <em>must</em> become the ID of the observation.
    //     *
    //     * @param observation          the new observation, whose {@link Observation#getId()} <em>must</em> return
    //     *                             {@link Observation#UNASSIGNED_ID} before the call.
    //     * @param relationshipSource   can be null
    //     * @param connection           cn be null
    //     * @param relationshipMetadata can be null
    //     * @return the ID for the new observation, which must be manually added to the passed peer.
    //     */
    //    long add(Observation observation, Object relationshipSource, DigitalTwin.Relationship connection,
    //             Metadata relationshipMetadata);
    //
    //    long link(Observation source, Observation destination, DigitalTwin.Relationship linkType,
    //              Metadata linkMetadata);
    //
    //    long add(Actuator actuator, Actuator parent);
    //
    //    long add(Provenance.Node node, Provenance.Node parent);

    void shutdown();
}
