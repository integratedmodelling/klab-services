package org.integratedmodelling.klab.api.digitaltwin;

import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Mutable;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

/**
 * The digital twin is a graph model composed of observations and all their history. Each {@link
 * org.integratedmodelling.klab.api.scope.ContextScope} points to a digital twin and contains the
 * methods to access it. Digital twins can be built from pairing others in a federated fashion.
 */
public interface DigitalTwin {

  /**
   * The type of relationships in the graph. All relationship carry further information, to be fully
   * defined.
   */
  enum Relationship {
    HAS_PARENT,
    AFFECTED,
    EMERGED_FROM,
    HAS_OBSERVER,
    HAS_PLAN,
    BY_AGENT,
    CREATED,
    HAS_DATAFLOW,
    HAS_PROVENANCE,
    HAS_ACTIVITY,
    HAS_CHILD,
    TRIGGERED,
    CONTEXTUALIZED;
  }

  /**
   * The full knowledge graph, including observations, actuators and provenance, referring to this
   * digital twin.
   *
   * @return
   */
  KnowledgeGraph getKnowledgeGraph();

  /**
   * Return the storage for all "datacube" content.
   *
   * @return
   */
  StateStorage getStateStorage();

  /**
   * The scheduler manages everything having to do with time, and coordinates with the {@link
   * KnowledgeGraph} for the management of events and occurrent observers and observations.
   *
   * @return
   */
  Scheduler getScheduler();

  /**
   * The provenance graph contextualized to the passed context.
   *
   * @param context can be null for the entire provenance graph (effect is the same as passing the
   *     original context scope)
   * @return the graph starting at the passed contextualization
   */
  Provenance getProvenanceGraph(ContextScope context);

  /**
   * The dataflow graph contextualized to the passed context. This is extracted from the provenance
   * graph
   *
   * @param context can be null for the entire dataflow (effect is the same as passing the original
   *     context scope)
   * @return the dataflow starting at the passed contextualization
   */
  Dataflow<Observation> getDataflowGraph(ContextScope context);

  /**
   * Ingest the contextualized data coming from a resource contextualization into the passed
   * observation target.
   *
   * @param data
   * @param target
   * @return
   */
  boolean ingest(Data data, Observation target, ContextScope scope);

  /**
   * Dispose of all storage and data, either in memory only or also on any attached storage. Whether
   * the disposal is permanent depends on the graph database used and its configuration.
   */
  void dispose();

  /**
   * Assemble the passed parameters into an unresolved Observation, to be inserted into the
   * knowledge graph and resolved.
   *
   * <p>Accepts all the needed elements for the observation, including geometry, observable and the
   * like. If two geometries are passed, the second is the observer's (FIXME that should be
   * deprecated). In dependent observations, the geometry may be omitted and the geometry of the
   * owning substantial will be used.
   *
   * <p>If the observation is an observer definition, the geometry ends up as a constraint in the
   * scope. If an observer's own geometry is unspecified, a default scalar one is attributed.
   *
   * @param scope a scope. If a context scope and we use an observer definition, the scope's
   *     resolution constraints will include an observer geometry after the call.
   * @param resolvables
   * @return
   */
  static ObservationImpl createObservation(@Mutable Scope scope, Object... resolvables) {

    final Set<String> knownKeys = Set.of("observation", "semantics", "space", "time");

    String name = null;
    Geometry geometry = null;
    Geometry observerGeometry = null;
    Observable observable = null;
    String resourceUrn = null;
    String modelUrn = null;
    String defaultValue = null;
    Metadata metadata = Metadata.create();
    boolean isObserver = false;

    Geometry ogeom = null;
    if (resolvables != null) {
      for (Object o : resolvables) {
        if (o instanceof Observable obs) {
          observable = obs;
        } else if (o instanceof Geometry geom) {
          geometry = geom;
        } else if (o instanceof String string) {
          if (name == null) {
            name = string;
          } else {
            defaultValue = string;
          }
        } else if (o instanceof Urn urn) {
          resourceUrn = urn.getUrn();
        } else if (o instanceof Data data) {
          observable = scope.getService(Reasoner.class).resolveObservable(data.semantics());
          geometry = data.geometry();
          name = data.name();
          metadata.putAll(data.metadata());
        } else if (o instanceof KimSymbolDefinition symbol) {

          // must be an "observation" class
          if (("observation".equals(symbol.getDefineClass())
                  || "observer".equals(symbol.getDefineClass()))
              && symbol.getValue() instanceof Map<?, ?> definition) {

            isObserver = "observer".equals(symbol.getDefineClass());

            name = symbol.getName();
            if (definition.containsKey("semantics")) {
              observable =
                  scope
                      .getService(Reasoner.class)
                      .resolveObservable(definition.get("semantics").toString());
              if (observable == null) {
                scope.error(
                    "Invalid semantics in observation definition: " + definition.get("semantics"));
                return null;
              }
            }
            if (definition.containsKey("space") || definition.containsKey("time")) {
              geometry = defineGeometry(definition);
            }

            if (definition.containsKey("geometry")
                && definition.get("geometry") instanceof Map<?, ?>) {
              ogeom = defineGeometry((Map<?, ?>) definition.get("geometry"));
            }

            if (isObserver) {
              observerGeometry = geometry;
              geometry = ogeom == null ? Geometry.builder().build() : ogeom;
            } else if (geometry == null && ogeom != null) {
              geometry = ogeom;
            }

            for (var key : definition.keySet()) {
              if (!knownKeys.contains(key.toString())) {
                metadata.put(key.toString(), definition.get(key));
              }
            }
          }
        } else if (o instanceof KimModel model) {
          // send the model URN and extract the observable. The modelUrn should become a
          // constraint within the requesting scope upstream.
          observable =
              scope
                  .getService(Reasoner.class)
                  .resolveObservable(model.getObservables().getFirst().getUrn());
          modelUrn = model.getUrn();
        } else if (o instanceof Map<?, ?> map) {
          // metadata
          metadata.putAll((Map<? extends String, ?>) map);
        } else if (o instanceof KimConcept concept) {
          observable = scope.getService(Reasoner.class).resolveObservable(concept.getUrn());
        } else if (o instanceof KimObservable obs) {
          observable = scope.getService(Reasoner.class).resolveObservable(obs.getUrn());
        }
      }
    }

    /*
    least requisite is having an observable. A quality observation doesn't need to specify
    a geometry.
     */
    if (observable != null) {
      ObservationImpl ret = new ObservationImpl();
      ret.setGeometry(geometry);
      ret.setMetadata(metadata);
      ret.setObservable(observable);
      ret.setValue(defaultValue);
      ret.setName(name);

      if (observerGeometry != null && scope instanceof ContextScope contextScope) {
        contextScope
            .getResolutionConstraints()
            .add(
                ResolutionConstraint.of(
                    ResolutionConstraint.Type.ObserverGeometry, observerGeometry));
      }

      return ret;
    }

    return null;
  }

  static Geometry defineGeometry(Map<?, ?> definition) {
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
        // TODO add bounding box etc
      }
      geometryBuilder = spaceBuilder.build();
    }
    if (definition.containsKey("time")) {
      var timeBuilder = geometryBuilder.time();
      if (definition.get("time") instanceof Map<?, ?> timeDefinition) {
        if (timeDefinition.containsKey("year")) {
          var year = timeDefinition.get("year");
          if (year instanceof Number number) {
            timeBuilder.year(number.intValue());
          } else if (year instanceof Identifier identifier
              && "default".equals(identifier.getValue())) {
            timeBuilder.year(TimeInstant.create().getYear());
          }
        }
      }
      geometryBuilder = timeBuilder.build();
    }
    return geometryBuilder.build();
  }
}
