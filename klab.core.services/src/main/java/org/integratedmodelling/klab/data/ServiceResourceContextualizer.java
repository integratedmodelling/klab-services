package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;

/**
 * Service-side contextualization helper, used when the adapter is available locally. One of these
 * is created per resource contextualization request. Drives the functions in the adapter to create
 * the contextualized resource payload directly within {@link
 * org.integratedmodelling.klab.api.digitaltwin.StorageManager} buffers.
 */
public class ServiceResourceContextualizer extends AbstractResourceContextualizer {

  private final Adapter adapter;
  private final DigitalTwin digitalTwin;

  /**
   * Pass a previously contextualized resource
   *
   * @param adapter
   * @param resource
   */
  public ServiceResourceContextualizer(
      Adapter adapter, Resource resource, Observation observation, DigitalTwin digitalTwin) {
    super(resource, observation);
    this.adapter = adapter;
    this.digitalTwin = digitalTwin;
  }

  @Override
  protected Data getData(Geometry geometry, Scheduler.Event event, ContextScope scope) {

    var name =
        observation.getObservable().getStatedName() == null
            ? observation.getObservable().getUrn()
            : observation.getObservable().getStatedName();

    // FIXME needs a server-side builder that uses the DT's buffers
    var builder = new ContextualizingDataBuilder(name, getInputData(scope), observation, digitalTwin);

    // TODO add observation, observable, urn, input data if the resource requires them, observation
    //  storage and anything the adapter may want.
    adapter.encode(
        resource,
        geometry,
        event,
        builder,
        observation,
        observable,
        urn,
        urnParameters,
//        inputData,
        scope);
    return builder.build();
  }
}
