package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabContextualizationException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.concurrent.ExecutionException;

/**
 * One of these is created per resource contextualization operation. Drives the functions in the
 * adapter to create the contextualized resource payload, which is an Instance object from the Avro
 * schema.
 */
public class ClientResourceContextualizer extends AbstractResourceContextualizer {

  private final ResourcesService service;

  /**
   * Pass a previously contextualized resource
   *
   * @param service
   * @param resource
   */
  public ClientResourceContextualizer(
      ResourcesService service, Resource resource, Observation observation) {
    super(resource, observation);
    this.service = service;
  }

  @Override
  protected Data getData(Geometry geometry, Scheduler.Event event, ContextScope scope) {
    try {
      // this one is synchronous, called within a CompletableFuture anyway
      return service.contextualize(resource, observation, event, getInputData(scope), scope).get();
    } catch (Exception e) {
      throw new KlabResourceAccessException(e);
    }
  }
}
