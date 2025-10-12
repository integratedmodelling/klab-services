package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.Map;

/**
 * One of these is created per resource contextualization operation. Drives the functions in the
 * adapter to create the contextualized resource payload, which is an Instance object from the Avro
 * schema.
 */
public class RemoteResourceContextualizer extends AbstractResourceContextualizer {

  private final ResourcesService service;

  /**
   * Pass a resource to have it contextualized by a remote resources service.
   *
   * @param service
   * @param resource
   */
  public RemoteResourceContextualizer(
      ResourcesService service,
      Resource resource,
      Observation observation,
      Map<String, Observable> localNames) {
    super(resource, observation, localNames);
    this.service = service;
  }

  @Override
  protected Data.Builder getData(
      Storage.Scanner scanner, Scheduler.Event event, ContextScope scope) {
    try {
      var data =
          service
              .contextualize(
                  resource,
                  observation,
                  scanner.shard().getGeometry(),
                  event,
                  getInputData(scope),
                  scope)
              // this one is synchronous, called within a CompletableFuture anyway
              .get();

      return new WrappingDataBuilder(
              data, observation, event, scanner, resource.getAdapterType(), scope)
          .fillShards();

    } catch (Exception e) {
      throw new KlabResourceAccessException(e);
    }
  }
}
