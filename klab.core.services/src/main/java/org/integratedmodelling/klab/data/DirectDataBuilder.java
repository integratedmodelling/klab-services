package org.integratedmodelling.klab.data;

import java.util.*;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** The builder to use when the digital twin is available locally. */
public class DirectDataBuilder extends ScannerAdapter implements Data.Builder {

  //  private final Map<String, Storage.Scanner> scanners = new HashMap<>();
  private final ContextScope scope;
  private final String name;
  private final Data inputData;
  private final Observation observation;
  private List<Data.Builder> objects = new ArrayList<>();
  private final List<Notification> notifications;
  private final List<Observation> cessations;
  private Urn identity;

  public DirectDataBuilder(
      String name,
      Data inputData,
      Observation observation,
      ContextScope contextScope,
      Urn identity) {
    this(
        name, inputData, observation, contextScope, identity, new ArrayList<>(), new ArrayList<>());
  }

  private DirectDataBuilder(
      String name,
      Data inputData,
      Observation observation,
      ContextScope contextScope,
      Urn identity,
      List<Observation> cessations,
      List<Notification> notifications) {
    this.cessations = cessations;
    this.notifications = notifications;
    this.scope = contextScope;
    this.name = name;
    this.inputData = inputData;
    this.observation = observation;
    this.identity = identity;
  }

  private DirectDataBuilder(DirectDataBuilder other) {
    this.cessations = other.cessations;
    this.notifications = other.notifications;
    this.scope = other.scope;
    this.name = other.name;
    this.inputData = other.inputData;
    this.observation = other.observation;
    this.identity = other.identity;
    //    this.scanners.putAll(other.scanners);
  }

  @Override
  public Data.Builder notification(Notification notification) {
    this.notifications.add(notification);
    return this;
  }

  @Override
  public List<Data.Builder> getObjects() {
    return objects;
  }

  @Override
  public Data.Builder cease(Observation target) {
    if (target == null
        || target.getId() <= 0
        || target.isEmpty()
        || target.getObservable() == null
        || target.getObservable().getSemantics() == null
        || target.getObservable().getSemantics().isCollective()
        || target
            .getObservable()
            .is(org.integratedmodelling.klab.api.knowledge.SemanticType.CONFIGURATION)
        || !(target
                .getObservable()
                .is(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT)
            || target
                .getObservable()
                .is(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT)))
      throw new IllegalArgumentException(
          "Cessation requires an existing singular Subject or Agent");
    // Preserve the exact supplied handle. Resolving a DTO by ID here would invent authority.
    synchronized (cessations) {
      if (cessations.stream().noneMatch(existing -> existing == target)) cessations.add(target);
    }
    return this;
  }

  @Override
  public List<Observation> getCessations() {
    synchronized (cessations) {
      return List.copyOf(cessations);
    }
  }

  @Override
  public Data.Builder identity(String namespace, String id) {
    this.identity = Urn.of(namespace + ":" + id);
    return this;
  }

  //  @Override
  //  public Data.Builder adapter(String adapterId) {
  //    return this;
  //  }

  @Override
  public Data.Builder metadata(String key, Object value) {
    observation.getMetadata().put(key, value);
    return this;
  }

  @Override
  public Data.Builder state(String observableKey) {
    var ret = new DirectDataBuilder(this);
    // TODO find the observation and substitute it
    // TODO change the "self" scanner to the one from the observation and put the master obs in with
    //  its name
    return this;
  }

  @Override
  public Data.Builder object(String name, Observable observable, Geometry geometry, Urn identity) {
    var observation =
        scope.observation(observable).geometry(geometry).identity(identity).register();
    var builder =
        new DirectDataBuilder(name, null, observation, scope, identity, cessations, notifications);
    objects.add(builder);
    return builder;
  }

  //  @Override
  //  public <T extends Storage.Scanner> T scanner(Class<T> scannerClass) {
  //    return adapt(scanners.get(Dataflow.SELF_ID), scannerClass);
  //  }
  //
  //  @Override
  //  public <T extends Storage.Scanner> T scanner(String identifier, Class<T> scannerClass) {
  //    return adapt(scanners.get(identifier), scannerClass);
  //  }

  @Override
  public Observation getObservation() {
    return observation;
  }

  @Override
  public Data.Builder relationship(
      String name,
      Observable observable,
      Geometry geometry,
      Urn identity,
      Observation source,
      Observation target) {
    var relationship =
        scope
            .observation(observable)
            .geometry(geometry)
            .identity(identity)
            .between(source, target)
            .register();
    var builder =
        new DirectDataBuilder(name, null, relationship, scope, identity, cessations, notifications);
    objects.add(builder);
    return builder;
  }

  @Override
  public Collection<Notification> getNotifications() {
    return this.notifications;
  }

  //  public void setScanner(String self, Storage.Scanner scanner) {
  //    scanners.put(self, scanner);
  //  }
}
