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
  private long parentId = -1000;

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
   * Main function that constructs the client-side KG structure after a successful submission. Not
   * all elements in the remote KG will be present, but those that are must be coherently linked.
   * The graph is kept in sync for what pertains to the known observations and those implied or
   * connected to them, but seeing the entire graph requires an explicit action that triggers a new
   * server query. If all submission messages are received, federated graphs should remain complete.
   *
   * @param event
   */
  public synchronized void ingest(Message event) {

    // only the finished submission events are relevant for now.
    switch (event.getMessageType()) {
      case ObservationSubmissionFinished ->
          getKnowledgeGraph().ingest(event.getPayload(Observation.class));
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

  @Override
  public long getParentId() {
    return parentId;
  }
}
