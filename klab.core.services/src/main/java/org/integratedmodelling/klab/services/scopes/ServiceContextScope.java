package org.integratedmodelling.klab.services.scopes;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.services.base.BaseService;
import org.ojalgo.concurrent.Parallelism;

/**
 * The service-side {@link ContextScope}. Does most of the heavy lifting in the runtime service
 * through the services chosen by the session scope. Uses agents as needed. Relies on external
 * instrumentation after creation.
 *
 * <p>Instrumented by {@link KlabService#declareContextScope(ContextScope, SessionScope,
 * UserScope)}}.
 *
 * <p>Maintained by the {@link ScopeManager}
 */
public class ServiceContextScope extends ServiceSessionScope implements ContextScope {

  // TODO make this configurable
  private static long MAX_CACHED_OBSERVATIONS = 100;
  private static long MAX_CACHED_GEOMETRIES = 20;
  private DigitalTwin.Configuration configuration;
  private final AtomicLong idGenerator;
  private final Map<String, DigitalTwin.Transaction> transactions;
  private Observation observer;
  private Observation contextObservation;
  private Observation sourceObservation;
  private Observation targetObservation;
  private URL url;
  private DigitalTwin digitalTwin;
  private Data.ShardingStrategy shardingStrategy;
  private String remoteTransactionId;

  // FIXME there's also parentScope (generic) and I'm not sure these should be duplicated
  protected ServiceContextScope parent;
  protected Map<ResolutionConstraint.Type, ResolutionConstraint> resolutionConstraints =
      new LinkedHashMap<>();
  protected Map<Observation, Geometry> currentlyObservedGeometries = new HashMap<>();

  /**
   * The splits for parallelization of scalar computation are assigned on a first-come, first-served
   * basis but must be the same within a context. They are reassigned to undefined (-1) at each
   * "within" and established when the first model that makes an explicit choice or using configured
   * defaults.
   */
  private int splits = -1;

  LoadingCache<Long, Observation> observationCache;
  private DigitalTwin.Transaction currentTransaction;

  public ServiceContextScope(ServiceContextScope parent) {
    super(parent);
    this.parent = parent;
    this.splits = parent.splits;
    this.observer = parent.observer;
    this.data = parent.data;
    this.contextObservation = parent.contextObservation;
    this.sourceObservation = parent.sourceObservation;
    this.targetObservation = parent.targetObservation;
    this.digitalTwin = parent.digitalTwin;
    this.observationCache = parent.observationCache;
    this.serviceMap.putAll(parent.serviceMap);
    this.resolutionConstraints.putAll(parent.resolutionConstraints);
    this.currentTransaction = parent.currentTransaction;
    this.configuration = parent.configuration;
    this.shardingStrategy = parent.shardingStrategy;
    this.idGenerator = parent.idGenerator;
    this.transactions = parent.transactions;
    this.remoteTransactionId = parent.remoteTransactionId;
    copyMessagingSetup(parent);
  }

  @Override
  ServiceContextScope copy() {
    return new ServiceContextScope(this);
  }

  public ServiceContextScope(
      ServiceSessionScope parent,
      DigitalTwin.Configuration configuration,
      UserIdentity userIdentity) {
    super(parent);
    // for remotes services, different user create a context using the session
    if (userIdentity != null) {
      this.setUser(userIdentity);
    }
    this.observer = null;
    this.data = Parameters.create();
    this.data.putAll(parent.data);
    this.configuration = configuration;
    this.transactions = new HashMap<>();
    this.idGenerator = new AtomicLong(Observation.UNASSIGNED_ID);
    this.setName(configuration.getName());
    // TODO use the configuration to override the sharding strategy
    this.shardingStrategy = Data.ShardingStrategy.neutral();
    this.observationCache =
        CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHED_OBSERVATIONS)
            .build(
                new CacheLoader<Long, Observation>() {
                  @Override
                  public Observation load(Long key) throws Exception {
                    var ret =
                        digitalTwin
                            .getKnowledgeGraph()
                            .getAsset(key, ServiceContextScope.this, Observation.class);
                    if (ret == null) {
                      Logging.INSTANCE.error(
                          "CATXO null observation retrieved for key "
                              + key
                              + " in service "
                              + KlabService.Type.classify(service));
                    }
                    return ret;
                  }
                });
  }

  @Override
  public void setId(String id) {
    super.setId(id);
    if (this.configuration instanceof ConfigurationImpl configurationImpl) {
      configurationImpl.setId(id);
    }
  }

  @Override
  public DigitalTwin.Transaction getCurrentTransaction() {
    return currentTransaction;
  }

  @Override
  public Observation getObserver() {
    return this.observer;
  }

  @Override
  public <T extends Observation> Collection<T> getPerspectives(Observable observable) {
    return List.of();
  }

  /**
   * Retrieve the observation with the passed ID straight from the digital twin. This is non-API and
   * is the fastest way. The knowledge graph should in turn cache scales, so that no geometries are
   * created unnecessarily.
   *
   * <p>TODO check if the caching logic should be entirely within the knowledge graph (probably).
   *
   * @param id
   * @return
   */
  public Observation getObservation(long id) {

    if (id == Observation.UNASSIGNED_ID) {
      return null;
    }

    if (currentTransaction != null) {
      // look first in the currentTransaction graph if there is a transaction
      for (var obs :
          currentTransaction.assets().stream().filter(o -> o instanceof Observation).toList()) {
        if (obs.getId() == id) {
          return (Observation) obs;
        }
      }
    }

    // at this point if it's unresolved we can't find it in the DT
    if (id <= Observation.UNASSIGNED_ID) {
      return null;
    }

    try {
      return observationCache.get(id);
    } catch (ExecutionException e) {
      throw new KlabInternalErrorException(e);
    }
  }

  @Override
  public Observation getObserverOf(Observation observation) {
    var ret =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .target(observation)
            .along(GraphModel.Relationship.HAS_OBSERVER)
            .run(this);
    return ret.isEmpty() ? null : ret.getFirst();
  }

  //  @Override
  public Collection<Observation> getRootObservations() {
    return getRootContextScope().getObservations();
  }

  @Override
  public ServiceContextScope withObserver(Observation observer) {
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.observer = observer;
    return ret;
  }

  public ServiceContextScope withIdentity(Identity identity) {
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.setIdentity(identity);
    if (identity instanceof UserIdentity userIdentity) {
      ret.setUser(userIdentity);
    }
    return ret;
  }

  //  @Override
  //  public CompletableFuture<Observation> submit(Observation observation) {
  //    if (!isOperative()) {
  //      return null;
  //    }
  //    var runtime = getService(RuntimeService.class);
  //    return runtime.submit(observation, this);
  //  }

  @Override
  public Provenance getProvenance() {
    return digitalTwin.getProvenanceGraph(this);
  }

  @Override
  public Report getReport() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Dataflow getDataflow() {
    return digitalTwin.getDataflowGraph(this);
  }

  @Override
  public ContextScope getRootContextScope() {
    var ret = this;
    while (ret.parent != null) {
      ret = ret.parent;
    }
    return ret;
  }

  @Override
  public RuntimeAsset getParentOf(RuntimeAsset observation) {
    // FIXME reimplement using KG get() and links()
    var ret =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .target(observation)
            .along(GraphModel.Relationship.HAS_CHILD)
            .run(this);
    return ret.isEmpty() ? null : ret.getFirst();
  }

  @Override
  public Collection<RuntimeAsset> getChildrenOf(RuntimeAsset observation) {

    // FIXME reimplement using KG get() and links()
    var ret = new ArrayList<RuntimeAsset>();

    if (currentTransaction != null && currentTransaction.assets().contains(observation)) {
      ret.addAll(
          currentTransaction.outgoing(observation).stream()
              .filter(edge -> edge.type() == GraphModel.Relationship.HAS_CHILD)
              .map(KnowledgeGraph.Link::target)
              .toList());
    }

    if (observation.getId() > 0) {
      ret.addAll(
          digitalTwin
              .getKnowledgeGraph()
              .query(RuntimeAsset.class, this)
              .source(observation)
              .along(GraphModel.Relationship.HAS_CHILD)
              .run(this));
    }

    return ret;
  }

  @Override
  public Collection<RuntimeAsset> getOutgoingRelationshipsOf(RuntimeAsset observation) {
    var ret = new ArrayList<RuntimeAsset>();
    if (currentTransaction != null && currentTransaction.assets().contains(observation)) {
      ret.addAll(
          currentTransaction.outgoing(observation).stream()
              .filter(edge -> edge.type() == GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
              .map(KnowledgeGraph.Link::target)
              .toList());
    }
    if (observation.getId() > 0) {
      ret.addAll(
          digitalTwin
              .getKnowledgeGraph()
              .query(RuntimeAsset.class, this)
              .source(observation)
              .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
              .run(this));
    }
    return ret;
  }

  @Override
  public Collection<RuntimeAsset> getIncomingRelationshipsOf(RuntimeAsset observation) {
    var ret = new ArrayList<RuntimeAsset>();
    if (currentTransaction != null && currentTransaction.assets().contains(observation)) {
      ret.addAll(
          currentTransaction.incoming(observation).stream()
              .filter(edge -> edge.type() == GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
              .map(KnowledgeGraph.Link::target)
              .toList());
    }
    if (observation.getId() > 0) {
      ret.addAll(
          digitalTwin
              .getKnowledgeGraph()
              .query(RuntimeAsset.class, this)
              .target(observation)
              .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
              .run(this));
    }
    return ret;
  }

  //  @Override
  //  public Collection<Observation> affecting(Observation observation) {
  //
  //    var ret = new ArrayList<Observation>();
  //    if (currentTransaction != null && currentTransaction.assets().contains(observation)) {
  //      ret.addAll(
  //          currentTransaction.incoming(observation).stream()
  //              .filter(
  //                  edge ->
  //                      edge.type() == GraphModel.Relationship.AFFECTS
  //                          && edge.source() instanceof Observation)
  //              .map(edge -> (Observation) edge.source())
  //              .toList());
  //    }
  //    if (observation.getId() > 0) {
  //      ret.addAll(
  //          digitalTwin
  //              .getKnowledgeGraph()
  //              .query(Observation.class, this)
  //              .target(observation)
  //              .along(GraphModel.Relationship.AFFECTS)
  //              .run(this));
  //    }
  //
  //    return ret;
  //  }
  //
  //  @Override
  //  public Collection<Observation> affected(Observation observation) {
  //    var ret = new ArrayList<Observation>();
  //    if (currentTransaction != null && currentTransaction.assets().contains(observation)) {
  //      ret.addAll(
  //          currentTransaction.outgoing(observation).stream()
  //              .filter(
  //                  edge ->
  //                      edge.type() == GraphModel.Relationship.AFFECTS
  //                          && edge.target() instanceof Observation)
  //              .map(edge -> (Observation) edge.target())
  //              .toList());
  //    }
  //
  //    if (observation.getId() > 0) {
  //      ret.addAll(
  //          digitalTwin
  //              .getKnowledgeGraph()
  //              .query(Observation.class, this)
  //              .source(observation)
  //              .along(GraphModel.Relationship.AFFECTS)
  //              .run(this));
  //    }
  //
  //    return ret;
  //  }

  @Override
  public URL getUrl() {
    return url;
  }

  @Override
  public ContextScope connect(ContextScope remoteContext) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Observation getContextObservation() {
    return this.contextObservation;
  }

  @Override
  public Observation getSourceObservation() {
    return sourceObservation;
  }

  @Override
  public Observation getTargetObservation() {
    return targetObservation;
  }

  @Override
  public ServiceContextScope within(Observation contextObservation) {
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.contextObservation = contextObservation;
    ret.splits = -1;
    return ret;
  }

  /**
   * Return a child scope executing the passed activity. If the original scope does not have an
   * active DT transaction, one is created. Otherwise, the active transaction is specialized for the
   * activity, and the activity will be linked to the previously executing one. In all cases {@link
   * #getCurrentTransaction()} will never return null when called on the resulting scope.
   *
   * @param currentActivity
   * @param runtimeAssets
   * @return
   */
  public ServiceContextScope executing(Activity currentActivity, Object... runtimeAssets) {

    ServiceContextScope ret = new ServiceContextScope(this);
    var parentActivity = this.getActivity();
    var assets = new ArrayList<>();
    assets.add(parentActivity == null ? RuntimeAsset.PROVENANCE_ASSET : parentActivity);
    if (runtimeAssets != null) {
      assets.addAll(Arrays.asList(runtimeAssets));
    }
    ret.currentTransaction =
        currentTransaction == null
            ? getDigitalTwin().transaction(currentActivity, this, assets.toArray())
            : currentTransaction.getChild(currentActivity, ret, assets.toArray());

    send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityStarted, currentActivity);

    return ret;
  }

  /**
   * @param observation
   * @return
   */
  public ServiceContextScope contextualizeFor(Observation observation) {
    if (contextObservation != null && observation.getObservable().is(SemanticType.COUNTABLE)) {
      return within(null);
    }
    return this;
  }

  @Override
  public ContextScope between(Observation source, Observation target) {
    return null;
  }

  @Override
  public ServiceContextScope withResolutionConstraints(
      ResolutionConstraint... resolutionConstraints) {
    ServiceContextScope ret = new ServiceContextScope(this);
    if (resolutionConstraints == null) {
      ret.resolutionConstraints.clear();
    } else {
      for (var constraint : resolutionConstraints) {
        if (constraint == null || constraint.empty()) {
          continue;
        }
        if (constraint.getType() == ResolutionConstraint.Type.UnresolvedContextObservation) {
          ret.contextObservation = constraint.payload(Observation.class).getFirst();
        } else if (constraint.getType().incremental
            && ret.resolutionConstraints.containsKey(constraint.getType())) {
          ret.resolutionConstraints.put(
              constraint.getType(),
              ret.resolutionConstraints.get(constraint.getType()).merge(constraint));
        } else {
          ret.resolutionConstraints.put(constraint.getType(), constraint);
        }
      }
    }
    return ret;
  }

  /**
   * Return the number of split segments in scalar computation of qualities, assigning them to the
   * passed suggested value if they are still undefined.
   *
   * @param suggestedSplits
   * @return
   */
  public int getSplits(int suggestedSplits) {
    if (this.splits < 0) {
      this.splits = suggestedSplits;
    }
    return this.splits;
  }

  @Override
  public List<ResolutionConstraint> getResolutionConstraints() {
    return Utils.Collections.promoteToList(this.resolutionConstraints.values());
  }

  @Override
  public Provenance getProvenanceOf(Observation observation) {
    // TODO
    return null;
  }

  @Override
  public <T> T getConstraint(ResolutionConstraint.Type type, T defaultValue) {
    var constraint = resolutionConstraints.get(type);
    if (constraint == null || constraint.size() == 0) {
      return defaultValue;
    }
    return (T) constraint.payload(defaultValue.getClass()).getFirst();
  }

  @Override
  public <T> T getConstraint(ResolutionConstraint.Type type, Class<T> resultClass) {
    var constraint = resolutionConstraints.get(type);
    if (constraint == null || constraint.size() == 0) {
      return null;
    }
    return (T) constraint.payload(resultClass).getFirst();
  }

  @Override
  public <T> List<T> getConstraints(ResolutionConstraint.Type type, Class<T> resultClass) {
    var constraint = resolutionConstraints.get(type);
    if (constraint == null || constraint.size() == 0) {
      return List.of();
    }
    return constraint.payload(resultClass);
  }

  @Override
  public DigitalTwin.Configuration getConfiguration() {
    return configuration;
  }

  @Override
  public String getTransactionId() {
    return currentTransaction == null ? remoteTransactionId : currentTransaction.getId();
  }

  public void setRemoteTransactionId(String remoteTransactionId) {
    this.remoteTransactionId = remoteTransactionId;
  }

  @Override
  public boolean initializeAgents(String scopeId) {
    // setting the ID here is dirty as technically this is still being set and will be set again
    // later,
    // but
    // no big deal for now. Alternative is a complicated restructuring of messages to take multiple
    // payloads.
    //    setId(scopeId);
    //    setStatus(Status.WAITING);
    //    KActorsBehavior.Ref contextAgent =
    //        parentScope.ask(
    //            KActorsBehavior.Ref.class,
    //            Message.MessageClass.ActorCommunication,
    //            Message.MessageType.CreateContext,
    //            this);
    //    if (contextAgent != null && !contextAgent.isEmpty()) {
    //      setStatus(Status.STARTED);
    //      setAgent(contextAgent);
    //      return true;
    //    }
    //    setStatus(Status.ABORTED);
    //    return false;
    return true;
  }

  @Override
  public DigitalTwin getDigitalTwin() {
    return digitalTwin;
  }

  public void setDigitalTwin(DigitalTwin digitalTwin) {
    this.digitalTwin = digitalTwin;
  }

  @Override
  public void close() {

    send(Message.MessageType.ContextClosed, Message.MessageClass.DigitalTwin, this.configuration);

    // TODO when we're not in a runtime, we should not touch the digital twin (which is null) and
    //  we MUST call closeContext on all the other services we have paired with

    // TODO we also must persist the current observed geometries for all observers.

    digitalTwin.dispose();

    // TODO if the DT has no more owners, also send a DigitalTwinDeleted message before continuing.

    // Call close() on all closeables in our dataset, including AutoCloseable if any.
    for (String key : getData().keySet()) {
      Object object = getData().get(key);
      if (object instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    closeMessaging();

    var runtime = getService(RuntimeService.class);
    if (runtime instanceof BaseService baseService) {
      baseService.getScopeManager().releaseScope(this.getId());
    }
  }

  /**
   * Return all the observations visible in this context, ordered by submission timestamp.
   *
   * @return
   */
  @Override
  public List<Observation> getObservations() {
    return digitalTwin
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .source(this)
        .along(GraphModel.Relationship.HAS_CHILD)
        .run(this);
  }

  @Override
  public Observation getObservation(Observation observation) {

    //    var instanceUrn = observation.getMetadata().get(Metadata.IM_FEATURE_URN);

    // look first in the currentTransaction graph if there is a transaction
    if (currentTransaction != null) {
      for (var obs :
          currentTransaction
              .outgoing(
                  contextObservation == null ? RuntimeAsset.CONTEXT_ASSET : contextObservation)
              .stream()
              .filter(link -> link.type() == GraphModel.Relationship.HAS_CHILD)
              .toList()) {
        // TODO for substantials, this should be in the context of a collective, and the collective
        //  should provide the identification strategy
        if (obs.target() instanceof Observation o
            && (isSameInstance(observation, o)
                || (SemanticType.isDependent(observation.getObservable().getSemantics().getType())
                    && observation
                        .getObservable()
                        .getSemantics()
                        .getUrn()
                        .equals(observation.getObservable().getSemantics().getUrn())))) {
          return o;
        }
      }
    }

    //    if (observation.getId() > 0) {

    // TODO for substantials, this should always be in the context of a collective, and the
    //  collective should provide the identification strategy

    var query =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .source(contextObservation == null ? this : contextObservation)
            .along(GraphModel.Relationship.HAS_CHILD);

    if (!observation.getObservable().getSemantics().isCollective()
        && SemanticType.isSubstantial(observation.getObservable().getSemantics().getType())) {
      query =
          query.where(
              "urn",
              KnowledgeGraph.Query.Operator.EQUALS,
              getId()
                  + ":"
                  + ObservationImpl.INDIVIDUALS_CATALOG_NAME
                  + ":"
                  + observation.getUrn());
    } else {
      query =
          query.where(
              "semantics",
              KnowledgeGraph.Query.Operator.EQUALS,
              observation.getObservable().asConcept().getUrn());
    }

    var ret = query.run(this);
    return ret.isEmpty() ? null : ret.getFirst();
  }

  private boolean isSameInstance(Observation observation, Observation o) {
    return !observation.getObservable().getSemantics().isCollective()
        && SemanticType.isSubstantial(observation.getObservable().getSemantics().getType())
        && observation
            .getUrn()
            .equals(getId() + ":" + ObservationImpl.INDIVIDUALS_CATALOG_NAME + ":" + o.getUrn());
  }

  @Override
  public Data getData(Observation... observations) {
    // TODO
    return null;
  }

  public Parallelism getParallelism() {
    // TODO
    return Parallelism.CORES;
  }

  /**
   * Return a scope with the context observation and the observer set according to the same
   * pertaining to the passed observation. Used when the scheduler needs to independently work on an
   * observation due to an event, outside of the scope that has contextualized it.
   *
   * @param observation
   * @return
   */
  public ServiceContextScope of(Observation observation) {

    var observer = getObserverOf(observation);
    Observation contextObs = null;
    if (observation.getObservable().is(SemanticType.QUALITY)
        || (observation.getObservable().is(SemanticType.COUNTABLE)
            && !observation.getObservable().asConcept().isCollective())) {
      var parent = getParentOf(observation);
      contextObs = parent instanceof Observation ? (Observation) parent : null;
    }

    if (observer != null || contextObs != null) {
      var ret = new ServiceContextScope(this);
      ret.contextObservation = contextObs;
      ret.observer = observer;
      return ret;
    }

    return this;
  }

  @Override
  public Data.ShardingStrategy getShardingStrategy(Observation observation) {
    return shardingStrategy;
  }

  public long commit() {
    if (getActivity() instanceof ActivityImpl activity) {
      activity.setOutcome(Activity.Outcome.SUCCESS);
      activity.setName(activity.getType().name().substring(0, 3) + " OK");
      if (getActivity().getType() == Activity.Type.RESOLUTION
          && getActivity().getOutcome() == Activity.Outcome.SUCCESS) {
        // add the resolved graph as metadata to the activity instead
        getActivity()
            .getMetadata()
            .put(Metadata.IM_RESOLUTION_GRAPH, getCurrentTransaction().getGraph());
      }
    }

    if (this.currentTransaction == null) {
      return -1;
    }

    var ret = this.currentTransaction.commit();
    send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, getActivity());
    return ret;
  }

  public GraphModel.KnowledgeGraph getResolvedGraph() {
    return this.currentTransaction.getGraph();
  }

  public Activity getActivity() {
    return currentTransaction == null ? null : currentTransaction.getActivity();
  }

  public void contextualize(Observation observation) {
    this.digitalTwin.getScheduler().submit(observation, this);
  }

  public void fail(Throwable t) {
    this.currentTransaction.fail(t);
    if (getActivity() instanceof ActivityImpl activity) {
      activity.setOutcome(Activity.Outcome.INTERNAL_FAILURE);
      activity.setName(activity.getType().name().substring(0, 3) + " EXCEPTION");
    }
    send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, getActivity());
  }

  public void fail(Object... details) {
    Throwable throwable = null;
    if (getActivity() instanceof ActivityImpl activity) {
      activity.setOutcome(Activity.Outcome.FAILURE);
      activity.setName(activity.getType().name().substring(0, 3) + " FAIL");
      for (var detail : details) {
        if (detail instanceof Notification notification
            && (notification.getLevel() == Notification.Level.Error
                || notification.getLevel() == Notification.Level.Error)) {
          activity.setDescription(notification.getMessage());
        } else if (detail instanceof Throwable t) {
          throwable = t;
        }
      }
    }
    this.currentTransaction.fail(throwable);
    send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, getActivity());
  }

  /** Reinitialize a context scope after a timeout if so configured. */
  public void reinitialize() {
    // TODO zap the KG and all caches; leave a trace somewhere for provenance.
  }

  @Override
  public Observation.Builder observation(Observable observable) {

    return new ObservationBuilderImpl(observable, this) {
      @Override
      public CompletableFuture<Observation> submit() {
        var observation = build();
        // save a call
        if (observation.getId() > 0 || observation.isEmpty()) {
          return CompletableFuture.completedFuture(observation);
        }
        return getService(RuntimeService.class).submit(observation, ServiceContextScope.this);
      }

      @Override
      public Observation register() {
        return getService(RuntimeService.class).register(build(), ServiceContextScope.this);
      }
    };
  }

  /**
   * Get a unique ID for a new observation to be registered in this scope before being submitted for
   * resolution.
   *
   * @return
   */
  public long getNextObservationId() {
    return idGenerator.decrementAndGet();
  }

  public void registerTransaction(DigitalTwin.Transaction transaction) {
    transactions.put(transaction.getId(), transaction);
  }

  public void unregisterTransaction(DigitalTwin.Transaction transaction) {
    transactions.remove(transaction.getId());
  }

  public DigitalTwin.Transaction getTransaction(String key) {
    return transactions.get(key);
  }

  /**
   * ONLY to be used when reconstructing scopes from remote requests. The transaction is there to
   * locate observations that haven't been committed yet. NO operations should be performed on this
   * scope.
   *
   * @param transaction
   * @return
   */
  public ServiceContextScope withTransaction(DigitalTwin.Transaction transaction) {
    var ret = new ServiceContextScope(this);
    ret.currentTransaction = transaction;
    return ret;
  }
}
