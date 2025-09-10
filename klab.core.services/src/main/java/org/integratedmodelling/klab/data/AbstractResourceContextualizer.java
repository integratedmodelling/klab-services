package org.integratedmodelling.klab.data;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

public abstract class AbstractResourceContextualizer {

  protected Resource resource;
  protected Urn urn;
  protected Parameters<String> urnParameters;
  protected Observation observation;
  protected Observable observable;
  protected ContextScope scope;
  protected final Map<String, Observable> localNames;

  protected AbstractResourceContextualizer(
      Resource resource,
      Observation observation,
      Map<String, Observable> localNames,
      ContextScope scope) {
    this.resource = resource;
    this.urn = Urn.of(resource.getUrn());
    this.urnParameters = Parameters.create(this.urn.getParameters());
    this.observation = observation;
    this.localNames = localNames;
    this.observable = observation.getObservable();
    this.scope = scope;
  }

  public boolean contextualize(Storage.Scanner scanner, Scheduler.Event event) {

    try {
      // FIXME this must be done once per shard using the shard's geometry
      var data = getData(scanner.shard().getGeometry(), event, scope);
      if (data == null || data.empty()) {
        return false;
      }
      var adapters = observation.getMetadata().get(Metadata.KLAB_ADAPTER_URNS, String.class);
      adapters =
          adapters == null || adapters.isEmpty()
              ? resource.getAdapterType()
              : (adapters + "," + resource.getAdapterType());
      observation.getMetadata().put(Metadata.KLAB_ADAPTER_URNS, adapters);

      // FIXME this must be outside, after 1+ contextualizations have been done per shard
      return scope
          .getDigitalTwin()
          .ingest(data, observation, event, /* FIXME DIO CAN */ null, scope);
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
   * @param geometry
   * @param event
   * @param scope
   * @return
   */
  protected abstract Data getData(Geometry geometry, Scheduler.Event event, ContextScope scope);
}
