package org.integratedmodelling.klab.api.knowledge.observation.impl;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.scope.ContextScope;

public abstract class ObservationBuilderImpl implements Observation.Builder {

  private final Observable observable;
  private final ContextScope scope;
  private Urn identity;
  private Geometry geometry;
  private Object defaultValue;
  private Metadata metadata = Metadata.create();
  private Observation.ContextualizationData contextualizationData;

  public ObservationBuilderImpl(Observable observable, ContextScope contextScope) {
    this.scope = contextScope;
    this.observable = observable;
  }

  @Override
  public Observation.Builder geometry(Geometry geometry) {
    return this;
  }

  @Override
  public Observation.Builder definition(KimSymbolDefinition definition) {
    return this;
  }

  @Override
  public Observation.Builder contextualizationData(
      Observation.ContextualizationData contextualizationData) {
    return null;
  }

  @Override
  public Observation.Builder value(Object value) {
    return this;
  }

  @Override
  public Observation.Builder identity(String namespace, String name) {
    return this;
  }

  @Override
  public Observation.Builder metadata(String key, Object value) {
    return this;
  }

  protected ObservationImpl build() {
    ObservationImpl ret = new ObservationImpl();
    ret.setGeometry(geometry);
    ret.getMetadata().putAll(metadata);
    ret.setObservable(observable);
    ret.setValue(defaultValue);
    ret.setType(observable.getArtifactType());
    ret.setContextualizationData(contextualizationData);

    if (identity != null) {
      // mandatory for substantials, and must be namespace:name
      if (identity.length() != 2) {
        scope.error("Identity must be in the form namespace:id");
        return null;
      }
      if (!observable.getSemantics().isCollective()
          && SemanticType.isSubstantial(observable.getSemantics().getType())) {
        ret.setUrn(identity.getUrn());
      }
    } else if (!observable.getSemantics().isCollective()
        && SemanticType.isSubstantial(observable.getSemantics().getType())) {
      scope.error(
          "Observations of individual substantials must specify a unique identity, passed as a Urn object <namespace>:<identifier>");
      return null;
    }

    return ret;
  }
}
