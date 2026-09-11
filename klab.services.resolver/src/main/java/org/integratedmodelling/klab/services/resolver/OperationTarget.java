package org.integratedmodelling.klab.services.resolver;

import java.util.Collection;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;

/** Resolver-local planning target. Never registered, persisted or transported as an observation. */
record OperationTarget(Observable observable, Observation context) implements Resolvable {
  public String getUrn() {
    return observable.getUrn();
  }

  public Metadata getMetadata() {
    return observable.getMetadata();
  }

  public String getServiceId() {
    return observable.getServiceId();
  }

  public Collection<Annotation> getAnnotations() {
    return observable.getAnnotations();
  }
}
