package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.common.data.Instance;

/**
 * One of these is created per resource contextualization operation. Drives the functions in the
 * adapter to create the contextualized resource payload, which is an Instance object from the Avro
 * schema.
 */
public class ClientResourceContextualizer {

  private final ResourcesService service;
  private final Resource resource;

  /**
   * Pass a previously contextualized resource
   *
   * @param service
   * @param resource
   */
  public ClientResourceContextualizer(ResourcesService service, Resource resource) {
    this.service = service;
    this.resource = resource;
  }

  public boolean contextualize(Observation observation, ContextScope scope) {
    return false;
  }

  /**
   * Produce the contextualized data from the resource in the passed geometry. Any errors will end
   * up in the Instance notifications.
   *
   * @return
   */
  private Instance getData() {

    var builder = Instance.newBuilder();

    return builder.build();
  }
}
