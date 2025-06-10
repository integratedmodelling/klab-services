package org.integratedmodelling.common.services.client.digitaltwin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
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

/**
 * Client-side digital twin, connected to the actual DT through the messages it gets from it (and
 * nothing more for now). Will NOT reconstruct the entire runtime-side DT and many functions will
 * throw exxeptions at the beginning. Later we may implement a lazy request-driven strategy to
 * obtain the rest, but requesting entire knowledge graphs should not be expected to work at client
 * side for now. Same for state storage if it needs to be used with any level of efficiency - the
 * implementation should be limited to statistical info.
 */
public class ClientDigitalTwin implements DigitalTwin {

  private final ContextScope scope;
  private ClientKnowledgeGraph knowledgeGraph;
  private RuntimeService runtimeClient;
  private List<Consumer<Message>> eventConsumers = new ArrayList<>();
  private long transientId = Klab.getNextId();

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
  public void ingest(Message event) {
    /*
    We load contextualization info in each observation's metadata
     */
    switch (event.getMessageType()) {
      case KnowledgeGraphCommitted ->
          knowledgeGraph.ingest(event.getPayload(GraphModel.KnowledgeGraph.class));
      case ContextualizationStarted, ContextualizationAborted, ContextualizationSuccessful ->
          knowledgeGraph.update(event.getPayload(Observation.class), event.getMessageType());
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
  public KnowledgeGraph getKnowledgeGraph() {
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
  public boolean ingest(Data data, Observation target, Scheduler.Event event, ContextScope scope) {
    // should never be called on the client, at least with the current logic. Technically it is
    // possible for this to operate in client mode.
    throw new KlabIllegalStateException("ingest() called on a client-side digital twin");
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
  public Configuration getOptions() {
    // TODO
    return null;
  }
}
