package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

public abstract class AbstractResourceContextualizer {

  // These are used to satisfy the dependencies of the methods
  protected Resource resource;
  protected Urn urn;
  protected Parameters<String> urnParameters;
  protected Observation observation;
  protected Observable observable;

  protected AbstractResourceContextualizer(Resource resource, Observation observation) {
    this.resource = resource;
    this.urn = Urn.of(resource.getUrn());
    this.urnParameters = Parameters.create(this.urn.getParameters());
    this.observation = observation;
    this.observable = observation.getObservable();
  }

  public boolean contextualize(Observation observation, ContextScope scope) {
    var data = getData(observation.getGeometry(), scope);
    var reasoner = scope.getService(Reasoner.class);
    if (data == null || data.empty()) {
      return false;
    }
    if (observation.getObservable().is(SemanticType.QUALITY)) {

    } else if (observation.getObservable().is(SemanticType.COUNTABLE)) {
      var cScope = scope.within(observation);
//      Observable obs;
//      for (int i = 0; i < data.getObjectCount(); i++) {
//          obs = obs == null ? data.getObjectScale()
//      }
    }
    return true;
  }

  /**
   * Retrieve all the input data the resource wants.
   *
   * @param scope
   * @return
   */
  protected Data getInputData(ContextScope scope) {
    return null;
  }

  protected abstract Data getData(Geometry geometry, ContextScope scope);
}
