package org.integratedmodelling.klab.data;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AbstractResourceContextualizer {

  private static final Logger log = LoggerFactory.getLogger(AbstractResourceContextualizer.class);
  protected Resource resource;
  protected Urn urn;
  protected Parameters<String> urnParameters;
  protected Observation observation;
  protected Observable observable;
  protected final Map<String, Observation> dependencies = new HashMap<>();

  protected AbstractResourceContextualizer(
      Resource resource, Observation observation, Map<String, Observation> dependencies) {
    this.resource = resource;
    this.urn = Urn.of(resource.getUrn());
    this.urnParameters = Parameters.create(this.urn.getParameters());
    this.observation = observation;
    this.observable = observation.getObservable();
  }

  public boolean contextualize(Storage.Scanner scanner, Scheduler.Event event, ContextScope scope) {

    if (observation.getContextualizationData()
        instanceof ObservationImpl.ContextualizationDataImpl contextualizationData) {
      contextualizationData.setAdapterId(resource.getAdapterType());
      contextualizationData.getParameters().putAll(resource.getParameters());
    }

    try {
      var builder = getData(scanner, event, scope);

      // TODO the shards have been filled. Update shard data in the transaction for KG update
      if (scanner != null) {
        // TODO
      }

      this.observation.getNotifications().addAll(builder.getNotifications());

      if (Utils.Notifications.hasErrors(builder.getNotifications())) {
        return false;
      }

      // FIXME this is fundamental logic and must be brought upstream (something like handleAdapterResponse() in
      //  the DT). In the handler, each collective description must trigger the correspondent individual resolution.
      //  Should be accomplished through an "execution scope" provided by the runtime at dataflow run.
      if (observable.is(SemanticType.COUNTABLE)) {
        // scope contextualized to the collective observation
        var observationScope = scope.within(observation);
        List<Callable<Observation>> tasks = new ArrayList<>();
        if (observation instanceof ObservationImpl observationImpl) {
          observationImpl.setChildrenCount(builder.getObjects().size());
        }
        for (var instance : builder.getObjects()) {
          var child = instance.getObservation();
          if (child != null) {
            // ingest the observation according to the native shards
            // TODO this must be brought upstream
            tasks.add(
                Executors.callable(
                    () -> {
                      var result =
                          observationScope
                              .getService(RuntimeService.class)
                              .submit(child, observationScope)
                              .thenAccept(
                                  (obs -> {
                                    // TODO if states are there, should use a `klab.inline` adapter
                                    //  that just matches a buffer to the geometry, and
                                    //  the inline method for the observation resolution.
                                    /*                                                                    // resolve any child observations, states or instances
                                        if (instance.hasStates() || instance.size() > 0) {
                                            ingest(
                                                    instance,
                                                    child,
                                                    event,
                                                    // FIXME not sure - child observations should have their own
                                                    // strategy, so null?
                                                    null,
                                                    observationScope);
                                        }
                                    */ }));
                    },
                    child));
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

      return true;

    } catch (Exception e) {
      scope.error(e);
      return false;
    }
  }

  /**
   * Retrieve all the input data the resource wants.
   *
   * @param scope
   * @return
   */
  protected Data getInputData(ContextScope scope) {
    // TODO
    return null;
  }

  /**
   * Invoke the service API or, if the adapter is local, create a Data.Builder and pass it to the
   * adapter for direct retrieval.
   *
   * @param scanner
   * @param event
   * @param scope
   * @return TODO CHECK this shouldn't need to return Data in all situations, just when remote, so
   *     absorb into the specific API
   */
  protected abstract Data.Builder getData(
      Storage.Scanner scanner, Scheduler.Event event, ContextScope scope);
}
