package org.integratedmodelling.common.services.client.digitaltwin;

import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.RuntimeAssetGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
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

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ClientKnowledgeGraph represents a local client-specific implementation of the KnowledgeGraph
 * interface, which allows interaction with a runtime knowledge graph, handling assets,
 * relationships, and queries. This implementation does not permit modifying operations directly on
 * the graph but facilitates querying and ingesting new data from external sources.
 *
 * <p>The ClientKnowledgeGraph manages a directed graph of RuntimeAssets and their relationships. It
 * initializes a default graph with predefined assets and supports ingestion of additional graph
 * data, updates to asset metadata, and querying of assets and relationships.
 */
public class ClientKnowledgeGraph implements KnowledgeGraph {

  private final ContextScope scope;
  private final RuntimeClient runtimeClient;
  private Graph<RuntimeAsset, Relationship> graph = new DefaultDirectedGraph<>(Relationship.class);
  private Map<Long, RuntimeAsset> catalog = new LinkedHashMap<>();

  public ClientKnowledgeGraph(ContextScope scope, RuntimeClient runtimeClient) {
    this.scope = scope;
    this.runtimeClient = runtimeClient;
    this.graph.addVertex(RuntimeAsset.CONTEXT_ASSET);
    this.graph.addVertex(RuntimeAsset.PROVENANCE_ASSET);
    this.graph.addVertex(RuntimeAsset.DATAFLOW_ASSET);
    this.catalog.put(RuntimeAsset.CONTEXT_ASSET.getId(), RuntimeAsset.CONTEXT_ASSET);
    this.catalog.put(RuntimeAsset.PROVENANCE_ASSET.getId(), RuntimeAsset.PROVENANCE_ASSET);
    this.catalog.put(RuntimeAsset.DATAFLOW_ASSET.getId(), RuntimeAsset.DATAFLOW_ASSET);
  }

  public void ingest(RuntimeAssetGraph graph) {

    for (var nodeId : graph.getNodes().keySet()) {

      var node = graph.getNodes().get(nodeId);
      var asset = node.getAsset();

      if (asset.getId() == RuntimeAsset.CONTEXT_ASSET.getId()) {
        asset = RuntimeAsset.CONTEXT_ASSET;
      } else if (asset.getId() == RuntimeAsset.PROVENANCE_ASSET.getId()) {
        asset = RuntimeAsset.PROVENANCE_ASSET;
      } else if (asset.getId() == RuntimeAsset.DATAFLOW_ASSET.getId()) {
        asset = RuntimeAsset.DATAFLOW_ASSET;
      }

      var id = Long.parseLong(nodeId);
      if (!catalog.containsKey(id)) {
        catalog.put(id, asset);
        this.graph.addVertex(asset);
      }
    }
    for (var edge : graph.getEdges()) {
      long source = Long.parseLong(edge.getSource());
      long target = Long.parseLong(edge.getTarget());
      // TODO edge metadata
      this.graph.addEdge(
          catalog.get(source),
          catalog.get(target),
          new Relationship(GraphModel.Relationship.valueOf(edge.getLabel()), edge.getMetadata()));
    }
  }

  public Graph<RuntimeAsset, Relationship> getGraph() {
    return graph;
  }

  public void update(RuntimeAsset asset, Message.MessageType messageType) {
    // TODO set metadata according to type. Differently from server-side KG, we also get
    //  observations that are in error or before contextualization.
    if (!catalog.containsKey(asset.getId())) {
      catalog.put(asset.getId(), asset);
      this.graph.addVertex(asset);
    } else {
      // switch to the actual object for indexing in the graph
      asset = catalog.get(asset.getId());
    }
    var status =
        switch (messageType) {
          case ContextualizationStarted -> RuntimeAsset.Status.UNRESOLVED;
          case ContextualizationAborted -> RuntimeAsset.Status.CORRUPTED;
          case ContextualizationSuccessful -> RuntimeAsset.Status.CONTEXTUALIZED;
          default -> throw new IllegalStateException("Unexpected value: " + messageType);
        };

    if (asset instanceof Knowledge knowledge) {
      knowledge.getMetadata().put("status", status);
    }
  }

  /**
   * Retrieve asset by URN
   *
   * @param id
   * @return
   */
  public RuntimeAsset getAsset(long id) {
    return catalog.get(id);
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
    var asset = catalog.get(target.getId());
    return graph.incomingEdgesOf(asset).stream()
        .filter(edge -> relationship == null || edge.relationship == relationship)
        .map(defaultEdge -> graph.getEdgeSource(defaultEdge))
        .toList();
  }

  /**
   * Retrieves a list of outgoing {@link RuntimeAsset} nodes connected to the given source node
   * through edges that match the specified relationship, if provided.
   *
   * @param source the source {@link RuntimeAsset} node for which outgoing assets will be retrieved
   * @param relationship the relationship type to filter by; pass null to include all relationships
   * @return a list of {@link RuntimeAsset} nodes that are incoming to the source node
   */
  public List<RuntimeAsset> outgoing(RuntimeAsset source, GraphModel.Relationship relationship) {
    var asset = catalog.get(source.getId());
    return graph.outgoingEdgesOf(asset).stream()
        .filter(edge -> relationship == null || edge.relationship == relationship)
        .map(defaultEdge -> graph.getEdgeTarget(defaultEdge))
        .toList();
  }

  public List<RuntimeAsset> assets() {
    return List.copyOf(catalog.values());
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

  @Override
  public KnowledgeGraph contextualize(ContextScope scope) {
    return null;
  }

  @Override
  public <T extends RuntimeAsset> T get(long id, ContextScope scope, Class<T> resultClass) {
    var ret = query(resultClass, scope).id(id).peek(scope);
    return (T) ret.orElse(null);
  }

  @Override
  public void update(RuntimeAsset observation, ContextScope scope, Object... arguments) {
    throw new KlabIllegalStateException(
        "Modifying operations not allowed on the client-side knowledge graph");
  }

  @Override
  public Agent requireAgent(String agentName) {
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

  public static class Relationship extends DefaultEdge {

    public GraphModel.Relationship relationship;
    public Map<String, String> metadata;

    public Relationship(GraphModel.Relationship relationship, Map<String, String> metadata) {
      this.relationship = relationship;
      this.metadata = metadata;
    }
  }
}
