package org.integratedmodelling.common.services.client.digitaltwin;

import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.RuntimeAssetGraph;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** At the client side, the knowledge graph can only be queried and rejects any operation. */
public class ClientKnowledgeGraph implements KnowledgeGraph {

  private final ContextScope scope;
  private final RuntimeClient runtimeClient;
  private Graph<RuntimeAsset, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
  private Map<Long, RuntimeAsset> catalog = new HashMap<>();

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
      var id = Long.parseLong(nodeId);
      if (!catalog.containsKey(id)) {
        catalog.put(id, asset);
        this.graph.addVertex(asset);
      }
    }
    for (var edge : graph.getEdges()) {
      long source = Long.parseLong(edge.getSource());
      long target = Long.parseLong(edge.getTarget());
      this.graph.addEdge(catalog.get(source), catalog.get(target));
    }
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
   * @param urn
   * @return
   */
  public RuntimeAsset getAsset(String urn) {
    return catalog.get(urn);
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
}
