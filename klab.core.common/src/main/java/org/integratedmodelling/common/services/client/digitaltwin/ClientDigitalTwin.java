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
import org.integratedmodelling.common.utils.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

/**
 * Client-side digital twin, synchronized through live messages and durable transition history.
 * It will NOT reconstruct the entire runtime-side DT and many server-side functions
 * will throw exceptions.
 *
 * <p>In the client, transactions are reconstructed and logged based on server messages and only
 * concern the activities started at the same client side. Whatever is not communicated through
 * messaging remains invisible in the client knowledge graph, but resolution of observations already
 * present in the DT will be instant and bring the observations into view.
 */
public class ClientDigitalTwin implements DigitalTwin {

  private final ContextScope scope;
  private final String contextId;
  private final String messageListenerId;
  private volatile long nextRecoveryAttempt;
  private int recoveryFailures;
  private String lastRecoveryFailure;
  private final java.util.concurrent.atomic.AtomicBoolean recovering = new java.util.concurrent.atomic.AtomicBoolean();
  private ClientKnowledgeGraph knowledgeGraph;
  private RuntimeService runtimeClient;
  private List<Consumer<Message>> eventConsumers = new ArrayList<>();
  private long transientId = Klab.getNextId();
  private long parentTransientId = -1000;
  private int childrenCount = 0;
  private long parentId = -1000;
  private final TransitionHistory transitionHistory;
  private final java.util.concurrent.ScheduledExecutorService recovery =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        var thread=new Thread(r,"digital-twin-history");thread.setDaemon(true);return thread;
      });

  public ClientDigitalTwin(ContextScope scope, String id) {
    this(scope,id,true);
  }

  ClientDigitalTwin(ContextScope scope, String id, boolean recover) {
    this.scope = scope;
    this.contextId = id;
    this.transitionHistory = new TransitionHistory(id);
    this.runtimeClient = scope.getService(RuntimeService.class);
    if (this.runtimeClient instanceof RuntimeClient rc) {
      this.knowledgeGraph = new ClientKnowledgeGraph(scope, rc);
      messageListenerId=scope.onMessage((channel, message) -> ingest(message), Message.Queue.Events);
      if(recover) recovery.scheduleWithFixedDelay(() -> {
        if(nextRecoveryAttempt==0 || System.nanoTime()-nextRecoveryAttempt>=0) recoverTransitions();
      },1,10,java.util.concurrent.TimeUnit.SECONDS);
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
  public synchronized void addEventConsumer(Consumer<Message> consumer) {
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
    if(recovery.isShutdown()) return;

    // only the finished submission events are relevant for now.
    switch (event.getMessageType()) {
      case ContextClosed, DigitalTwinDeleted -> dispose();
      case ActivityFinished -> {
        var activity=event.getPayload(Activity.class);
        var encoded=activity.getMetadata().get(org.integratedmodelling.klab.api.digitaltwin.TransitionCommit.METADATA_KEY);
        if(encoded!=null && activity.getOutcome()==Activity.Outcome.SUCCESS) {
          var transition=Utils.Json.parseObject(encoded.toString(),org.integratedmodelling.klab.api.digitaltwin.TransitionCommit.class);
          if(transitionHistory.containsCommit(transition.commit().getId())) return;
          knowledgeGraph.ingestTransition(transition);
          // Do not acknowledge UI consumption until changed quality snapshots are fetchable.
          // Graph application is independently idempotent, so recovery retries failed fetches.
          for(var delta:transition.qualities())
            if(knowledgeGraph.getAsset(delta.observationId(),scope,Observation.class)==null)
              throw new IllegalStateException("Committed quality is temporarily unavailable: "+delta.observationId());
          transitionHistory.accept(transition);
        }
      }
      case ObserverResolved, ObserverGeometryChanged ->
          getKnowledgeGraph().ingestObserver(event.getPayload(Observation.class));
      case ObservationSubmissionFinished ->
          getKnowledgeGraph().ingest(event.getPayload(Observation.class));
    }

    for (var consumer : List.copyOf(eventConsumers)) {
      try { consumer.accept(event); }
      catch(RuntimeException failure) { scope.warn("Digital twin viewer failed",failure); }
    }
  }

  public TransitionHistory getTransitionHistory() { return transitionHistory; }
  public String getContextId() { return contextId; }
  public boolean isDisposed() { return recovery.isShutdown(); }

  /** Repeat a complete paged scan: late commits may have IDs reserved before a newer commit. */
  public void recoverTransitions() {
    if(recovery.isShutdown() || !recovering.compareAndSet(false,true)) return;
    try {
      for(long offset=0;!recovery.isShutdown();offset+=256) {
        var activities=knowledgeGraph.query(Activity.class,scope)
            .order(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.Order.ascending(GraphModel.Fields.ID))
            .offset(offset).limit(256).run(scope);
        if(activities==null) throw new IllegalStateException("Transition history is unavailable");
        for(var activity:activities) if(activity.getMetadata().containsKey(
            org.integratedmodelling.klab.api.digitaltwin.TransitionCommit.METADATA_KEY))
          ingest(Message.create(scope,Message.MessageClass.DigitalTwin,Message.MessageType.ActivityFinished,activity));
        if(activities.size()<256) break;
      }
      recoveryFailures=0;lastRecoveryFailure=null;nextRecoveryAttempt=0;
    } catch(Exception failure) {
      if(!recovery.isShutdown()) {
        recoveryFailures=Math.min(6,recoveryFailures+1);
        long delaySeconds=Math.min(300,10L << (recoveryFailures-1));
        nextRecoveryAttempt=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(delaySeconds);
        var signature=failure.getClass().getName()+":"+failure.getMessage();
        if(!signature.equals(lastRecoveryFailure)) {
          scope.warn("Cannot recover temporal history for "+contextId+"; retrying with backoff",failure);
          lastRecoveryFailure=signature;
        }
      }
    } finally { recovering.set(false); }
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
  public synchronized void dispose() {
    if(recovery.isShutdown()) return;
    recovery.shutdownNow();
    if(messageListenerId!=null) scope.unregisterMessageListener(messageListenerId);
  }

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
  public boolean isClient() {
    return true;
  }

  @Override
  public long getParentId() {
    return parentId;
  }
}
