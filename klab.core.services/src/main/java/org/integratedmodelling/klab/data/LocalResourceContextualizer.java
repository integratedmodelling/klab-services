package org.integratedmodelling.klab.data;

import java.util.Map;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;

/**
 * Service-side contextualization helper, used when an embeddable adapter is available locally in
 * the runtime. One of these is created per resource contextualization request. Drives the functions
 * in the adapter to create the contextualized resource payload directly within {@link
 * org.integratedmodelling.klab.api.digitaltwin.StorageManager} buffers.
 */
public class LocalResourceContextualizer extends AbstractResourceContextualizer {

  private final Adapter adapter;

  /**
   * Pass a previously contextualized resource
   *
   * @param adapter
   * @param resource
   */
  public LocalResourceContextualizer(
      Adapter adapter,
      Resource resource,
      Observation observation,
      Map<String, Observation> dependencies) {
    super(resource, observation, dependencies);
    this.adapter = adapter;
  }

  @Override
  protected Data.Builder getData(
      Storage.Scanner scanner, Scheduler.Event event, ContextScope scope) {

    var name =
        observation.getObservable().getStatedName() == null
            ? observation.getObservable().getUrn()
            : observation.getObservable().getStatedName();

    // FIXME needs a server-side builder that uses the DT's buffers
    // FIXME doesn't need a builder unless the function requires one
    // FIXME the input data should be injected by name
    var builder = new DirectDataBuilder(name, getInputData(scope), observation, scope, null);

    if (scanner != null) {
      for (var entry : dependencies.keySet()) {
        Observation observation =
            dependencies.get(entry); // TODO get the obs with the keyed observable
        if (observation != null) {
          var storage = scope.getDigitalTwin().getStorageManager().getStorage(observation);
          var shards = storage.scan(event, scanner.shard().getShardingStrategy(), null, true);
          scanner = shards.get(scanner.shard().getShardIndex());
        }
      }
    }

    // TODO add observation, observable, urn, input data if the resource requires them, observation
    //  storage and anything the adapter may want.
    adapter.encode(
        resource,
        scanner == null ? observation.getGeometry() : scanner.shard().getGeometry(),
        event,
        builder,
        scanner,
        observation,
        observable,
        urn,
        urnParameters,
        scope);

    return builder;
  }
}
