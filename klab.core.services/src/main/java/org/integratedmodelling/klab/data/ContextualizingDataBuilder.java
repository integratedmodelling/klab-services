package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** The builder to use when the digital twin is available locally. */
public class ContextualizingDataBuilder implements Data.Builder {
  public ContextualizingDataBuilder(
      String name, Observation observation, DigitalTwin digitalTwin) {}

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
  public <T extends Storage.Shard> T scanner(Class<T> scannerClass) {
    return null;
  }

  @Override
  public <T extends Storage.Shard> T scanner(String identifier, Class<T> scannerClass) {
    return null;
  }

  @Override
  public Data build() {
    return null;
  }
}
