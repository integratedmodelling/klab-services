package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.common.data.DoubleDataImpl;
import org.integratedmodelling.common.data.IntDataImpl;
import org.integratedmodelling.common.data.LongDataImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
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
import org.integratedmodelling.klab.runtime.storage.IntStorage;
import org.integratedmodelling.klab.runtime.storage.LongStorage;
import org.integratedmodelling.klab.runtime.storage.StateStorageImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

public class DigitalTwinImpl implements DigitalTwin {

  KnowledgeGraph knowledgeGraph;
  StateStorage stateStorage;
  ContextScope rootScope;

  public DigitalTwinImpl(RuntimeService service, ContextScope scope, KnowledgeGraph database) {
    this.rootScope = scope;
    this.knowledgeGraph = database.contextualize(scope);
    this.stateStorage = new StateStorageImpl(service, scope);
  }

  @Override
  public KnowledgeGraph knowledgeGraph() {
    return this.knowledgeGraph;
  }

  @Override
  public StateStorage stateStorage() {
    return this.stateStorage;
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
      var storage = scope.getDigitalTwin().stateStorage().getOrCreateStorage(target, Storage.class);

      if (data instanceof DoubleDataImpl doubleData) {
        // TODO handle floats
        var doubleStorage =
            scope
                .getDigitalTwin()
                .stateStorage()
                .promoteStorage(target, storage, DoubleStorage.class);
        var buffer = doubleStorage.buffer(data.geometry(), data.fillCurve());
        while (doubleData.hasNext()) {
          buffer.add(doubleData.nextDouble());
        }
      } else if (data instanceof LongDataImpl longData) {
        var longStorage =
            scope
                .getDigitalTwin()
                .stateStorage()
                .promoteStorage(target, storage, LongStorage.class);
        var buffer = longStorage.buffer(data.geometry(), data.fillCurve());
        while (longData.hasNext()) {
          buffer.add(longData.nextLong());
        }
      } else if (data instanceof IntDataImpl intData) {
        var key = intData.getDataKey();
        if (key == null) {
          var intStorage =
                  scope
                          .getDigitalTwin()
                          .stateStorage()
                          .promoteStorage(target, storage, IntStorage.class);
          var buffer = intStorage.buffer(data.geometry(), data.fillCurve());
          while (intData.hasNext()) {
            buffer.add(intData.nextInt());
          }
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
