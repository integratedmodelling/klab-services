package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.common.data.DoubleDataImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.runtime.knowledge.DataflowGraph;
import org.integratedmodelling.klab.runtime.knowledge.ProvenanceGraph;
import org.integratedmodelling.klab.runtime.storage.StorageManagerImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class DigitalTwinImpl implements DigitalTwin {

  private final KnowledgeGraph knowledgeGraph;
  private final StorageManager storageManager;
  private final ContextScope rootScope;
  private final Scheduler scheduler;

  public DigitalTwinImpl(
      RuntimeService service, ServiceContextScope scope, KnowledgeGraph database) {
    this.rootScope = scope;
    this.knowledgeGraph = database.contextualize(scope);
    this.storageManager = new StorageManagerImpl(service, scope);
    this.scheduler = new SchedulerImpl(scope, this);
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
