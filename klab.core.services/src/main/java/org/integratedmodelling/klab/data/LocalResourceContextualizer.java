package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;

import java.util.Map;

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
      Map<String, Observable> localNames,
      ContextScope scope) {
    super(resource, observation, localNames, scope);
    this.adapter = adapter;
  }

  @Override
  protected Data getData(Geometry geometry, Scheduler.Event event, ContextScope scope) {

    var name =
        observation.getObservable().getStatedName() == null
            ? observation.getObservable().getUrn()
            : observation.getObservable().getStatedName();

    // FIXME needs a server-side builder that uses the DT's buffers
    var builder = new DirectDataBuilder(name, getInputData(scope), observation, scope);

    // TODO add observation, observable, urn, input data if the resource requires them, observation
    //  storage and anything the adapter may want.
    adapter.encode(
        resource, geometry, event, builder, observation, observable, urn, urnParameters, scope);
    return builder.build();
  }
}
