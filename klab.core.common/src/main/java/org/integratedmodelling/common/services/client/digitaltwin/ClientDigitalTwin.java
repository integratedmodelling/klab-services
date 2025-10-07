package org.integratedmodelling.common.services.client.digitaltwin;

import java.util.*;
import java.util.function.Consumer;

import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

/**
 * Client-side digital twin, connected to the actual DT through the messages it gets from it (and
 * nothing more). It will NOT reconstruct the entire runtime-side DT and many server-side functions
 * will throw exceptions.
 *
 * <p>In the client, transactions are reconstructed and logged based on server messages and only
 * concern the activities started at the same client side. Whatever is not communicated through
 * messaging remains invisible in the client knowledge graph, but resolution of observations already
 * present in the DT will be instant and bring the observations into view.
 */
public class ClientDigitalTwin implements DigitalTwin {

  private final ContextScope scope;
  private ClientKnowledgeGraph knowledgeGraph;
  private RuntimeService runtimeClient;
  private List<Consumer<Message>> eventConsumers = new ArrayList<>();
  private long transientId = Klab.getNextId();
  private long parentTransientId = -1000;
  private int childrenCount = 0;

  private Map<Long, RemoteTransaction> transactions =
      Collections.synchronizedMap(new LinkedHashMap<>());

  /**
   * Each transaction is only for the client-initiated activity during the lifetime of the current
   * client. The transient IDs are used to organize observations (and later other assets) into a
   * temporary graph that gets dumped into the overall graph when successful. If a transaction
   * fails, it remains in the client where it can be retrieved for inspection.
   *
   * <p>Each transaction has at most two levels of inheritance between the involved assets. If a
   * child has children, its nChildrenCount will be > 1 for user-initiated expansion.
   */
  class RemoteTransaction {
    final long id;
    boolean success = false;
    boolean finalized = false;
    // used to establish if an observation belongs to the transaction
    Map<Long, RuntimeAsset> assets = new HashMap<>();
    Graph<Long, ClientKnowledgeGraph.Relationship> graph =
        new DefaultDirectedGraph<>(ClientKnowledgeGraph.Relationship.class);

    public RemoteTransaction(Observation observation) {
      this.id = observation.getTransientId();
      this.assets.put(observation.getTransientId(), observation);
      graph.addVertex(observation.getTransientId());
    }

    public void commit(RuntimeAsset asset, boolean success) {
      this.success = success;
      this.assets.put(asset.getTransientId(), asset);
      this.finalized = true;
      if (success) {
        knowledgeGraph.ingest(toGraph());
      }
    }

    private Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> toGraph() {
      Graph<RuntimeAsset, ClientKnowledgeGraph.Relationship> ret =
          new DefaultDirectedGraph<>(ClientKnowledgeGraph.Relationship.class);
      for (var asset : assets.values()) {
        ret.addVertex(asset);
      }
      for (var edge : graph.edgeSet()) {
        ret.addEdge(
            assets.get(graph.getEdgeSource(edge)),
            assets.get(graph.getEdgeTarget(edge)),
            new ClientKnowledgeGraph.Relationship(edge.relationship, edge.metadata));
      }
      return ret;
    }

    public boolean connect(Observation observation, Message.MessageType messageType) {
      if (assets.containsKey(observation.getTransientId())
          || observation.getParentTransientId() <= 0) {
        // this one has the finalized
        assets.put(observation.getTransientId(), observation);
        success = messageType == Message.MessageType.ContextualizationSuccessful;
        // TODO we can add an error notification to the transaction to explain what failed
        return true;
      } else if (assets.containsKey(observation.getParentTransientId())) {
        assets.put(observation.getTransientId(), observation);
        success = messageType == Message.MessageType.ContextualizationSuccessful;
        // TODO info notifications with time etc., although the activities are the reference
        graph.addVertex(observation.getTransientId());
        graph.addEdge(
            observation.getParentTransientId(),
            observation.getTransientId(),
            new ClientKnowledgeGraph.Relationship(GraphModel.Relationship.HAS_CHILD, Map.of()));
        return true;
      }
      return false;
    }
  }

  public ClientDigitalTwin(ContextScope scope, String id) {
    this.scope = scope;
    this.runtimeClient = scope.getService(RuntimeService.class);
    if (this.runtimeClient instanceof RuntimeClient rc) {
      this.knowledgeGraph = new ClientKnowledgeGraph(scope, rc);
      scope.onMessage((channel, message) -> ingest(message), Message.Queue.Events);
    } else {
      throw new KlabInternalErrorException("Non-client runtime class in client digital twin");
    }
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  @Override
  public int getChildrenCount() {
    return childrenCount;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  /**
   * Add a message consumer to be called after the said message has modified the knowledge graph.
   *
   * @param consumer
   */
  public void addEventConsumer(Consumer<Message> consumer) {
    this.eventConsumers.add(consumer);
  }

  /**
   * Main function that constructs the client-side KG structure. Not all elements in the remote KG
   * will be present, but those that are must be coherently linked.
   *
   * <p>From a UI perspective we can just show the root observations that get here and use queries
   * to show the graph on demand according to the level of detail chosen.
   *
   * <p>Resolved observations MUST contain their n. of children so we can show it without
   * downloading them.
   *
   * <p>Keep the failed observations with their contexts at the client side so that we can check for
   * previous failures.
   *
   * @param event
   */
  public synchronized void ingest(Message event) {

    switch (event.getMessageType()) {
      case ObservationSubmissionStarted -> {
        transactions.put(
            event.getPayload(Observation.class).getTransientId(),
            new RemoteTransaction(event.getPayload(Observation.class)));
      }
      case ObservationSubmissionAborted, ObservationSubmissionFinished -> {
        var transaction = transactions.get(event.getPayload(Observation.class).getTransientId());
        if (transaction != null) {
          boolean success =
              event.getMessageType() == Message.MessageType.ObservationSubmissionFinished;
          transaction.commit(event.getPayload(Observation.class), success);
        }
      }
      case ContextualizationAborted, ContextualizationSuccessful -> {
        for (var transaction : transactions.values()) {
          // TODO skip finalized? Events may come from outside at some point.
          if (transaction.connect(event.getPayload(Observation.class), event.getMessageType())) {
            break;
          }
        }
      }
    }

    for (var consumer : eventConsumers) {
      consumer.accept(event);
    }
  }

  @Override
  public Transaction transaction(Activity activity, ContextScope scope, Object... runtimeAssets) {
    throw new KlabIllegalStateException(
        "Digital twin transactions can only be invoked at server side");
  }

  @Override
  public ClientKnowledgeGraph getKnowledgeGraph() {
    return knowledgeGraph;
  }

  @Override
  public Provenance getProvenanceGraph(ContextScope context) {
    return null;
  }

  @Override
  public Dataflow getDataflowGraph(ContextScope context) {
    return null;
  }

  @Override
  public StorageManager getStorageManager() {
    // TODO should throw an exception I guess - images and stats should come from the KG
    return null;
  }

  @Override
  public Scheduler getScheduler() {
    // should never be called on the client, at least with the current logic. Technically it is
    // possible for this to operate in client mode.
    throw new KlabIllegalStateException("getScheduler() called on a client-side digital twin");
  }

  @Override
  public void dispose() {}

  @Override
  public long getId() {
    return 0;
  }

  @Override
  public Type classify() {
    return Type.CONTEXT;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }

  @Override
  public Configuration getOptions() {
    // TODO
    return null;
  }
}
