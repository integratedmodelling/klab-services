package org.integratedmodelling.klab.api.knowledge.observation.impl;

import java.util.*;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

public abstract class ObservationBuilderImpl implements Observation.Builder {

  private Observable observable;
  private ContextScope scope;
  private Urn identity;
  private Map<Observation.GeometryRelationship, Geometry> geometries = new HashMap<>();
  private Object defaultValue;
  private final Metadata metadata = Metadata.create();
  private Observation.ContextualizationData contextualizationData;
  private final List<Notification> notifications = new ArrayList<>();
  private String name;
  private boolean query = false;

  private final Set<String> knownKeys = Set.of("observation", "semantics", "space", "time");

  public ObservationBuilderImpl(Observable observable, ContextScope contextScope) {
    this.scope = contextScope;
    this.observable = observable;
  }

  public ObservationBuilderImpl(Map<?, ?> definition, ContextScope contextScope) {
    this.scope = contextScope;
    defineFromMap(definition, null);
  }

  /**
   * This is to build an observation from a data object coming from an adapter. Its use is internal
   * to the runtime service. For substantials, an identity is mandatory: if the name provided in the
   * data objects does not contain a namespace, the scope ID is used. For non-substantials, any name
   * in the data is ignored.
   *
   * @param data
   * @param scope
   */
  public ObservationBuilderImpl(Data data, ContextScope scope) {
    this.scope = scope;
    observable = scope.getService(Reasoner.class).resolveObservable(data.semantics());
    // TODO Data must have the same geometry breakdown
    geometries.put(Observation.GeometryRelationship.OCCUPIES, data.geometry());
    if (observable == null) {
      notifications.add(Notification.error("Cannot resolve observable: " + data.semantics()));
    } else if (observable.getSemantics().is(SemanticType.COUNTABLE)
        && !observable.getSemantics().isCollective()) {
      var namespace = data.name().contains(":") ? data.name().split(":")[0] : scope.getId();
      var name = data.name().contains(":") ? data.name().split(":")[1] : data.name();
      this.identity = Urn.of(namespace + ":" + name);
      this.name = name;
    }
    metadata.putAll(data.metadata());
  }

  protected ObservationBuilderImpl() {}

  public ObservationBuilderImpl query() {
    this.query = true;
    return this;
  }

  /**
   * Clients can use this constructor to create an observation from a statement.
   *
   * @param observable
   * @param contextScope
   */
  public ObservationBuilderImpl(KlabStatement observable, ContextScope contextScope) {
    this.scope = contextScope;
    switch (observable) {
      case KimModel model ->
          this.observable =
              scope
                  .getService(Reasoner.class)
                  .resolveObservable(model.getObservables().getFirst().getUrn());
      case KimSymbolDefinition symbol -> defineFromSymbol(symbol);
      case KimConcept concept ->
          this.observable =
              contextScope.getService(Reasoner.class).resolveObservable(concept.getUrn());
      case KimObservable obs ->
          this.observable = contextScope.getService(Reasoner.class).resolveObservable(obs.getUrn());
      default -> {}
    }

    if (this.observable == null) {
      notifications.add(Notification.error("Cannot resolve observable: " + observable));
    }
  }

  @Override
  public Observation.Builder definition(Map<?, ?> definition) {
    if (definition == null) {
      return this;
    }
    defineFromMap(definition, null);
    return this;
  }

  @Override
  public Observation.Builder identity(Urn urn) {
    this.identity = urn;
    if (urn != null) {
      this.name = Utils.Paths.getLast(urn.toString(), ':');
    }
    return this;
  }

  @Override
  public Observation.Builder metadata(Map<String, Object> metadata) {
    this.metadata.putAll(metadata);
    return this;
  }

  private void defineFromMap(Map<?, ?> definition, String urn) {

    if (urn == null) {
      // must have either `urn` or `namespace` + `name`
      if (definition.containsKey("namespace") && definition.containsKey("name")) {
        identity =
            Urn.of(
                definition.get("namespace").toString() + ":" + definition.get("name").toString());
      } else if (!definition.containsKey("urn")) {
        identity = Urn.of(definition.get("urn").toString());
      } else {
        notifications.add(
            Notification.error("Observation must have either `urn` or `namespace` + `name`"));
      }
    }

    if (definition.containsKey("semantics")) {
      observable =
          scope
              .getService(Reasoner.class)
              .resolveObservable(definition.get("semantics").toString());
      if (observable == null) {
        notifications.add(
            Notification.error(
                "Invalid semantics in observation definition: " + definition.get("semantics")));
      }
    }
    if (definition.containsKey("space") || definition.containsKey("time")) {
      geometries.put(Observation.GeometryRelationship.OCCUPIES, defineGeometry(definition));
    }

    if (definition.containsKey("geometry") && definition.get("geometry") instanceof Map<?, ?>) {
      geometries.put(
          Observation.GeometryRelationship.OCCUPIES,
          defineGeometry((Map<?, ?>) definition.get("geometry")));
    }

    if (definition.containsKey("contextualization")
        && definition.get("contextualization") instanceof Map<?, ?> contextualization) {
      // TODO must be either collective or quality. Geometry is supplied externally and it's
      //  illegal here.
      if (geometries.get(Observation.GeometryRelationship.OCCUPIES) != null) {
        notifications.add(
            Notification.error(
                "Geometry cannot be supplied when contextualization data are given. Observation: "
                    + urn));
      } else {
        contextualizationData = defineContextualization(contextualization, scope);
      }
    }

    for (var key : definition.keySet()) {
      if (!knownKeys.contains(key.toString())) {
        metadata.put(key.toString(), definition.get(key));
      }
    }
  }

  private void defineFromSymbol(KimSymbolDefinition symbol) {

    // must be an "observation" class
    if (("observation".equals(symbol.getDefineClass())
            || "observer".equals(symbol.getDefineClass()))
        && symbol.getValue() instanceof Map<?, ?> definition) {

      if ("observer".equals(symbol.getDefineClass())) {
        // tell the clients that this has been defined as an observer
        metadata.put(Metadata.IM_OBSERVER_TAG, true);
      }
      identity = Urn.of(symbol.getNamespace() + ":" + symbol.getName());
      defineFromMap(definition, symbol.getUrn());
    }
  }

  @Override
  public Observation.Builder geometry(Geometry geometry) {
    this.geometries.put(Observation.GeometryRelationship.OCCUPIES, geometry);
    return this;
  }

  @Override
  public Observation.Builder geometry(
      Geometry geometry, Observation.GeometryRelationship relationship) {
    this.geometries.put(relationship, geometry);
    return this;
  }

  @Override
  public Observation.Builder value(Object value) {
    defaultValue = value;
    return this;
  }

  @Override
  public Observation.Builder identity(String namespace, String name) {
    if (namespace != null && name != null) {
      this.identity = Urn.of(namespace + ":" + name);
      this.name = name;
    }
    return this;
  }

  @Override
  public Observation.Builder metadata(String key, Object value) {
    metadata.put(key, value);
    return this;
  }

  public ObservationImpl build() {

    ObservationImpl ret = new ObservationImpl();
    ret.setGeometry(geometries.get(Observation.GeometryRelationship.OCCUPIES));
    ret.getMetadata().putAll(metadata);
    ret.setObservable(observable);
    ret.setValue(defaultValue);
    ret.setContextualizationData(contextualizationData);
    ret.setName(name);

    if (query) {
      ret.setId(Observation.QUERY_ID);
    }

    if (identity != null) {
      // mandatory for substantials, and must be namespace:name
      if (identity.length() != 2) {
        notifications.add(Notification.error("Identity must be in the form namespace:id"));
      }
    }

    if (observable != null
        && !observable.getSemantics().isCollective()
        && SemanticType.isSubstantial(observable.getSemantics().getType())) {
      if (identity == null) {
        notifications.add(
            Notification.error(
                "Observations of individual substantials must specify a unique identity, passed as a Urn object <namespace>:<identifier>"));
      } else {
        ret.setUrn(identity.getUrn());
      }
    }

    ret.getNotifications().addAll(notifications);
    if (Utils.Notifications.hasErrors(ret.getNotifications())) {
      ret.setEmpty(true);
    }

    return ret;
  }

  static Observation.ContextualizationData defineContextualization(
      Map<?, ?> contextualization, Scope scope) {

    var ret = new ObservationImpl.ContextualizationDataImpl();

    ret.setAdapterId(contextualization.get("adapter").toString());
    ret.setServiceId(scope.getService(RuntimeService.class).serviceId());
    ret.setServiceUrl(scope.getService(RuntimeService.class).getUrl());

    if ((contextualization.containsKey("persist")
            && contextualization.get("persist") instanceof Boolean persist
            && persist)
        || (contextualization.get("resource") instanceof Map<?, ?>)) {
      ret.setPersistent(true);
    }

    for (var key : contextualization.keySet()) {
      if (!"adapter".equals(key.toString()) && !"persist".equals(key.toString())) {
        ret.getParameters().put(key.toString(), contextualization.get(key));
      }
    }

    return ret;
  }

  private Geometry defineGeometry(Map<?, ?> definition) {
    var geometryBuilder = Geometry.builder();
    if (definition.containsKey("space")) {
      var spaceBuilder = geometryBuilder.space();
      if (definition.get("space") instanceof Map<?, ?> spaceDefinition) {
        if (spaceDefinition.containsKey("shape")) {
          spaceBuilder.shape(spaceDefinition.get("shape").toString());
        }
        if (spaceDefinition.containsKey("grid")) {
          spaceBuilder.resolution(spaceDefinition.get("grid").toString());
        }
        if (spaceDefinition.containsKey("projection")) {
          spaceBuilder.projection(spaceDefinition.get("projection").toString());
        } else if (spaceDefinition.containsKey("crs")) {
          spaceBuilder.projection(spaceDefinition.get("crs").toString());
        } else if (spaceDefinition.containsKey("shape")
            && spaceDefinition.get("shape").toString().contains(" ")) {
          var split = spaceDefinition.get("shape").toString().split(" ");
          spaceBuilder.projection(split[0]);
        } else {
          spaceBuilder.projection("EPSG:4326");
          notifications.add(
              Notification.warning("No spatial projection in shape: assuming EPSG:4326"));
        }
        // TODO add bounding box etc
      }
      geometryBuilder = spaceBuilder.build();
    }
    if (definition.containsKey("time")) {
      var timeBuilder = geometryBuilder.time();
      if (definition.get("time") instanceof Map<?, ?> timeDefinition) {
        if (timeDefinition.containsKey("start") && timeDefinition.containsKey("end")) {
          var start = timeDefinition.get("start");
          var end = timeDefinition.get("end");
          if (start instanceof Number startNumber && end instanceof Number endNumber) {

            if (startNumber.longValue() >= endNumber.longValue()) {
              throw new KlabIllegalArgumentException("Start time cannot be after end time");
            }

            if (startNumber.longValue() > 0 && startNumber.longValue() < 3000) {
              // assume year
              startNumber = TimeInstant.create(startNumber.intValue()).getMilliseconds();
            }
            if (endNumber.longValue() > 0 && endNumber.longValue() < 3000) {
              // assume year
              endNumber = TimeInstant.create(endNumber.intValue()).getMilliseconds();
            }

            timeBuilder.start(startNumber.longValue()).end(endNumber.longValue());
          }
        } else if (timeDefinition.containsKey("year")) {
          var year = timeDefinition.get("year");
          if (year instanceof Number number) {
            timeBuilder.year(number.intValue());
          } else if (year instanceof Identifier identifier
              && "default".equals(identifier.getValue())) {
            timeBuilder.year(TimeInstant.create().getYear());
          }
        }

        if (timeDefinition.containsKey("step")) {
          var step =
              timeDefinition.get("step") instanceof Quantity quantity
                  ? quantity
                  : Quantity.create(timeDefinition.get("step").toString());
          timeBuilder.step(step);
        }
      }
      geometryBuilder = timeBuilder.build();
    }
    return geometryBuilder.build();
  }

  @Override
  public Observation.Builder observable(Observable observable) {
    this.observable = observable;
    return this;
  }
}
