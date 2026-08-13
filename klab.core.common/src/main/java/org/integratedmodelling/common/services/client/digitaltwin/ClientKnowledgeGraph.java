package org.integratedmodelling.common.services.client.digitaltwin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.graph.DefaultEdge;

/**
 * The ClientKnowledgeGraph represents a local client-specific implementation of the KnowledgeGraph
 * interface, which allows interaction with a runtime knowledge graph, handling assets,
 * relationships, and queries. This implementation does not permit modifying operations directly on
 * the graph but facilitates querying and ingesting new data from external sources.
 *
 * <p>Assets in here should only be added after successful contextualization and are indexed by
 * their stable ID. Adding an unresolved asset results in an exception being thrown.
 *
 * <p>The ClientKnowledgeGraph maintains a directed graph of RuntimeAssets in RAM and their
 * relationships. It initializes a default graph with predefined assets and supports ingestion of
 * additional graph data, updates to asset metadata, and querying of assets and relationships. It
 * also exposes local methods for querying that do not require a round-trip to the runtime.
 *
 * <p>Completeness is tracked per asset, direction and relationship. Missing adjacency slices are
 * loaded from the service on demand and are only marked complete after all referenced assets have
 * been retrieved. Modifying commits invalidate the affected slices so a later graph walk refreshes
 * them.
 *
 * <p>If the local methods are used instead of querying the remote graph, the contents of the
 * client-side KG may be incomplete w.r.t. the service-side graph. The presence of observations is
 * only guaranteed for those submitted and resolved within the same client. This implementation will
 * keep things synchronized in a lazy fashion as long as the ingest() function is called upon
 * messaging related to new observations or changes in existing ones.
 */
public class ClientKnowledgeGraph implements KnowledgeGraph {

  private static final int DEFAULT_QUERY_DEPTH = 2;

  private final ContextScope scope;
  private final RuntimeClient runtimeClient;
  private final Graph<Long, Relationship> graph = new DirectedPseudograph<>(Relationship.class);
  /** Adjacencies that have been completely retrieved from the service. Guarded by {@link #graph}. */
  private final Set<Adjacency> loadedAdjacencies = new HashSet<>();
  /** Assets whose cached representation must not be reused after a modifying commit. */
  private final Set<Long> invalidatedAssets = new HashSet<>();
  /** Commit IDs are the idempotency key for AMQP redelivery and local/remote duplicate events. */
  private final Map<Long, KnowledgeGraph.Commit> appliedCommits = new HashMap<>();
  private final Queue<KnowledgeGraph.Commit> commitQueue = new ConcurrentLinkedQueue<>();
  private Cache<Long, RuntimeAsset> assetCache =
      CacheBuilder.newBuilder()
          .maximumSize(/* TODO initialize from engine settings */ 500)
          .expireAfterAccess(/* TODO this too */ 3, TimeUnit.HOURS)
          .build();

  public ClientKnowledgeGraph(ContextScope scope, RuntimeClient runtimeClient) {
    this.scope = scope;
    this.runtimeClient = runtimeClient;
    this.graph.addVertex(RuntimeAsset.CONTEXT_ASSET.getId());
    this.graph.addVertex(RuntimeAsset.PROVENANCE_ASSET.getId());
    this.graph.addVertex(RuntimeAsset.DATAFLOW_ASSET.getId());
    this.assetCache.put(RuntimeAsset.CONTEXT_ASSET.getId(), RuntimeAsset.CONTEXT_ASSET);
    this.assetCache.put(RuntimeAsset.PROVENANCE_ASSET.getId(), RuntimeAsset.PROVENANCE_ASSET);
    this.assetCache.put(RuntimeAsset.DATAFLOW_ASSET.getId(), RuntimeAsset.DATAFLOW_ASSET);
    this.graph.addEdge(
        RuntimeAsset.CONTEXT_ASSET.getId(),
        RuntimeAsset.PROVENANCE_ASSET.getId(),
        new Relationship(
            GraphModel.Relationship.HAS_PROVENANCE,
            RuntimeAsset.CONTEXT_ASSET.getId(),
            RuntimeAsset.PROVENANCE_ASSET.getId(),
            Map.of("builtin", true)));
    this.graph.addEdge(
        RuntimeAsset.CONTEXT_ASSET.getId(),
        RuntimeAsset.DATAFLOW_ASSET.getId(),
        new Relationship(
            GraphModel.Relationship.HAS_DATAFLOW,
            RuntimeAsset.CONTEXT_ASSET.getId(),
            RuntimeAsset.DATAFLOW_ASSET.getId(),
            Map.of("builtin", true)));
  }

  public synchronized void ingest(Observation observation) {
    if (observation == null || !isAddressableAssetId(observation.getId())) {
      scope.warn(
          "Ignoring observation that is not stored in the knowledge graph"
              + (observation == null ? "" : ": " + observation.getId()));
      return;
    }
    Set<Long> focusIds = new HashSet<>();
    focusIds.add(observation.getId());

    if (observation.getMetadata().containsKey(Metadata.IM_COMMIT_ID)) {

      var commitId = observation.getMetadata().get(Metadata.IM_COMMIT_ID, Number.class);
      synchronized (graph) {
        var appliedCommit = appliedCommits.get(commitId.longValue());
        if (appliedCommit != null) {
          observation.getMetadata().put(Metadata.IM_COMMIT, appliedCommit);
          return;
        }
      }
      assetCache.put(observation.getId(), observation);
      var commit = runtimeClient.getCommit(commitId.longValue(), scope);
      if (commit != null) {
        synchronized (graph) {
          var appliedCommit = appliedCommits.get(commit.getId());
          if (appliedCommit != null) {
            observation.getMetadata().put(Metadata.IM_COMMIT, appliedCommit);
            return;
          }
          try {
            applyCommit(commit, focusIds);
            appliedCommits.put(commit.getId(), commit);
            commitQueue.add(commit);
            // Add it to the observation so downstream consumers do not need another request.
            observation.getMetadata().put(Metadata.IM_COMMIT, commit);
            // The payload is the freshest representation when it is also marked as modified.
            assetCache.put(observation.getId(), observation);
            invalidatedAssets.remove(observation.getId());
          } catch (Throwable t) {
            scope.warn("Cannot apply knowledge graph commit " + commit.getId(), t);
          }
        }
      }

      //      /** Notify the UI of the new observations. */
      //      if (scope.getDigitalTwin() instanceof ClientDigitalTwin clientDigitalTwin) {
      //        clientDigitalTwin.ingest(
      //            Message.create(
      //                scope,
      //                Message.MessageClass.UserInterface,
      //                Message.MessageType.ObservationsInFocus,
      //                Utils.Strings.join(focusIds, ",")));
      //      }
    } else {
      assetCache.put(observation.getId(), observation);
    }
  }

  private void applyCommit(KnowledgeGraph.Commit commit, Set<Long> focusIds) {

    // Validate relationship names before changing local state so malformed commits are atomic.
    for (var id : commit.getDeletedAssets()) {
      requireAddressableAssetId(id, commit);
    }
    for (var id : commit.getModifiedAssets()) {
      requireAddressableAssetId(id, commit);
    }
    for (var id : commit.getAddedAssets()) {
      requireAddressableAssetId(id, commit);
    }
    for (var id : commit.getAddedObservations()) {
      requireAddressableAssetId(id, commit);
    }
    for (var id : commit.getAddedCohorts()) {
      requireAddressableAssetId(id, commit);
    }
    for (var link : commit.getDeletedLinks()) {
      requireAddressableAssetId(link.getFirst(), commit);
      requireAddressableAssetId(link.getSecond(), commit);
      GraphModel.Relationship.valueOf(link.getThird());
    }
    for (var link : commit.getAddedLinks()) {
      requireAddressableAssetId(link.getFirst(), commit);
      requireAddressableAssetId(link.getSecond(), commit);
      GraphModel.Relationship.valueOf(link.getThird());
    }

    for (var link : commit.getDeletedLinks()) {
      var relationship = GraphModel.Relationship.valueOf(link.getThird());
      if (removeRelationship(link.getFirst(), link.getSecond(), relationship)) {
        adjustChildCount(link.getFirst(), relationship, -1);
      }
    }

    for (var id : commit.getDeletedAssets()) {
      if (graph.containsVertex(id)) {
        graph.removeVertex(id);
      }
      assetCache.invalidate(id);
      invalidatedAssets.add(id);
      invalidateAdjacencies(id);
    }

    for (var id : commit.getModifiedAssets()) {
      assetCache.invalidate(id);
      invalidatedAssets.add(id);
      invalidateAdjacencies(id);
    }

    commit.getAddedAssets().forEach(graph::addVertex);
    for (var link : commit.getAddedLinks()) {
      var relationship = GraphModel.Relationship.valueOf(link.getThird());
      graph.addVertex(link.getFirst());
      graph.addVertex(link.getSecond());
      if (addRelationship(
          link.getFirst(),
          link.getSecond(),
          relationship,
          Map.of("commit", commit.getId()))) {
        adjustChildCount(link.getFirst(), relationship, 1);
      }
      if (!commit.getAddedObservations().contains(link.getFirst())
          && commit.getAddedObservations().contains(link.getSecond())
          && relationship == GraphModel.Relationship.HAS_CHILD) {
        focusIds.add(link.getSecond());
      }
    }
  }

  private boolean addRelationship(
      long sourceId,
      long targetId,
      GraphModel.Relationship relationship,
      Map<String, Object> metadata) {
    if (!isAddressableAssetId(sourceId) || !isAddressableAssetId(targetId)) {
      return false;
    }
    if (findRelationship(sourceId, targetId, relationship) != null) {
      return false;
    }
    graph.addEdge(
        sourceId,
        targetId,
        new Relationship(relationship, sourceId, targetId, metadata == null ? Map.of() : metadata));
    return true;
  }

  private Relationship findRelationship(
      long sourceId, long targetId, GraphModel.Relationship relationship) {
    if (!graph.containsVertex(sourceId) || !graph.containsVertex(targetId)) {
      return null;
    }
    return graph.getAllEdges(sourceId, targetId).stream()
        .filter(edge -> edge.relationship == relationship)
        .findFirst()
        .orElse(null);
  }

  private boolean removeRelationship(
      long sourceId, long targetId, GraphModel.Relationship relationship) {
    var edge = findRelationship(sourceId, targetId, relationship);
    return edge != null && graph.removeEdge(edge);
  }

  private void adjustChildCount(
      long sourceId, GraphModel.Relationship relationship, int difference) {
    var source = assetCache.getIfPresent(sourceId);
    if (relationship == GraphModel.Relationship.HAS_CHILD
        && source instanceof ObservationImpl observation) {
      observation.setChildrenCount(Math.max(0, observation.getChildrenCount() + difference));
    } else if (relationship == GraphModel.Relationship.HAS_MEMBER
        && source instanceof CohortImpl cohort) {
      cohort.setChildrenCount(Math.max(0, cohort.getChildrenCount() + difference));
    }
  }

  private void invalidateAdjacencies(long assetId) {
    loadedAdjacencies.removeIf(adjacency -> adjacency.assetId == assetId);
  }

  /**
   * The commit queue for all commits we got during the lifetime of the scope that hosts this
   *
   * @return
   */
  public Queue<KnowledgeGraph.Commit> getCommitQueue() {
    return commitQueue;
  }

  /// Extract a subgraph from the current graph at a given hierarchy depth and showing a specified
  /// set of relationships.
  ///
  /// Logic is:
  ///
  /// 1. pass only one arg with all the observations that must be visible in the graph;
  /// 2. set focus to first, actualDepth to depth;
  /// 3. if arg.size() > 1, call graph common ancestor function; set actualDepth = actualDepth +
  ///    CA.maxPathLength; set focus to CA.commonAncestor;
  /// 4. Then create new graph result and follow the graph from focus, retrieving the assets with
  ///    the desired relationships to the desired depth.
  ///
  /// The relationship direction is taken into account. The only relationships that is followed at
  /// depth is HAS_CHILD. Note that as most other relationships have a known type of asset as
  /// a target or source, assets of all types will be returned. They can be removed from the
  /// vertices, and the links will also disappear.
  ///
  /// @param focus
  /// @param depth
  /// @param acceptedRelationships
  /// @return the graph with the desired options
  ///
  public Graph<RuntimeAsset, Relationship> getSubgraph(
      List<RuntimeAsset> focus,
      int depth,
      Collection<GraphModel.Relationship> acceptedRelationships) {

    Graph<RuntimeAsset, Relationship> ret = new DirectedPseudograph<>(Relationship.class);

    var focalAsset = focus.iterator().next();
    var actualDepth = depth;

    if (focus.size() > 1) {
      var tca = Utils.Graphs.findCommonAncestry(focus, scope::getParentOf);
      focalAsset = tca.getCommonAncestor();
      actualDepth += tca.getMaxDistance();
    }

    ret.addVertex(focalAsset);
    addChildren(focalAsset, actualDepth, ret);

    // add the non-recursive relationships at depth 1
    EnumSet<GraphModel.Relationship> nonRecursive = EnumSet.copyOf(acceptedRelationships);
    nonRecursive.remove(GraphModel.Relationship.HAS_CHILD);
    for (var relationship : nonRecursive) {
      for (var asset : new ArrayList<>(ret.vertexSet())) {
        for (var link : getLinks(asset, relationship.direction(), scope, relationship)) {
          ret.addVertex(
              relationship.direction() == GraphModel.Relationship.Direction.OUTGOING
                  ? link.target()
                  : link.source());
          ret.addEdge(link.source(), link.target(), new Relationship(link));
        }
      }
    }

    return ret;
  }

  private void addChildren(RuntimeAsset focus, int depth, Graph<RuntimeAsset, Relationship> ret) {
    if (depth > 0) {
      for (var child : getChildAssets(focus)) {
        ret.addVertex(child);
        ret.addEdge(
            focus,
            child,
            new Relationship(
                GraphModel.Relationship.HAS_CHILD, focus.getId(), child.getId(), Map.of()));
        addChildren(child, depth - 1, ret);
      }
    }
  }

  /**
   * Get the children of the passed asset, loading or refreshing the corresponding service-side
   * adjacency when necessary.
   *
   * @return
   */
  public List<RuntimeAsset> getChildAssets(RuntimeAsset asset) {
    var relationship =
        asset instanceof Cohort
            ? GraphModel.Relationship.HAS_MEMBER
            : GraphModel.Relationship.HAS_CHILD;
    return getLinks(asset, GraphModel.Relationship.Direction.OUTGOING, scope, relationship).stream()
        .map(Link::target)
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public Transaction createTransaction(ContextScope scope) {
    throw new KlabIllegalStateException(
        "Modifying operations not allowed on the client-side knowledge graph");
  }

  @Override
  public <T extends RuntimeAsset> Query<T> query(Class<T> resultClass, Scope scope) {
    return new KnowledgeGraphQuery<>(KnowledgeGraphQuery.AssetType.classify(resultClass)) {
      @Override
      public List<T> run(Scope scope) {
        return runtimeClient.queryKnowledgeGraph(this, scope);
      }
    };
  }

  @Override
  public <T extends RuntimeAsset> List<T> query(
      Query<T> knowledgeGraphQuery, Class<T> resultClass, Scope scope) {
    return runtimeClient.queryKnowledgeGraph(knowledgeGraphQuery, scope);
  }

  /**
   * Retrieves a list of incoming {@link RuntimeAsset} nodes connected to the given target node
   * through edges that match the specified relationship, if provided.
   *
   * @param target the source {@link RuntimeAsset} node for which incoming assets will be retrieved
   * @param relationship the relationship type to filter by; pass null to include all relationships
   * @return a list of {@link RuntimeAsset} nodes that are incoming to the source node
   */
  public List<RuntimeAsset> incoming(RuntimeAsset target, GraphModel.Relationship relationship) {
    synchronized (graph) {
      var asset = assetCache.getIfPresent(target.getId());
      if (asset == null || !graph.containsVertex(asset.getId())) {
        return List.of();
      }
      return graph.incomingEdgesOf(asset.getId()).stream()
          .filter(edge -> relationship == null || edge.relationship == relationship)
          .map(defaultEdge -> getAsset(graph.getEdgeSource(defaultEdge), scope, RuntimeAsset.class))
          .filter(Objects::nonNull)
          .toList();
    }
  }

  /**
   * Retrieves a list of incoming {@link RuntimeAsset} nodes connected to the given target node
   * through edges that match the specified relationship, if provided.
   *
   * @param target the source {@link RuntimeAsset} node for which incoming and outoling assets will
   *     be retrieved
   * @param relationships the relationship types to filter by; pass nothing to include all
   *     relationships
   * @return a list of {@link RuntimeAsset} nodes that are incoming or outgoing to the source node
   *     along with the relationship they are connected to it with. Use {@link
   *     GraphModel.Relationship#direction()} to understand the direction if needed
   * @see #incoming(RuntimeAsset, GraphModel.Relationship)
   * @see #outgoing(RuntimeAsset, GraphModel.Relationship)
   */
  public List<Pair<RuntimeAsset, GraphModel.Relationship>> related(
      RuntimeAsset target, GraphModel.Relationship... relationships) {

    EnumSet<GraphModel.Relationship> rels = EnumSet.noneOf(GraphModel.Relationship.class);
    if (relationships != null) {
      rels.addAll(List.of(relationships));
    }

    synchronized (graph) {
      var asset = assetCache.getIfPresent(target.getId());
      if (asset == null || !graph.containsVertex(asset.getId())) {
        return List.of();
      }
      var incoming =
          graph.incomingEdgesOf(asset.getId()).stream()
              .filter(edge -> rels.isEmpty() || rels.contains(edge.relationship))
              .map(
                  edge ->
                      Pair.of(
                          getAsset(graph.getEdgeSource(edge), scope, RuntimeAsset.class),
                          edge.relationship))
              .filter(pair -> pair.getFirst() != null)
              .toList();
      var outgoing =
          graph.outgoingEdgesOf(asset.getId()).stream()
              .filter(edge -> rels.isEmpty() || rels.contains(edge.relationship))
              .map(
                  edge ->
                      Pair.of(
                          getAsset(graph.getEdgeTarget(edge), scope, RuntimeAsset.class),
                          edge.relationship))
              .filter(pair -> pair.getFirst() != null)
              .toList();
      return Utils.Collections.join(incoming, outgoing);
    }
  }

  /**
   * Retrieves a list of outgoing {@link RuntimeAsset} nodes connected to the given source node
   * through edges that match the specified relationship, if provided.
   *
   * @param source the source {@link RuntimeAsset} node for which outgoing assets will be retrieved
   * @param relationship the relationship type to filter by; pass null to include all relationships
   * @return a list of {@link RuntimeAsset} nodes that are incoming to the source node
   *     <p>TODO improve with multiple relationships from a set
   */
  public List<RuntimeAsset> outgoing(RuntimeAsset source, GraphModel.Relationship relationship) {
    synchronized (graph) {
      var asset = assetCache.getIfPresent(source.getId());
      if (asset == null || !graph.containsVertex(asset.getId())) {
        return List.of();
      }
      return graph.outgoingEdgesOf(asset.getId()).stream()
          .filter(edge -> relationship == null || edge.relationship == relationship)
          .map(defaultEdge -> getAsset(graph.getEdgeTarget(defaultEdge), scope, RuntimeAsset.class))
          .filter(Objects::nonNull)
          .toList();
    }
  }

  @Override
  public void deleteContext() {}

  @Override
  public Agent user() {
    return null;
  }

  @Override
  public Agent klab() {
    return null;
  }

  @Override
  public RuntimeAsset scope() {
    return null;
  }

  @Override
  public RuntimeAsset provenance() {
    return null;
  }

  @Override
  public RuntimeAsset dataflow() {
    return null;
  }

  @Override
  public List<ContextInfo> getExistingContexts(UserScope scope) {
    return List.of();
  }

  @Override
  public void deleteContext(ContextInfo contextScope, ServiceScope serviceScope) {
    throw new KlabIllegalStateException(
        "Admin operations not allowed on the client-side knowledge graph");
  }

  @Override
  public void clear() {}

  private <T extends RuntimeAsset> T retrieveFromGraph(long id, Class<T> assetClass, Scope scope) {
    if (!isAddressableAssetId(id)) {
      return null;
    }
    try {
      return runtimeClient.getAsset(id, assetClass, scope);
    } catch (Throwable t) {
      scope.warn("Ignoring unexpected error in service-side knowledge graph", t);
      return null;
    }
  }

  @Override
  public <T extends RuntimeAsset> T getAsset(long id, Scope scope, Class<T> resultClass) {
    if (!isAddressableAssetId(id)) {
      return null;
    }
    try {
      return (T) assetCache.get(id, () -> retrieveFromGraph(id, resultClass, scope));
    } catch (Throwable e) {
      // fall back to other strategy
      scope.warn("Ignoring unexpected cache error in service-side knowledge graph", e);
    }
    return null;
  }

  @Override
  public Collection<Link> getLinks(
      RuntimeAsset asset,
      GraphModel.Relationship.Direction direction,
      ContextScope scope,
    GraphModel.Relationship... relationship) {

    if (asset == null || !isAddressableAssetId(asset.getId())) {
      return List.of();
    }
    synchronized (graph) {
      // A commit may already have supplied useful links. A failed remote refresh must not hide
      // those links; leaving the adjacency unloaded ensures a later walk will retry.
      loadAdjacency(asset, direction, scope, relationship);

      if (!graph.containsVertex(asset.getId())) {
        return List.of();
      }
      var accepted =
          relationship == null || relationship.length == 0
              ? EnumSet.allOf(GraphModel.Relationship.class)
              : EnumSet.copyOf(Arrays.asList(relationship));
      var edges =
          direction == GraphModel.Relationship.Direction.OUTGOING
              ? graph.outgoingEdgesOf(asset.getId())
              : graph.incomingEdgesOf(asset.getId());
      var ret = new ArrayList<Link>();
      for (var rel : edges) {
        if (!accepted.contains(rel.relationship)) {
          continue;
        }
        var sourceAsset = getAsset(rel.sourceId, scope, RuntimeAsset.class);
        var targetAsset = getAsset(rel.targetId, scope, RuntimeAsset.class);
        if (sourceAsset == null || targetAsset == null) {
          continue;
        }
        var link = new LinkImpl();
        link.setSource(sourceAsset);
        link.setTarget(targetAsset);
        link.setRelationship(rel.relationship);
        link.getProperties().putAll(rel.metadata);
        ret.add(link);
      }
      return ret;
    }
  }

  /**
   * Load and atomically replace one slice of an asset's adjacency. A failed request leaves the
   * slice unloaded, allowing the next graph walk to retry.
   */
  private boolean loadAdjacency(
      RuntimeAsset asset,
      GraphModel.Relationship.Direction direction,
      ContextScope requestScope,
      GraphModel.Relationship... relationships) {

    if (asset == null || !isAddressableAssetId(asset.getId())) {
      return false;
    }
    var cachedAsset = assetCache.getIfPresent(asset.getId());
    if (invalidatedAssets.contains(asset.getId())) {
      cachedAsset = retrieveFromGraph(asset.getId(), RuntimeAsset.class, requestScope);
      if (!isExpectedAsset(cachedAsset, asset.getId())) {
        return false;
      }
      assetCache.put(asset.getId(), cachedAsset);
      invalidatedAssets.remove(asset.getId());
    } else if (cachedAsset == null) {
      cachedAsset = asset;
      assetCache.put(asset.getId(), asset);
    }
    var focalAsset = cachedAsset;

    var requested =
        relationships == null || relationships.length == 0
            ? EnumSet.allOf(GraphModel.Relationship.class)
            : EnumSet.copyOf(Arrays.asList(relationships));
    if (requested.stream().allMatch(type -> adjacencyLoaded(focalAsset.getId(), direction, type))) {
      return true;
    }

    Collection<KnowledgeGraph.LinkInfo> remoteLinks;
    try {
      remoteLinks =
          runtimeClient.getLinkInfo(
              focalAsset,
              direction,
              requestScope,
              requested.toArray(GraphModel.Relationship[]::new));
    } catch (Throwable t) {
      requestScope.warn("Cannot retrieve knowledge graph links for asset " + focalAsset.getId(), t);
      return false;
    }
    if (remoteLinks == null) {
      return false;
    }

    var acceptedLinks = new ArrayList<KnowledgeGraph.LinkInfo>();
    var retrievedAssets = new HashMap<Long, RuntimeAsset>();
    retrievedAssets.put(focalAsset.getId(), focalAsset);
    for (var link : remoteLinks) {
      if (!isAddressableAssetId(link.getSourceId())
          || !isAddressableAssetId(link.getTargetId())) {
        requestScope.warn(
            "Ignoring knowledge graph link with invalid endpoint: "
                + link.getSourceId()
                + " -> "
                + link.getTargetId());
        continue;
      }
      for (var id : List.of(link.getSourceId(), link.getTargetId())) {
        if (!retrievedAssets.containsKey(id)) {
          var endpoint = assetCache.getIfPresent(id);
          if (endpoint == null) {
            endpoint = retrieveFromGraph(id, RuntimeAsset.class, requestScope);
          }
          if (!isExpectedAsset(endpoint, id)) {
            requestScope.warn(
                "Cannot load knowledge graph endpoint "
                    + id
                    + ": runtime returned "
                    + (endpoint == null ? "no asset" : "asset " + endpoint.getId()));
            return false;
          }
          retrievedAssets.put(id, endpoint);
        }
      }
      acceptedLinks.add(link);
    }

    graph.addVertex(focalAsset.getId());
    retrievedAssets.forEach(
        (id, endpoint) -> {
          graph.addVertex(id);
          assetCache.put(id, endpoint);
        });

    var existing =
        direction == GraphModel.Relationship.Direction.OUTGOING
            ? new ArrayList<>(graph.outgoingEdgesOf(focalAsset.getId()))
            : new ArrayList<>(graph.incomingEdgesOf(focalAsset.getId()));
    for (var edge : existing) {
      if (requested.contains(edge.relationship)
          && !edge.metadata.containsKey("commit")
          && !edge.metadata.containsKey("builtin")) {
        graph.removeEdge(edge);
      }
    }
    for (var link : acceptedLinks) {
      var properties =
          link.getProperties() == null
              ? Map.<String, Object>of()
              : new HashMap<String, Object>(link.getProperties());
      addRelationship(
          link.getSourceId(), link.getTargetId(), link.getType(), properties);
    }
    requested.forEach(
        type -> loadedAdjacencies.add(new Adjacency(focalAsset.getId(), direction, type)));
    return true;
  }

  private static boolean isAddressableAssetId(long id) {
    return id > 0
        || id == RuntimeAsset.CONTEXT_ASSET_ID
        || id == RuntimeAsset.PROVENANCE_ASSET_ID
        || id == RuntimeAsset.DATAFLOW_ASSET_ID;
  }

  private static boolean isExpectedAsset(RuntimeAsset asset, long requestedId) {
    return asset != null
        && isAddressableAssetId(asset.getId())
        && asset.getId() == requestedId;
  }

  private static void requireAddressableAssetId(long id, KnowledgeGraph.Commit commit) {
    if (!isAddressableAssetId(id)) {
      throw new IllegalArgumentException(
          "Commit " + commit.getId() + " contains invalid asset ID " + id);
    }
  }

  private boolean adjacencyLoaded(
      long assetId,
      GraphModel.Relationship.Direction direction,
      GraphModel.Relationship relationship) {
    return loadedAdjacencies.contains(new Adjacency(assetId, direction, relationship));
  }

  @Override
  public Agent requireAgent(String agentName) {
    return null;
  }

  @Override
  public KnowledgeGraph contextualize(
      DigitalTwin.Configuration digitalTwinConfig, UserScope userScope) {
    return null;
  }

  @Override
  public KnowledgeGraph merge(URL remoteDigitalTwinURL) {
    return null;
  }

  @Override
  public boolean isOnline() {
    return true;
  }

  @Override
  public void shutdown() {}

  @Override
  public List<ContextInfo> getContextInfo(Scope scope) {
    return List.of();
  }

  public void populate(RuntimeAsset contextAsset, GraphModel.Relationship relationship, int i) {

    if (i > 0) {
      for (var link :
          getLinks(
              contextAsset,
              GraphModel.Relationship.Direction.OUTGOING,
              scope,
              relationship)) {
        populate(link.target(), relationship, i - 1);
      }
    }
  }

  private record Adjacency(
      long assetId,
      GraphModel.Relationship.Direction direction,
      GraphModel.Relationship relationship) {}

  /**
   * Relationship for the client graph has equals() and hashCode() so that no duplicated
   * relationships can be inserted when the graph is updated.
   *
   * <p>TODO can use LinkInfo now
   */
  public static class Relationship extends DefaultEdge {

    public GraphModel.Relationship relationship;
    public Map<String, Object> metadata;
    public long sourceId;
    public long targetId;

    public Relationship(
        GraphModel.Relationship relationship,
        long sourceId,
        long targetId,
        Map<String, Object> metadata) {
      this.relationship = relationship;
      this.metadata = metadata;
      this.sourceId = sourceId;
      this.targetId = targetId;
    }

    public Relationship(KnowledgeGraph.Link link) {
      this(link.type(), link.source().getId(), link.target().getId(), link.properties().asMap());
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      Relationship that = (Relationship) o;
      return sourceId == that.sourceId
          && targetId == that.targetId
          && relationship == that.relationship;
    }

    @Override
    public int hashCode() {
      return Objects.hash(relationship, sourceId, targetId);
    }

    public String toString() {
      return relationship.toString().toLowerCase();
    }
  }
}
