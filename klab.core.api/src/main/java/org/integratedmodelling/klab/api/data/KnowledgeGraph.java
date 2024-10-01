package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;

import java.net.URL;
import java.util.List;

/**
 * A persistent knowledge graph instrumented for k.LAB operation, hosting all the runtime assets managed
 * within a {@link DigitalTwin}. Must be able to store and connect knowledge such as observations, actuators
 * and provenance nodes, all implementing {@link RuntimeAsset}. Implementations must use this interface only,
 * to implement persistent or non-persistent, distributed or local digital twin operation according to
 * configuration.
 * <p>
 * The way this is intended is that a {@link org.integratedmodelling.klab.api.services.RuntimeService}
 * contains a main knowledge graph, initialized at startup, which is {@link #contextualize(ContextScope)}d to
 * obtain the knowledge graph for each observation scope. The API depends on this behavior so that persistent
 * sessions and contexts that have not expired can be retrieved for a given {@link UserScope} by the
 * respective service calls.
 */
public interface KnowledgeGraph {

    /**
     * Operations are defined and run to modify the knowledge graph. The operation API guarantees the proper
     * updating of provenance in the graph so that any modification is recorded, attributed and saves in
     * replayable history.
     */
    interface Operation {

        /**
         * Run the operation as configured and return the ID of the last object created or modified, or
         * {@link Observation#UNASSIGNED_ID} if the operation failed or was wrongly defined.
         *
         * @return a valid ID or {@link Observation#UNASSIGNED_ID}
         */
        long run(ContextScope scope);

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
         * Sets an existing asset as the target for future links or updates called on the operation. If
         * properties are passed that differ from the existing ones, an update is performed at
         * {@link #run(ContextScope)} and the appropriate provenance records are inserted in the graph.
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
         * @param assetFrom
         * @param assetTo
         * @param linkData
         * @return
         */
        Operation link(RuntimeAsset assetFrom, RuntimeAsset assetTo, DigitalTwin.Relationship relationship,
                       Object... linkData);

        /**
         * Link the passed asset directly to the root object of reference - provenance, context or dataflow.
         *
         * @param asset
         * @param linkData
         * @return
         */
        Operation rootLink(RuntimeAsset asset, Object... linkData);

        /**
         * Call after run() when the activity has finished without errors to ensure that all info in the
         * knowledge graph is up to date from a successful run.
         *
         * @param scope
         * @return
         */
        Operation success(ContextScope scope, RuntimeAsset... assets);

        /**
         * Call after run() when the activity has finished with errors to ensure that all info in the
         * knowledge graph reflect what has gone wrong.
         *
         * @param scope
         * @param assets anything pertinent, assets, exceptions and the like
         * @return
         */
        Operation fail(ContextScope scope, Object... assets);

    }

    /**
     * Create a new operation to be run on the graph, resulting in assets being created or modified, with
     * recording of provenance information. As a result, even an atomic operation may add several assets and
     * relationships. Unless the operation is empty, exactly one Activity node is always created at run().
     * <p>
     * If no parameters are passed, the first target must be set manually on the returned operation. Otherwise
     * the target will be set according to which parameters are passed and their existence in the graph. No
     * change is made to the graph until {@link Operation#run(ContextScope)} is called.
     * <p>
     * Obtaining and running an operation (i.e. creating a new Activity in the knowledge graph) will also
     * reset the idle time counter to 0 for those scopes whose expiration is IDLE_TIME. Operations may be
     * invoked on the scope by the API user, but also triggered by applications, behaviors etc.
     * <p>
     * When the operation has run, call success() or fail() to update the knowledge graph with any IDs and
     * states.
     *
     * @param agent   the agent that will own the activity created
     * @param scope   the specific scope, whose observer and context will determine the links made
     * @param targets any additional parameters. A string will be interpreted as the description of the
     *                activity generated. An activity will be interpreted as the parent for the activity
     *                generated.
     * @return a graph modification operation description ready for run()
     */
    Operation activity(Agent agent, ContextScope scope, Object... targets);

    /**
     * Remove all data relative to the currently contextualized scope. Graph becomes unusable after this is
     * called, and runtime exceptions will be thrown if the graph is not contextualized or any other method is
     * called.
     */
    void deleteContext();

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
     * Return a list of context IDs and creation timestamps for each context existing in the graph. This can
     * be called on the main graph or the contextualized graph with the same result.
     *
     * @param scope user to which the contexts belong. May be null, in which case all contexts will be
     *              returned.
     * @return a list of matching IDs and creation timestamps
     */
    List<ContextInfo> getExistingContexts(UserScope scope);

    /**
     * Clear the knowledge graph - if contextualized, clear all the assets linked to the context, otherwise
     * delete everything.
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
    <T extends RuntimeAsset> List<T> get(RuntimeAsset source, DigitalTwin.Relationship linkType,
                                         Class<T> resultClass);

    /**
     * Query starting at the point implied by the scope and return matching objects using the query parameters
     * passed.
     *
     * @param scope
     * @param resultClass     Can be an individual object (Observation, Actuator or Provenance node) or an
     *                        entire Dataflow or Provenance
     * @param queryParameters
     * @param <T>
     * @return
     */
    <T extends RuntimeAsset> List<T> get(ContextScope scope, Class<T> resultClass, Object... queryParameters);

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

    /**
     * Do anything needed to shut down the graph. Should be called at end of VM on the non-contextualized
     * graph; can also clean up temporary info for a single context scope.
     */
    void shutdown();
}
