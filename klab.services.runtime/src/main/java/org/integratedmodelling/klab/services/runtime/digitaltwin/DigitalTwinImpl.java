package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.common.data.DoubleDataImpl;
import org.integratedmodelling.common.data.IntDataImpl;
import org.integratedmodelling.common.data.LongDataImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StateStorage;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.runtime.knowledge.DataflowGraph;
import org.integratedmodelling.klab.runtime.knowledge.ProvenanceGraph;
import org.integratedmodelling.klab.runtime.storage.DoubleStorage;
import org.integratedmodelling.klab.runtime.storage.LongStorage;
import org.integratedmodelling.klab.runtime.storage.IntStorage;
import org.integratedmodelling.klab.runtime.storage.StateStorageImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

public class DigitalTwinImpl implements DigitalTwin {

  private final KnowledgeGraph knowledgeGraph;
  private final StateStorage stateStorage;
  private final ContextScope rootScope;
  private final Scheduler scheduler;

  public DigitalTwinImpl(
      RuntimeService service, ServiceContextScope scope, KnowledgeGraph database) {
    this.rootScope = scope;
    this.knowledgeGraph = database.contextualize(scope);
    this.stateStorage = new StateStorageImpl(service, scope);
    this.scheduler = new SchedulerImpl();
  }

  @Override
  public KnowledgeGraph getKnowledgeGraph() {
    return this.knowledgeGraph;
  }

  @Override
  public StateStorage getStateStorage() {
    return this.stateStorage;
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

      // TODO negotiate any partial fill strategy from parallelized access to the storage
      var storage =
          scope.getDigitalTwin().getStateStorage().getOrCreateStorage(target, Storage.class);

      if (data instanceof DoubleDataImpl doubleData) {
        // TODO handle floats
        var doubleStorage =
            scope
                .getDigitalTwin()
                .getStateStorage()
                .promoteStorage(target, storage, DoubleStorage.class);
        var buffer = doubleStorage.buffer(data.geometry(), data.fillCurve());
        var filler = buffer.filler(Data.DoubleFiller.class);
        while (doubleData.hasNext()) {
          filler.add(doubleData.nextDouble());
        }
        return true;
      } else if (data instanceof LongDataImpl longData) {
        var longStorage =
            scope
                .getDigitalTwin()
                .getStateStorage()
                .promoteStorage(target, storage, LongStorage.class);
        var buffer = longStorage.buffer(data.geometry(), data.fillCurve());
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
          var buffer = intStorage.buffer(data.geometry(), data.fillCurve());
          var filler = buffer.filler(Data.IntFiller.class);
          while (intData.hasNext()) {
            filler.add(intData.nextInt());
          }
          return true;
        } else {
          // TODO have the data object adapt the key to the observable before use
          var table = new HashMap<Integer, Object>();
        }
      }
    }

    return false;
  }

  @Override
  public Provenance getProvenanceGraph(ContextScope context) {
    return new ProvenanceGraph(this.knowledgeGraph, this.rootScope);
  }

  @Override
  public Dataflow<Observation> getDataflowGraph(ContextScope context) {
    return new DataflowGraph(this.knowledgeGraph, this.rootScope);
  }

  @Override
  public void dispose() {
    this.knowledgeGraph.deleteContext();
    this.stateStorage.clear();
  }
}
