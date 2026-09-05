package org.integratedmodelling.klab.services.runtime.digitaltwin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.runtime.knowledge.DataflowGraph;
import org.integratedmodelling.klab.runtime.knowledge.ProvenanceGraph;
import org.integratedmodelling.klab.runtime.storage.StorageManagerImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/** TODO each digital twin should have its own logger */
public class DigitalTwinImpl implements DigitalTwin {

  private final KnowledgeGraphNeo4j knowledgeGraph;
  private final StorageManager storageManager;
  private final ContextScope rootScope;
  private final Scheduler scheduler;
  private final Configuration configuration;
  private long transientId = Klab.getNextId();
  private long parentTransientId = -1000;
  private Cache<Long, KnowledgeGraph.Commit> commitCache =
      CacheBuilder.newBuilder()
          .maximumSize(/* TODO initialize from service settings */ 200)
          .expireAfterAccess(/* TODO this too */ 10, TimeUnit.MINUTES)
          .build();

  @Override
  public long getId() {
    return CONTEXT_ASSET.getId();
  }

  @Override
  public long getParentId() {
    return 0;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  @Override
  public int getChildrenCount() {
    return -1; // TODO should implement so that we have a main obs count
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }

  @Override
  public Type classify() {
    return Type.CONTEXT;
  }

  /**
   * The transactional graph is a directed acyclic graph that represents the execution of a digital
   * twin activity, capturing the relationships and changes between assets. The graph is robust to
   * identity switching between assets as long as their ID remains consistent.
   */
  public class TransactionImpl implements Transaction {

    private final String id = Utils.Names.fastName();
    private final Set<RuntimeAsset> modified;
    private final Set<RuntimeAsset> added;
    private Observation target;
    private final Activity activity;
    private final ServiceContextScope scope;
    private final Queue<Throwable> failures;
    private final Graph<RuntimeAsset, RelationshipEdge> graph;
    private final Map<Observation, Executor> contextualizers;
    private TransactionImpl parent; // null in the root activity
    private Map<Concept, Scale> cohortGeometries = new HashMap<>();

    static class RelationshipEdge extends DefaultEdge {
      GraphModel.Relationship relationship;
      Geometry geometry;
      int sequence = -1;

      public RelationshipEdge(GraphModel.Relationship relationship, Object... data) {
        this.relationship = relationship;
        if (data != null) {
          for (int i = 0; i < data.length; i++) {
            if (data[i] instanceof Integer seq) {
              this.sequence = seq;
            } // TODO geometry and more
          }
        }
      }
    }

    /** Call this before contextualization to commit to register any new executors. */
    @Override
    public void registerExecutors() {
      for (var observation : contextualizers.keySet()) {
        scheduler.registerExecutor(
            observation, (g, e, s) -> contextualizers.get(observation).run(g, e, s));
      }
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public Transaction getParent() {
      return parent;
    }

    public TransactionImpl(Activity activity, ServiceContextScope scope, Object... data) {

      this.graph = new DefaultDirectedGraph<>(RelationshipEdge.class);
      this.activity = activity;
      this.scope = scope;
      this.modified = new HashSet<>();
      this.added = new HashSet<>();
      this.failures = new ConcurrentLinkedQueue<>();
      this.contextualizers = new ConcurrentHashMap<>();
      this.cohortGeometries = new HashMap<>();
      boolean activityLinked = false;

      synchronized (graph) {
        this.graph.addVertex(activity);

        if (data != null) {
          for (Object datum : data) {
            if (datum == RuntimeAsset.PROVENANCE_ASSET
                || datum == RuntimeAsset.CONTEXT_ASSET
                || datum == RuntimeAsset.DATAFLOW_ASSET) {
              this.graph.addVertex((RuntimeAsset) datum);
            } else if (datum instanceof Agent agent) {
              this.graph.addVertex(agent);
              this.graph.addEdge(
                  activity, agent, new RelationshipEdge(GraphModel.Relationship.BY_AGENT));
            } else if (datum instanceof Activity activity1) {
              this.graph.addVertex(activity1);
              this.graph.addEdge(
                  activity1, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));
              activityLinked = true;
            } else if (datum instanceof Observation observation) {
              setTarget(observation);
            } else if (datum instanceof Dataflow dataflow) {
              // serialize and record the dataflow with the activity
              if (activity instanceof ActivityImpl activity1) {
                activity1.setDescription(Utils.Dataflows.encode(dataflow, scope));
              }
            }
          }
        }

        if (!activityLinked) {
          this.graph.addEdge(
              RuntimeAsset.PROVENANCE_ASSET,
              activity,
              new RelationshipEdge(GraphModel.Relationship.HAS_CHILD));
        }
      }

      scope.registerTransaction(this);
    }

    private TransactionImpl(TransactionImpl parent, Activity activity, Object... data) {

      this.graph = parent.graph;
      this.target = parent.target;
      this.activity = activity;
      this.scope = parent.scope; // TODO careful with executing() being called outside
      this.parent = parent;
      // A child transaction contributes to the one atomic root commit. Keep all mutable commit
      // state root-shared just like the graph itself; otherwise modifications and failures from
      // secondary submissions disappear when the root assembles its commit result.
      this.modified = parent.modified;
      this.added = parent.added;
      this.failures = parent.failures;
      this.cohortGeometries = parent.cohortGeometries;
      this.contextualizers = parent.contextualizers;

      synchronized (graph) {
        this.graph.addVertex(activity);
        this.graph.addEdge(
            parent.activity, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));

        this.graph.addVertex(activity);
        if (data != null) {
          for (Object datum : data) {
            if (datum instanceof Agent agent) {
              this.graph.addVertex(agent);
              this.graph.addEdge(
                  activity, agent, new RelationshipEdge(GraphModel.Relationship.BY_AGENT));
            } else if (datum instanceof Observation observation) {
              // only link contextualization to the contextualized observation
              if (activity.getType() == Activity.Type.CONTEXTUALIZATION) {
                var obs =
                    graph.vertexSet().stream()
                        .filter(
                            a -> a instanceof Observation oo && oo.getId() == observation.getId())
                        .findFirst()
                        .orElse(observation);
                try {
                  this.graph.addEdge(
                      activity, obs, new RelationshipEdge(GraphModel.Relationship.CONTEXTUALIZED));
                } catch (Exception e) {
                  Logging.INSTANCE.error(e, obs);
                }
              }
            } // TODO see if we need anything else
          }
        }
      }
    }

    public void setTarget(Observation observation) {
      this.target = observation;
      synchronized (graph) {
        this.graph.addVertex(observation);
        this.graph.addEdge(
            activity, observation, new RelationshipEdge(GraphModel.Relationship.CREATED));
      }
    }

    @Override
    public Activity getActivity() {
      return this.activity;
    }

    @Override
    public void checkCohortGeometry(Cohort cohort, Geometry observedGeometry) {
      if (cohort == null || observedGeometry == null) {
        return;
      }
      synchronized (graph) {
        var cohortObservable = cohort.getObservable().getSemantics();
        var currentGeometry = cohortGeometries.get(cohortObservable);
        var newGeometry =
            mergeCohortGeometry(
                cohortObservable.getType(),
                currentGeometry == null ? cohort.getGeometry() : currentGeometry,
                observedGeometry);

        this.cohortGeometries.put(cohortObservable, newGeometry);
        applyCohortGeometry(cohort, newGeometry);
      }
    }

    static Scale mergeCohortGeometry(
        Set<SemanticType> semanticTypes, Geometry currentGeometry, Geometry observedGeometry) {
      var current =
          currentGeometry == null || currentGeometry.isUniversal()
              ? null
              : GeometryRepository.INSTANCE.scale(currentGeometry);
      var observed = GeometryRepository.INSTANCE.scale(observedGeometry);
      if (!SemanticType.isOccurrentSubstantial(semanticTypes)) {
        current = withoutTime(current);
        observed = withoutTime(observed);
      }
      if (current == null || current.isUniversal()) {
        return observed;
      }
      return GeometryRepository.INSTANCE.outerUnion(observed, current);
    }

    private static Scale withoutTime(Scale scale) {
      return scale != null && scale.getTime() != null
          ? scale.without(Geometry.Dimension.Type.TIME)
          : scale;
    }

    /**
     * This allows us to keep the graph consistent without having to enforce object identity during
     * contextualization. The logic only applies to observations, as all other assets are generated
     * contextually to the transaction and their IDs may overlap those of observations.
     *
     * @param asset
     * @return
     */
    private RuntimeAsset checkPresentAsset(RuntimeAsset asset) {
      if (!(asset instanceof Observation) || asset.getId() == Observation.UNASSIGNED_ID) {
        return asset; // should never happen, but just in case, never deduplicate UNASSIGNED_ID —
        // IDs are not yet
        // stable
      }
      return graph.vertexSet().stream()
          .filter(a -> a instanceof Observation && a.getId() == asset.getId())
          .findFirst()
          .orElse(asset);
    }

    @Override
    public void add(RuntimeAsset asset) {
      synchronized (graph) {
        asset = checkPresentAsset(asset);
        graph.addVertex(asset);
        added.add(asset);
      }
    }

    @Override
    public void link(
        RuntimeAsset sourceOrig,
        RuntimeAsset destinationOrig,
        GraphModel.Relationship relationship,
        Object... data) {
      synchronized (graph) {
        var source = checkPresentAsset(sourceOrig);
        var destination = checkPresentAsset(destinationOrig);

        graph.addVertex(source);
        graph.addVertex(destination);
        graph.addEdge(source, destination, new RelationshipEdge(relationship, data));

        // TODO do this for all others as well?
        if (source instanceof ObservationImpl sourceObs
            && destination instanceof ObservationImpl targetObs
            && relationship == GraphModel.Relationship.HAS_CHILD) {
          sourceObs.setChildrenCount(sourceObs.getChildrenCount() + 1);
          update(sourceObs);
          targetObs.setParentTransientId(sourceObs.getTransientId());
        } else if (source instanceof CohortImpl sourceCohort
            && destination instanceof ObservationImpl targetObs
            && relationship == GraphModel.Relationship.HAS_MEMBER) {
          sourceCohort.setChildrenCount(sourceCohort.getChildrenCount() + 1);
          update(sourceCohort);
          targetObs.setParentTransientId(sourceCohort.getTransientId());
        }
      }
    }

    @Override
    public void update(RuntimeAsset asset) {
      synchronized (graph) {
        modified.add(checkPresentAsset(asset));
      }
    }

    @Override
    public void resolveWith(Observation observation, Executor executor) {
      this.contextualizers.put(observation, executor);
    }

    @Override
    public long commit() {

      if (!failures.isEmpty()) {
        failures.forEach(t -> scope.error(t));
        return -1;
      }

      if (activity instanceof ActivityImpl activity1) {
        activity1.setEnd(System.currentTimeMillis());
        activity1.setOutcome(Activity.Outcome.SUCCESS);
        activity1.setName(activity.getType().name().substring(0, 3) + " OK");
      }

      // if nothing was done, we just store the HAS_CHILD relationships that point to observations.
      // TODO/CHECK the logics here may require some attention
//      var trivial = false;
//      synchronized (graph) {
//        trivial =
//            contextualizers.isEmpty()
//                && graph.vertexSet().stream().noneMatch(a -> a instanceof Storage.Shard);
//      }

      var ret = Transaction.INTERMEDIATE_COMMIT_ID;

      /*
      Open transaction in the knowledge graph and store everything that needs to, then make all connections
       */
      if (parent == null) {
        // Reserve the commit identity before storing assets so the durable observation metadata and
        // the object returned by submit() expose the same finalized lifecycle information.
        var commitId = knowledgeGraph.nextKey();
        prepareObservationsForStorage(commitId);
        var kgTransaction = knowledgeGraph.createTransaction(scope);
        var stored = new ArrayList<RuntimeAsset>();
        var linked = new ArrayList<Triple<Long, Long, String>>();
        try (kgTransaction) {

          registerCohortUpdates();

          synchronized (graph) {
            var assets = new ArrayList<>(graph.vertexSet());
            // Observations receive their persistent URNs before activities snapshot the URNs of
            // the observations they created or resolved.
            assets.sort(Comparator.comparing(asset -> asset instanceof Activity));
            for (var asset : assets) {
              if (asset instanceof ActivityImpl storedActivity) {
                prepareActivityForStorage(storedActivity);
              }
              if (setupForStorage(asset, false)) {
                kgTransaction.store(asset);
                if (asset.getId() <= 0) {
                  throw new KlabInternalErrorException(
                      "Knowledge graph did not persist " + asset.getClass().getSimpleName());
                }
                stored.add(asset);
              }
            }

            for (var asset : modified) {
              kgTransaction.update(asset);
            }

            for (var edge : graph.edgeSet()) {
              var source = graph.getEdgeSource(edge);
              var target = graph.getEdgeTarget(edge);
//              if (trivial
//                  && !(target instanceof Observation)
//                  && edge.relationship != GraphModel.Relationship.HAS_CHILD) {
//                continue;
//              }
              var relationshipData = getRelationshipData(edge);
              // KLAB-DEBUG-GUARD: links are intentionally still submitted and advertised; expose
              // unassigned endpoints without changing the existing commit behavior.
              if (source.getId() == 0 || target.getId() == 0) {
                Logging.INSTANCE.warn(
                    "KLAB-DEBUG-GUARD: KG link has unassigned endpoint: source={}({}) target={}({}) "
                        + "relationship={} activity={} trivial={}",
                    source.getId(),
                    source.getClass().getName(),
                    target.getId(),
                    target.getClass().getName(),
                    edge.relationship,
                    activity.getId(),
                    false);
              }
              kgTransaction.link(source, target, edge.relationship, relationshipData);
              // Only advertise relationships that were actually persisted. In particular, trivial
              // transactions contain transient activity edges that are intentionally skipped.
              linked.add(Triple.of(source.getId(), target.getId(), edge.relationship.name()));
            }
          }
        } catch (Exception e) {
          scope.error(e);
          kgTransaction.fail(e);
          ((ActivityImpl) activity).setOutcome(Activity.Outcome.INTERNAL_FAILURE);
          ((ActivityImpl) activity)
              .setName(activity.getType().name().substring(0, 3) + " EXCEPTION");
          ((ActivityImpl) activity).setEnd(System.currentTimeMillis());
          ((ActivityImpl) activity).setStackTrace(Utils.Exceptions.stackTrace(e));
          return -1;
        }

        var commit = createCommit(commitId, scope.getUser().getUsername(), stored, modified, linked);
        commitCache.put(commit.getId(), commit);

        ret = commit.getId();
      }

      /* Storage mutates the exact submitted object. Never recover it by observable equality: an
       * instantiator routinely creates multiple observations with identical semantics. */
      finalizeCommittedTarget(parent == null, target, (ActivityImpl) activity);

      ((ActivityImpl) activity).setOutcome(Activity.Outcome.SUCCESS);
      ((ActivityImpl) activity).setName(activity.getType().name().substring(0, 3) + " OK");
      ((ActivityImpl) activity).setEnd(System.currentTimeMillis());

      scope.unregisterTransaction(this);

      return ret;
    }

    static void finalizeCommittedTarget(
        boolean rootCommit, Observation target, ActivityImpl activity) {
      if (!rootCommit || target == null) {
        return;
      }
      if (target.getId() <= 0) {
        throw new KlabInternalErrorException(
            "Committed transaction did not finalize its target observation");
      }
      activity.setObservationUrn(target.getUrn());
    }

    /**
     * Materialize the public observation identity and a compact provenance summary before Neo4j
     * takes its property snapshot. Detailed provenance remains represented by Activity links.
     */
    private void prepareObservationsForStorage(long commitId) {
      synchronized (graph) {
        for (var asset : graph.vertexSet()) {
          if (!(asset instanceof Observation observation)) {
            continue;
          }
          if (observation instanceof ObservationImpl implementation) {
            implementation.setName(observation.getName());
          }
          var creator = creationActivity(observation);
          var metadata = observation.getMetadata();
          metadata.put(Metadata.IM_COMMIT_ID, commitId);
          metadata.putIfAbsent(Metadata.IM_CONTEXT_ID, scope.getId());
          metadata.putIfAbsent(Metadata.IM_CREATION_AGENT, scope.getUser().getUsername());
          if (creator != null) {
            metadata.putIfAbsent(
                Metadata.IM_CREATION_ACTIVITY_TYPE,
                creator.getType() == null ? null : creator.getType().name());
            metadata.putIfAbsent(Metadata.IM_CREATION_TIMESTAMP, creator.getStart());
            metadata.putIfAbsent(Metadata.IM_CREATION_SERVICE_ID, creator.getServiceId());
            metadata.putIfAbsent(
                Metadata.IM_CREATION_SERVICE_TYPE,
                creator.getServiceType() == null ? null : creator.getServiceType().name());
          }
          metadata.values().removeIf(Objects::isNull);
        }
      }
    }

    private Activity creationActivity(Observation observation) {
      return graph.incomingEdgesOf(observation).stream()
          .filter(edge -> edge.relationship == GraphModel.Relationship.CREATED)
          .map(graph::getEdgeSource)
          .filter(Activity.class::isInstance)
          .map(Activity.class::cast)
          .sorted(
              Comparator.comparing(
                  candidate -> candidate.getType() != Activity.Type.SUBMISSION))
          .findFirst()
          .orElse(null);
    }

    private void prepareActivityForStorage(ActivityImpl storedActivity) {
      graph.outgoingEdgesOf(storedActivity).stream()
          .filter(
              edge ->
                  edge.relationship == GraphModel.Relationship.CREATED
                      || edge.relationship == GraphModel.Relationship.RESOLVED)
          .map(graph::getEdgeTarget)
          .filter(Observation.class::isInstance)
          .map(Observation.class::cast)
          .map(Observation::getUrn)
          .filter(Objects::nonNull)
          .findFirst()
          .ifPresent(storedActivity::setObservationUrn);
    }

    static CommitImpl createCommit(
        long commitId,
        String owner,
        Collection<RuntimeAsset> stored,
        Collection<RuntimeAsset> modified,
        Collection<Triple<Long, Long, String>> linked) {
      var commit = new CommitImpl();
      commit.setId(commitId);
      commit.setTimestamp(System.currentTimeMillis());
      commit.setOwner(owner);
      commit.getAddedAssets().addAll(stored.stream().map(RuntimeAsset::getId).toList());
      commit
          .getAddedObservations()
          .addAll(
              stored.stream()
                  .filter(a -> a instanceof Observation)
                  .map(RuntimeAsset::getId)
                  .toList());
      commit
          .getAddedCohorts()
          .addAll(
              stored.stream()
                  .filter(a -> a instanceof Cohort)
                  .map(RuntimeAsset::getId)
                  .toList());
      commit.getAddedLinks().addAll(linked);
      /*
       * Cohorts are durable context-catalog assets and may have been created in a short
       * knowledge-graph transaction before this observation transaction existed. Reassert their
       * context ownership in the observation commit so a client whose Context adjacency is
       * already loaded learns about the cohort. This is a synchronization assertion only: the
       * Context -HAS_CHILD-> Cohort relationship is already present in Neo4j and is not written a
       * second time.
       */
      modified.stream()
          .filter(Cohort.class::isInstance)
          .map(RuntimeAsset::getId)
          .filter(id -> id > 0)
          .map(
              id ->
                  Triple.of(
                      RuntimeAsset.CONTEXT_ASSET_ID,
                      id,
                      GraphModel.Relationship.HAS_CHILD.name()))
          .forEach(commit.getAddedLinks()::add);
      commit
          .getModifiedAssets()
          .addAll(modified.stream().map(RuntimeAsset::getId).filter(id -> id > 0).toList());

      // Secondary submissions return before the root commit exists. Attach the final commit ID to
      // every newly stored observation now, not only to the root observation returned by submit().
      stored.stream()
          .filter(Observation.class::isInstance)
          .map(Observation.class::cast)
          .forEach(observation -> observation.getMetadata().put(Metadata.IM_COMMIT_ID, commitId));
      return commit;
    }

    /**
     * Register cohorts whose geometry was updated, either in an existing new cohort or as a
     * modified cohort in the modified array.
     */
    private void registerCohortUpdates() {
      synchronized (graph) {
        for (var cohortObservable : cohortGeometries.keySet()) {
          var geometry = cohortGeometries.get(cohortObservable);
          for (var asset : graph.vertexSet()) {
            if (asset instanceof Cohort cohort
                && cohort.getObservable().getSemantics().equals(cohortObservable)) {
              applyCohortGeometry(cohort, geometry);
            }
          }
        }
      }
    }

    private void applyCohortGeometry(Cohort cohort, Scale geometry) {
      if (cohort instanceof CohortImpl cohortImpl && geometry != null) {
        cohortImpl.setGeometry(GeometryRepository.INSTANCE.geometry(geometry));
        synchronized (graph) {
          modified.add(cohortImpl);
        }
      }
    }

    @Override
    public Transaction getChild(Activity activity, ContextScope scope, Object... runtimeAssets) {
      var ret = new TransactionImpl(this, activity, runtimeAssets);
      if (scope instanceof ServiceContextScope serviceContextScope) {
        serviceContextScope.registerTransaction(ret);
      }
      return ret;
    }

    private Object[] getRelationshipData(RelationshipEdge edge) {
      var ret = new ArrayList<Object>();
      if (edge.relationship == GraphModel.Relationship.AFFECTS) {
        ret.add("sequence");
        ret.add(edge.sequence);
      }
      return ret.toArray();
    }

    private boolean setupForStorage(RuntimeAsset asset, boolean trivial) {
      return switch (asset) {
        case Observation observation -> observation.getId() < 0;
        case Cohort cohort -> cohort.getId() < 0;
        case Actuator actuator -> !trivial;
        case Activity activity -> {
          var ret = activity.getId() < 0 && !trivial;
//          if (ret
//              && activity.getType() == Activity.Type.RESOLUTION
//              && activity.getMetadata().containsKey(Metadata.IM_RESOLUTION_GRAPH)) {
//            ret =
//                // don't store resolutions that produced nothing, i.e. just the observation is in
//                // the graph
//                activity
//                        .getMetadata()
//                        .get(Metadata.IM_RESOLUTION_GRAPH, GraphModel.KnowledgeGraph.class)
//                        .getNodes()
//                        .size()
//                    > 1;
//          }
          yield ret;
        }
        case Storage.Shard shard -> shard.getId() < 0;
        default -> false;
      };
    }

    @Override
    public Transaction fail(Throwable compilationError) {
      ((ActivityImpl) activity).setOutcome(Activity.Outcome.FAILURE);
      ((ActivityImpl) activity).setName(activity.getType().name().substring(0, 3) + " FAIL");
      ((ActivityImpl) activity).setEnd(System.currentTimeMillis());
      if (compilationError != null) {
        this.failures.add(compilationError);
        ((ActivityImpl) activity).setStackTrace(Utils.Exceptions.stackTrace(compilationError));
      }
      scope.unregisterTransaction(this);
      return this;
    }

    @Override
    public Collection<RuntimeAsset> assets() {
      synchronized (graph) {
        return new ArrayList<>(graph.vertexSet());
      }
    }

    @Override
    public Collection<KnowledgeGraph.Link> incoming(RuntimeAsset asset) {
      var ret = new ArrayList<KnowledgeGraph.Link>();
      synchronized (graph) {
        asset = checkPresentAsset(asset);
        if (graph.vertexSet().contains(asset)) {
          for (var edge : graph.incomingEdgesOf(asset)) {
            var link = new LinkImpl(graph.getEdgeSource(edge), asset, edge.relationship);
            link.setSequence(edge.sequence);
            link.setGeometry(edge.geometry);
            ret.add(link);
          }
        }
      }
      return ret;
    }

    @Override
    public Collection<KnowledgeGraph.Link> outgoing(RuntimeAsset asset) {
      var ret = new ArrayList<KnowledgeGraph.Link>();
      synchronized (graph) {
        asset = checkPresentAsset(asset);
        if (graph.vertexSet().contains(asset)) {
          for (var edge : graph.outgoingEdgesOf(asset)) {
            var link = new LinkImpl(asset, graph.getEdgeTarget(edge), edge.relationship);
            link.setSequence(edge.sequence);
            link.setGeometry(edge.geometry);
            ret.add(link);
          }
        }
      }
      return ret;
    }

    @Override
    public GraphModel.KnowledgeGraph getGraph() {
      var ret = new GraphModel.KnowledgeGraph();
      synchronized (graph) {
        // the graph has everything; add only the nodes that correspond to the assets added in the
        // transaction.
        for (var asset : graph.vertexSet()) {
          if (added.contains(asset)) {
            ret.getNodes().put(asset.getId() + "", encodeRuntimeAsset(asset, ret.getNodes()));
          }
        }
        for (var edge : graph.edgeSet()) {
          var source = graph.getEdgeSource(edge);
          var target = graph.getEdgeTarget(edge);
          if (added.contains(source) && added.contains(target)) {
            ret.getEdges()
                .add(encodeLink(source, target, edge.relationship, getRelationshipData(edge)));
          }
        }
      }
      return ret;
    }
  }

  private GraphModel.KnowledgeGraph.Edge encodeLink(
      RuntimeAsset source,
      RuntimeAsset target,
      GraphModel.Relationship relationship,
      Object... relationshipData) {
    return new GraphModel.KnowledgeGraph.Edge(
        (source.getId() < 0 ? source.getTransientId() : source.getId()) + "",
        (target.getId() < 0 ? target.getTransientId() : target.getId()) + "",
        relationship.name(),
        true,
        Utils.Maps.makeStringMap(relationshipData));
  }

  private GraphModel.KnowledgeGraph.Node encodeRuntimeAsset(
      RuntimeAsset asset, Map<String, GraphModel.KnowledgeGraph.Node> nodes) {
    return nodes.computeIfAbsent(
        (asset.getId() < 0 ? asset.getTransientId() : asset.getId()) + "",
        id -> new GraphModel.KnowledgeGraph.Node(asset));
  }

  public DigitalTwinImpl(
      RuntimeService service,
      ServiceContextScope scope, // root scope used by the scheduler
      String scopeId, // the ID to use. The scope may or may not have it according to state
      UserScope userScope,
      KnowledgeGraphNeo4j database) {
    this.rootScope = scope;
    this.configuration =
        DigitalTwin.Configuration.builder(scope.getConfiguration())
            .id(scopeId)
            .serviceId(service.serviceId())
            .build();
    this.knowledgeGraph = (KnowledgeGraphNeo4j) database.contextualize(configuration, userScope);
    this.storageManager = new StorageManagerImpl(service, scope);
    this.scheduler = new SchedulerImpl(scope, this);
  }

  @Override
  public Transaction transaction(Activity activity, ContextScope scope, Object... runtimeAssets) {
    return new TransactionImpl(activity, (ServiceContextScope) scope, runtimeAssets);
  }

  @Override
  public KnowledgeGraph getKnowledgeGraph() {
    return this.knowledgeGraph;
  }

  @Override
  public StorageManager getStorageManager() {
    return this.storageManager;
  }

  @Override
  public Scheduler getScheduler() {
    return this.scheduler;
  }

  @Override
  public Provenance getProvenanceGraph(ContextScope context) {
    return new ProvenanceGraph(this.knowledgeGraph, this.rootScope);
  }

  @Override
  public Dataflow getDataflowGraph(ContextScope context) {
    return new DataflowGraph(this.knowledgeGraph, this.rootScope);
  }

  public KnowledgeGraph.Commit getCommit(long id) {
    return commitCache.getIfPresent(id);
  }

  @Override
  public Configuration getOptions() {
    return this.configuration;
  }

  @Override
  public boolean isClient() {
    return false;
  }

  @Override
  public void dispose() {
    ((SchedulerImpl) scheduler).close();
    this.knowledgeGraph.deleteContext();
    this.storageManager.clear();
  }

  /** Release process-local resources without deleting a persistent context or its data files. */
  public void shutdown() {
    ((SchedulerImpl) scheduler).close();
    ((StorageManagerImpl) storageManager).close();
  }
}
