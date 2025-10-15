package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
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
  private Configuration configuration;
  private long transientId = Klab.getNextId();
  private long parentTransientId = -1000;
  private Cache<String, KnowledgeGraph.Commit> commitCache =
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

  public class TransactionImpl implements Transaction {

    private final Set<RuntimeAsset> modified = new HashSet<>();
    private final Set<RuntimeAsset> added = new HashSet<>();
    private Observation target;
    private final Activity activity;
    private final ServiceContextScope scope;
    private final List<Throwable> failures = new ArrayList<>();
    private final Graph<RuntimeAsset, RelationshipEdge> graph;
    private final Map<Observation, Executor> contextualizers;
    private TransactionImpl parent; // null in the root activity

    static class LinkImpl implements KnowledgeGraph.Link {
      private RuntimeAsset source;
      private RuntimeAsset target;
      private GraphModel.Relationship relationship;
      private int sequence = -1;
      private Geometry geometry;
      private Parameters<String> properties = Parameters.create();

      public LinkImpl(
          RuntimeAsset source, RuntimeAsset target, GraphModel.Relationship relationship) {
        this.source = source;
        this.target = target;
        this.relationship = relationship;
      }

      public void setSource(RuntimeAsset source) {
        this.source = source;
      }

      public void setTarget(RuntimeAsset target) {
        this.target = target;
      }

      public void setRelationship(GraphModel.Relationship relationship) {
        this.relationship = relationship;
      }

      public void setSequence(int sequence) {
        this.sequence = sequence;
      }

      public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
      }

      @Override
      public GraphModel.Relationship type() {
        return relationship;
      }

      @Override
      public Parameters<String> properties() {
        return properties;
      }

      @Override
      public RuntimeAsset source() {
        return source;
      }

      @Override
      public RuntimeAsset target() {
        return target;
      }

      @Override
      public int sequence() {
        return sequence;
      }

      @Override
      public Geometry geometry() {
        return geometry;
      }

      @Override
      public long getId() {
        return -1;
      }

      @Override
      public long getParentId() {
        return -1;
      }

      @Override
      public long getTransientId() {
        return -1;
      }

      @Override
      public int getChildrenCount() {
        return 0;
      }

      @Override
      public long getParentTransientId() {
        return -1;
      }

      @Override
      public Type classify() {
        return Type.LINK;
      }
    }

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

    public TransactionImpl(Activity activity, ServiceContextScope scope, Object... data) {

      this.graph = new DefaultDirectedGraph<>(RelationshipEdge.class);
      this.activity = activity;
      this.scope = scope;
      this.contextualizers = new ConcurrentHashMap<>();

      synchronized (graph) {
        this.graph.addVertex(activity);

        if (data != null) {
          for (Object datum : data) {
            if (datum instanceof Agent agent) {
              this.graph.addVertex(agent);
              this.graph.addEdge(
                  activity, agent, new RelationshipEdge(GraphModel.Relationship.BY_AGENT));
            } else if (datum instanceof Activity activity1) {
              this.graph.addVertex(activity1);
              this.graph.addEdge(
                  activity1, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));
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
      }
      //      scope.send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityStarted,
      // activity);
    }

    private TransactionImpl(TransactionImpl parent, Activity activity) {

      this.graph = parent.graph;
      this.activity = activity;
      this.scope = parent.scope; // TODO careful with executing() being called outside
      this.parent = parent;
      this.contextualizers = parent.contextualizers;

      synchronized (graph) {
        this.graph.addVertex(activity);
        this.graph.addEdge(
            parent.activity, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));
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
    public void add(RuntimeAsset asset) {
      synchronized (graph) {
        graph.addVertex(asset);
      }
      added.add(asset);
    }

    @Override
    public void link(
        RuntimeAsset source,
        RuntimeAsset destination,
        GraphModel.Relationship relationship,
        Object... data) {
      synchronized (graph) {
        graph.addVertex(source);
        graph.addVertex(destination);
        graph.addEdge(source, destination, new RelationshipEdge(relationship, data));
      }

      // TODO do this for all others as well
      if (source instanceof ObservationImpl sourceObs
          && destination instanceof ObservationImpl targetObs
          && relationship == GraphModel.Relationship.HAS_CHILD) {
        sourceObs.setChildrenCount(sourceObs.getChildrenCount() + 1);
        targetObs.setParentTransientId(sourceObs.getTransientId());
      }
    }

    @Override
    public void update(RuntimeAsset asset) {
      modified.add(asset);
    }

    @Override
    public void resolveWith(Observation observation, Executor executor) {
      this.contextualizers.put(observation, executor);
    }

    @Override
    public String commit() {

      if (!failures.isEmpty()) {
        failures.forEach(t -> scope.error(t));
        return null;
      }

      if (activity instanceof ActivityImpl activity1) {
        activity1.setEnd(System.currentTimeMillis());
      }

      // if nothing was done, we just store the HAS_CHILD relationships that point to observations.
      // TODO/CHECK the logics here may require some attention
      var trivial = false;
      synchronized (graph) {
        trivial =
            contextualizers.isEmpty()
                && graph.vertexSet().stream().noneMatch(a -> a instanceof Storage.Shard);
      }

      var ret = Transaction.INTERMEDIATE_COMMIT_ID;

      /*
      Open transaction in the knowledge graph and store everything that needs to, then make all connections
       */
      if (parent == null) {
        var kgTransaction = knowledgeGraph.createTransaction();
        var stored = new ArrayList<Long>();
        var linked = new ArrayList<Triple<Long, Long, GraphModel.Relationship>>();
        try (kgTransaction) {

          synchronized (graph) {
            for (var asset : graph.vertexSet()) {
              if (setupForStorage(asset, trivial)) {
                kgTransaction.store(asset);
                stored.add(asset.getId());
              }
            }

            for (var asset : modified) {
              kgTransaction.update(asset);
            }

            for (var edge : graph.edgeSet()) {
              var source = graph.getEdgeSource(edge);
              var target = graph.getEdgeTarget(edge);

              linked.add(Triple.of(source.getId(), target.getId(), edge.relationship));

              if (trivial
                  && !(target instanceof Observation)
                  && edge.relationship != GraphModel.Relationship.HAS_CHILD) {
                continue;
              }
              var relationshipData = getRelationshipData(edge);
              kgTransaction.link(source, target, edge.relationship, relationshipData);
            }
          }

          if (!trivial) {
            kgTransaction.link(
                knowledgeGraph.provenance(), activity, GraphModel.Relationship.HAS_CHILD);
            linked.add(
                Triple.of(
                    knowledgeGraph.provenance().getId(),
                    activity.getId(),
                    GraphModel.Relationship.HAS_CHILD));
          }

        } catch (Exception e) {
          scope.error(e);
          kgTransaction.fail(e);
          ((ActivityImpl) activity).setOutcome(Activity.Outcome.INTERNAL_FAILURE);
          ((ActivityImpl) activity).setEnd(System.currentTimeMillis());
          ((ActivityImpl) activity).setStackTrace(Utils.Exceptions.stackTrace(e));
          return null;
        } finally {
          // dio sanguinaccio
          try {
            kgTransaction.close();
            var commit = new CommitImpl();
            commit.setId(Utils.Names.shortUUID());
            commit.setTimestamp(System.currentTimeMillis());
            commit.getNewAssets().addAll(stored);
            commit.getNewLinks().addAll(linked);
            commitCache.put(commit.getId(), commit);
            ret = commit.getId();
          } catch (IOException e) {
            Logging.INSTANCE.error(e);
          }
        }
      }

      /* Upon successful commit, establish the ID for any target that was passed in the initialization
       * TODO see if anything else needs to be finalized, like the actuators and the activity */
      if (target != null && target.getId() < 0) {
        for (var asset : graph.vertexSet()) {
          if (asset instanceof ObservationImpl observation
              && observation.getObservable().equals(target.getObservable())
              && target instanceof ObservationImpl targetObservation) {
            targetObservation.setId(asset.getId());
            break;
          }
        }
      }

      ((ActivityImpl) activity).setOutcome(Activity.Outcome.SUCCESS);
      ((ActivityImpl) activity).setEnd(System.currentTimeMillis());

      return ret;
    }

    @Override
    public Transaction getChild(Activity activity, ContextScope scope) {
      return new TransactionImpl(this, activity);
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
        case Actuator actuator -> !trivial;
        case Activity activity -> {
          var ret = activity.getId() < 0 && !trivial;
          if (ret
              && activity.getType() == Activity.Type.RESOLUTION
              && activity.getMetadata().containsKey(Metadata.IM_RESOLUTION_GRAPH)) {
            ret =
                // don't store resolutions that produced nothing, i.e. just the observation is in
                // the graph
                activity
                        .getMetadata()
                        .get(Metadata.IM_RESOLUTION_GRAPH, GraphModel.KnowledgeGraph.class)
                        .getNodes()
                        .size()
                    > 1;
          }
          yield ret;
        }
        case Storage.Shard ignored -> true;
        default -> false;
      };
    }

    @Override
    public Transaction fail(Throwable compilationError) {
      ((ActivityImpl) activity).setOutcome(Activity.Outcome.FAILURE);
      ((ActivityImpl) activity).setEnd(System.currentTimeMillis());
      if (compilationError != null) {
        this.failures.add(compilationError);
        ((ActivityImpl) activity).setStackTrace(Utils.Exceptions.stackTrace(compilationError));
      }
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
    var configuration =
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

  public KnowledgeGraph.Commit getCommit(String id) {
    return commitCache.getIfPresent(id);
  }

  @Override
  public Configuration getOptions() {
    return this.configuration;
  }

  @Override
  public void dispose() {
    this.knowledgeGraph.deleteContext();
    this.storageManager.clear();
  }
}
