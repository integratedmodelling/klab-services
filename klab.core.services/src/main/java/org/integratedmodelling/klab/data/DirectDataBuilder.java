package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** The builder to use when the digital twin is available locally. */
public class DirectDataBuilder implements Data.Builder {
  public DirectDataBuilder(
          String name, Data inputData, Observation observation, ContextScope contextScope) {}

  @Override
  public Data.Builder notification(Notification notification) {
    return null;
  }

  @Override
  public Data.Builder adapter(String adapterId) {
    return null;
  }

  @Override
  public Data.Builder metadata(String key, Object value) {
    return null;
  }

  @Override
  public Data.Builder state(String observable) {
    return null;
  }

  @Override
  public Data.Builder object(String name, Observable observable, Geometry geometry) {
    return null;
  }

  @Override
  public <T extends Storage.Scanner> T scanner(Class<T> scannerClass) {
    return null;
  }

  @Override
  public <T extends Storage.Scanner> T scanner(String identifier, Class<T> scannerClass) {
    return null;
  }

//  @Override
  public Data build() {
    return null;
  }
}
