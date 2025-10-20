package org.integratedmodelling.common.services.client.digitaltwin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
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
 * <p>The finalizedAsset field is a set of IDs for all assets whose child structure in the client
 * graph reflects the service-level graph. When an asset's ID is not in the set, the client will ask
 * the service for the structure of the asset's children at any request, then put it back in the
 * set. When a KG commit comes with a modification notice, the ID is removed to invalidate the
 * graph's structure relative to that asset.
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
  private final Graph<Long, Relationship> graph = new DefaultDirectedGraph<>(Relationship.class);
  private final Set<Long> finalizedAssets = new HashSet<>();
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
            Map.of()));
    this.graph.addEdge(
        RuntimeAsset.CONTEXT_ASSET.getId(),
        RuntimeAsset.DATAFLOW_ASSET.getId(),
        new Relationship(
            GraphModel.Relationship.HAS_DATAFLOW,
            RuntimeAsset.CONTEXT_ASSET.getId(),
            RuntimeAsset.DATAFLOW_ASSET.getId(),
            Map.of()));
  }

  public void ingest(Observation observation) {

    assetCache.put(observation.getId(), observation);

    Set<Long> focusIds = new HashSet<>();
    focusIds.add(observation.getId());

    if (observation.getMetadata().containsKey(Metadata.IM_COMMIT_ID)) {

      var commitId = observation.getMetadata().get(Metadata.IM_COMMIT_ID, String.class);
      var commit = runtimeClient.getCommit(commitId, scope);
      if (commit != null) {

        synchronized (graph) {
          /* Add all IDs to the thin graph and let get() do the rest when the assets are needed. */
          commit.getAddedAssets().forEach(graph::addVertex);
          graph.removeAllVertices(commit.getDeletedAssets());
          finalizedAssets.addAll(commit.getAddedAssets());
          finalizedAssets.removeAll(commit.getDeletedAssets());
          finalizedAssets.removeAll(commit.getModifiedAssets());
          for (var link : commit.getAddedLinks()) {
            graph.addVertex(link.getFirst());
            graph.addVertex(link.getSecond());
            graph.addEdge(
                link.getFirst(),
                link.getSecond(),
                new Relationship(
                    GraphModel.Relationship.valueOf(link.getThird()),
                    link.getFirst(),
                    link.getSecond(),
                    Map.of("commit", commit.getId())));
            if (!commit.getAddedObservations().contains(link.getFirst())
                && commit.getAddedObservations().contains(link.getSecond())
                && link.getThird().equals(GraphModel.Relationship.HAS_CHILD.toString())) {
              // this isn't linked to the observation, record it for the UI
              focusIds.add(link.getSecond());
            }
          }
        }
      }

      /** Notify the UI of the new observations. */
      if (scope.getDigitalTwin() instanceof ClientDigitalTwin clientDigitalTwin) {
        clientDigitalTwin.ingest(
            Message.create(
                scope,
                Message.MessageClass.UserInterface,
                Message.MessageType.ObservationsInFocus,
                Utils.Strings.join(focusIds, ",")));
      }
    }
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

    Graph<RuntimeAsset, Relationship> ret = new DefaultDirectedGraph<>(Relationship.class);

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
      for (var asset : ret.vertexSet()) {
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
   * Get the children of the passed asset, revising the hierarchy by querying the server-side KG
   * whenever the asset is not in the finalizedAssets set, meaning it does not come from a commit or
   * has been modified at service side.
   *
   * @return
   */
  public List<RuntimeAsset> getChildAssets(RuntimeAsset asset) {
    if (!finalizedAssets.contains(asset.getId())) {
      var children = new ArrayList<RuntimeAsset>();
      // do NOT add the children! If this is from a commit, they will have been added when ingesting
      // it
      finalizedAssets.add(asset.getId());
      for (var child : scope.getChildrenOf(asset)) {
        assetCache.put(child.getId(), child);
        graph.addVertex(child.getId());
        graph.addEdge(
            asset.getId(),
            child.getId(),
            new Relationship(
                GraphModel.Relationship.HAS_CHILD, asset.getId(), child.getId(), Map.of()));
        children.add(child);
      }
      return children;
    }
    return outgoing(asset, GraphModel.Relationship.HAS_CHILD);
  }

  @Override
  public Transaction createTransaction() {
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
    var asset = assetCache.getIfPresent(target.getId());
    return graph.incomingEdgesOf(asset.getId()).stream()
        .filter(edge -> relationship == null || edge.relationship == relationship)
        .map(defaultEdge -> getAsset(graph.getEdgeSource(defaultEdge), scope, RuntimeAsset.class))
        .toList();
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

    var asset = assetCache.getIfPresent(target.getId());
    var incoming =
        graph.incomingEdgesOf(asset.getId()).stream()
            .filter(edge -> rels.isEmpty() || rels.contains(edge.relationship))
            .map(
                defaultEdge ->
                    Pair.of(
                        assetCache.getIfPresent(graph.getEdgeSource(defaultEdge)),
                        defaultEdge.relationship))
            .toList();
    var outgoing =
        graph.outgoingEdgesOf(asset.getId()).stream()
            .filter(edge -> rels.isEmpty() || rels.contains(edge.relationship))
            .map(
                defaultEdge ->
                    Pair.of(
                        assetCache.getIfPresent(graph.getEdgeTarget(defaultEdge)),
                        defaultEdge.relationship))
            .toList();
    return Utils.Collections.join(incoming, outgoing);
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
    var asset = assetCache.getIfPresent(source.getId());
    return graph.outgoingEdgesOf(asset.getId()).stream()
        .filter(edge -> relationship == null || edge.relationship == relationship)
        .map(defaultEdge -> getAsset(graph.getEdgeTarget(defaultEdge), scope, RuntimeAsset.class))
        .toList();
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
  public void clear() {}

  private <T extends RuntimeAsset> T retrieveFromGraph(long id, Class<T> assetClass, Scope scope) {
    return runtimeClient.getAsset(id, assetClass, scope);
  }

  @Override
  public <T extends RuntimeAsset> T getAsset(long id, Scope scope, Class<T> resultClass) {
    try {
      return (T) assetCache.get(id, () -> retrieveFromGraph(id, resultClass, scope));
    } catch (ExecutionException e) {
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

    if (relationship == null || relationship.length == 0) {
      return List.of();
    }

    // TODO create a set.split(predicate) method to avoid this pain
    var ret = new ArrayList<Link>();
    var out =
        Arrays.stream(relationship)
            .filter(r -> r.direction() == GraphModel.Relationship.Direction.OUTGOING)
            .toList();
    EnumSet<GraphModel.Relationship> outgoing =
        out.isEmpty() ? EnumSet.noneOf(GraphModel.Relationship.class) : EnumSet.copyOf(out);
    var in =
        Arrays.stream(relationship)
            .filter(r -> r.direction() == GraphModel.Relationship.Direction.INCOMING)
            .toList();
    EnumSet<GraphModel.Relationship> incoming =
        in.isEmpty() ? EnumSet.noneOf(GraphModel.Relationship.class) : EnumSet.copyOf(in);

    synchronized (graph) {
      if (!outgoing.isEmpty()) {
        for (var rel : graph.outgoingEdgesOf(asset.getId())) {
          if (outgoing.contains(rel.relationship)) {
            var l = new LinkImpl();
            l.setSource(getAsset(rel.sourceId, scope, RuntimeAsset.class));
            l.setTarget(getAsset(rel.targetId, scope, RuntimeAsset.class));
            l.setRelationship(rel.relationship);
            l.getProperties().putAll(rel.metadata);
            ret.add(l);
          }
        }
      }

      if (!incoming.isEmpty()) {
        for (var rel : graph.incomingEdgesOf(asset.getId())) {
          if (incoming.contains(rel.relationship)) {
            var l = new LinkImpl();
            l.setSource(getAsset(rel.sourceId, scope, RuntimeAsset.class));
            l.setTarget(getAsset(rel.targetId, scope, RuntimeAsset.class));
            l.setRelationship(rel.relationship);
            l.getProperties().putAll(rel.metadata);
            ret.add(l);
          }
        }
      }
    }
    return ret;
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
  public List<SessionInfo> getSessionInfo(Scope scope) {
    return List.of();
  }

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
