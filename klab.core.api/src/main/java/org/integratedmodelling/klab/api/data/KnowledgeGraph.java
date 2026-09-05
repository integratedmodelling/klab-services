package org.integratedmodelling.klab.api.data;

import java.io.Closeable;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;

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
   * <p>The commit is a RuntimeAsset that provides a unique identifier and timestamp. This is meant
   * to enable storing in the knowledge graph (currently we don't do that) and presents a uniform
   * interface for treating as part of a RuntimeAsset graph (for example as the root for
   * visualization). Even if not in the KG, the ID must be guaranteed unique within the KG.
   *
   * <p>The commit ID is available as part of the metadata of the committed observation and it can
   * be used to obtain the commit through the digital twin API.
   */
  interface Commit extends RuntimeAsset {

    long getId();

    /**
     * Server-side timestamp can be used to provide sequencing.
     *
     * @return
     */
    long getTimestamp();

    /**
     * IDs of all the assets that were created in the transaction.
     *
     * @return
     */
    Set<Long> getAddedAssets();

    /**
     * Separately list the IDs of all new assets that are observations. These are also included in
     * the result of #getAddedAssets().
     *
     * @return
     */
    Set<Long> getAddedObservations();

    /**
     * Separately list the IDs of all new assets that are cohorts. These are also included in the
     * result of #getAddedAssets().
     *
     * @return
     */
    Set<Long> getAddedCohorts();

    /**
     * IDs of all the links that were created in the transaction, plus durable catalog links that
     * must be asserted to synchronize a client with assets created by an earlier independent
     * transaction. Each link is a triple with the source asset ID, the target asset ID and the
     * relationship type. The IDs may refer to assets previously seen in the graph and not present
     * in {@link #getAddedAssets()}.
     *
     * @return
     */
    Set<Triple<Long, Long, String>> getAddedLinks();

    /**
     * Deleted assets are those agents that become inoperative.
     *
     * @return
     */
    Set<Long> getDeletedAssets();

    /**
     * Inoperative agents will lose causal links to their processes
     *
     * @return
     */
    Set<Triple<Long, Long, String>> getDeletedLinks();

    Set<Long> getModifiedAssets();

    String getOwner();
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

  interface LinkInfo {
    GraphModel.Relationship getType();

    Parameters<String> getProperties();

    long getSourceId();

    long getTargetId();
  }

  /**
   * A mutable, typed runtime-graph query. With no anchor, searches the owning context for all
   * assets of the requested type. Results are distinct assets, not paths. Use {@link GraphModel.Fields}
   * constants for properties and {@link GraphModel.Relationship} for edges. Values are bound
   * parameters and retain their JSON scalar types.
   *
   * <p>Example: {@code graph.query(Observation.class, scope).source(parent)
   * .along(GraphModel.Relationship.HAS_CHILD).hops(1, 4)
   * .where(GraphModel.Fields.SIZE, Operator.GE, 10L)
   * .order(Order.descending(GraphModel.Fields.SIZE)).limit(20).run(scope)}.
   *
   * <p>AND, OR and NOT operate within the same context and result type. Apply ordering and
   * pagination to the combined query, not its children. No matches returns an empty list;
   * invalid, unsupported, unavailable or failed queries throw {@link QueryException}.
   * Neo4j bounds traversal at 64 hops and query trees at 128 nodes; pagination is deterministic
   * only while the graph is unchanged. Geometry descriptors, projections and aggregates are
   * not runtime-asset query results.
   *
   * @param <T>
   */
  interface Query<T extends RuntimeAsset> {

    enum SortDirection { ASC, DESC }

    record Order(String field, SortDirection direction) {
      public static Order ascending(String field) { return new Order(field, SortDirection.ASC); }
      public static Order descending(String field) { return new Order(field, SortDirection.DESC); }
    }

    /** Typed, JSON-serializable property predicate. Values are parameters, never query source. */
    record Criterion(String field, Operator operator, Object argument) {}

    /**
     * EQUALS accepts null to match property absence. LT/LE/GT/GE compare typed properties;
     * combine comparisons for ranges. LIKE is case-sensitive with SQL-style % and _ wildcards.
     * BEFORE/AFTER compare epoch milliseconds or ISO-8601 instant strings strictly.
     * INTERSECT/COVERS/NEAREST currently throw UNSUPPORTED_QUERY pending a geometry/CRS contract.
     */
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

    /** Traverse outgoing edges from an asset, root marker, scope, or typed class wildcard. */
    Query<T> source(Object startingPoint);

    /** Traverse incoming edges toward an anchor; returns the source-side assets. */
    Query<T> target(Object startingPoint);

    /** Select an edge type; optional field/value pairs must match every edge in the path. */
    Query<T> along(GraphModel.Relationship relationship, Object... parameters);

    /**
     * Find directed relationships between the endpoints. Requires {@link Link} results and
     * exactly one hop. Preserves properties and orientation; parallel links remain separate.
     *
     * @param source
     * @param target
     * @return
     */
    Query<T> between(Object source, Object target, GraphModel.Relationship relationship);

    /** Inclusive path lengths 1 through depth; equivalent to hops(1, depth). Default is 1. */
    Query<T> depth(int depth);

    /** Inclusive path length bounds; zero includes the anchor itself. Maximum supported is 64. */
    default Query<T> hops(int minimum, int maximum) {
      throw new UnsupportedOperationException("Hop ranges are not supported");
    }

    /** Maximum returned rows, after ordering and offset. -1 is unlimited; zero returns none. */
    Query<T> limit(long n);

    /** Skip a nonnegative number of sorted results. Not a snapshot across concurrent writes. */
    Query<T> offset(long n);

    /** Add an AND predicate on a declared GraphModel field. See {@link Operator} for values. */
    Query<T> where(String field, Operator operator, Object argument);

    /** Ordered {@link Order} records or field-name constants (ascending). */
    Query<T> order(Object... criteria);

    List<T> run(Scope scope);

    Optional<T> peek(Scope scope);

    Query<T> or(Query<T> query);

    Query<T> and(Query<T> query);

    /** Complement within the authorized context and this query's result type. */
    default Query<T> not() {
      throw new UnsupportedOperationException("Query negation is not supported");
    }
  }

  /**
   * Query failures are never represented as an empty result list. The HTTP endpoint maps
   * INVALID_QUERY to 400, UNSUPPORTED_QUERY to 422, ACCESS_DENIED to 403,
   * BACKEND_UNAVAILABLE to 503 and EXECUTION_FAILED to 500, with a ProblemDetail code property.
   */
  class QueryException extends RuntimeException {
    public enum Code { INVALID_QUERY, UNSUPPORTED_QUERY, ACCESS_DENIED, BACKEND_UNAVAILABLE, EXECUTION_FAILED }
    private final Code code;
    public QueryException(Code code, String message) { super(message); this.code = code; }
    public QueryException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public Code getCode() { return code; }
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
   * Restore the committed scheduler registry for this context. Implementations must preserve
   * observation geometry and event timestamps and must not initialize or execute observations.
   */
  default List<Observation> getScheduledObservations(ContextScope scope) {
    throw new UnsupportedOperationException("Scheduler registry restoration is not supported");
  }

  /**
   * Create a transaction which will make changes in the knowledge graph when closed.
   *
   * @return a new transaction
   */
  Transaction createTransaction(ContextScope scope);

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
   * Admin-only: delete the knowledge related to a particular scope. The lifetime of the digital
   * twin is manage through {@link ContextScope#close()} and the persistence configuration: this
   * should only happen at service side and throw an exception if called on the client.
   *
   * @param contextScope
   */
  void deleteContext(ContextInfo contextScope, ServiceScope serviceScope);

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
   * Directly retrieve the committed asset that has the specified ID from the graph, ensuring it is
   * of the passed class (pass <code>RuntimeAsset.class</code> if the class isn't known). Expected
   * to be the fastest way to retrieve a node when the ID is known, therefore available besides the
   * more general {@link #query(Class, Scope)}. Implementations should use a properly configured
   * cache to avoid repeated lookups.
   *
   * @param id
   * @param resultClass
   * @param <T>
   * @return the asset or null if not found or not of the passed class
   */
  <T extends RuntimeAsset> T getAsset(long id, Scope scope, Class<T> resultClass);

  /**
   * Directly retrieve a committed asset by its canonical URN. Unlike numeric-ID parsing this also
   * supports named substantial observations, whose URNs are stable catalog identities and do not
   * end in an ID.
   *
   * @param urn canonical graph URN
   * @param scope authorized scope
   * @param resultClass expected asset class
   * @return the asset, or null if it is absent or has a different class
   */
  <T extends RuntimeAsset> T getAsset(String urn, Scope scope, Class<T> resultClass);

  /**
   * Retrieve all links from the graph or the current transaction that match the arguments. This one
   * is the generic way to retrieve anything from the graph when a single link is involved.
   *
   * <p>If the context scope is executing a transaction, the links in the transaction must also be
   * returned.
   *
   * @param asset the source or target asset
   * @param direction if OUTGOING, the <code>asset</code> is the source, otherwise the target.
   * @param relationship choose the relationship; if none is passed, all links are returned.
   * @return
   */
  Collection<KnowledgeGraph.Link> getLinks(
      RuntimeAsset asset,
      GraphModel.Relationship.Direction direction,
      ContextScope scope,
      GraphModel.Relationship... relationship);

  //  /**
  //   * Called to quickly update an object
  //   *
  //   * @param observation
  //   * @param scope
  //   * @param arguments additional parameters to add to the observation or to override existing
  // ones
  //   */
  //  void update(RuntimeAsset observation, Scope scope, Object... arguments);

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
   * Retrieve information relative to all contexts that are currently active in this scope.
   * According to the scope type, different info will be retrieved.
   *
   * @param scope
   * @return
   */
  List<ContextInfo> getContextInfo(Scope scope);
}
