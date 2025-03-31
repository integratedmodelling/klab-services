package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.common.data.DoubleDataImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class DigitalTwinImpl implements DigitalTwin {

  private final KnowledgeGraphNeo4j knowledgeGraph;
  private final StorageManager storageManager;
  private final ContextScope rootScope;
  private final Scheduler scheduler;

  public class TransactionImpl implements Transaction {

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

    private Observation target;
    private Activity activity;
    private ServiceContextScope scope;
    private List<Throwable> failures = new ArrayList<>();
    private Graph<RuntimeAsset, RelationshipEdge> graph =
        new DefaultDirectedGraph<>(RelationshipEdge.class);
    private Map<Observation, Executor> contextualizers = new HashMap<>();

    public TransactionImpl(Activity activity, ServiceContextScope scope, Object... data) {
      this.activity = activity;
      this.scope = scope;
      this.graph.addVertex(activity);
      if (data != null) {
        for (int i = 0; i < data.length; i++) {
          if (data[i] instanceof Agent agent) {
            this.graph.addVertex(agent);
            this.graph.addEdge(
                activity, agent, new RelationshipEdge(GraphModel.Relationship.BY_AGENT));
          } else if (data[i] instanceof Activity activity1) {
            this.graph.addVertex(activity1);
            this.graph.addEdge(
                activity, activity1, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));
          }
        }
      }
    }

    public void setTarget(Observation observation) {
      this.target = observation;
      this.graph.addVertex(observation);
      this.graph.addEdge(
          activity, observation, new RelationshipEdge(GraphModel.Relationship.CREATED));
    }

    @Override
    public Activity getActivity() {
      return this.activity;
    }

    @Override
    public void add(RuntimeAsset asset) {
      graph.addVertex(asset);
    }

    @Override
    public void link(
        RuntimeAsset source,
        RuntimeAsset destination,
        GraphModel.Relationship relationship,
        Object... data) {
      graph.addVertex(source);
      graph.addVertex(destination);
      graph.addEdge(source, destination, new RelationshipEdge(relationship, data));
    }

    @Override
    public void resolveWith(Observation observation, Executor executor) {
      this.contextualizers.put(observation, executor);
    }

    @Override
    public boolean commit() {

      if (!failures.isEmpty()) {
        failures.forEach(t -> scope.error(t));
        return false;
      }

      /*
      Open transaction in the knowledge graph and store everything that needs to, then make all connections
       */
      try (var kgTransaction = knowledgeGraph.createTransaction()) {

        for (var asset : graph.vertexSet()) {
          if (setupForStorage(asset)) {
            kgTransaction.store(asset);
          }
        }

        for (var edge : graph.edgeSet()) {
          var source = graph.getEdgeSource(edge);
          var target = graph.getEdgeTarget(edge);
          kgTransaction.link(source, target, edge.relationship, getRelationshipData(edge));
        }

        kgTransaction.link(
            knowledgeGraph.provenance(), activity, GraphModel.Relationship.HAS_CHILD);

      } catch (Exception e) {
        scope.error(e);
        return false;
      }

      for (var observation : contextualizers.keySet()) {
        scheduler.registerExecutor(
            observation, (g, e, s) -> contextualizers.get(observation).run(g, e, s));
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

      return true;
    }

    private Object[] getRelationshipData(RelationshipEdge edge) {
      var ret = new ArrayList<Object>();
      if (edge.relationship == GraphModel.Relationship.AFFECTS) {
        ret.add("sequence");
        ret.add(edge.sequence);
      }
      return ret.toArray();
    }

    private boolean setupForStorage(RuntimeAsset asset) {
      return switch (asset) {
        case Observation observation -> observation.getId() < 0;
        case Actuator actuator -> true;
        case Activity activity -> true;
        default -> false;
      };
    }

    @Override
    public Transaction fail(Throwable compilationError) {
      this.failures.add(compilationError);
      return this;
    }
  }

  public DigitalTwinImpl(
      RuntimeService service, ServiceContextScope scope, KnowledgeGraphNeo4j database) {
    this.rootScope = scope;
    this.knowledgeGraph = (KnowledgeGraphNeo4j) database.contextualize(scope);
    this.storageManager = new StorageManagerImpl(service, scope);
    this.scheduler = new SchedulerImpl(scope, this);
  }

  @Override
  public Transaction transaction(Activity activity, ContextScope scope, Object... runtimeAssets) {
    var ret = new TransactionImpl(activity, (ServiceContextScope) scope);
    if (runtimeAssets != null) {
      for (var asset : runtimeAssets) {
        if (asset instanceof Observation observation) {
          ret.setTarget(observation);
        } else if (asset instanceof Dataflow dataflow) {
          // serialize and record the dataflow with the activity
          if (activity instanceof ActivityImpl activity1) {
            activity1.setDescription(Utils.Dataflows.encode(dataflow, scope));
          }
        }
      }
    }
    return ret;
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
  public boolean ingest(Data data, Observation target, ContextScope scope) {

    if (target.getObservable().is(SemanticType.COUNTABLE)) {
      // scope contextualized to the collective observation
      var observationScope = scope.within(target);
      List<Callable<Observation>> tasks = new ArrayList<>();
      for (var instance : data.children()) {
        var observation = DigitalTwin.createObservation(observationScope, instance);
        if (observation != null) {
          // TODO ingest the observation. Must have an operation in the scope.
          tasks.add(
              Executors.callable(
                  () -> {
                    var result =
                        observationScope
                            .observe(observation)
                            .thenAccept(
                                (obs -> {
                                  // resolve any child observations, states or instances
                                  if (instance.hasStates() || instance.size() > 0) {
                                    ingest(instance, observation, observationScope);
                                  }
                                }));
                  },
                  observation));
        }
      }
      if (!tasks.isEmpty()) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
          return executor.invokeAll(tasks).stream().noneMatch(Future::isCancelled);
        } catch (InterruptedException e) {
          scope.error(e);
          return false;
        }
      }
    }

    if (data.hasStates()) {

      // TODO the observation should have collected @split and @fillcurve annotations from models
      // and observables,
      //  along with colormap from concepts and all that.
      var storage =
          scope
              .getDigitalTwin()
              .getStorageManager()
              .getStorage(
                  target, Utils.Annotations.mergeAnnotations("storage", data, target, scope));

      if (data instanceof DoubleDataImpl doubleData) {

        var buffers = storage.buffers(data.geometry(), Storage.DoubleBuffer.class);

        /* all buffers run in parallel */
        return Utils.Java.distributeComputation(
            buffers,
            buffer -> {
              var scanner = buffer.scan();
              while (doubleData.hasNext()) {
                scanner.add(doubleData.nextDouble());
              }
            });
      } /*else if (data instanceof LongDataImpl longData) {
          var longStorage =
              scope
                  .getDigitalTwin()
                  .getStateStorage()
                  .promoteStorage(target, storage, LongStorage.class);
          var buffer =
              longStorage.buffer(
                  data.geometry().size(), data.fillCurve(), data.geometry().getExtentOffsets());
          var filler = buffer.filler(Data.LongFiller.class);
          while (longData.hasNext()) {
            filler.add(longData.nextLong());
          }
        } else if (data instanceof IntDataImpl intData) {
          var key = intData.getDataKey();
          if (key == null) {
            var intStorage =
                scope
                    .getDigitalTwin()
                    .getStateStorage()
                    .promoteStorage(target, storage, IntStorage.class);
            var buffer =
                intStorage.buffer(
                    data.geometry().size(), data.fillCurve(), data.geometry().getExtentOffsets());
            var filler = buffer.filler(Data.IntFiller.class);
            while (intData.hasNext()) {
              filler.add(intData.nextInt());
            }
            return true;
          } else {
            // TODO have the data object adapt the key to the observable before use
            var table = new HashMap<Integer, Object>();
          }
        }*/
    }

    return false;
  }

  @Override
  public Provenance getProvenanceGraph(ContextScope context) {
    return new ProvenanceGraph(this.knowledgeGraph, this.rootScope);
  }

  @Override
  public Dataflow getDataflowGraph(ContextScope context) {
    return new DataflowGraph(this.knowledgeGraph, this.rootScope);
  }

  @Override
  public void dispose() {
    this.knowledgeGraph.deleteContext();
    this.storageManager.clear();
  }
}
