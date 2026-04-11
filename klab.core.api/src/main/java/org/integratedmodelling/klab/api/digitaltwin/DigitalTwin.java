package org.integratedmodelling.klab.api.digitaltwin;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationBuilder;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * The digital twin is a graph model composed of observations and all their history. Each {@link
 * ContextScope} points to a digital twin and contains the methods to access it. Digital twins can
 * be built from pairing others in a federated fashion.
 */
public interface DigitalTwin extends RuntimeAsset {

  /**
   * A Configuration object is passed when the digital twin is created. The Configuration object is
   * also the payload of all {@link Message}s regarding digital twin creation, deletion or
   * connection.
   */
  interface Configuration {

    /**
     * The URL does not need to be filled in if the configuration is passed to a {@link
     * SessionScope#createContext(Configuration)}. Otherwise, the URL should be that of the chosen
     * runtime, with or without the <code>/dt/<id>
     * </code> path.
     *
     * @return
     */
    URL getUrl();

    /**
     * The service URL is provided separately to avoid complicated inference and accommodate
     * possible situations where the DT has been proxied or served through a different URL.
     *
     * @return
     */
    URL getServiceUrl();

    /**
     * Service ID is added by the back-end for easy attribution of a specific DT configuration in a
     * multi-service environment.
     *
     * @return
     */
    String getServiceId();

    /**
     * The timeout in {@link #getTimeoutUnit()}. If {@link #getPersistence()} returns {@link
     * Persistence#IDLE_TIMEOUT}, the digital twin will be removed after this many {@link
     * #getTimeoutUnit()}s.
     *
     * @return the timeout multiplier
     */
    long getTimeout();

    /**
     * The time unit for the timeout of the digital twin if {@link #getPersistence()} returns {@link
     * Persistence#IDLE_TIMEOUT}.
     *
     * @return the time unit for timeouts
     */
    TimeUnit getTimeoutUnit();

    /**
     * The owning username
     *
     * @return
     */
    String getOwner();

    /**
     * Access rights define who can access the digital twin and the modality of the access.
     * Individual observations should also allow distinct levels of access within the scope of the
     * overall rights.
     *
     * <p>Federated users will
     *
     * <p>Even if the user is federated, a digital twin whose rights enable access only to the owner
     * user (default) will not be advertised through the messaging system.
     *
     * @return
     */
    ResourcePrivileges getAccessRights();

    /**
     * These may be present when the configuration is the return value of a connect call.
     *
     * @return
     */
    List<Notification> getNotifications();

    Persistence getPersistence();

    String getName();

    /**
     * Passing an ID is only allowed if the user is federated, so that the digital twin identity can
     * be assigned in a coordinated way among federated users. Any pre-existing DT with ID <code>
     * <federation_id>/<requested-id></code> will be usable by all members of the federation; the DT
     * will be created if not existing using the remaining options. The latter will be ignored if
     * the DT is pre-existing, with a warning if they differ.
     *
     * @return
     */
    String getId();

    @Deprecated
    boolean isCreateWhenAbsent();

    /**
     * Used at client side before a scope request is made. This should add anything implied by the
     * current scope that wasn't filled or throw an exception if any of the settings requested are
     * inconsistent.
     *
     * @param scope the scope to validate against. According to the scope class the validation may
     *     be different.
     * @return the same object or a new one with valid and complete settings
     * @throws KlabValidationException if any settings are inconsistent with the scope
     */
    Configuration validate(Scope scope) throws KlabValidationException;

    /**
     * A specific sharding strategy may be requested to override what's feasible inthe
     * contextualizations.
     *
     * @return
     */
    Data.ShardingStrategy getShardingStrategy();

    static Configuration create(URL url, UserScope scope) {
      return new ConfigurationBuilder(url, scope).build();
    }

    static ConfigurationBuilder builder() {
      return new ConfigurationBuilder();
    }

    static ConfigurationBuilder builder(Configuration config) {
      return new ConfigurationBuilder(config);
    }

    String getDescription();

    void defineFromExisting(Configuration descriptor);
  }

  /**
   * An executor is a runnable operation linked to an observation, compiled from an actuator in the
   * dataflow. It can be serialized in the KnowledgeGraph as a sequence of {@link ServiceCall}s and
   * reconstructed from them. Executors, like actuators, may cover partial geometries, so more than
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
   * that all operations on the knowledge graph are linked to an activity that gets recorded in
   * provenance.
   *
   * <p>TODO storage allocation must also be transactional, calling "release" on failure and
   * finalizing the storage commitment on commit.
   */
  interface Transaction {

    /**
     * The ID returned when a commit was done successfully on a non-root transition. In recursive
     * code the commit result should be checked against this to know if the transaction was a
     * root-level one, the only one that .
     */
    long INTERMEDIATE_COMMIT_ID = 0l;

    void registerExecutors();

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
     * Commit the transaction and return the commit ID if it was successful. In intermediate
     * transactions, this should always return INTERMEDIATE_COMMIT_ID or null. The root transaction
     * must return a valid commit ID or -1.
     *
     * @return a valid commit ID or -1 if the commit failed.
     */
    long commit();

    /**
     * Get a child transaction for the given activity. The child transaction must be committed
     * normally but won't cause knowledge graph commits until the root transaction is committed. If
     * a child transaction fails, the whole transaction tree fails.
     *
     * @param activity
     * @param scope in a federated session, may have a different owner every time
     * @param runtimeAssets any other assets related to the transaction that may be relevant (e.g.
     *     agent)
     * @return
     */
    Transaction getChild(Activity activity, ContextScope scope, Object... runtimeAssets);

    /**
     * Signal compilation failure. Return a transaction that will throw the same exception at
     * commit() with as much tracking info as practical.
     *
     * @param compilationError
     * @return a failed transaction that will throw the error at commit
     */
    Transaction fail(Throwable compilationError);

    /**
     * All the uncommitted assets in the transaction.
     *
     * @return
     */
    Collection<RuntimeAsset> assets();

    Collection<KnowledgeGraph.Link> incoming(RuntimeAsset asset);

    Collection<KnowledgeGraph.Link> outgoing(RuntimeAsset asset);

    /**
     * Produce the serializable and visualizable graph containing all the new assets created and
     * their structure, using only what has been produced within the individual transition. If
     * called before the root transaction is committed, the assets in the graph will be unresolved,
     * with ID == -1 and no URN, so the ID will be the transient ID, and users must be aware of
     * this.
     *
     * @return the runtime asset graph for this transaction
     */
    GraphModel.KnowledgeGraph getGraph();
  }

  /**
   * Return the options with which this digital twin was created. Options are immutable after
   * creation.
   *
   * @return
   */
  Configuration getOptions();

  /**
   * Important for cleanup. Only service-side DTs that are hosted locally should return false here.
   *
   * @return
   */
  boolean isClient();

  /**
   * Get a new transaction to make changes in the knowledge graph. Nothing is modified until {@link
   * Transaction#commit()} is invoked on the root-level transaction and returns true.
   *
   * <p>NOTE: this should not be called as a rule. It should only be called within the service-side
   * {@link ContextScope}, which will manage the transaction tree and commit after resolution and
   * contextualization.
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
   * Dispose of all storage and data, either in memory only or also on any attached storage. Whether
   * the disposal is permanent depends on the graph database used and its configuration.
   */
  void dispose();

  @Deprecated // FIXME remove, put one in each service-side ContextScope
  AtomicLong idGenerator = new AtomicLong(Observation.UNASSIGNED_ID);

  /**
   * Assemble the passed parameters into an unresolved Observation, to be inserted into the
   * knowledge graph and resolved.
   *
   * <p>The observation will have a negative ID (meaning "unresolved") unless a long parameter is
   * passed with <code>resolvables</code> to serve as the ID. The ID is never repeated across a VM.
   *
   * @param scope any valid scope, used to resolve semantics.
   * @param resolvables
   * @return a new unresolved observation, or null if the parameters do not resolve to a valid one
   * @deprecated use the improved ContextScope API
   */
  static ObservationImpl createObservation(Scope scope, Object... resolvables) {

    final Set<String> knownKeys = Set.of("observation", "semantics", "space", "time");

    String name = null;
    Geometry geometry = null;
    Observable observable = null;
    String defaultValue = null;
    Metadata metadata = Metadata.create();
    long id = Observation.UNASSIGNED_ID;
    Urn identity = null;
    Observation.ContextualizationData contextualizationData = null;

    Geometry oGeom = null;
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
          identity = urn;
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

            if ("observer".equals(symbol.getDefineClass())) {
              // tell the clients that this has been defined as an observer
              metadata.put(Metadata.IM_OBSERVER_TAG, true);
            }

            identity = Urn.of(symbol.getNamespace() + ":" + symbol.getName());

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
              oGeom = defineGeometry((Map<?, ?>) definition.get("geometry"));
            }

            if (definition.containsKey("contextualization")
                && definition.get("contextualization") instanceof Map<?, ?> contextualization) {
              // TODO must be either collective or quality. Geometry is supplied externally and it's
              //  illegal here.
              if (geometry != null) {
                scope.error(
                    "Geometry cannot be supplied when contextualization data are given. Observation: "
                        + symbol.getUrn());
                return null;
              }
              contextualizationData = defineContextualization(contextualization, scope);
            }

            if (geometry == null && oGeom != null) {
              geometry = oGeom;
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
      ret.setId(id == Observation.UNASSIGNED_ID ? idGenerator.decrementAndGet() : id);
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

    return null;
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
        if (spaceDefinition.containsKey("projection")) {
          spaceBuilder.projection(spaceDefinition.get("projection").toString());
        } else if (spaceDefinition.containsKey("crs")) {
          spaceBuilder.projection(spaceDefinition.get("crs").toString());
        } else if (spaceDefinition.containsKey("shape")
            && spaceDefinition.get("shape").toString().contains(" ")) {
          var split = spaceDefinition.get("shape").toString().split(" ");
          spaceBuilder.projection(split[0]);
        } else {
          // TODO last resort; should warn or use configured value for default projection
          spaceBuilder.projection("EPSG:4326");
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
}
