package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.List;

public class WrappingDataBuilder implements Data.Builder {

  private final Data data;
  private final Observation observation;

    public WrappingDataBuilder(
      Data data, Observation observation, Scheduler.Event event, Storage.Scanner scanner, ContextScope scope) {
    this.data = data;
    this.observation = observation;
  }

  @Override
  public Data.Builder notification(Notification notification) {
    return null;
  }

  @Override
  public List<Data.Builder> getObjects() {
    for (var child : data.children()) {}

    return List.of();
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
  public Data.Builder state(String outputId) {
    return null;
  }

  @Override
  public Data.Builder object(String name, Observable observable, Geometry geometry) {
    throw new KlabIllegalStateException("Operation not admitted on a wrapping data builder.");
  }

  @Override
  public <T extends Storage.Scanner> T scanner(Class<T> scannerClass) {
    return null;
  }

  @Override
  public <T extends Storage.Scanner> T scanner(String identifier, Class<T> scannerClass) {
    return null;
  }

    @Override
    public Observation getObservation() {
        return null;
    }

    public Data.Builder fillShards() {
    return this;
  }
}
