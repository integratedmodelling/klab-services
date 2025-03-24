package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;

import java.io.Closeable;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * A persistent knowledge graph instrumented for k.LAB operation, hosting all the runtime assets
 * managed within a {@link DigitalTwin}. Must be able to store and connect knowledge such as
 * observations, actuators and provenance nodes, all implementing {@link RuntimeAsset}.
 * Implementations must use this interface only, to implement persistent or non-persistent,
 * distributed or local digital twin operation according to configuration.
 *
 * <p>The way this is intended is that a {@link
 * org.integratedmodelling.klab.api.services.RuntimeService} contains a main knowledge graph,
 * initialized at startup, which is {@link #contextualize(ContextScope)}d to obtain the knowledge
 * graph for each observation scope. The API depends on this behavior so that persistent sessions
 * and contexts that have not expired can be retrieved for a given {@link UserScope} by the
 * respective service calls.
 */
public interface KnowledgeGraph {

  /**
   * Simple query interface. Obtain a query, if needed combine it with others, and run it to obtain
   * the contents of the knowledge graph.
   *
   * @param <T>
   */
  interface Query<T extends RuntimeAsset> {

    enum Operator {
      EQUALS,
      LT,
      GT,
      LE,
      GE,
      LIKE,
      INTERSECT,
      COVERS,
      NEAREST,
      BEFORE,
      AFTER
    }

    /**
     * Select the object with the passed ID and return it. Because the result is only zero or one
     * objects, the appropriate call after this is peek() and any other condition is ignored. The KG
     * should be optimized to run this kind of query as fast as possible. The query must be able to
     * retrieve observations that are not yet committed to the knowledge graph but are cached in the
     * scope during resolution.
     *
     * @param id
     * @return the query, ready to run
     */
    Query<T> id(long id);

    Query<T> source(Object startingPoint);

    Query<T> target(Object startingPoint);

    Query<T> along(GraphModel.Relationship relationship, Object... parameters);

    Query<T> depth(int depth);

    Query<T> limit(long n);

    Query<T> offset(long n);

    Query<T> where(String field, Operator operator, Object argument);

    Query<T> order(Object... criteria);

    List<T> run(Scope scope);

    Optional<T> peek(Scope scope);

    Query<T> or(Query<T> query);

    Query<T> and(Query<T> query);
  }

  interface Transaction extends Closeable {
    /**
     * Store the passed asset, return its unique long ID.
     *
     * @param asset
     * @param additionalProperties any pair of properties we want overridden. Pass pairs and do it
     *     right or you'll get an exception.
     * @return
     */
    void store(RuntimeAsset asset, Object... additionalProperties);

    /**
     * Link the two passed assets.
     *
     * <p>*
     *
     * @param source
     * @param destination
     * @param additionalProperties any pair of properties we want overridden. Pass pairs and do it
     *     right or you'll get an exception.
     */
    void link(
        RuntimeAsset source,
        RuntimeAsset destination,
        GraphModel.Relationship relationship,
        Object... additionalProperties);
  }

  /**
   * Create a transaction which will make changes in the knowledge graph when closed.
   *
   * @return a new transaction
   */
  Transaction createTransaction();

  //
  //  /**
  //   * Operations are defined and run to modify the knowledge graph. The operation API guarantees
  // the
  //   * proper updating of provenance in the graph so that any modification is recorded, attributed
  // and
  //   * saves in re-playable history.
  //   *
  //   * <p>At close, the operation commits or rolls back changes (except the activity it creates in
  //   * provenance) according to which finalization mechanism has been called. If none has been
  // called,
  //   * the activity will be stored as an internal failure and everything else rolled back.
  //   */
  //  interface Operation extends Closeable {
  //
  //    /**
  //     * Any operation on the KG is done by someone or something, dutifully recorded in the
  //     * provenance.
  //     *
  //     * @return
  //     */
  //    Agent getAgent();
  //
  //    /**
  //     * This is only used to pass the activity to a child operation.
  //     *
  //     * @return
  //     */
  //    Activity getActivity();
  //
  //    /**
  //     * Create a child operation using the same transaction and representing a new activity,
  // which
  //     * will be linked as a subordinate to the current one. Pass anything that can affect the
  // child
  //     * activity, at minimum an Activity.Type and a description.
  //     *
  //     * @param activityData
  //     * @return
  //     */
  //    Operation createChild(Object... activityData);
  //
  //    /**
  //     * Store the passed asset, return its unique long ID.
  //     *
  //     * @param asset
  //     * @param additionalProperties any pair of properties we want overridden. Pass pairs and do
  // it
  //     *     right or you'll get an exception.
  //     * @return
  //     */
  //    long store(RuntimeAsset asset, Object... additionalProperties);
  //
  //    /**
  //     * Link the two passed assets.
  //     *
  //     * <p>*
  //     *
  //     * @param source
  //     * @param destination
  //     * @param additionalProperties any pair of properties we want overridden. Pass pairs and do
  // it
  //     *     right or you'll get an exception.
  //     */
  //    void link(
  //        RuntimeAsset source,
  //        RuntimeAsset destination,
  //        GraphModel.Relationship relationship,
  //        Object... additionalProperties);
  //
  //    /**
  //     * Link the passed asset to the root node it "naturally" belongs to in the scope.
  //     *
  //     * <p>*
  //     *
  //     * @param destination
  //     * @param additionalProperties any pair of properties we want overridden. Pass pairs and do
  // it
  //     *     right or you'll get an exception.
  //     */
  //    void linkToRootNode(
  //        RuntimeAsset destination,
  //        GraphModel.Relationship relationship,
  //        Object... additionalProperties);
  //
  //    /**
  //     * Call after run() when the activity has finished without errors to ensure that all info in
  // the
  //     * knowledge graph is up to date from a successful run.
  //     *
  //     * @param scope
  //     * @return
  //     */
  //    Operation success(ContextScope scope, Object... assets);
  //
  //    /**
  //     * Call after run() when the activity has finished with errors to ensure that all info in
  // the
  //     * knowledge graph reflect what has gone wrong.
  //     *
  //     * @param scope
  //     * @param assets anything pertinent, assets, exceptions and the like
  //     * @return
  //     */
  //    Operation fail(ContextScope scope, Object... assets);
  //
  //    Scope.Status getOutcome();
  //  }
  //
  //  /**
  //   * Create a new operation with a new activity and a transaction, which can be committed or
  // rolled
  //   * back after using it to define the graph.
  //   *
  //   * @return
  //   */
  //  Operation operation(
  //      Agent agent, Activity parentActivity, Activity.Type activityType, Object... data);

  /**
   * Obtain a query for an object of a specific type, to be specified and then run to obtain the
   * results.
   *
   * @param resultClass
   * @return
   * @param <T>
   */
  <T extends RuntimeAsset> Query<T> query(Class<T> resultClass, Scope scope);

  /**
   * Execute a previously built query. Equivalent to calling run() on the query itself.
   *
   * @param knowledgeGraphQuery
   * @param resultClass
   * @param scope the scope for the query
   * @return
   * @param <T>
   */
  <T extends RuntimeAsset> List<T> query(
      Query<T> knowledgeGraphQuery, Class<T> resultClass, Scope scope);

  /**
   * Remove all data relative to the currently contextualized scope. Graph becomes unusable after
   * this is called, and runtime exceptions will be thrown if the graph is not contextualized or any
   * other method is called.
   */
  void deleteContext();

  /**
   * Returns the user agent asset from the provenance graph, which is created automatically when a
   * KnowledgeGraph is contextualized. If the graph was not contextualized, a {@link
   * org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException} is thrown.
   *
   * @return the user agent
   */
  Agent user();

  /**
   * Returns the k.LAB agent asset from the provenance graph, which is created automatically when a
   * KnowledgeGraph is contextualized. If the graph was not contextualized, a {@link
   * org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException} is thrown.
   *
   * @return the user agent
   */
  Agent klab();

  /**
   * Return a list of context IDs and creation timestamps for each context existing in the graph.
   * This can be called on the main graph or the contextualized graph with the same result.
   *
   * @param scope user to which the contexts belong. May be null, in which case all contexts will be
   *     returned.
   * @return a list of matching IDs and creation timestamps
   */
  List<ContextInfo> getExistingContexts(UserScope scope);

  /**
   * Clear the knowledge graph - if contextualized, clear all the assets linked to the context,
   * otherwise delete everything.
   */
  void clear();

  /**
   * The graph should only be used in a contextualized form, which will establish any possible
   * long-lived connection so that performance is optimal. Implementations should throw an exception
   * when contextualization has not happened.
   *
   * <p>If the database serves multiple contexts, the contextualization operation should also build
   * or load a main context node, to which all root observations will be linked, and the
   * context-specific dataflow and provenance roots..
   *
   * @param scope
   * @return
   */
  KnowledgeGraph contextualize(ContextScope scope);

  /**
   * Extract and return the one asset that has the specified ID from the graph, ensuring it is of
   * the passed class. Expected to be the fastest way to retrieve a node when the ID is known,
   * therefore available besides the more general {@link #query(Class, Scope)}. The scope must be
   * passed to ensure that cached objects that may not yet be committed to the graph can be
   * retrieved.
   *
   * @param id
   * @param resultClass
   * @param <T>
   * @return
   */
  <T extends RuntimeAsset> T get(long id, ContextScope scope, Class<T> resultClass);

  //  /**
  //   * Extract and return all the assets linked to the passed one in the graph.
  //   *
  //   * @param source
  //   * @param linkType
  //   * @param <T>
  //   * @return
  //   * @deprecated use query()
  //   */
  //  <T extends RuntimeAsset> List<T> get(
  //      RuntimeAsset source, DigitalTwin.Relationship linkType, Class<T> resultClass);

  /**
   * Called when an observation has been contextualized
   *
   * @param observation
   * @param scope
   * @param arguments additional parameters to add to the observation or to override existing ones
   * @deprecated remove from API
   */
  void update(RuntimeAsset observation, ContextScope scope, Object... arguments);

  //  /**
  //   * Query starting at the point implied by the scope and return matching objects using the
  // query
  //   * parameters passed.
  //   *
  //   * @param scope
  //   * @param resultClass Can be an individual object (Observation, Actuator or Provenance node)
  // or an
  //   *     entire Dataflow or Provenance
  //   * @param queryParameters
  //   * @param <T>
  //   * @return
  //   * @deprecated use query()
  //   */
  //  <T extends RuntimeAsset> List<T> get(
  //      ContextScope scope, Class<T> resultClass, Object... queryParameters);

  /**
   * Find an agent by name. If the agent is not found, create it with the passed name. If the name
   * is null, return a default agent for the implementation.
   *
   * @param agentName
   * @return
   * @deprecated should be non-API
   */
  Agent requireAgent(String agentName);

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
   * Do anything needed to shut down the graph. Should be called at end of VM on the
   * non-contextualized graph; can also clean up temporary info for a single context scope.
   */
  void shutdown();

  /**
   * Retrieve information relative to all sessions that are currently active in this scope.
   * According to the scope type, different info will be retrieved; if the scope is a {@link
   * ContextScope}, the session to which it belongs will be retrieved but only that scope will be
   * listed in it.
   *
   * @param scope
   * @return
   */
  List<SessionInfo> getSessionInfo(Scope scope);
}
