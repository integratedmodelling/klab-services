package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;

import java.io.Closeable;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A persistent knowledge graph instrumented for k.LAB operation, hosting all the runtime assets
 * managed within a {@link DigitalTwin}. Must be able to store and connect knowledge such as
 * observations, actuators and provenance nodes, all implementing {@link RuntimeAsset}.
 * Implementations must use this interface only, to implement persistent or non-persistent,
 * distributed or local digital twin operation according to configuration.
 *
 * <p>The way this is intended is that a {@link
 * org.integratedmodelling.klab.api.services.RuntimeService} contains a main knowledge graph,
 * initialized at startup, which is contextualized on the {@link ContextScope} to obtain the
 * knowledge graph for that scope. The API depends on this behavior so that persistent sessions and
 * contexts that have not expired can be retrieved for a given {@link UserScope} by the respective
 * service calls.
 */
public interface KnowledgeGraph {

  /**
   * Each transaction produces a commit that details the changes made in the transaction. These are
   * kept in a temporary cache and can be retrieved for a short time after the transaction has
   * produced a committed observation. The commit is intended for clients that want to keep the
   * knowledge graph updated without transferring too much information, and should be queried
   * immediately after the new observation comes out of {@link ContextScope#submit(Observation)}.
   * The client side of the knowledge graph can do this in interactive applications.
   *
   * <p>The commit ID is available as part of the metadata of the committed observation and it can
   * be used to obtain the commit through the digital twin API.
   */
  interface Commit {

    String id();

    /**
     * Server-side timestamp can be used to provide sequencing.
     *
     * @return
     */
    long timestamp();

    /**
     * IDs of all the assets that were created in the transaction.
     *
     * @return
     */
    Set<Long> newAssets();

    /**
     * IDs of all the links that were created in the transaction. Each link is a triple with the
     * source asset ID, the target asset ID and the relationship type. The IDs may refer to assets
     * previously seen in the graph and not present in #newAssets().
     *
     * @return
     */
    Set<Triple<Long, Long, GraphModel.Relationship>> newLinks();
  }

  /**
   * A runtime asset representing a relationship. Used when submitting queries whose return value is
   * the link, to inspect the relationships.
   */
  interface Link extends RuntimeAsset {

    GraphModel.Relationship type();

    Parameters<String> properties();

    RuntimeAsset source();

    RuntimeAsset target();

    int sequence();

    Geometry geometry();
  }

  /**
   * Simple query interface. Get a new query; if necessary combine it with other queries, and run it
   * to get the contents of the knowledge graph.
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

    /**
     * Find the (assumed unique) relationship between <code>source</code> and <code>target</code> of
     * the passed type, and adapt the result to the query target class, which should normally be a
     * {@link java.util.Map} where the relationship properties are recorded.
     *
     * @param source
     * @param target
     * @return
     */
    Query<T> between(Object source, Object target, GraphModel.Relationship relationship);

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

    void update(RuntimeAsset asset, Object... properties);

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

    /**
     * Call this to indicate that the transaction has failed and make the DB roll back at close().
     *
     * @param e
     */
    void fail(Exception e);
  }

  /**
   * Create a transaction which will make changes in the knowledge graph when closed.
   *
   * @return a new transaction
   */
  Transaction createTransaction();

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
   * The graph node that represents the scope we run under. If the KG is not contextualized to a
   * scope, this will throw an exception.
   *
   * @return
   */
  RuntimeAsset scope();

  /**
   * The graph node that represents the root provenance node within the scope we run under. If the
   * KG is not contextualized to a scope, this will throw an exception.
   *
   * @return
   */
  RuntimeAsset provenance();

  /**
   * The graph node that represents the root dataflow node within the scope we run under. If the KG
   * is not contextualized to a scope, this will throw an exception.
   *
   * @return
   */
  RuntimeAsset dataflow();

  /**
   * Extract a subgraph at a specified depth and level of detail for visualization and reporting.
   * The graph must be contextualized.
   *
   * @param focalNodeId the ID of the center node
   * @param depth the depth of the subgraph, 0 for the center node only
   * @param requiredNodes optional list of IDs of nodes that must be present in the subgraph. Can be
   *     empty or null.
   * @param acceptedTypes the type of assets that can be present in the subgraph.
   * @param acceptedRelationships the relationships that can be present in the subgraph. Can be
   *     empty or null.
   * @param scope the scope for the query
   * @deprecated change to bookkeeping done via commits
   * @return
   */
  GraphModel.KnowledgeGraph subgraph(
      long focalNodeId,
      int depth,
      Collection<Long> requiredNodes,
      Collection<RuntimeAsset.Type> acceptedTypes,
      Collection<GraphModel.Relationship> acceptedRelationships,
      GraphModel.KnowledgeGraph.Detail detail,
      ContextScope scope);

  /**
   * Extract and return the committed asset that has the specified ID from the graph, ensuring it is
   * of the passed class. Expected to be the fastest way to retrieve a node when the ID is known,
   * therefore available besides the more general {@link #query(Class, Scope)}. Implementations
   * should use a properly configured cache to avoid repeated lookups.
   *
   * @param id
   * @param resultClass
   * @param <T>
   * @return the asset or null if not found or not of the passed class
   */
  <T extends RuntimeAsset> T get(long id, Scope scope, Class<T> resultClass);

  /**
   * Called when an observation has been contextualized
   *
   * @param observation
   * @param scope
   * @param arguments additional parameters to add to the observation or to override existing ones
   * @deprecated remove from API and move to {@link Transaction}
   */
  void update(RuntimeAsset observation, Scope scope, Object... arguments);

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
   * The graph should only be used in a contextualized form, which will establish any possible
   * long-lived connection so that performance is optimal. This method must return a new instance of
   * the knowledge graph contextualized on the passed configuration. Implementations should throw an
   * exception when contextualization has not happened.
   *
   * <p>The ID in the digitalTwinConfig configuration may not exist in the graph, or exist from
   * previous operations, with the same OR a different user reconnecting to an existing knowledge
   * graph. If the connection is requested, the rights of the passed user to connect have been
   * validated upstream.
   *
   * <p>If the database serves multiple contexts, the contextualization operation should also build
   * or load a main context node, to which all root observations will be linked, and the
   * context-specific dataflow and provenance roots.
   *
   * @param digitalTwinConfig
   * @param userScope
   * @return
   */
  KnowledgeGraph contextualize(DigitalTwin.Configuration digitalTwinConfig, UserScope userScope);

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
