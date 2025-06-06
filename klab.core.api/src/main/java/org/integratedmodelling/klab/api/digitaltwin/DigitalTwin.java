package org.integratedmodelling.klab.api.digitaltwin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.OptionsBuilder;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

/**
 * The digital twin is a graph model composed of observations and all their history. Each {@link
 * org.integratedmodelling.klab.api.scope.ContextScope} points to a digital twin and contains the
 * methods to access it. Digital twins can be built from pairing others in a federated fashion.
 */
public interface DigitalTwin extends RuntimeAsset {

  /** An Options object is passed when the digital twin is created. */
  interface Options {

    /**
     * The timeout multiplier for operations on this digital twin.
     *
     * @return the timeout multiplier
     */
    long getTimeout();

    /**
     * The time unit for the timeout operations on this digital twin.
     *
     * @return the time unit for timeouts
     */
    java.util.concurrent.TimeUnit getTimeoutUnit();

    /**
     * Access rights define who can access the digital twin and the modality of the access.
     * Individual observations should also allow distinct levels of access within the scope of the
     * overall rights.
     *
     * @return
     */
    ResourcePrivileges getAccessRights();

    Persistence getPersistence();

    String getName();

    /**
     * Passing a ID is only allowed if the user is federated, so that the digital twin identity can
     * be assigned in a coordinated way among federated users. Any pre-existing DT with ID <code>
     * <federation_id>/<requested-id></code> will be usable by all members of the federation; the DT
     * will be created if not existing with the remaining options, which will be ignored if
     * pre-existing, with a warning if they differ.
     *
     * @return
     */
    String getId();

    static OptionsBuilder builder() {
      return new OptionsBuilder();
    }
  }

  /**
   * An executor is a runnable operation linked to an observation, compiled from an actuator in the
   * dataflow. It can be serialized in the KnowledgeGraph as a sequence of {@link ServiceCall}s and
   * reconstructed from them. Executors, like actuators, may cover partial geometries so more than
   * one can coexist for the same observation.
   */
  interface Executor {

    List<ServiceCall> serialized();

    /**
     * @param geometry
     * @param event
     * @param scope
     * @return true if execution was successful
     */
    boolean run(Geometry geometry, Scheduler.Event event, ContextScope scope);
  }

  /**
   * Operations that modify the digital twin are transactional and use this object, which guarantees
   * that all operations are linked to an activity that gets recorded in provenance.
   */
  interface Transaction {

    /**
     * Each transaction represents a provenance activity that cannot be null.
     *
     * @return the activity
     */
    Activity getActivity();

    /**
     * Record a new runtime asset in the graph. If the asset's ID is not {@link
     * Observation#UNASSIGNED_ID}, the asset is already present in the KG; otherwise it will be
     * added at commit() and the object in the graph will be modified to include its ID.
     *
     * @param asset
     */
    void add(RuntimeAsset asset);

    /**
     * Link two assets in the graph. The passed data will be matched to relationship properties
     * according to the relationship.
     *
     * @param source
     * @param destination
     * @param relationship
     * @param data
     */
    void link(
        RuntimeAsset source,
        RuntimeAsset destination,
        GraphModel.Relationship relationship,
        Object... data);

    /**
     * Register the current state of an asset so that it will be updated in the knowledge graph at
     * commit.
     *
     * @param asset
     */
    void update(RuntimeAsset asset);

    void resolveWith(Observation observation, Executor executor);

    /**
     * Commit the transaction and return true if it was successful.
     *
     * @return true if the commit succeeded
     */
    boolean commit();

    /**
     * Signal compilation failure. Return a transaction that will throw the same exception at
     * commit() with as much tracking info as practical.
     *
     * @param compilationError
     * @return a failed transaction that will throw the error at commit
     */
    Transaction fail(Throwable compilationError);

    /**
     * Produce the serializable and visualizable graph containing all the new assets created and
     * their structure in the graph. The incremental graph structure is sent using a {@link
     * org.integratedmodelling.klab.api.services.runtime.Message.MessageType#KnowledgeGraphCommitted}
     * message. Upon reception, the parent of each root observation should be looked up in the scope
     * for proper bookkeeping if a global KG is kept at client side.
     *
     * @return the runtime asset graph for this transaction
     */
    RuntimeAssetGraph getGraph();
  }

  /**
   * Return the options with which this digital twin was created. Options are immutable after
   * creation.
   *
   * @return
   */
  Options getOptions();

  /**
   * Obtain a new transaction to make changes in the knowledge graph. Nothing is modified until
   * {@link Transaction#commit()} is invoked on the returned object and returns true.
   *
   * @param activity
   * @param scope
   * @param runtimeAssets any other assets related to the transaction that may be relevant or may
   *     need to be finalized on commit. For example a resolution transaction may set the final
   *     knowledge graph IDs in the arguments, so that they are available after commit.
   * @return a new transaction object to modify the knowledge graph
   */
  Transaction transaction(Activity activity, ContextScope scope, Object... runtimeAssets);

  /**
   * The full knowledge graph, including observations, actuators and provenance, referring to this
   * digital twin.
   *
   * @return the complete knowledge graph for this digital twin
   */
  KnowledgeGraph getKnowledgeGraph();

  /**
   * Return the storage for all "datacube" content.
   *
   * @return the storage manager for this digital twin
   */
  StorageManager getStorageManager();

  /**
   * The scheduler manages everything having to do with time, and coordinates with the {@link
   * KnowledgeGraph} for the management of events and occurrent observers and observations.
   *
   * @return the dataflow graph starting at the given context
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
  Dataflow getDataflowGraph(ContextScope context);

  /**
   * Ingest the contextualized data coming from a resource contextualization into the passed
   * observation target.
   *
   * @param data
   * @param target
   * @return true if ingestion was successful
   */
  boolean ingest(Data data, Observation target, Scheduler.Event event, ContextScope scope);

  /**
   * Dispose of all storage and data, either in memory only or also on any attached storage. Whether
   * the disposal is permanent depends on the graph database used and its configuration.
   */
  void dispose();

  /**
   * Assemble the passed parameters into an unresolved Observation, to be inserted into the
   * knowledge graph and resolved.
   *
   * @param scope a scope used to resolve semantics.
   * @param resolvables
   * @return a new unresolved observation, or null if the parameters do not resolve to a valid one
   */
  static ObservationImpl createObservation(Scope scope, Object... resolvables) {

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
    long id = Observation.UNASSIGNED_ID;

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

            /*            if (isObserver) {
              observerGeometry = geometry;
              geometry = ogeom == null ? Geometry.builder().build() : ogeom;
            } else */
            if (geometry == null && ogeom != null) {
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
        } else if (o instanceof Long oid) {
          id = oid;
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
      ret.setId(id);
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
