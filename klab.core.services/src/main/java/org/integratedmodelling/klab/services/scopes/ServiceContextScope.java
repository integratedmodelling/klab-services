package org.integratedmodelling.klab.services.scopes;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Cache;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
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
  private final Map<String, Map<Long, Observation>> provisionalObservations;
  private Observation observer;
  private long observerRevision = -1;
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

  Cache<Long, Observation> observationCache;
  private long observationCacheRevision;
  private DigitalTwin.Transaction currentTransaction;
  private java.util.function.BooleanSupplier requestCancelled = () -> false;
  private org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause consequenceCause;
  private java.util.concurrent.atomic.AtomicReference<String> consequenceFailure =
      new java.util.concurrent.atomic.AtomicReference<>();
  private volatile boolean consequenceErrors;
  private java.util.concurrent.atomic.AtomicReference<String> consequenceWarning =
      new java.util.concurrent.atomic.AtomicReference<>();

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
    this.provisionalObservations = parent.provisionalObservations;
    this.remoteTransactionId = parent.remoteTransactionId;
    this.requestCancelled = parent.requestCancelled;
    this.consequenceCause = parent.consequenceCause;
    this.consequenceFailure = parent.consequenceFailure;
    this.consequenceErrors = parent.consequenceErrors;
    this.consequenceWarning = parent.consequenceWarning;
    copyMessagingSetup(parent);
  }

  /** A background consequence keeps connection semantics, but owns a fresh transaction/cancellation. */
  public ServiceContextScope forConsequence(
      org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause cause) {
    if (cause != null && !Objects.equals(getId(), cause.contextId()))
      throw new IllegalArgumentException("Consequence belongs to another digital twin");
    var ret = new ServiceContextScope(this);
    ret.currentTransaction = null;
    ret.remoteTransactionId = null;
    ret.requestCancelled = () -> false;
    ret.consequenceCause = cause;
    ret.consequenceFailure = new java.util.concurrent.atomic.AtomicReference<>();
    ret.consequenceErrors = false;
    ret.consequenceWarning = new java.util.concurrent.atomic.AtomicReference<>();
    return ret;
  }

  @Override
  public org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause getConsequenceCause() {
    return consequenceCause;
  }

  /** Failure details are reported once by the engine's decision notification, not as scope errors. */
  public String getConsequenceFailure() { return consequenceFailure.get(); }
  public String getConsequenceWarning() { return consequenceWarning.get(); }

  @Override
  public void warn(Object... details) {
    if (consequenceCause == null) { super.warn(details); return; }
    consequenceWarning.compareAndSet(null, Notification.warning(details).getMessage());
  }

  @Override
  public void error(Object... details) {
    if (consequenceCause == null) { super.error(details); return; }
    consequenceErrors = true;
    consequenceFailure.compareAndSet(null, Notification.error(details).getMessage());
  }

  @Override
  public boolean hasErrors() {
    return consequenceCause == null ? super.hasErrors() : consequenceErrors;
  }

  @Override
  public Message send(Object... arguments) {
    if (consequenceCause != null) {
      var message = Message.create(this, arguments);
      if (message.getQueue() == Message.Queue.Errors) {
        error(message.getPayload(Notification.class));
        return message;
      }
      if (message.getQueue() == Message.Queue.Warnings) {
        warn(message.getPayload(Notification.class));
        return message;
      }
    }
    return super.send(arguments);
  }

  /** Derive a request-local cancellation signal, inherited by its execution scopes only. */
  public ServiceContextScope withCancellation(java.util.function.BooleanSupplier cancelled) {
    Objects.requireNonNull(cancelled);
    var ret = new ServiceContextScope(this);
    var inherited = requestCancelled;
    ret.requestCancelled = () -> inherited.getAsBoolean() || cancelled.getAsBoolean();
    return ret;
  }

  @Override
  public boolean isInterrupted() {
    return super.isInterrupted() || requestCancelled.getAsBoolean();
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
    this.transactions = new ConcurrentHashMap<>();
    this.provisionalObservations = new ConcurrentHashMap<>();
    this.idGenerator = new AtomicLong(Observation.UNASSIGNED_ID);
    this.setName(configuration.getName());
    // TODO use the configuration to override the sharding strategy
    this.shardingStrategy = Data.ShardingStrategy.neutral();
    this.observationCache = CacheBuilder.newBuilder().maximumSize(MAX_CACHED_OBSERVATIONS).build();
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
    if (observer != null
        && observer.getId() > 0
        && digitalTwin != null
        && currentTransaction == null) {
      long revision = digitalTwin.getKnowledgeGraph().getSemanticRevision();
      if (revision != observerRevision) {
        var current = selectedObserver(GraphModel.Fields.ID, observer.getId());
        if (current == null || current.getObservable() == null
            || !current.getObservable().is(SemanticType.AGENT))
          throw new IllegalStateException("Selected observer is no longer an accessible Agent");
        observer = current;
        observerRevision = revision;
      }
    }
    return this.observer;
  }

  /** A connection configuration is private to its user; the registered twin scope stays neutral. */
  public ServiceContextScope forObserverConnection(Observation observer) {
    var ret = withObserver(observer);
    ret.configuration =
        Utils.Json.parseObject(Utils.Json.asString(configuration), ConfigurationImpl.class);
    ((ConfigurationImpl) ret.configuration).setObserver(Observation.forTransport(observer));
    return ret;
  }

  @Override
  public <T extends Observation> Collection<T> getPerspectives(Observable observable) {
    return org.integratedmodelling.common.runtime.ObservationPerspectives.find(this, observable);
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
  @Override
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
      var provisional = provisionalObservations.get(rootTransactionId(currentTransaction));
      if (provisional != null && provisional.containsKey(id)) {
        return provisional.get(id);
      }
    }

    // at this point if it's unresolved we can't find it in the DT
    if (id <= Observation.UNASSIGNED_ID) {
      return null;
    }

    try {
      var revision = digitalTwin.getKnowledgeGraph().getSemanticRevision();
      if (revision != observationCacheRevision) {
        observationCache.invalidateAll();
        observationCacheRevision = revision;
      }
      return observationCache.get(id, () -> loadObservation(id));
    } catch (ExecutionException e) {
      throw new KlabInternalErrorException(e);
    }
  }

  /** Drop the shared cached representation only after a successful durable update. */
  public void invalidateObservation(long id) {
    observationCache.invalidate(id);
  }

  private Observation loadObservation(long id) {
    if (digitalTwin == null) {
      throw new KlabIllegalStateException(
          "Cannot retrieve observation " + id + " before the context digital twin is initialized");
    }
    var ret = digitalTwin.getKnowledgeGraph().getAsset(id, this, Observation.class);
    if (ret == null) {
      Logging.INSTANCE.error(
          "CATXO null observation retrieved for key "
              + id
              + " in service "
              + KlabService.Type.classify(service));
    }
    return ret;
  }

  @Override
  public Observation getObserverOf(Observation observation) {
    return org.integratedmodelling.common.runtime.ObserverProvenance.recover(this,observation).observer();
  }

  //  @Override
  public Collection<Observation> getRootObservations() {
    return getRootContextScope().getObservations();
  }

  @Override
  public ServiceContextScope withObserver(Observation observer) {
    if (observer != null
        && (observer.getId() <= 0
            || observer.getObservable() == null
            || !observer.getObservable().is(SemanticType.AGENT))) {
      throw new IllegalArgumentException("An observer must be a persisted agent");
    }
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.observer = observer;
    ret.resolutionConstraints.remove(ResolutionConstraint.Type.Observer);
    ret.resolutionConstraints.remove(ResolutionConstraint.Type.ObserverUrn);
    if (observer != null)
      ret.resolutionConstraints.put(
          ResolutionConstraint.Type.Observer,
          ResolutionConstraint.of(ResolutionConstraint.Type.Observer, observer.getId()));
    return ret;
  }

  /** Select only an observation visible through this knowledge graph's owning context. */
  private Observation selectedObserver(String field, Object value) {
    if (digitalTwin == null)
      throw new IllegalArgumentException("Observer selection requires a knowledge graph");
    // Selecting or refreshing an observer must not depend on that observer. Remote graph
    // queries encode their scope through getObserver(), which would re-enter this lookup.
    // Keep the caller's identity and context focus, but remove the observer selection.
    var lookupScope = withObserver(null);
    var matches = digitalTwin.getKnowledgeGraph().query(Observation.class, lookupScope)
        .where(field, KnowledgeGraph.Query.Operator.EQUALS, value).run(lookupScope);
    if (matches.size() != 1) return null;
    var selected = matches.getFirst();
    return selected.getId() > 0 && selected.getObservable() != null
        && selected.getObservable().is(SemanticType.AGENT) ? selected : null;
  }

  public ServiceContextScope withIdentity(Identity identity) {
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.setIdentity(identity);
    if (identity instanceof UserIdentity userIdentity) {
      ret.setUser(userIdentity);
    }
    return ret;
  }

  /** Bind a root execution scope to the caller after its context ACL has been checked. */
  public ServiceContextScope forRequest(ServiceUserScope requester) {
    var ret = withIdentity(requester.getUser());
    // Subsequent focused scopes must resolve their root to this caller, not the cached owner.
    ret.parent = null;
    ret.useRequestAuthority(requester);
    var session = getParentScope(Type.SESSION, ServiceSessionScope.class);
    ret.setParentScope(session == null ? requester : session.forRequest(requester));
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

    if (observation instanceof Observation view && view.getId() == Observation.QUERY_ID
        && view.getObservable() != null && view.getObservable().getSemantics().isCollective()) {
      return new ArrayList<>(getService(RuntimeService.class).getMembers(view, this));
    }

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
    return new ArrayList<>(
        participantLinks(
            observation,
            false,
            GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
            GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT));
  }

  @Override
  public Collection<RuntimeAsset> getIncomingRelationshipsOf(RuntimeAsset observation) {
    return new ArrayList<>(
        participantLinks(
            observation,
            false,
            GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
            GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT));
  }

  @Override
  public List<Observation> getRelationshipParticipants(Observation relationship) {
    if (!relationship.getObservable().is(SemanticType.RELATIONSHIP)
        || relationship.getObservable().getSemantics().isCollective()) return List.of();
    org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(relationship.getObservable().getSemantics().getType())
        .requireComplete(relationship.getObservable().getSemantics().getUrn());
    return relationship.getObservable().is(SemanticType.BIDIRECTIONAL)
        ? participantLinks(relationship, true, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT)
        : participantLinks(
            relationship,
            true,
            GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
            GraphModel.Relationship.HAS_RELATIONSHIP_TARGET);
  }

  private List<Observation> participantLinks(
      RuntimeAsset anchor, boolean outgoing, GraphModel.Relationship... types) {
    var result = new LinkedHashMap<Long, Observation>();
    for (var type : types) {
      // An existing participant need not itself belong to assets() to have new incoming edges.
      if (currentTransaction != null) {
        var links =
            outgoing ? currentTransaction.outgoing(anchor) : currentTransaction.incoming(anchor);
        for (var link : links) {
          var other = outgoing ? link.target() : link.source();
          if (link.type() == type && other instanceof Observation observation)
            result.put(observation.getId(), observation);
        }
      }
      if (anchor.getId() > 0) {
        var query = digitalTwin.getKnowledgeGraph().query(Observation.class, this).along(type);
        if (outgoing) query.source(anchor);
        else query.target(anchor);
        for (var observation : query.run(this))
          result.putIfAbsent(observation.getId(), observation);
      }
    }
    return new ArrayList<>(result.values());
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

  private Observation canonicalDependencyTarget(Observation requested) {
    if(requested==null || requested.getId()==Observation.UNASSIGNED_ID || requested.getId()==Observation.QUERY_ID)
      throw new IllegalArgumentException("A bound modeled observation is required");
    var transaction=getCurrentTransaction();Observation canonical;
    if(transaction!=null) {
      var matches=transaction.assets().stream().filter(Observation.class::isInstance).map(Observation.class::cast)
          .filter(observation->observation.getId()==requested.getId()).toList();
      if(matches.size()!=1)throw new IllegalStateException("Modeled target is not uniquely owned by the live attempt");
      canonical=matches.getFirst();
    } else canonical=getObservation(requested.getId());
    if(canonical==null || !java.util.Objects.equals(canonical.getObservable().getUrn(),requested.getObservable().getUrn())
        || canonical.getParentId()!=requested.getParentId()
        || !org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.sanitize(canonical.getGeometry()).encode().equals(
            org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.sanitize(requested.getGeometry()).encode())
        || !java.util.Objects.equals(canonical.getMetadata().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY),
            requested.getMetadata().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY)))
      throw new IllegalStateException("Modeled target binding changed");
    return canonical;
  }

  private static boolean sameDependencyMeaning(org.integratedmodelling.klab.api.data.StorageScan.Semantics left,
      org.integratedmodelling.klab.api.data.StorageScan.Semantics right) {
    return left.meaning().equals(right.meaning()) && left.unit().equals(right.unit()) && left.currency().equals(right.currency())
        && left.range().equals(right.range()) && left.contextualDimensions().equals(right.contextualDimensions());
  }

  @Override
  public java.util.List<org.integratedmodelling.klab.api.services.runtime.DependencyFamily> getDependencyFamilies(Observation modeledObservation) {
    var target=canonicalDependencyTarget(modeledObservation);
    var encoded=target.getMetadata().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY);
    if(encoded==null)return java.util.List.of();
    return java.util.List.of(Utils.Json.parseObject(encoded.toString(),org.integratedmodelling.klab.api.services.runtime.DependencyFamily[].class));
  }

  @Override
  public java.util.List<org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Input> getDependencyInputs(Observation modeledObservation, String familyId) {
    var family=getDependencyFamilies(modeledObservation).stream().filter(f->f.id().equals(familyId)).findFirst()
        .orElseThrow(()->new IllegalArgumentException("Unknown dependency family"));
    var target=canonicalDependencyTarget(modeledObservation);var transaction=getCurrentTransaction();
    if(isInterrupted())throw new java.util.concurrent.CancellationException("Dependency input access cancelled");
    java.util.Collection<KnowledgeGraph.Link> links;
    if(transaction!=null) {
      if(transaction.isFailed())throw new IllegalStateException("Dependency input attempt failed");
      var root=transaction;while(root.getParent()!=null)root=root.getParent();
      if(!root.getId().equals(family.rootAttempt()))throw new IllegalStateException("Dependency family belongs to another attempt");
      links=transaction.incoming(target);
    } else links=getDigitalTwin().getKnowledgeGraph().getLinks(target,GraphModel.Relationship.Direction.INCOMING,this,GraphModel.Relationship.AFFECTS);
    var inputs=new java.util.HashMap<String,org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Input>();
    for(var link:links) {
      if(link.type()!=GraphModel.Relationship.AFFECTS || !(link.source() instanceof Observation source)
          || !(link.target() instanceof Observation destination) || destination.getId()!=target.getId())continue;
      var encoded=link.properties().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Binding.PROPERTY);
      if(encoded==null || encoded.toString().isBlank())continue;
      var binding=Utils.Json.parseObject(encoded.toString(),org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Binding.class);
      if(!binding.familyId().equals(family.id()))continue;
      var branch=family.branches().stream().filter(b->b.id().equals(binding.branchId())).findFirst()
          .orElseThrow(()->new IllegalStateException("Unknown concrete dependency branch"));
      if(!binding.rootAttempt().equals(family.rootAttempt()) || !binding.observable().equals(branch.observable())
          || branch.outcome()!=org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Outcome.RESOLVED)
        throw new IllegalStateException("Concrete dependency binding changed");
      var mediation=link.properties().get(org.integratedmodelling.klab.api.data.StorageScan.Binding.PROPERTY);
      if(mediation==null)throw new IllegalStateException("Concrete dependency has no storage binding");
      var storageBinding=Utils.Json.parseObject(mediation.toString(),org.integratedmodelling.klab.api.data.StorageScan.Binding.class);
      if(!storageBinding.source().equals(org.integratedmodelling.klab.api.data.StorageScan.semantics(source.getObservable()))
          || !sameDependencyMeaning(storageBinding.target(),branch.requested())
          || !"PREREQUISITE".equalsIgnoreCase(java.util.Objects.toString(link.properties().get(org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE),""))
          || !"CURRENT".equals(link.properties().get("readState")))
        throw new IllegalStateException("Concrete dependency mediation or prerequisite role changed");
      var input=new org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Input(family,branch,source,storageBinding);
      if(inputs.putIfAbsent(branch.id(),input)!=null)throw new IllegalStateException("Duplicate concrete dependency binding");
    }
    var result=new java.util.ArrayList<org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Input>();
    for(var branch:family.branches())if(branch.outcome()==org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Outcome.RESOLVED) {
      var input=inputs.get(branch.id());if(input==null)throw new IllegalStateException("Missing resolved dependency edge");result.add(input);
    }
    return java.util.List.copyOf(result);
  }

  @Override
  public Observation getTargetObservation() {
    return targetObservation;
  }

  @Override
  public ServiceContextScope within(Observation contextObservation) {
    ServiceContextScope ret = new ServiceContextScope(this);
    ret.contextObservation = contextObservation;
    // A new non-null within focus replaces relationship endpoints. Null only clears ambient
    // context (used while planning independent substantials) and preserves a paired scope.
    if (contextObservation != null) {
      ret.sourceObservation = null;
      ret.targetObservation = null;
      ret.resolutionConstraints.remove(ResolutionConstraint.Type.UnresolvedSourceObservation);
      ret.resolutionConstraints.remove(ResolutionConstraint.Type.UnresolvedTargetObservation);
    }
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

  /** Start a scheduler attempt without inheriting a resolution or already committed transaction. */
  public ServiceContextScope executingFresh(Activity activity, Object... assets) {
    var fresh = new ServiceContextScope(this);
    fresh.currentTransaction = null;
    fresh.remoteTransactionId = null;
    return fresh.executing(activity, assets);
  }

  public long commit() {
    if (getActivity() instanceof ActivityImpl activity) {
      activity.setOutcome(Activity.Outcome.SUCCESS);
      activity.setName(activity.getType().name().substring(0, 3) + " OK");
    }

    if (this.currentTransaction == null) {
      return -1;
    }

    var ret = this.currentTransaction.commit();
    try {
      send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, getActivity());
    } catch (RuntimeException deliveryFailure) {
      warn(
          "Activity delivery failed; committed temporal history remains available",
          deliveryFailure);
    }
    return ret;
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
    var ret = new ServiceContextScope(this);
    ret.sourceObservation = source;
    ret.targetObservation = target;
    return ret;
  }

  @Override
  public ServiceContextScope withResolutionConstraints(
      ResolutionConstraint... resolutionConstraints) {
    ServiceContextScope ret = new ServiceContextScope(this);
    if (resolutionConstraints == null) {
      ret.resolutionConstraints.clear();
    } else {
      // A named per-submission selection wins over an inherited numeric default,
      // regardless of transport/list ordering. It remains a scope choice, not a query clause.
      var namedObservers = java.util.Arrays.stream(resolutionConstraints)
          .filter(java.util.Objects::nonNull)
          .filter(c -> c.getType() == ResolutionConstraint.Type.ObserverUrn && !c.empty())
          .flatMap(c -> c.payload(String.class).stream()).distinct().toList();
      if (namedObservers.size() > 1 || namedObservers.stream().anyMatch(String::isBlank))
        throw new IllegalArgumentException("A submission must select one nonblank observer URN");
      for (var constraint : resolutionConstraints) {
        if (constraint == null || constraint.empty()) {
          continue;
        }
        if (constraint.getType() == ResolutionConstraint.Type.UnresolvedContextObservation) {
          ret.contextObservation = constraint.payload(Observation.class).getFirst();
        } else if (constraint.getType() == ResolutionConstraint.Type.ObserverUrn) {
          if (ret.getDigitalTwin() == null)
            throw new IllegalArgumentException("Observer selection requires a knowledge graph");
          var selected = ret.selectedObserver(GraphModel.Fields.URN, namedObservers.getFirst());
          if (selected == null) throw new IllegalArgumentException("Unknown observer");
          ret = ret.withObserver(selected);
        } else if (constraint.getType() == ResolutionConstraint.Type.Observer) {
          if (!namedObservers.isEmpty()) continue;
          var selected = ret.selectedObserver(GraphModel.Fields.ID,
              constraint.payload(Long.class).getFirst());
          if (selected == null) throw new IllegalArgumentException("Unknown observer");
          ret = ret.withObserver(selected);
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
    var endpoints = ResolutionConstraint.unresolvedRelationshipScope(ret, resolutionConstraints);
    if (endpoints != null) {
      ret.sourceObservation = endpoints.source();
      ret.targetObservation = endpoints.target();
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
  void releaseLocalResources() {
    try {
      if (digitalTwin != null && digitalTwin.isClient()) digitalTwin.dispose();
    } finally {
      super.releaseLocalResources();
    }
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

    var querySourceIds =
        observation.getId() == Observation.QUERY_ID
            ? observation.getMetadata().getList(Metadata.IM_QUERY_SOURCE_IDS, Long.class)
            : List.<Long>of();

    var visibleMatches = new java.util.ArrayList<Observation>();
    // A base Concept match alone does not identify an observed-as wrapper.
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
            && (!querySourceIds.isEmpty()
                ? querySourceIds.contains(o.getId())
                : (isSameInstance(observation, o)
                    || (SemanticType.isDependent(
                            observation.getObservable().getSemantics().getType())
                        && observation
                            .getObservable()
                            .getSemantics()
                            .getUrn()
                            .equals(o.getObservable().getSemantics().getUrn()))))) {
          visibleMatches.add(o);
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

    if (!querySourceIds.isEmpty()) {
      query = query.where("id", KnowledgeGraph.Query.Operator.EQUALS, querySourceIds.getFirst());
    } else if (!observation.getObservable().getSemantics().isCollective()
        && SemanticType.isEnumerableSubstantial(
            observation.getObservable().getSemantics().getType())) {
      query =
          query.where(
              "urn",
              KnowledgeGraph.Query.Operator.EQUALS,
              ObservationImpl.catalogUrn(getId(), observation.getUrn()));
    } else {
      query =
          query.where(
              "semantics",
              KnowledgeGraph.Query.Operator.EQUALS,
              observation.getObservable().asConcept().getUrn());
    }

    visibleMatches.addAll(query.run(this));
    if (!SemanticType.isDependent(observation.getObservable().getSemantics().getType()))
      return visibleMatches.isEmpty() ? null : visibleMatches.getFirst();
    Observation complete=null,partial=null;
    int completePreference=Integer.MAX_VALUE,partialPreference=Integer.MAX_VALUE;
    double partialCoverage=-1;
    for(var candidate:visibleMatches) {
      int preference=org.integratedmodelling.klab.runtime.ObserverResolution.preference(
          candidate.getObservable(),observation.getObservable(),this);
      if(preference<0)continue;
      var requestedGeometry=observation.getGeometry();
      if(requestedGeometry==null && contextObservation!=null)requestedGeometry=contextObservation.getGeometry();
      // An ID-zero query may intentionally ask for the known value without a support constraint.
      // Its source extent is the answer's domain, never proof of covering an explicit request.
      if(requestedGeometry==null && observation.getId()==Observation.QUERY_ID)requestedGeometry=candidate.getGeometry();
      var coverage=org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(candidate,requestedGeometry);
      if(coverage==null || coverage.getCoverage()<=0)continue;
      if(coverage.isComplete()) {
        if(preference<completePreference) {complete=candidate;completePreference=preference;}
      } else if(preference<partialPreference || preference==partialPreference && coverage.getCoverage()>partialCoverage) {
        partial=candidate;partialPreference=preference;partialCoverage=coverage.getCoverage();
      }
    }
    // One-result lookup cannot compose multiple existing perspectives. A partial specific cannot
    // hide a complete fallback; if none is complete the caller receives measured partial support.
    return complete!=null?complete:partial;
  }

  private boolean isSameInstance(Observation observation, Observation o) {
    return !observation.getObservable().getSemantics().isCollective()
        && SemanticType.isEnumerableSubstantial(
            observation.getObservable().getSemantics().getType())
        && Objects.equals(
            ObservationImpl.logicalUrn(observation.getUrn()),
            ObservationImpl.logicalUrn(o.getUrn()));
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

    var observer = org.integratedmodelling.common.runtime.ObserverProvenance
        .recover(this,observation).requireKnown();
    Observation contextObs = null;
    if (observation.getObservable().is(SemanticType.QUALITY)
        || (observation.getObservable().is(SemanticType.COUNTABLE)
            && !observation.getObservable().asConcept().isCollective())) {
      var parent = getParentOf(observation);
      contextObs = parent instanceof Observation ? (Observation) parent : null;
    }

    var ret = withObserver(observer); // Deliberately null must also clear the current default.
    ret.contextObservation = contextObs;
    return ret;
  }

  @Override
  public Data.ShardingStrategy getShardingStrategy(Observation observation) {
    return shardingStrategy;
  }

  public GraphModel.KnowledgeGraph getResolvedGraph() {
    return this.currentTransaction.getGraph();
  }

  public Activity getActivity() {
    return currentTransaction == null ? null : currentTransaction.getActivity();
  }

  public boolean contextualize(Observation observation) {
    return this.digitalTwin.getScheduler().submit(observation, this);
  }

  /** Reinitialize a context scope after a timeout if so configured. */
  public void reinitialize() {
    // TODO zap the KG and all caches; leave a trace somewhere for provenance.
  }

  @Override
  public Observation.Builder observation(Observable observable) {
    // Only the root consequence request is optional. Required model dependencies must retain
    // their normal failure semantics inside the transaction.
    var requested = consequenceCause != null && getTransactionId() == null && observable != null
            && !observable.isOptional()
        ? observable.builder(this).optional(true).buildObservable() : observable;
    return new ObservationBuilderImpl(requested, this) {
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
    if (transaction.getParent() == null) {
      provisionalObservations.remove(transaction.getId());
    }
  }

  /**
   * Make a registered candidate retrievable during resolution without adding it to the commit
   * graph. Rejected model candidates must never be persisted merely because they were registered.
   * Sibling resolution attempts share the root transaction's temporary identity registry.
   */
  public void registerProvisionalObservation(Observation observation) {
    if (currentTransaction != null && observation.getId() < Observation.UNASSIGNED_ID) {
      provisionalObservations
          .computeIfAbsent(rootTransactionId(currentTransaction), key -> new ConcurrentHashMap<>())
          .putIfAbsent(observation.getId(), observation);
    }
  }

  private static String rootTransactionId(DigitalTwin.Transaction transaction) {
    while (transaction.getParent() != null) {
      transaction = transaction.getParent();
    }
    return transaction.getId();
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
