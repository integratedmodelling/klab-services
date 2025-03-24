package org.integratedmodelling.klab.services.scopes;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.services.base.BaseService;
import org.ojalgo.concurrent.Parallelism;

/**
 * The service-side {@link ContextScope}. Does most of the heavy lifting in the runtime service
 * through the services chosen by the session scope. Uses agents as needed. Relies on external
 * instrumentation after creation.
 *
 * <p>Maintained by the {@link ScopeManager}
 */
public class ServiceContextScope extends ServiceSessionScope implements ContextScope {

  // TODO make this configurable
  private static long MAX_CACHED_OBSERVATIONS = 100;
  private static long MAX_CACHED_GEOMETRIES = 20;

  private Observation observer;
  private Observation contextObservation;
  private URL url;
  private DigitalTwin digitalTwin;
//  private KnowledgeGraph.Operation currentOperation;

  // FIXME there's also parentScope (generic) and I'm not sure these should be duplicated
  protected ServiceContextScope parent;
  protected Map<ResolutionConstraint.Type, ResolutionConstraint> resolutionConstraints =
      new LinkedHashMap<>();
  protected Map<Observation, Geometry> currentlyObservedGeometries = new HashMap<>();

  private Map<Long, Observation> resolutionCache;
  private AtomicLong nextResolutionId;

  /**
   * The splits for parallelization of scalar computation are assigned on a first-come, first-served
   * basis but must be the same within a context. They are reassigned to undefined (-1) at each
   * "within" and established when the first model that makes an explicit choice or using configured
   * defaults.
   */
  private int splits = -1;

  LoadingCache<Long, Observation> observationCache;

  // This uses the SAME catalog, which should only be redefined when changing context or perspective
  private ServiceContextScope(ServiceContextScope parent) {
    super(parent);
    this.parent = parent;
    this.splits = parent.splits;
    this.observer = parent.observer;
    this.contextObservation = parent.contextObservation;
    this.digitalTwin = parent.digitalTwin;
    this.observationCache = parent.observationCache;
    this.resolutionConstraints.putAll(parent.resolutionConstraints);
//    this.currentOperation = parent.currentOperation;
    this.resolutionCache = parent.resolutionCache;
    this.nextResolutionId = parent.nextResolutionId;
    this.jobManager = parent.jobManager;
  }

  @Override
  ServiceContextScope copy() {
    return new ServiceContextScope(this);
  }

  // next 3 are overridden with the same code as the parent because they need to use the local maps,
  // not the
  // parent's

  @Override
  public <T extends KlabService> T getService(Class<T> serviceClass) {
    return (T) defaultServiceMap.get(KlabService.Type.classify(serviceClass));
  }

  @Override
  public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {
    for (var service : getServices(serviceClass)) {
      if (serviceId.equals(service.serviceId())) {
        return service;
      }
    }
    throw new KlabResourceAccessException(
        "cannot find service with ID=" + serviceId + " in the scope");
  }

  @Override
  public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
    return new org.integratedmodelling.klab.api.utils.Utils.Casts<KlabService, T>()
        .cast((Collection<KlabService>) serviceMap.get(KlabService.Type.classify(serviceClass)));
  }

  ServiceContextScope(ServiceSessionScope parent) {
    super(parent);
    this.observer = null;
    this.data = Parameters.create();
    this.data.putAll(parent.data);
    this.resolutionCache = new HashMap<>();
    this.jobManager = parent.jobManager;
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
                            .get(key, ServiceContextScope.this, Observation.class);
                    if (ret == null) {
                      System.out.println(
                          "CATXO null observation "
                              + key
                              + ": I am "
                              + KlabService.Type.classify(service));
                    }
                    return ret;
                  }
                });
    this.nextResolutionId = new AtomicLong(-1L);
    /*
     * TODO choose the services if this context or user requires specific ones
     */
  }

  @Override
  public Observation getObserver() {
    return this.observer;
  }

  @Override
  public boolean isConsistent() {
    return false;
  }

  @Override
  public Collection<Observation> getInconsistencies(boolean dependentOnly) {
    return List.of();
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
    if (id <= Observation.UNASSIGNED_ID) {
      var ret = resolutionCache.get(id);
      if (ret == null && !(this.service instanceof RuntimeService)) {
        var obs = digitalTwin.getKnowledgeGraph().query(Observation.class, this).id(id).peek(this);
        if (obs.isPresent()) {
          ret = (ObservationImpl) obs.get();
          resolutionCache.put(id, ret);
          return ret;
        }
      }
      return ret;
//      throw new KlabInternalErrorException("QUERY FOR UNRESOLVED OBSERVATION -- this should no longer happen");
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

  @Override
  public Collection<Observation> getRootObservations() {
    return getRootContextScope().getObservations();
  }

  @Override
  public ServiceContextScope withObserver(Observation observer) {
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.observer = observer;
    return ret;
  }

//  /**
//   * Store the current KG operation so that we can correctly record provenance and maintain graph
//   * integrity in secondary resolutions.
//   *
//   * @param operation
//   * @return
//   */
//  public ServiceContextScope withinOperation(KnowledgeGraph.Operation operation) {
//
//    if (operation == null) {
//      return this;
//    }
//
//    ServiceContextScope ret = new ServiceContextScope(this);
//    ret.currentOperation = operation;
//    return ret;
//  }
//
//  public KnowledgeGraph.Operation getCurrentOperation() {
//    return currentOperation;
//  }

  @Override
  public CompletableFuture<Observation> observe(Observation observation) {
    if (!isOperative()) {
      return null;
    }
    var runtime = getService(RuntimeService.class);
    return runtime.submit(observation, this);
  }

//  public void finalizeObservation(
//      Observation observation, KnowledgeGraph.Operation operation, boolean successful) {
//    if (successful) {
//      if (observation.getObservable().is(SemanticType.QUALITY)) {
//        var storage = digitalTwin.getStorageManager().getStorage(observation);
//        if (storage != null) {
//          for (var buf : storage.allBuffers()) {
//
//            // TODO if geometry is scalar, save state as property instead
//            operation.store(buf);
//            // The HAS_DATA link contains the offsets for the geometry, if any.
//            operation.link(
//                observation, buf, GraphModel.Relationship.HAS_DATA, "offset", buf.offset());
//          }
//        }
//      }
//    }
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
  public Observation getParentOf(Observation observation) {
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
  public Collection<Observation> getChildrenOf(Observation observation) {
    return digitalTwin
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .source(observation)
        .along(GraphModel.Relationship.HAS_CHILD)
        .run(this);
  }

  @Override
  public Collection<Observation> getOutgoingRelationshipsOf(Observation observation) {
    return digitalTwin
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .source(observation)
        .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
        .run(this);
  }

  @Override
  public Collection<Observation> getIncomingRelationshipsOf(Observation observation) {
    return digitalTwin
        .getKnowledgeGraph()
        .query(Observation.class, this)
        .target(observation)
        .along(GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)
        .run(this);
  }

  //  @Override
  //  public <T extends RuntimeAsset> List<T> query(Class<T> resultClass, Object... queryData) {
  //    return getService(RuntimeService.class).retrieveAssets(this, resultClass, queryData);
  //  }

  //  @Override
  //  public <T extends RuntimeAsset> List<T> queryKnowledgeGraph(
  //      KnowledgeGraph.Query<T> knowledgeGraphQuery) {
  //    if (knowledgeGraphQuery instanceof KnowledgeGraphQuery<T> qc) {
  //      return digitalTwin
  //          .getKnowledgeGraph()
  //          .query(knowledgeGraphQuery, (Class<T>) qc.getResultType().getAssetClass(), this);
  //    }
  //    throw new KlabUnimplementedException("Not ready to compile arbitrary KG query
  // implementations");
  //  }

  @Override
  public void runTransitions() {
    // TODO Auto-generated method stub

  }

  @Override
  public Collection<Observation> affects(Observation observation) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<Observation> affected(Observation observation) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public URL getUrl() {
    return url;
  }

  @Override
  public ContextScope connect(URL remoteContext) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Observation getContextObservation() {
    return this.contextObservation;
  }

  @Override
  public ContextScope within(Observation contextObservation) {
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.contextObservation = contextObservation;
    ret.splits = -1;
    return ret;
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
  public boolean initializeAgents(String scopeId) {
    // setting the ID here is dirty as technically this is still being set and will be set again
    // later,
    // but
    // no big deal for now. Alternative is a complicated restructuring of messages to take multiple
    // payloads.
    setId(scopeId);
    setStatus(Status.WAITING);
    KActorsBehavior.Ref contextAgent =
        parentScope.ask(
            KActorsBehavior.Ref.class,
            Message.MessageClass.ActorCommunication,
            Message.MessageType.CreateContext,
            this);
    if (contextAgent != null && !contextAgent.isEmpty()) {
      setStatus(Status.STARTED);
      setAgent(contextAgent);
      return true;
    }
    setStatus(Status.ABORTED);
    return false;
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

    // TODO when we're not in a runtime, we should not touch the digital twin (which is null) and
    //  we MUST call closeContext on all the other services we have paired with

    // TODO we also must persist the current observed geometries for all observers.

    digitalTwin.dispose();

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
  public Observation getObservation(Semantics observable) {

    if (contextObservation.getId() < 0) {
      // This situation happens during uncommitted resolution chains and would mess up the knowledge
      // graph at the runtime side.
      for (var obs : resolutionCache.values()) {
        // TODO check if this is good enough or we need an additional map
        if (obs.getObservable().equals(observable)) {
          return obs;
        }
      }
      return null;
    }

    var ret =
        digitalTwin
            .getKnowledgeGraph()
            .query(Observation.class, this)
            .source(this)
            .along(GraphModel.Relationship.HAS_CHILD)
            .where(
                "semantics", KnowledgeGraph.Query.Operator.EQUALS, observable.asConcept().getUrn())
            .run(this);
    // TODO may need to adapt units or the like if the request is an observable
    return ret.isEmpty() ? null : ret.getFirst();
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
    Observation contextobs = null;
    if (observation.getObservable().is(SemanticType.QUALITY)
        || (observation.getObservable().is(SemanticType.COUNTABLE)
            && !observation.getObservable().asConcept().isCollective())) {
      contextobs = getParentOf(observation);
    }

    if (observer != null || contextobs != null) {
      var ret = new ServiceContextScope(this);
      ret.contextObservation = contextobs;
      ret.observer = observer;
      return ret;
    }

    return this;
  }

  public void registerObservation(Observation observation) {
    if (observation instanceof ObservationImpl observation1) {
      if (observation.getId() == Observation.UNASSIGNED_ID) {
        observation1.setId(nextResolutionId.decrementAndGet());
        resolutionCache.put(observation1.getId(), observation1);
      }
      return;
    }
    throw new KlabInternalErrorException(
        "ServiceContextScope::registerObservation: unexpected observation implementation");
  }

  public void initializeResolution() {
    nextResolutionId.set(-1L);
    resolutionCache.clear();
  }

//  public Map<Long, Observation> getResolvedObservations() {
//    return resolutionCache;
//  }
}
