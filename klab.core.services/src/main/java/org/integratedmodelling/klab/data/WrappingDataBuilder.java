package org.integratedmodelling.klab.data;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WrappingDataBuilder extends ScannerAdapter implements Data.Builder {

  private final Data data;
  private final Observation observation;
  private final ContextScope scope;
  private final String adapterId;
  private final Scheduler.Event event;
  private final Storage.Scanner scanner;
  private final List<Notification> notifications = new ArrayList<>();

  public WrappingDataBuilder(
      Data data,
      Observation observation,
      Scheduler.Event event,
      Storage.Scanner scanner,
      String adapterId,
      ContextScope scope) {
    this.data = data;
    this.observation = observation;
    this.scope = scope;
    this.adapterId = adapterId;
    this.event = event;
    this.scanner = scanner;
  }

  @Override
  public Data.Builder notification(Notification notification) {
    notifications.add(notification);
    return this;
  }

  @Override
  public List<Data.Builder> getObjects() {
    var ret = new ArrayList<Data.Builder>();
    var reasoner = scope.getService(Reasoner.class);
    for (var child : data.children()) {
      var observable = reasoner.resolveObservable(child.semantics());
      var observation =
          DigitalTwin.createObservation(
              scope, child.name(), observable, child.geometry(), child.metadata());
      if (observation.getContextualizationData()
          instanceof ObservationImpl.ContextualizationDataImpl contextualizationData) {
        contextualizationData.setAdapterId(adapterId);
        // TODO may need more
      }

      ret.add(new WrappingDataBuilder(child, observation, event, null, adapterId, scope));
    }

    return ret;
  }

  @Override
  public Data.Builder adapter(String adapterId) {
    return null;
  }

  @Override
  public Data.Builder metadata(String key, Object value) {
    observation.getMetadata().put(key, value);
    return this;
  }

  @Override
  public Data.Builder state(String outputId) {
    // TODO DIOCAN
    return null;
  }

  @Override
  public Data.Builder object(String name, Observable observable, Geometry geometry) {
    throw new KlabIllegalStateException("Operation not admitted on a wrapping data builder.");
  }

  @Override
  public <T extends Storage.Scanner> T scanner(Class<T> scannerClass) {
    // NO DIOCAN
    return adapt(scanner, scannerClass);
  }

  @Override
  public <T extends Storage.Scanner> T scanner(String identifier, Class<T> scannerClass) {
    // NO HOSTIA
    return null;
  }

  @Override
  public Observation getObservation() {
    return observation;
  }

  @Override
  public Collection<Notification> getNotifications() {
    return this.notifications;
  }

  public Data.Builder fillShards() {
    return this;
  }
}
