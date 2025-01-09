package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;

/**
 * One of these is created per resource contextualization request. Drives the functions in the
 * adapter to create the contextualized resource payload, which is an Instance object from the Avro
 * schema.
 */
public class ServiceResourceContextualizer extends AbstractResourceContextualizer {

  private final Adapter adapter;
  private final Resource resource;

  /**
   * Pass a previously contextualized resource
   *
   * @param adapter
   * @param resource
   */
  public ServiceResourceContextualizer(Adapter adapter, Resource resource) {
    this.adapter = adapter;
    this.resource = resource;
  }

  @Override
  protected Data getData(Resource resource, Geometry geometry, ContextScope scope) {
    Data.Builder builder = Data.builder();
    adapter.encode(resource, geometry, builder, scope);
    return builder.build();
  }
}
