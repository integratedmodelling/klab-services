package org.integratedmodelling.klab.services.runtime.neo4j;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabStorageException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Plan;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.provenance.impl.PlanImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.utilities.Utils;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.neo4j.cypherdsl.core.*;
import org.neo4j.driver.*;

/**
 * TODO check spatial queries:
 * https://www.lyonwj.com/blog/neo4j-spatial-procedures-congressional-boundaries and
 * https://neo4j-contrib.github.io/spatial/0.24-neo4j-3.1/index.html
 *
 * <p>TODO must figure out where the heck the neo4j-spatial-5.20.0.jar is (no, it's not in
 * https://github.com/neo4j-contrib/m2 nor in osgeo)
 */
public abstract class KnowledgeGraphNeo4j extends AbstractKnowledgeGraph {

  private static final Set<String> OBSERVATION_PROPERTIES =
      Set.of(
          GraphModel.Fields.METADATA,
          GraphModel.Fields.NAME,
          GraphModel.Fields.TYPE,
          GraphModel.Fields.URN,
          GraphModel.Fields.CHILDREN_COUNT,
          GraphModel.Fields.SEMANTICTYPE,
          GraphModel.Fields.SEMANTICS,
          GraphModel.Fields.OBSERVABLE,
          GraphModel.Fields.ID,
          GraphModel.Fields.PARENT_ID,
          GraphModel.Fields.EVENT_TIMESTAMPS,
          GraphModel.Fields.HISTOGRAMS,
          GraphModel.Fields.HISTOGRAM,
          GraphModel.Fields.SUBSTANTIAL,
          GraphModel.Fields.ADAPTER_ID,
          GraphModel.Fields.ADAPTER_PARAMETERS,
          GraphModel.Fields.FILL_CURVE,
          GraphModel.Fields.SUGGESTED_SPLITS,
          GraphModel.Fields.MAX_BUFFER_SIZE,
          GraphModel.Fields.MIN_SPLIT_SIZE,
          GraphModel.Fields.DATA_TYPE,
          GraphModel.Fields.SHAPE,
          GraphModel.Fields.LATITUDE,
          GraphModel.Fields.LONGITUDE);

  protected Driver driver;
  protected Agent user;
  protected Agent klab;
  protected String rootContextId;
  private final RuntimeAsset contextNode = RuntimeAsset.CONTEXT_ASSET;
  private final RuntimeAsset dataflowNode = RuntimeAsset.DATAFLOW_ASSET;
  private final RuntimeAsset provenanceNode = RuntimeAsset.PROVENANCE_ASSET;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
  protected String serviceId;
  private Cache<Long, RuntimeAsset> assetCache =
      CacheBuilder.newBuilder()
          .maximumSize(/* TODO initialize from service settings */ 1000)
          .expireAfterAccess(/* TODO this too */ 3, TimeUnit.HOURS)
          .build();

  protected void startMaintenanceThread(int periodInSeconds) {
    executor.scheduleAtFixedRate(() -> maintenanceThread(), 0, periodInSeconds, TimeUnit.SECONDS);
  }

  private void maintenanceThread() {}

  private String getShapeLayerName() {
    return getShapeLayerName(rootContextId);
  }

  private String getShapeLayerName(String contextId) {
    return "shape_" + Utils.Paths.getLast(contextId, '.');
  }

  /**
   * Predefined Cypher queries. FIXME some should be substituted by programmatic queries, leaving
   * only graph initialization
   */
  interface Queries {

    String REMOVE_CONTEXT = ("match (n:" + GraphModel.Labels.CONTEXT + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + "})-[*]->(c) detach delete n, c");
    String FIND_CONTEXT = ("MATCH (ctx:" + GraphModel.Labels.CONTEXT + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + "}) RETURN ctx");
    String CREATE_WITH_PROPERTIES = ("CREATE (n:{" + GraphModel.Fields.TYPE + "}) SET n = $" + GraphModel.Fields.PROPERTIES + " RETURN n");
    String CREATE_WITH_SHAPE =
        ("CREATE (n:{" + GraphModel.Fields.TYPE + "}) SET n = $" + GraphModel.Fields.PROPERTIES + " WITH n CALL spatial.addNode($" + GraphModel.Fields.LAYER_NAME + ", n) YIELD node RETURN node");
    String UPDATE_PROPERTIES = ("MATCH (n:{" + GraphModel.Fields.TYPE + "} {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "}) SET n += $" + GraphModel.Fields.PROPERTIES + " RETURN n");
    String UPDATE_PROPERTIES_GENERIC = ("MATCH (n {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "}) SET n += $" + GraphModel.Fields.PROPERTIES + " RETURN n");
    String[] INITIALIZATION_QUERIES =
        new String[] {
          ("MATCH (klab:" + GraphModel.Labels.AGENT + " {" + GraphModel.Fields.NAME + ": 'k.LAB'}), (" + GraphModel.Fields.USER + ":" + GraphModel.Labels.AGENT + " {" + GraphModel.Fields.NAME + ": $" + GraphModel.Fields.USERNAME + "}) CREATE // main context ")
              + "node\n"
              + ("\t(ctx:" + GraphModel.Labels.CONTEXT + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + ", " + GraphModel.Fields.NAME + ": $" + GraphModel.Fields.NAME + ", " + GraphModel.Fields.USER + ": $" + GraphModel.Fields.USERNAME + ", " + GraphModel.Fields.CREATED + ": ")
              + ("$" + GraphModel.Fields.TIMESTAMP + ", ")
              + (GraphModel.Fields.RIGHTS + ": $" + GraphModel.Fields.RIGHTS + ", ")
              + (GraphModel.Fields.FEDERATION + ": $" + GraphModel.Fields.FEDERATION + ", ")
              + (GraphModel.Fields.DESCRIPTION + ": $" + GraphModel.Fields.DESCRIPTION + ", ")
              + (GraphModel.Fields.LAST_UPDATE + ": $" + GraphModel.Fields.LAST_UPDATE + ", ")
              + (GraphModel.Fields.EXPIRATION + ": $" + GraphModel.Fields.EXPIRATION_TYPE + "}),\n")
              + "\t// main provenance and dataflow nodes\n"
              + ("\t(prov:" + GraphModel.Labels.PROVENANCE + " {" + GraphModel.Fields.NAME + ": '" + GraphModel.Labels.PROVENANCE + "', " + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + " + '.PROVENANCE'}), ")
              + ("(df:" + GraphModel.Labels.DATAFLOW + " ")
              + ("{" + GraphModel.Fields.NAME + ": '" + GraphModel.Labels.DATAFLOW + "', " + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + " + '.DATAFLOW'}),\n")
              + ("\t(ctx)-[:" + GraphModel.Relationship.HAS_PROVENANCE.name() + "]->(prov),\n")
              + ("\t(ctx)-[:" + GraphModel.Relationship.HAS_DATAFLOW.name() + "]->(df),\n")
              + ("\t(prov)-[:" + GraphModel.Relationship.HAS_AGENT.name() + "]->(" + GraphModel.Fields.USER + "),\n")
              + ("\t(prov)-[:" + GraphModel.Relationship.HAS_AGENT.name() + "]->(klab),\n")
              + "\t// ACTIVITY that created the whole thing\n"
              + ("\t(creation:" + GraphModel.Labels.ACTIVITY + " {" + GraphModel.Fields.START + ": $" + GraphModel.Fields.TIMESTAMP + ", " + GraphModel.Fields.END + ": $" + GraphModel.Fields.TIMESTAMP + ", " + GraphModel.Fields.TYPE + ": ")
              + ("'CONTEXT_INITIALIZATION', " + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ACTIVITY_ID + "}),\n")
              + "\t// created by user\n"
              + ("\t(creation)-[:" + GraphModel.Relationship.BY_AGENT.name() + "]->(" + GraphModel.Fields.USER + "),\n")
              + ("\t(ctx)<-[:" + GraphModel.Relationship.CREATED.name() + "]-(creation),\n")
              + ("(prov)-[:" + GraphModel.Relationship.HAS_CHILD.name() + "]->(creation)")
        };
  }

  class TransactionImpl implements Transaction {

    private final org.neo4j.driver.Transaction transaction;
    private final org.neo4j.driver.Session session;
    private final Set<RuntimeAsset> stored = new HashSet<>();
    private final Map<Long, RuntimeAsset> idCache = new HashMap<>();
    private final List<Pair<Long, Long>> links = new ArrayList<>();
    private boolean closed;
    private boolean sessionClosed;
    private final ContextScope contextScope;

    TransactionImpl(ContextScope contextScope) {
      this.contextScope = contextScope;
      this.session = driver.session(); // new session should make this thread safe
      this.transaction =
          this.session.beginTransaction(
              TransactionConfig.builder().withTimeout(Duration.ZERO).build());
    }

    @Override
    public void store(RuntimeAsset asset, Object... additionalProperties) {
      if (closed) {
        throw new KlabStorageException(
            "Cannot store an asset in a closed knowledge-graph transaction");
      }
      if (asset == RuntimeAsset.CONTEXT_ASSET
          || asset == RuntimeAsset.PROVENANCE_ASSET
          || asset == RuntimeAsset.DATAFLOW_ASSET) {
        return;
      }

      try {
        var id =
            KnowledgeGraphNeo4j.this.store(transaction, asset, contextScope, additionalProperties);
        if (id <= 0 || asset.getId() <= 0) {
          throw new KlabStorageException(
              "Knowledge graph did not assign a persistent ID to "
                  + asset.getClass().getSimpleName()
                  + " (returned ID "
                  + id
                  + ", asset ID "
                  + asset.getId()
                  + ")");
        }
        stored.add(asset);
        idCache.put(id, asset);
      } catch (Exception e) {
        closed = true;
        throw storageFailure("storing " + asset.getClass().getSimpleName(), e);
      }
    }

    @Override
    public void update(RuntimeAsset asset, Object... properties) {
      if (closed) {
        throw new KlabStorageException(
            "Cannot update an asset in a closed knowledge-graph transaction");
      }
      try {
        KnowledgeGraphNeo4j.this.update(transaction, asset, userScope, properties);
      } catch (Exception e) {
        closed = true;
        throw storageFailure("updating " + asset.getClass().getSimpleName(), e);
      }
    }

    @Override
    public void link(
        RuntimeAsset source,
        RuntimeAsset destination,
        GraphModel.Relationship relationship,
        Object... additionalProperties) {
      if (closed) {
        throw new KlabStorageException(
            "Cannot link assets in a closed knowledge-graph transaction");
      }
      try {
        // KLAB-DEBUG-GUARD: links with unassigned endpoints are still attempted by design here;
        // report them without changing submission or commit behavior.
        if (source.getId() == 0 || destination.getId() == 0) {
          Logging.INSTANCE.warn(
              "KLAB-DEBUG-GUARD: attempting KG link with unassigned endpoint: source={} target={} "
                  + "relationship={}",
              source.getId(),
              destination.getId(),
              relationship);
        }
        KnowledgeGraphNeo4j.this.link(
            transaction, source, destination, relationship, userScope, additionalProperties);
        if (relationship == GraphModel.Relationship.HAS_CHILD
            || relationship == GraphModel.Relationship.HAS_MEMBER) {
          links.add(Pair.of(source.getId(), destination.getId()));
        }
      } catch (Exception e) {
        closed = true;
        throw storageFailure("linking assets through " + relationship, e);
      }
    }

    private KlabStorageException storageFailure(String operation, Exception cause) {
      Logging.INSTANCE.error("Knowledge-graph transaction failed while " + operation, cause);
      return cause instanceof KlabStorageException storageException
          ? storageException
          : new KlabStorageException(cause);
    }

    @Override
    public void fail(Exception e) {
      this.closed = true;
      rollbackOpenTransaction();
      clearTransactionState();
      closeSession();
    }

    @Override
    public void close() throws IOException {

      if (this.closed) {
        rollbackOpenTransaction();
        clearTransactionState();
        closeSession();
        return;
      }
      this.closed = true;

      try {
        // create parent IDs
        for (var link : links) {
          var asset = idCache.get(link.getSecond());
          if (asset != null) {
            var props = Map.of(GraphModel.Fields.PARENT_ID, link.getFirst());
            // update parent ID in the asset that gets out of the transaction
            setParentId(asset, link.getFirst());
            query(
                transaction,
                Queries.UPDATE_PROPERTIES_GENERIC,
                Map.of(GraphModel.Fields.ID, asset.getId(), GraphModel.Fields.PROPERTIES, props),
                userScope);
          }
        }

        // update time of last successful operation
        var props = Map.of(GraphModel.Fields.LAST_UPDATE, System.currentTimeMillis());
        query(
            transaction,
            Queries.UPDATE_PROPERTIES.replace("{type}", GraphModel.Labels.CONTEXT),
            Map.of(GraphModel.Fields.ID, rootContextId, GraphModel.Fields.PROPERTIES, props),
            userScope);

        commitTransaction();
      } catch (RuntimeException e) {
        rollbackOpenTransaction();
        throw e;
      } finally {
        closeSession();
      }
    }

    private void setParentId(RuntimeAsset asset, Long first) {
      switch (asset) {
        case ObservationImpl observation -> observation.setParentId(first);
        case PlanImpl plan -> plan.setParentId(first);
        case ActuatorImpl actuator -> actuator.setParentId(first);
        case CohortImpl cohort -> cohort.setParentId(first);
        default -> {}
      }
    }

    public void commitTransaction() {

      if (!transaction.isOpen()) {
        Logging.INSTANCE.warn("Transaction is not open, skipping commit. Shouldn't happen");
        return;
      }
      try {
        transaction.commit();
        stored.forEach(asset -> assetCache.put(asset.getId(), asset));
      } finally {
        clearTransactionState();
      }
    }

    private void rollbackOpenTransaction() {
      if (!sessionClosed && transaction.isOpen()) {
        transaction.rollback();
      }
    }

    private void clearTransactionState() {
      stored.clear();
      links.clear();
      idCache.clear();
    }

    private void closeSession() {
      if (!sessionClosed) {
        session.close();
        sessionClosed = true;
      }
    }
  }

  @Override
  public Transaction createTransaction(ContextScope contextScope) {
    return new TransactionImpl(contextScope);
  }

  protected synchronized EagerResult query(
      String query, Map<String, Object> parameters, Scope scope) {
    if (isOnline()) {
      try {
        //                System.out.printf("\nQUERY " + query + "\n     WITH " + parameters);
        return parameters == null || parameters.isEmpty()
            ? driver.executableQuery(query).execute()
            : driver.executableQuery(query).withParameters(parameters).execute();
      } catch (Throwable t) {
        if (scope != null) {
          scope.error(t.getMessage(), t);
        } else {
          Logging.INSTANCE.error(t);
        }
      }
    }
    return null;
  }

  protected synchronized Result query(
      org.neo4j.driver.Transaction transaction,
      String query,
      Map<String, Object> parameters,
      Scope scope) {
    if (isOnline()) {
      try {
        return transaction.run(query, parameters);
      } catch (Throwable t) {
        if (scope != null) {
          scope.error(t.getMessage(), t);
        } else {
          Logging.INSTANCE.error(t);
        }
      }
    }
    return null;
  }

  /** Ensure things are OK re: main agents and the like. Must be called only once */
  protected void initializeContext(DigitalTwin.Configuration configuration, UserScope scope) {

    this.rootContextId = configuration.getId();
    this.userScope = scope;
    this.serviceId = scope.getHostServiceId();
    this.klab = getOrCreateAgent("k.LAB", "AI");
    this.user = getOrCreateAgent(scope.getUser().getUsername(), "USER");

    ensureRuntimeIndexes(scope);

    var result = query(Queries.FIND_CONTEXT, Map.of(GraphModel.Fields.CONTEXT_ID, configuration.getId()), scope);

    if (result.records().isEmpty()) {

      long timestamp = System.currentTimeMillis();
      var activityId = nextKey();

      var federation = Klab.INSTANCE.getFederationData(scope.getUser());
      var rights = configuration.getAccessRights();
      if (rights == null) {
        rights = ResourcePrivileges.create(scope);
      }

      for (var query : Queries.INITIALIZATION_QUERIES) {
        query(
            query,
            Map.of(
                GraphModel.Fields.CONTEXT_ID,
                configuration.getId(),
                GraphModel.Fields.NAME,
                configuration.getName(),
                GraphModel.Fields.RIGHTS,
                rights.toString(),
                GraphModel.Fields.TIMESTAMP,
                timestamp,
                GraphModel.Fields.FEDERATION,
                (federation == null ? "" : federation.getId()),
                GraphModel.Fields.DESCRIPTION,
                (configuration.getDescription() == null
                    ? "No description given"
                    : configuration.getDescription()),
                GraphModel.Fields.LAST_UPDATE,
                System.currentTimeMillis(),
                GraphModel.Fields.USERNAME,
                scope.getUser().getUsername(),
                GraphModel.Fields.EXPIRATION_TYPE,
                configuration.getPersistence().name(),
                GraphModel.Fields.ACTIVITY_ID,
                activityId),
            scope);
      }

      // create spatial layers only if they don't already exist
      String layerName = getShapeLayerName(configuration.getId());
      var layerCheck =
          query(
              ("CALL spatial.layers() YIELD " + GraphModel.Fields.NAME + " WHERE " + GraphModel.Fields.NAME + " = $" + GraphModel.Fields.LAYER_NAME + " RETURN count(" + GraphModel.Fields.NAME + ") > 0 AS " + GraphModel.Fields.EXISTS),
              Map.of(GraphModel.Fields.LAYER_NAME, layerName),
              scope);
      boolean layerExists =
          layerCheck != null
              && !layerCheck.records().isEmpty()
              && layerCheck.records().getFirst().get(GraphModel.Fields.EXISTS).asBoolean(false);
      if (!layerExists) {
        query(
            ("CALL spatial.addLayer($" + GraphModel.Fields.LAYER_NAME + ", 'WKB', '" + GraphModel.Fields.SHAPE + "')"),
            Map.of(GraphModel.Fields.LAYER_NAME, layerName),
            scope);
      }
    }
  }

  private void ensureRuntimeIndexes(Scope scope) {
    for (var statement :
        List.of(
            ("CREATE INDEX observation_id IF NOT EXISTS FOR (n:" + GraphModel.Labels.OBSERVATION + ") ON (n." + GraphModel.Fields.ID + ")"),
            ("CREATE INDEX observation_urn IF NOT EXISTS FOR (n:" + GraphModel.Labels.OBSERVATION + ") ON (n." + GraphModel.Fields.URN + ")"),
            ("CREATE INDEX observation_semantics IF NOT EXISTS FOR (n:" + GraphModel.Labels.OBSERVATION + ") ON (n." + GraphModel.Fields.SEMANTICS + ")"),
            ("CREATE INDEX observation_observable IF NOT EXISTS FOR (n:" + GraphModel.Labels.OBSERVATION + ") ON (n." + GraphModel.Fields.OBSERVABLE + ")"),
            ("CREATE INDEX cohort_id IF NOT EXISTS FOR (n:" + GraphModel.Labels.COHORT + ") ON (n." + GraphModel.Fields.ID + ")"),
            ("CREATE INDEX cohort_urn IF NOT EXISTS FOR (n:" + GraphModel.Labels.COHORT + ") ON (n." + GraphModel.Fields.URN + ")"),
            ("CREATE INDEX cohort_observable IF NOT EXISTS FOR (n:" + GraphModel.Labels.COHORT + ") ON (n." + GraphModel.Fields.OBSERVABLE + ")"),
            ("CREATE INDEX activity_id IF NOT EXISTS FOR (n:" + GraphModel.Labels.ACTIVITY + ") ON (n." + GraphModel.Fields.ID + ")"),
            ("CREATE INDEX activity_urn IF NOT EXISTS FOR (n:" + GraphModel.Labels.ACTIVITY + ") ON (n." + GraphModel.Fields.URN + ")"),
            ("CREATE INDEX data_id IF NOT EXISTS FOR (n:" + GraphModel.Labels.DATA + ") ON (n." + GraphModel.Fields.ID + ")"),
            ("CREATE INDEX data_urn IF NOT EXISTS FOR (n:" + GraphModel.Labels.DATA + ") ON (n." + GraphModel.Fields.URN + ")"))) {
      query(statement, Map.of(), scope);
    }
  }

  protected Agent getOrCreateAgent(String name, String ai) {
    var result =
        adapt(
            query(
                ("MATCH (a:" + GraphModel.Labels.AGENT + " {" + GraphModel.Fields.NAME + ": $" + GraphModel.Fields.AGENT_NAME + "}) RETURN a"),
                Map.of(GraphModel.Fields.AGENT_NAME, name),
                userScope),
            Agent.class,
            userScope);
    if (!result.isEmpty()) {
      return result.getFirst();
    }

    var agent = new AgentImpl();
    agent.setName(name);
    var id = store(agent, userScope, GraphModel.Fields.TYPE, ai);
    agent.setId(id);
    return agent;
  }

  @Override
  public void deleteContext() {
    query(
        ("CALL spatial.removeLayer($" + GraphModel.Fields.LAYER_NAME + ")"),
        Map.of(GraphModel.Fields.LAYER_NAME, getShapeLayerName(rootContextId)),
        userScope);
    query(Queries.REMOVE_CONTEXT, Map.of(GraphModel.Fields.CONTEXT_ID, rootContextId), userScope);
  }

  @Override
  public void deleteContext(ContextInfo contextScope, ServiceScope serviceScope) {
    query(
        ("CALL spatial.removeLayer($" + GraphModel.Fields.LAYER_NAME + ")"),
        Map.of(GraphModel.Fields.LAYER_NAME, getShapeLayerName(contextScope.getConfiguration().getId())),
        userScope);
    query(
        Queries.REMOVE_CONTEXT,
        Map.of(GraphModel.Fields.CONTEXT_ID, contextScope.getConfiguration().getId()),
        serviceScope);
  }

  /**
   * @param query
   * @param requiredClass
   * @param <T>
   * @return
   */
  protected <T> List<T> adapt(EagerResult query, Class<T> requiredClass, Scope scope) {

    List<T> ret = new ArrayList<>();

    for (var record : query.records()) {

      Value node = null;
      Map<String, Object> properties = new HashMap<>();
      if (!record.values().isEmpty()) {
        // must be one field for the node
        node = record.values().getFirst();
      }

      if (node == null) {
        continue;
      }

      var cls = requiredClass;
      if (cls == RuntimeAsset.class) {
        cls = null;
        for (var label : node.asNode().labels()) {
          cls =
              switch (label) {
                case GraphModel.Labels.OBSERVATION -> (Class<T>) Observation.class;
                case GraphModel.Labels.AGENT -> (Class<T>) Agent.class;
                case GraphModel.Labels.PLAN -> (Class<T>) Plan.class;
                case GraphModel.Labels.ACTUATOR -> (Class<T>) Actuator.class;
                case GraphModel.Labels.GEOMETRY -> (Class<T>) Geometry.class;
                case GraphModel.Labels.ACTIVITY -> (Class<T>) Activity.class;
                case GraphModel.Labels.CONTEXT -> (Class<T>) ContextScope.class;
                case GraphModel.Labels.DATAFLOW -> (Class<T>) Dataflow.class;
                case GraphModel.Labels.PROVENANCE -> (Class<T>) Provenance.class;
                case GraphModel.Labels.DATA -> (Class<T>) Storage.Shard.class;
                case GraphModel.Labels.COHORT -> (Class<T>) Cohort.class;
                default -> null;
              };
          if (cls != null) {
            break;
          }
        }
        if (cls == null) {
          continue;
        }
      }

      if (cls == ContextScope.class || cls == RuntimeAsset.ContextAsset.class) {
        ret.add((T) RuntimeAsset.CONTEXT_ASSET);
      } else if (cls == Dataflow.class || cls == RuntimeAsset.DataflowAsset.class) {
        ret.add((T) RuntimeAsset.DATAFLOW_ASSET);
      } else if (cls == Provenance.class || cls == RuntimeAsset.ProvenanceAsset.class) {
        ret.add((T) RuntimeAsset.PROVENANCE_ASSET);
      } else if (Map.class.isAssignableFrom(cls)) {

        ret.add((T) node.asMap(Map.of()));

      } else if (Link.class.isAssignableFrom(cls)) {

        var link = new LinkImpl();
        link.getProperties().putAll(node.asMap());
        ret.add((T) link);

      } else if (Agent.class.isAssignableFrom(cls)) {

        var instance = new AgentImpl();
        instance.setName(node.get(GraphModel.Fields.NAME).asString());
        instance.setId(node.get(GraphModel.Fields.ID).asLong());
        instance.setEmpty(false);

        ret.add((T) instance);

      } else if (Cohort.class.isAssignableFrom(cls)) {

        var instance = new CohortImpl();
        var reasoner = scope.getService(Reasoner.class);

        instance.setObservable(reasoner.resolveObservable(node.get(GraphModel.Fields.OBSERVABLE).asString()));
        instance.setUrn(node.get(GraphModel.Fields.URN).asString());
        instance.setId(node.get(GraphModel.Fields.ID).asLong());
        instance.setChildrenCount(node.get(GraphModel.Fields.CHILDREN_COUNT).asInt());
        instance.setParentId(node.get(GraphModel.Fields.PARENT_ID).asLong());
        if (!node.get(GraphModel.Fields.GEOMETRY).isNull()) {
          // TODO remove - this is a backward-compatibility check when the code is still tentative
          instance.setGeometry(
              GeometryRepository.INSTANCE.get(node.get(GraphModel.Fields.GEOMETRY).asString(), Geometry.class));
        }

        ret.add((T) instance);

      } else if (Observation.class.isAssignableFrom(cls)) {

        var instance = new ObservationImpl();
        var reasoner = scope.getService(Reasoner.class);

        instance.setName(node.get(GraphModel.Fields.NAME).asString());
        instance.setObservable(reasoner.resolveObservable(node.get(GraphModel.Fields.OBSERVABLE).asString()));
        instance.setUrn(node.get(GraphModel.Fields.URN).asString());
        instance.setId(node.get(GraphModel.Fields.ID).asLong());
        instance.setChildrenCount(node.get(GraphModel.Fields.CHILDREN_COUNT).asInt());
        instance.setParentId(node.get(GraphModel.Fields.PARENT_ID).asLong());
        instance.setEventTimestamps(node.get(GraphModel.Fields.EVENT_TIMESTAMPS).asList(value -> value.asLong()));
        instance.setSubstantialQuality(node.get(GraphModel.Fields.SUBSTANTIAL).asBoolean(false));
        restoreObservationMetadata(node, instance);
        if (!node.get(GraphModel.Fields.HISTOGRAMS).isNull()) {
          instance.setHistograms(
              Utils.Data.deserializeHistogramMap(node.get(GraphModel.Fields.HISTOGRAMS).asString()));
        } else if (!node.get(GraphModel.Fields.HISTOGRAM).isNull()) {
          // Legacy observations stored one aggregate histogram. Its temporal distribution cannot
          // be recovered, so retain it under the initialization timestamp until the observation is
          // contextualized again and a true slice map replaces it.
          instance.setHistograms(
              Map.of(
                  0L,
                  Utils.Json.parseObject(node.get(GraphModel.Fields.HISTOGRAM).asString(), HistogramImpl.class)));
        }
        //        var instanceUrn = node.get(GraphModel.Fields.URN).asString();
        //        if (instanceUrn != null) {
        //          instance.getMetadata().put(Metadata.IM_FEATURE_URN, instanceUrn);
        //        }
        var cData = new ObservationImpl.ContextualizationDataImpl();
        var service = scope.getService(RuntimeService.class);
        cData.setServiceUrl(service.getUrl());
        cData.setServiceId(serviceId);
        cData.setAdapterId(
            node.get(GraphModel.Fields.ADAPTER_ID).isNull() ? null : node.get(GraphModel.Fields.ADAPTER_ID).asString());
        if (!node.get(GraphModel.Fields.ADAPTER_PARAMETERS).isNull()) {
          var params =
              Utils.Json.parseObject(node.get(GraphModel.Fields.ADAPTER_PARAMETERS).asString(), Parameters.class);
          cData.getParameters().putAll(params);
        }

        // sharding strategy, if any.
        if (!node.get(GraphModel.Fields.FILL_CURVE).isNull()) {
          var shardingStrategy = new Data.ShardingStrategy();
          shardingStrategy.setDataType(Storage.Type.valueOf(node.get(GraphModel.Fields.DATA_TYPE).asString()));
          shardingStrategy.setCurve(Data.FillCurve.valueOf(node.get(GraphModel.Fields.FILL_CURVE).asString()));
          shardingStrategy.setSuggestedSplits(node.get(GraphModel.Fields.SUGGESTED_SPLITS).asInt());
          shardingStrategy.setMaxBufferSize(node.get(GraphModel.Fields.MAX_BUFFER_SIZE).asLong());
          shardingStrategy.setMinSplitSize(node.get(GraphModel.Fields.MIN_SPLIT_SIZE).asLong());
          cData.setNativeShardingStrategy(shardingStrategy);
        }

        instance.setContextualizationData(cData);

        var gResult =
            query(
                ("MATCH (o:" + GraphModel.Labels.OBSERVATION + ")-[:" + GraphModel.Relationship.HAS_GEOMETRY.name() + "]->(g:" + GraphModel.Labels.GEOMETRY + ") WHERE o." + GraphModel.Fields.ID)
                    + (" = $" + GraphModel.Fields.ID + " RETURN g"),
                Map.of(GraphModel.Fields.ID, node.get(GraphModel.Fields.ID).asLong()),
                scope);

        if (gResult != null && !gResult.records().isEmpty()) {
          instance.setGeometry(adapt(gResult, Geometry.class, scope).getFirst());
        }

        ret.add((T) instance);

      } else if (Activity.class.isAssignableFrom(cls)) {
        var instance = new ActivityImpl();
        instance.setStart(node.get(GraphModel.Fields.START).asLong(0));
        instance.setEnd(node.get(GraphModel.Fields.END).asLong(0));
        instance.setObservationUrn(
            node.get(GraphModel.Fields.OBSERVATION_URN).isNull() ? null : node.get(GraphModel.Fields.OBSERVATION_URN).asString());
        instance.setName(node.get(GraphModel.Fields.NAME).isNull() ? null : node.get(GraphModel.Fields.NAME).asString());
        instance.setServiceName(
            node.get(GraphModel.Fields.SERVICE_NAME).isNull() ? null : node.get(GraphModel.Fields.SERVICE_NAME).asString());
        instance.setServiceId(
            node.get(GraphModel.Fields.SERVICE_ID).isNull() ? null : node.get(GraphModel.Fields.SERVICE_ID).asString());
        instance.setServiceType(
            node.get(GraphModel.Fields.SERVICE_TYPE).isNull()
                ? null
                : KlabService.Type.valueOf(node.get(GraphModel.Fields.SERVICE_TYPE).asString()));
        instance.setUrn(node.get(GraphModel.Fields.URN).isNull() ? null : node.get(GraphModel.Fields.URN).asString());
        instance.setDataflow(
            node.get(GraphModel.Fields.DATAFLOW).isNull() ? null : node.get(GraphModel.Fields.DATAFLOW).asString());
        instance.setType(
            node.get(GraphModel.Fields.TYPE).isNull() ? null : Activity.Type.valueOf(node.get(GraphModel.Fields.TYPE).asString()));
        instance.setOutcome(
            node.get(GraphModel.Fields.OUTCOME).isNull()
                ? null
                : Activity.Outcome.valueOf(node.get(GraphModel.Fields.OUTCOME).asString()));
        instance.setCredits(node.get(GraphModel.Fields.CREDITS).asLong(0));
        instance.setSize(node.get(GraphModel.Fields.SIZE).asLong(0));
        instance.setSchedulerTime(node.get(GraphModel.Fields.SCHEDULER_TIME).asList(value -> value.asLong()));
        instance.setStackTrace(
            node.get(GraphModel.Fields.STACK_TRACE).isNull() ? null : node.get(GraphModel.Fields.STACK_TRACE).asString());
        instance.setTriggeringActivityUrn(
            node.get(GraphModel.Fields.TRIGGERING_ACTIVITY_URN).isNull()
                ? null
                : node.get(GraphModel.Fields.TRIGGERING_ACTIVITY_URN).asString());
        restoreMetadata(node, instance.getMetadata());
        instance.setDescription(
            node.get(GraphModel.Fields.DESCRIPTION).isNull()
                ? "No description"
                : node.get(GraphModel.Fields.DESCRIPTION).asString());
        instance.setId(node.get(GraphModel.Fields.ID).asLong());
        instance.setParentId(node.get(GraphModel.Fields.PARENT_ID).asLong());
        ret.add((T) instance);
      } else if (Actuator.class.isAssignableFrom(cls)) {
        var instance = new ActuatorImpl();
        instance.setId(node.get(GraphModel.Fields.ID).asLong());
        instance.setParentId(node.get(GraphModel.Fields.PARENT_ID).asLong(-1));
        instance.setName(node.get(GraphModel.Fields.NAME).asString(null));
        instance.setStrategyUrn(node.get(GraphModel.Fields.STRATEGY).asString(null));
        instance.setChildrenCount(node.get(GraphModel.Fields.CHILDREN_COUNT).asInt(0));
        if (!node.get(GraphModel.Fields.TYPE).isNull()) {
          instance.setType(org.integratedmodelling.klab.api.knowledge.Artifact.Type.valueOf(node.get(GraphModel.Fields.TYPE).asString()));
        }
        if (!node.get(GraphModel.Fields.ACTUATOR_TYPE).isNull()) {
          instance.setActuatorType(Actuator.Type.valueOf(node.get(GraphModel.Fields.ACTUATOR_TYPE).asString()));
        }
        if (!node.get(GraphModel.Fields.COVERAGE).isNull()) {
          instance.setCoverage(Geometry.create(node.get(GraphModel.Fields.COVERAGE).asString()));
        }
        if (!node.get(GraphModel.Fields.RESOLVED_GEOMETRY).isNull()) {
          instance.setResolvedGeometry(Geometry.create(node.get(GraphModel.Fields.RESOLVED_GEOMETRY).asString()));
        }
        instance.setResolvedCoverage(node.get(GraphModel.Fields.RESOLVED_COVERAGE).asDouble(0));
        // Legacy textual computations are not a lossless executable representation. Leave them
        // unavailable rather than fabricating runnable calls from incomplete historical nodes.
        if (node.get(GraphModel.Fields.ACTUATOR_SCHEMA_VERSION).asInt(0) == 1) {
          if (!node.get(GraphModel.Fields.DATA_JSON).isNull()) {
            instance.setData(Utils.Json.parseObject(node.get(GraphModel.Fields.DATA_JSON).asString(), Parameters.class));
          }
          instance.setComputation(node.get(GraphModel.Fields.COMPUTATION_JSON).asList(value ->
              Utils.Json.parseObject(value.asString(), org.integratedmodelling.klab.api.lang.ServiceCall.class)));
          if (!node.get(GraphModel.Fields.ANNOTATIONS_JSON).isNull()) {
            instance.setAnnotations(node.get(GraphModel.Fields.ANNOTATIONS_JSON).asList(value ->
                Utils.Json.parseObject(value.asString(), org.integratedmodelling.klab.api.lang.Annotation.class)));
          }
          if (!node.get(GraphModel.Fields.SHARDING_STRATEGY_JSON).isNull()) {
            instance.setShardingStrategy(Utils.Json.parseObject(node.get(GraphModel.Fields.SHARDING_STRATEGY_JSON).asString(), Data.ShardingStrategy.class));
          }
        }
        ret.add((T) instance);
      } else if (Plan.class.isAssignableFrom(cls)) {
        var instance = new PlanImpl();
        instance.setId(node.get(GraphModel.Fields.ID).asLong());
        instance.setName(node.get(GraphModel.Fields.NAME).asString(null));
        instance.setParentId(node.get(GraphModel.Fields.PARENT_ID).asLong(-1));
        restoreMetadata(node, instance.getMetadata());
        ret.add((T) instance);
      } else if (Geometry.class.isAssignableFrom(cls)) {
        // TODO use a cache storing scales
        ret.add(
            (T) GeometryRepository.INSTANCE.get(node.get(GraphModel.Fields.DEFINITION).asString(), Geometry.class));
      } else if (Storage.Shard.class.isAssignableFrom(cls)) {

        var shardingStrategy = new Data.ShardingStrategy();
        shardingStrategy.setDataType(Storage.Type.valueOf(node.get(GraphModel.Fields.DATA_TYPE).asString()));
        shardingStrategy.setCurve(Data.FillCurve.valueOf(node.get(GraphModel.Fields.FILL_CURVE).asString()));
        shardingStrategy.setSuggestedSplits(node.get(GraphModel.Fields.SUGGESTED_SPLITS).asInt());
        shardingStrategy.setMaxBufferSize(node.get(GraphModel.Fields.MAX_BUFFER_SIZE).asLong());
        shardingStrategy.setMinSplitSize(node.get(GraphModel.Fields.MIN_SPLIT_SIZE).asLong());

        var instance = new ShardImpl();
        instance.setUrn(node.get(GraphModel.Fields.URN).asString());
        instance.setId(node.get(GraphModel.Fields.ID).asLong());
        instance.setShardCount(node.get(GraphModel.Fields.SHARD_COUNT).asInt());
        instance.setNativeType(Storage.Type.valueOf(node.get(GraphModel.Fields.NATIVE_TYPE).asString()));
        instance.setTimestamp(node.get(GraphModel.Fields.TIMESTAMP).asLong());
        instance.setShardIndex(node.get(GraphModel.Fields.SHARD_INDEX).asInt());
        instance.setPersistence(Persistence.valueOf(node.get(GraphModel.Fields.PERSISTENCE).asString()));
        if (!node.get(GraphModel.Fields.HISTOGRAM).isNull()) {
          instance.setHistogram(
              Utils.Json.parseObject(node.get(GraphModel.Fields.HISTOGRAM).asString(), HistogramImpl.class));
        }
        instance.setShardingStrategy(shardingStrategy);

        var gResult =
            query(
                ("MATCH (o:" + GraphModel.Labels.DATA + ")-[:" + GraphModel.Relationship.HAS_GEOMETRY.name() + "]->(g:" + GraphModel.Labels.GEOMETRY + ") WHERE o." + GraphModel.Fields.URN) + (" = $" + GraphModel.Fields.URN + " RETURN g"),
                Map.of(GraphModel.Fields.URN, node.get(GraphModel.Fields.URN).asString()),
                scope);

        if (gResult != null && !gResult.records().isEmpty()) {
          instance.setGeometry(adapt(gResult, Geometry.class, scope).getFirst());
        }

        ret.add((T) instance);
      }
    }
    return ret;
  }

  @SuppressWarnings("unchecked")
  private void restoreObservationMetadata(Value node, ObservationImpl observation) {
    restoreMetadata(node, observation.getMetadata());
    // Compatibility with observations stored before metadata received its own serialized field.
    node.asNode()
        .asMap()
        .forEach(
            (key, value) -> {
              if (!OBSERVATION_PROPERTIES.contains(key)) {
                observation.getMetadata().putIfAbsent(key, value);
              }
            });
  }

  @SuppressWarnings("unchecked")
  private void restoreMetadata(Value node, Metadata metadataTarget) {
    if (!node.get(GraphModel.Fields.METADATA).isNull()) {
      var metadata = Utils.Json.parseObject(node.get(GraphModel.Fields.METADATA).asString(), Map.class);
      if (metadata != null) {
        metadataTarget.putAll(metadata);
      }
    }
  }

  @Override
  public Agent user() {
    return this.user;
  }

  @Override
  public Agent klab() {
    return this.klab;
  }

  public Geometry getAssetGeometry(RuntimeAsset asset, ContextScope scope) {

    if (asset instanceof Observation observation) {
      if (observation.getGeometry() != null) {
        return observation.getGeometry();
      }
      var result =
          query(
              ("MATCH (o:" + GraphModel.Labels.OBSERVATION + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "})-[:" + GraphModel.Relationship.HAS_GEOMETRY.name() + "]->(g:" + GraphModel.Labels.GEOMETRY + ") RETURN g"),
              Map.of(GraphModel.Fields.ID, observation.getId()),
              scope);
      if (result != null && !result.records().isEmpty()) {
        var geometries = adapt(result, Geometry.class, scope);
        return geometries.isEmpty() ? Geometry.EMPTY : geometries.getFirst();
      }
    } else if (asset instanceof Cohort cohort) {
      var space = getCohortSpatialExtent(asset.getId(), scope);
      var stored =
          cohort.getGeometry() == null || cohort.getGeometry().isUniversal()
              ? null
              : GeometryRepository.INSTANCE.scale(cohort.getGeometry());
      List<Extent<?>> extents =
          stored == null ? new ArrayList<>() : new ArrayList<>(stored.getExtents());
      extents.removeIf(
          extent ->
              (extent.getType() == Geometry.Dimension.Type.SPACE && space != null)
                  || (extent.getType() == Geometry.Dimension.Type.TIME
                      && !SemanticType.isOccurrentSubstantial(
                          cohort.getObservable().getSemantics().getType())));
      if (space != null) {
        extents.add(space);
      }
      return extents.isEmpty() ? Geometry.EMPTY : Scale.create(extents).as(Geometry.class);
    }
    return null;
  }

  private Space getCohortSpatialExtent(long cohortId, ContextScope scope) {
    var convexHull = getCohortMembersConvexHull(cohortId, scope);
    if (convexHull != null) {
      return ShapeImpl.create(convexHull, Projection.getLatLon());
    }
    return null;
  }

  /**
   * Return the convex hull of all indexed shapes for observations linked to the cohort by
   * HAS_MEMBER.
   *
   * <p>The spatial index is used to enumerate shape-bearing observation nodes from the current
   * context layer, then the graph pattern restricts the result to members of the requested cohort.
   *
   * @param cohort the cohort whose member observations are used
   * @param scope the current context scope
   * @return a JTS geometry in lat/lon coordinates, or null if the cohort has no indexed member
   *     shape
   */
  public org.locationtech.jts.geom.Geometry getCohortMembersConvexHull(
      Cohort cohort, ContextScope scope) {
    return cohort == null ? null : getCohortMembersConvexHull(cohort.getId(), scope);
  }

  /**
   * Return the convex hull of all indexed shapes for observations linked to the cohort by
   * HAS_MEMBER.
   *
   * @param cohortId the stored cohort node id
   * @param scope the current context scope
   * @return a JTS geometry in lat/lon coordinates, or null if the cohort has no indexed member
   *     shape
   */
  public org.locationtech.jts.geom.Geometry getCohortMembersConvexHull(
      long cohortId, ContextScope scope) {
    if (cohortId == Observation.UNASSIGNED_ID) {
      return null;
    }

    var result =
        query(
            """
            CALL spatial.bbox($%s, $%s, $%s) YIELD node
            MATCH (:%s {%s: $%s})-[:%s]->(node:%s)
            WHERE node.%s IS NOT NULL
            RETURN node.%s AS %s
            """.formatted(GraphModel.Fields.LAYER_NAME, GraphModel.Fields.LOWER_LEFT,
                GraphModel.Fields.UPPER_RIGHT, GraphModel.Labels.COHORT, GraphModel.Fields.ID,
                GraphModel.Fields.COHORT_ID, GraphModel.Relationship.HAS_MEMBER.name(),
                GraphModel.Labels.OBSERVATION, GraphModel.Fields.SHAPE, GraphModel.Fields.SHAPE,
                GraphModel.Fields.SHAPE),
            Map.of(
                GraphModel.Fields.LAYER_NAME,
                getShapeLayerName(),
                GraphModel.Fields.COHORT_ID,
                cohortId,
                GraphModel.Fields.LOWER_LEFT,
                Map.of(GraphModel.Fields.LONGITUDE, -180.0, GraphModel.Fields.LATITUDE, -90.0),
                GraphModel.Fields.UPPER_RIGHT,
                Map.of(GraphModel.Fields.LONGITUDE, 180.0, GraphModel.Fields.LATITUDE, 90.0)),
            scope);

    if (result == null || result.records().isEmpty()) {
      return null;
    }

    var reader = new WKBReader();
    List<org.locationtech.jts.geom.Geometry> shapes = new ArrayList<>();
    for (var record : result.records()) {
      var shape = readShape(reader, record.get(GraphModel.Fields.SHAPE), scope);
      if (shape != null && !shape.isEmpty()) {
        shapes.add(shape);
      }
    }

    if (shapes.isEmpty()) {
      return null;
    }

    var geometryFactory = shapes.getFirst().getFactory();
    var collection =
        geometryFactory.createGeometryCollection(
            shapes.toArray(new org.locationtech.jts.geom.Geometry[0]));
    return collection.convexHull();
  }

  private org.locationtech.jts.geom.Geometry readShape(WKBReader reader, Value shape, Scope scope) {
    if (shape == null || shape.isNull()) {
      return null;
    }
    try {
      return reader.read(shape.asByteArray());
    } catch (ParseException | IllegalArgumentException e) {
      if (scope != null) {
        scope.error("Cannot parse indexed observation shape", e);
      } else {
        Logging.INSTANCE.error(e);
      }
      return null;
    }
  }

  @Override
  public List<ContextInfo> getExistingContexts(UserScope scope) {

    var ret = new ArrayList<ContextInfo>();
    var result =
        scope == null
            ? query(
                ("match (c:" + GraphModel.Labels.CONTEXT + ")<-[:" + GraphModel.Relationship.CREATED.name() + "]-(a:" + GraphModel.Labels.ACTIVITY + ") return c." + GraphModel.Fields.ID + " as " + GraphModel.Fields.CONTEXT_ID + ", a." + GraphModel.Fields.START + " as ")
                    + GraphModel.Fields.START_TIME,
                Map.of(),
                scope)
            : query(
                ("match (c:" + GraphModel.Labels.CONTEXT + " {" + GraphModel.Fields.USER + ": $" + GraphModel.Fields.USERNAME + "})<-[:" + GraphModel.Relationship.CREATED.name() + "]-(a:" + GraphModel.Labels.ACTIVITY + ") return c")
                    + ("." + GraphModel.Fields.NAME + " as")
                    + (" contextName, c." + GraphModel.Fields.ID + " as " + GraphModel.Fields.CONTEXT_ID + ", a." + GraphModel.Fields.START + " as " + GraphModel.Fields.START_TIME),
                Map.of(GraphModel.Fields.USERNAME, scope.getUser().getUsername()),
                scope);

    for (var record : result.records()) {
      ContextInfo info = new ContextInfo();
      info.setCreationTime(record.get(GraphModel.Fields.START_TIME).asLong());
      info.setIdleTimeMs(System.currentTimeMillis() - record.get(GraphModel.Fields.LAST_UPDATE).asLong());
      info.setConfiguration(
          DigitalTwin.Configuration.builder()
              .url(
                  Utils.URLs.newURL(
                      scope.getService(RuntimeService.class).getUrl()
                          + ServicesAPI.RUNTIME.DIGITAL_TWIN.replace(
                              "{id}", record.get(GraphModel.Fields.ID).toString())))
              .id(record.get(GraphModel.Fields.ID).toString())
              .name(record.get(GraphModel.Fields.NAME).toString())
              .serviceId(serviceId)
              .owner(record.get(GraphModel.Fields.USER).toString())
              .description(record.get(GraphModel.Fields.DESCRIPTION).toString())
              .serverUrl(scope.getService(RuntimeService.class).getUrl())
              .persistence(Persistence.valueOf(record.get(GraphModel.Fields.EXPIRATION).toString()))
              .timeout(
                  scope
                      .getService(RuntimeService.class)
                      .settings()
                      .get(Setting.DIGITAL_TWIN_TIMEOUT_MINUTES, Integer.class),
                  TimeUnit.MINUTES)
              .build()
              .validate(scope));

      // TODO something is probably missing

      // TODO the rest
      ret.add(info);
    }
    return ret;
  }

  @Override
  public void clear() {
    if (userScope == null) {
      driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
    } else {
      query(Queries.REMOVE_CONTEXT, Map.of(GraphModel.Fields.CONTEXT_ID, rootContextId), userScope);
    }
  }

  private RuntimeAsset retrieveFromGraph(
      Object key, Class<? extends RuntimeAsset> assetClass, Scope scope) {
    var field = key instanceof String ? GraphModel.Fields.URN : GraphModel.Fields.ID;
    var result =
        assetClass == RuntimeAsset.class
            ? query("MATCH (n {" + field + (": $" + GraphModel.Fields.KEY + "}) return n"), Map.of(GraphModel.Fields.KEY, key), scope)
            : query(
                ("MATCH (n:{assetLabel} {" + field + (": $" + GraphModel.Fields.KEY + "}) return n"))
                    .replace("{assetLabel}", getLabel(assetClass)),
                Map.of(GraphModel.Fields.KEY, key),
                scope);
    var adapted = adapt(result, assetClass, scope);
    return adapted.isEmpty() ? null : adapted.getFirst();
  }

  @Override
  protected <T extends RuntimeAsset> T retrieve(Object key, Class<T> assetClass, Scope scope) {

    if (key instanceof Long id) {
      try {
        var ret = assetCache.get(id, () -> retrieveFromGraph(id, assetClass, scope));
        if (Cohort.class.isAssignableFrom(ret.getClass())
            && ret instanceof CohortImpl cohort
            && scope instanceof ContextScope contextScope) {
          // the cohort geometry is recomputed at each query
          var cohortGeometry = getAssetGeometry(ret, contextScope);
          if (!cohortGeometry.isEmpty()) {
            cohort.setGeometry(cohortGeometry);
          }
        }
        return (T) ret;
      } catch (Throwable e) {
        // fall back to other strategy
        Logging.INSTANCE.warn("Ignoring unexpected cache error in service-side knowledge graph", e);
      }
    }

    // this only happens in case of cache error or if the ID is not a long
    return (T) retrieveFromGraph(key, assetClass, scope);
  }

  @Override
  protected long store(RuntimeAsset asset, Scope scope, Object... additionalProperties) {

    var type = getLabel(asset);
    var props = asParameters(asset, additionalProperties);
    var ret = nextKey();
    if (ret <= 0) {
      throw new KlabStorageException("Could not allocate a persistent knowledge-graph ID");
    }
    props.put(GraphModel.Fields.ID, ret);
    if (asset instanceof Observation || asset instanceof Activity) {

      // URN for substantials will be not null and set to the pre-resolution identity
      // TODO substantial observations must carry their bounding box and centroid
      var urn =
          asset instanceof Observation observation
              ? (observation.getUrn() == null
                  ? (rootContextId + "." + ret)
                  : ObservationImpl.catalogUrn(rootContextId, observation.getUrn()))
              : (rootContextId + "." + ret);

      props.put(GraphModel.Fields.URN, urn);
    }
    var result =
        query(
            Queries.CREATE_WITH_PROPERTIES.replace("{type}", type),
            Map.of(GraphModel.Fields.PROPERTIES, props),
            scope);
    if (result != null && result.records().size() == 1) {
      setId(asset, ret, null);
      var geometry =
          switch (asset) {
            case Observation observation -> observation.getGeometry();
            case Actuator actuator -> actuator.getCoverage();
            // only store geometries for shards that are partial
            case Storage.Shard shard -> shard.getShardCount() > 1 ? shard.getGeometry() : null;
            default -> null;
          };

      if (geometry != null) {
        storeGeometry(geometry, asset, null);
      }
    }

    return ret;
  }

  protected long store(
      org.neo4j.driver.Transaction transaction,
      RuntimeAsset asset,
      ContextScope scope,
      Object... additionalProperties) {

    var type = getLabel(asset);
    var props = asParameters(asset, additionalProperties);
    var ret = nextKey();
    if (ret <= 0) {
      throw new KlabStorageException("Could not allocate a persistent knowledge-graph ID");
    }
    props.put(GraphModel.Fields.ID, ret);

    if (asset instanceof Observation || asset instanceof Activity) {

      // URN for substantials will be not null and set to the pre-resolution identity
      var urn =
          asset instanceof Observation observation
              ? (observation.getUrn() == null
                  ? (rootContextId + "." + ret)
                  : ObservationImpl.catalogUrn(rootContextId, observation.getUrn()))
              : (rootContextId + "." + ret);

      props.put(GraphModel.Fields.URN, urn);
    }

    boolean storeSpatialData =
        asset instanceof Observation observation
            && observation.getObservable().is(SemanticType.COUNTABLE)
            && observation.getGeometry() != null
            && observation.getGeometry().getDimensions().stream()
                .anyMatch(d -> d.getType() == Geometry.Dimension.Type.SPACE);

    if (storeSpatialData) {
      var scale = GeometryRepository.INSTANCE.scale(((Observation) asset).getGeometry());
      var shape = scale.getSpace().getGeometricShape().transform(Projection.getLatLon());
      if (shape instanceof ShapeImpl shape1) {
        props.put(GraphModel.Fields.SHAPE, ShapeImpl.wkbWriter.write(shape1.getJTSGeometry()));
        var xy = shape1.getCenter(true);
        props.put(GraphModel.Fields.LATITUDE, xy[1]);
        props.put(GraphModel.Fields.LONGITUDE, xy[0]);
      } else {
        storeSpatialData = false;
      }
    }

    var query =
        storeSpatialData
            ? Queries.CREATE_WITH_SHAPE.replace("{type}", type)
            : Queries.CREATE_WITH_PROPERTIES.replace("{type}", type);

    var parameters =
        storeSpatialData
            ? Map.<String, Object>of(GraphModel.Fields.PROPERTIES, props, GraphModel.Fields.LAYER_NAME, getShapeLayerName())
            : Map.<String, Object>of(GraphModel.Fields.PROPERTIES, props);
    var result = query(transaction, query, parameters, scope);
    if (result != null && result.hasNext()) {
      var record = result.next();
      var neo4jNode = record.get(0).asNode();
      setId(asset, ret, scope);
      var geometry =
          switch (asset) {
            case Observation observation -> observation.getGeometry();
            case Actuator actuator -> actuator.getCoverage();
            // only store geometries for shards that are partial
            case Storage.Shard shard -> shard.getShardCount() > 1 ? shard.getGeometry() : null;
            default -> null;
          };

      if (geometry != null) {
        storeGeometry(geometry, asset, transaction);
      }
    } else {
      // KLAB-DEBUG-GUARD: preserve the current no-ID-assignment path when CREATE produces no
      // record, but identify it before the caller records the asset as stored.
      Logging.INSTANCE.warn(
          ("KLAB-DEBUG-GUARD: " + "KG" + " CREATE returned no node record: class={} generatedId={} " + GraphModel.Fields.ASSET_ID + "={}"),
          asset.getClass().getName(),
          ret,
          asset.getId());
    }

    return ret;
  }

  protected void link(
      org.neo4j.driver.Transaction transaction,
      RuntimeAsset source,
      RuntimeAsset destination,
      GraphModel.Relationship relationship,
      Scope scope,
      Object... additionalProperties) {

    // find out if the internal ID or what stored ID should be used
    var sourceQuery = matchAsset(source, "n", GraphModel.Fields.SOURCE_ID);
    var targetQuery = matchAsset(destination, "c", GraphModel.Fields.TARGET_ID);
    var props = asParameters(null, additionalProperties);
    var query =
        ("MATCH (n:{fromLabel}), (c:{toLabel}) WHERE {sourceQuery} AND {targetQuery} CREATE (n)"
                + ("-[r:{relationshipLabel}]->(c) SET r = $" + GraphModel.Fields.PROPERTIES + " RETURN r"))
            .replace("{sourceQuery}", sourceQuery)
            .replace("{targetQuery}", targetQuery)
            .replace("{relationshipLabel}", relationship.name())
            .replace("{fromLabel}", getLabel(source))
            .replace("{toLabel}", getLabel(destination));

    query(
        transaction,
        query,
        Map.of(GraphModel.Fields.SOURCE_ID, getId(source), GraphModel.Fields.TARGET_ID, getId(destination), GraphModel.Fields.PROPERTIES, props),
        scope);
  }

  private void storeGeometry(
      Geometry geometry, RuntimeAsset asset, @Nullable org.neo4j.driver.Transaction transaction) {

    // This guarantees processed, stable geometry representation with WBT
    var encoded = GeometryRepository.INSTANCE.scale(geometry).encode();
    var relationship = GraphModel.Relationship.HAS_GEOMETRY.name();

    // Must be called after update() and this may happen more than once, so we must check to avoid
    // multiple relationships.
    var exists =
        transaction == null
            ? query(
                ("MATCH (n:{assetLabel} {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ASSET_ID + "})-[:{relationship}]->(g:" + GraphModel.Labels.GEOMETRY + ") RETURN g")
                    .replace("{assetLabel}", getLabel(asset))
                    .replace("{relationship}", relationship),
                Map.of(GraphModel.Fields.ASSET_ID, getId(asset)),
                userScope)
            : query(
                transaction,
                ("MATCH (n:{assetLabel} {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ASSET_ID + "})-[:{relationship}]->(g:" + GraphModel.Labels.GEOMETRY + ") RETURN g")
                    .replace("{assetLabel}", getLabel(asset))
                    .replace("{relationship}", relationship),
                Map.of(GraphModel.Fields.ASSET_ID, getId(asset)),
                userScope);

    if (checkExists(exists)) {
      return;
    }

    // only record fully specified scales, not syntactic specifications
    geometry = GeometryRepository.INSTANCE.scale(geometry);

    double coverage = geometry instanceof Coverage cov ? cov.getCoverage() : 1.0;

    // the idea is that looking up the size before the monster string can be faster.
    var query = ("MATCH (g:" + GraphModel.Labels.GEOMETRY + ") WHERE g." + GraphModel.Fields.DEFINITION + " = $" + GraphModel.Fields.DEFINITION + " RETURN g");
    var result =
        transaction == null
            ? query(query, Map.of(GraphModel.Fields.DEFINITION, encoded), userScope)
            : query(transaction, query, Map.of(GraphModel.Fields.DEFINITION, encoded), userScope);

    if (!checkExists(result)) {
      // TODO more geometry data (bounding box, time boundaries etc.)
      if (transaction == null) {
        query(
            ("CREATE (g:" + GraphModel.Labels.GEOMETRY + " {" + GraphModel.Fields.SIZE + ": $" + GraphModel.Fields.SIZE + ", " + GraphModel.Fields.DEFINITION + ": $" + GraphModel.Fields.DEFINITION + ", " + GraphModel.Fields.KEY + ": $" + GraphModel.Fields.KEY + "}) RETURN g"),
            Map.of(GraphModel.Fields.SIZE, geometry.size(), GraphModel.Fields.DEFINITION, encoded, GraphModel.Fields.KEY, geometry.key()),
            userScope);
      } else {
        query(
            transaction,
            ("CREATE (g:" + GraphModel.Labels.GEOMETRY + " {" + GraphModel.Fields.SIZE + ": $" + GraphModel.Fields.SIZE + ", " + GraphModel.Fields.DEFINITION + ": $" + GraphModel.Fields.DEFINITION + ", " + GraphModel.Fields.KEY + ": $" + GraphModel.Fields.KEY + "}) RETURN g"),
            Map.of(GraphModel.Fields.SIZE, geometry.size(), GraphModel.Fields.DEFINITION, encoded, GraphModel.Fields.KEY, geometry.key()),
            userScope);
      }
    }

    // TODO more properties pertaining to the link (e.g. separate space/time coverages etc)
    var properties = Map.of(GraphModel.Fields.COVERAGE, coverage);

    // link it with the associated coverage
    var rel =
        transaction == null
            ? query(
                (("MATCH (n:{assetLabel}), (g:" + GraphModel.Labels.GEOMETRY + ") WHERE n." + GraphModel.Fields.ID + " = $" + GraphModel.Fields.ASSET_ID + " AND g." + GraphModel.Fields.DEFINITION + " = $" + GraphModel.Fields.GEOMETRY_KEY)
                        + " CREATE (n)" // b
                        + ("-[r:{relationship}]->(g) SET r = $" + GraphModel.Fields.PROPERTIES + " RETURN r"))
                    .replace("{assetLabel}", getLabel(asset))
                    .replace("{relationship}", relationship),
                Map.of(GraphModel.Fields.ASSET_ID, getId(asset), GraphModel.Fields.GEOMETRY_KEY, encoded, GraphModel.Fields.PROPERTIES, properties),
                userScope)
            : query(
                transaction,
                (("MATCH (n:{assetLabel}), (g:" + GraphModel.Labels.GEOMETRY + ") WHERE n." + GraphModel.Fields.ID + " = $" + GraphModel.Fields.ASSET_ID + " AND g." + GraphModel.Fields.DEFINITION + " = $" + GraphModel.Fields.GEOMETRY_KEY)
                        + " CREATE (n)"
                        + ("-[r:{relationship}]->(g) SET r = $" + GraphModel.Fields.PROPERTIES + " RETURN r"))
                    .replace("{assetLabel}", getLabel(asset))
                    .replace("{relationship}", relationship),
                Map.of(GraphModel.Fields.ASSET_ID, getId(asset), GraphModel.Fields.GEOMETRY_KEY, encoded, GraphModel.Fields.PROPERTIES, properties),
                userScope);
  }

  private boolean checkExists(Object outcome) {
    if (outcome == null) {
      return false;
    }
    // one day I'll understand why these are unrelated
    return switch (outcome) {
      case EagerResult eagerResult -> !eagerResult.records().isEmpty();
      case Result result -> result.hasNext();
      default -> throw new KlabInternalErrorException("Unexpected Neo4j result type");
    };
  }

  @Override
  protected void link(
      RuntimeAsset source,
      RuntimeAsset destination,
      GraphModel.Relationship relationship,
      Scope scope,
      Object... additionalProperties) {

    // find out if the internal ID or what stored ID should be used
    var sourceQuery = matchAsset(source, "n", GraphModel.Fields.SOURCE_ID);
    var targetQuery = matchAsset(destination, "c", GraphModel.Fields.TARGET_ID);
    var props = asParameters(null, additionalProperties);
    var query =
        ("match (n:{fromLabel}), (c:{toLabel}) WHERE {sourceQuery} AND {targetQuery} CREATE (n)"
                + ("-[r:{relationshipLabel}]->(c) SET r = $" + GraphModel.Fields.PROPERTIES + " RETURN r"))
            .replace("{sourceQuery}", sourceQuery)
            .replace("{targetQuery}", targetQuery)
            .replace("{relationshipLabel}", relationship.name())
            .replace("{fromLabel}", getLabel(source))
            .replace("{toLabel}", getLabel(destination));

    query(
        query,
        Map.of(GraphModel.Fields.SOURCE_ID, getId(source), GraphModel.Fields.TARGET_ID, getId(destination), GraphModel.Fields.PROPERTIES, props),
        scope);
  }

  private String matchAsset(RuntimeAsset asset, String name, String queryVariable) {

    var ret =
        switch (asset) {
          case Activity ignored3 -> name + ("." + GraphModel.Fields.ID + " = $") + queryVariable;
          case Observation ignored2 -> name + ("." + GraphModel.Fields.ID + " = $") + queryVariable;
          case Cohort ignored2 -> name + ("." + GraphModel.Fields.ID + " = $") + queryVariable;
          case Actuator ignored1 -> name + ("." + GraphModel.Fields.ID + " = $") + queryVariable;
          case Storage.Shard ignored -> name + ("." + GraphModel.Fields.ID + " = $") + queryVariable;
          case Agent ignored -> name + ("." + GraphModel.Fields.NAME + " = $") + queryVariable;
          default -> null;
        };

    if (ret == null) {
      ret =
          switch (asset.classify()) {
            case DATAFLOW, PROVENANCE, DATA, CONTEXT -> name + ("." + GraphModel.Fields.ID + " = $") + queryVariable;
            default -> throw new KlabIllegalStateException("Unexpected value: " + asset.classify());
          };
    }

    return ret;
  }

  private Object getId(RuntimeAsset asset) {

    Object ret =
        switch (asset) {
          case ActuatorImpl actuator -> actuator.getId();
          case ActivityImpl activity -> activity.getId();
          case ObservationImpl observation -> observation.getId();
          case Agent agent -> agent.getName();
          case ShardImpl buffer -> buffer.getId();
          case Cohort cohort -> cohort.getId();
          default -> null;
        };

    if (ret == null) {
      // it's one of the preset ones
      ret =
          switch (asset.classify()) {
            case CONTEXT -> rootContextId;
            case DATAFLOW -> rootContextId + ".DATAFLOW";
            case PROVENANCE -> rootContextId + ".PROVENANCE";
            default -> throw new KlabIllegalStateException("Unexpected value: " + asset.classify());
          };
    }
    return ret;
  }

  private void setId(RuntimeAsset asset, long id, ContextScope scope) {
    switch (asset) {
      case ObservationImpl observation -> {
        var temporaryId = observation.getId();
        observation.setId(id);
        observation.setUrn(
            observation.getUrn() == null
                ? rootContextId + "." + id
                : ObservationImpl.catalogUrn(rootContextId, observation.getUrn()));
        if (scope != null && observation.getObservable().is(SemanticType.QUALITY)) {
          if (!scope.getDigitalTwin().getStorageManager().finalizeStorage(temporaryId, id)) {
            observation.getNotifications().add(Notification.error("Problem finalizing storage"));
          }
        }
      }
      case ActuatorImpl actuator -> actuator.setId(id);
      case ShardImpl buffer -> buffer.setId(id);
      case CohortImpl cohort -> cohort.setId(id);
      case ActivityImpl activity -> {
        activity.setId(id);
        activity.setUrn(rootContextId + "." + id);
      }
      case AgentImpl agent -> agent.setId(id);
      default -> {}
    }
  }

  @Override
  public RuntimeAsset dataflow() {
    if (userScope == null) {
      throw new KlabIllegalStateException(
          "Access to context node in a non-contexual knowledge graph");
    }
    return dataflowNode;
  }

  @Override
  public RuntimeAsset provenance() {
    if (userScope == null) {
      throw new KlabIllegalStateException(
          "Access to context node in a non-contexual knowledge graph");
    }
    return provenanceNode;
  }

  @Override
  public RuntimeAsset scope() {
    if (userScope == null) {
      throw new KlabIllegalStateException(
          "Access to context node in a non-contexual knowledge graph");
    }
    return contextNode;
  }

  private String encodeGeometry(Geometry observationGeometry) {

    /*
     * Ensure that the shape parameter is in WKB and any prescriptive grid parameters are resolved.
     * TODO we should cache the geometries and scales, then reuse them.
     */
    var ret = GeometryRepository.INSTANCE.scale(observationGeometry).encode(ShapeImpl.wkbEncoder);

    return ret;
  }

  private String getLabel(Object target) {

    if (target instanceof RuntimeAsset.Type asset) {
      return switch (asset) {
        case OBSERVATION -> GraphModel.Labels.OBSERVATION;
        case ACTUATOR -> GraphModel.Labels.ACTUATOR;
        case CONTEXT -> GraphModel.Labels.CONTEXT;
        case DATAFLOW -> GraphModel.Labels.DATAFLOW;
        case PROVENANCE -> GraphModel.Labels.PROVENANCE;
        case ACTIVITY -> GraphModel.Labels.ACTIVITY;
        case PLAN -> GraphModel.Labels.PLAN;
        case AGENT -> GraphModel.Labels.AGENT;
        case DATA -> GraphModel.Labels.DATA;
        case COHORT -> GraphModel.Labels.COHORT;
        default -> throw new KlabInternalErrorException("Cannot find a KG node label for " + asset);
      };
    }

    if (target instanceof KnowledgeGraphQuery.AssetType assetType) {
      return switch (assetType) {
        case SCOPE -> GraphModel.Labels.CONTEXT;
        case DATAFLOW -> GraphModel.Labels.DATAFLOW;
        case PROVENANCE -> GraphModel.Labels.PROVENANCE;
        case ACTUATOR -> GraphModel.Labels.ACTUATOR;
        case ACTIVITY -> GraphModel.Labels.ACTIVITY;
        case AGENT -> GraphModel.Labels.AGENT;
        case PLAN -> GraphModel.Labels.PLAN;
        case OBSERVATION -> GraphModel.Labels.OBSERVATION;
        case COHORT -> GraphModel.Labels.COHORT;
        case DATA -> GraphModel.Labels.DATA;
        case ANY -> null;
        default ->
            throw new KlabInternalErrorException("Cannot find a KG node label for " + assetType);
      };
    }

    if (target instanceof GraphModel.Relationship relationship) {
      return relationship.name();
    }

    if (target instanceof Class<?> cls) {
      if (Observation.class.isAssignableFrom(cls)) {
        return GraphModel.Labels.OBSERVATION;
      } else if (Activity.class.isAssignableFrom(cls)) {
        return GraphModel.Labels.ACTIVITY;
      } else if (Actuator.class.isAssignableFrom(cls)) {
        return GraphModel.Labels.ACTUATOR;
      } else if (Agent.class.isAssignableFrom(cls)) {
        return GraphModel.Labels.AGENT;
      } else if (Plan.class.isAssignableFrom(cls)) {
        return GraphModel.Labels.PLAN;
      } else if (Storage.Shard.class.isAssignableFrom(cls)) {
        return GraphModel.Labels.DATA;
      } else if (Cohort.class.isAssignableFrom(cls)) {
        return GraphModel.Labels.COHORT;
      }
    }

    var ret =
        switch (target) {
          case Observation x -> GraphModel.Labels.OBSERVATION;
          case Activity x -> GraphModel.Labels.ACTIVITY;
          case Actuator x -> GraphModel.Labels.ACTUATOR;
          case Agent x -> GraphModel.Labels.AGENT;
          case Cohort x -> GraphModel.Labels.COHORT;
          case Storage.Shard x -> GraphModel.Labels.DATA;
          case Plan x -> GraphModel.Labels.PLAN;
          default -> null;
        };

    if (ret == null && target instanceof RuntimeAsset runtimeAsset) {
      ret =
          switch (runtimeAsset.classify()) {
            case CONTEXT -> GraphModel.Labels.CONTEXT;
            case DATAFLOW -> GraphModel.Labels.DATAFLOW;
            case PROVENANCE -> GraphModel.Labels.PROVENANCE;
            default ->
                throw new KlabIllegalStateException("Unexpected value: " + runtimeAsset.classify());
          };
    }

    if (ret == null) {
      throw new KlabIllegalArgumentException(
          "Cannot store " + target.getClass().getCanonicalName() + " in knowledge graph");
    }

    return ret;
  }

  public void update(
      org.neo4j.driver.Transaction transaction,
      RuntimeAsset runtimeAsset,
      Scope scope,
      Object... parameters) {
    var props = asParameters(runtimeAsset, parameters);
    props.remove(GraphModel.Fields.ID);
    var result =
        query(
            transaction,
            Queries.UPDATE_PROPERTIES.replace("{type}", getLabel(runtimeAsset)),
            Map.of(GraphModel.Fields.ID, runtimeAsset.getId(), GraphModel.Fields.PROPERTIES, props),
            scope);
  }

  //  @Override
  public void update(RuntimeAsset runtimeAsset, Scope scope, Object... parameters) {
    var props = asParameters(runtimeAsset, parameters);
    props.remove(GraphModel.Fields.ID);
    var result =
        query(
            Queries.UPDATE_PROPERTIES.replace("{type}", getLabel(runtimeAsset)),
            Map.of(GraphModel.Fields.ID, runtimeAsset.getId(), GraphModel.Fields.PROPERTIES, props),
            scope);
  }

  @Override
  public synchronized long nextKey() {
    var ret = -1L;
    var lastActivity = System.currentTimeMillis();
    var result = query(("MATCH (n:" + GraphModel.Labels.STATISTICS + ") return n." + GraphModel.Fields.NEXT_ID), Map.of(), userScope);
    if (result != null) {
      if (result.records().isEmpty()) {
        ret = 1;
        query(("CREATE (n:" + GraphModel.Labels.STATISTICS + " {" + GraphModel.Fields.NEXT_ID + ": 1})"), Map.of(), userScope);
      } else {
        var id = result.records().getFirst().get(result.keys().getFirst()).asLong();
        ret = id + 1;
        query(
            ("MATCH (n:" + GraphModel.Labels.STATISTICS + ") WHERE n." + GraphModel.Fields.NEXT_ID + " = $" + GraphModel.Fields.ID + " SET n." + GraphModel.Fields.NEXT_ID + " = $" + GraphModel.Fields.NEXT_ID + ", n." + GraphModel.Fields.LAST_ACTIVITY + " = ")
                + ("$" + GraphModel.Fields.LAST_ACTIVITY),
            Map.of(GraphModel.Fields.ID, id, GraphModel.Fields.NEXT_ID, ret, GraphModel.Fields.LAST_ACTIVITY, lastActivity),
            userScope);
      }
    }
    // KLAB-DEBUG-GUARD: -1 means next-key allocation failed; preserve the existing return value.
    if (ret == 0) {
      Logging.INSTANCE.warn("KLAB-DEBUG-GUARD: KG nextKey() returned unassigned key: {}", ret);
    }
    return ret;
  }

  private List<Activity> getActivity(ContextScope scope, Object... queriables) {

    Map<String, Object> queryParameters = new LinkedHashMap<>();

    Activity rootActivity = null;
    if (queriables != null) {
      for (var parameter : queriables) {
        if (parameter instanceof Activity root) {
          rootActivity = root;
        } else if (parameter instanceof Long id) {
          queryParameters.put(GraphModel.Fields.ID, id);
        } else if (parameter instanceof Observation observation) {
          queryParameters.put(GraphModel.Fields.OBSERVATION_URN, observation.getUrn());
        } else if (parameter instanceof Activity.Type activityType) {
          queryParameters.put(GraphModel.Fields.TYPE, activityType.name());
        }
      }
    }

    var query = assetQuery("a", GraphModel.Labels.ACTIVITY, queryParameters.keySet());
    if (rootActivity != null) {
      query.append(("<-[*]-(r:" + GraphModel.Labels.ACTIVITY + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ROOT_ACTIVITY_ID + "})"));
      queryParameters.put(GraphModel.Fields.ROOT_ACTIVITY_ID, rootActivity.getId());
    } else {
      query.append(("<-[*]-(p:" + GraphModel.Labels.PROVENANCE + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.PROVENANCE_ID + "})"));
      queryParameters.put(GraphModel.Fields.PROVENANCE_ID, scope.getId() + ".PROVENANCE");
    }

    var result = query(query.append(" return a").toString(), queryParameters, scope);
    return adapt(result, Activity.class, scope);
  }

  private StringBuilder assetQuery(
      String variableName, String assetLabel, Collection<String> keys) {

    var ret = new StringBuilder("MATCH (").append(variableName).append(":").append(assetLabel);

    if (keys.isEmpty()) {
      ret.append(")");
    } else {
      int n = 0;
      for (String key : keys) {
        ret.append(n == 0 ? " {" : ", ");
        ret.append(key).append(": $").append(key);
        n++;
      }
      ret.append("})");
    }

    return ret;
  }

  private List<Agent> getAgent(ContextScope scope, Object... queriables) {

    Map<String, Object> queryParameters = new LinkedHashMap<>();
    var query =
        new StringBuilder(
            getScopeQuery(scope, queryParameters) + ("-[:" + GraphModel.Relationship.HAS_PROVENANCE.name() + "]->") + ("(p:" + GraphModel.Labels.PROVENANCE + ")"));

    if (queriables != null) {
      for (var parameter : queriables) {
        if (parameter instanceof Observable observable) {
          //
        } else if (parameter instanceof Activity rootActivity) {
        } else if (parameter instanceof Long id) {
          queryParameters.put(GraphModel.Fields.ID, id);
          query = new StringBuilder(("MATCH (a:" + GraphModel.Labels.AGENT + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "}"));
        } else if (parameter instanceof Observation observation) {
          // define start node as the one with the observation URN
        } else if (parameter instanceof String name) {
          queryParameters.put(GraphModel.Fields.NAME, name);
          query = new StringBuilder(("MATCH (a:" + GraphModel.Labels.AGENT + " {" + GraphModel.Fields.NAME + ": $" + GraphModel.Fields.NAME + "}"));
        }
      }
    }

    var result = query(query.append(") return a").toString(), queryParameters, scope);
    return adapt(result, Agent.class, scope);
  }

  private List<Observation> getObservation(ContextScope scope, Object... queriables) {

    Map<String, Object> queryParameters = new LinkedHashMap<>();
    var query = new StringBuilder(getScopeQuery(scope, queryParameters));

    if (queriables != null) {
      for (var parameter : queriables) {
        if (parameter instanceof Observable observable) {
          queryParameters.put(GraphModel.Fields.SEMANTICS, observable.getSemantics().getUrn());
          query.append(("MATCH (o:" + GraphModel.Labels.OBSERVATION + " {" + GraphModel.Fields.SEMANTICS + ": $" + GraphModel.Fields.SEMANTICS + "}"));
        } else if (parameter instanceof Activity rootActivity) {
        } else if (parameter instanceof Long id) {
          queryParameters.put(GraphModel.Fields.ID, id);
          query = new StringBuilder(("MATCH (o:" + GraphModel.Labels.OBSERVATION + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "}"));
        } else if (parameter instanceof Observation observation) {
          // define start node as the one with the observation URN
        } else if (parameter instanceof String urn) {
          queryParameters.put(GraphModel.Fields.URN, urn);
        }
      }
    }

    var result = query(query.append(") return o").toString(), queryParameters, scope);
    return adapt(result, Observation.class, scope);
  }

  private List<Actuator> getActuator(ContextScope scope, Object... queriables) {
    Map<String, Object> queryParameters = new LinkedHashMap<>();
    var query = new StringBuilder(getScopeQuery(scope, queryParameters));

    if (queriables != null) {
      for (var parameter : queriables) {
        if (parameter instanceof Observable observable) {
          //
        } else if (parameter instanceof Activity rootActivity) {
        } else if (parameter instanceof Long id) {
          queryParameters.put(GraphModel.Fields.ID, id);
          query = new StringBuilder(("MATCH (n:" + GraphModel.Labels.ACTUATOR + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "})"));
        } else if (parameter instanceof Observation observation) {
          // define start node as the one with the observation URN
        } else if (parameter instanceof String name) {
          queryParameters.put(GraphModel.Fields.NAME, name);
          query.append(("MATCH (n:" + GraphModel.Labels.ACTUATOR + " {" + GraphModel.Fields.NAME + ": $" + GraphModel.Fields.NAME + "})"));
        }
      }
    }

    var result = query(query.append(" return n").toString(), queryParameters, scope);
    return adapt(result, Actuator.class, scope);
  }

  private String getScopeQuery(ContextScope scope, Map<String, Object> parameters) {

    var scopeData = ContextScope.parseScopeId(ContextScope.getScopeId(scope));
    var ret = new StringBuilder(("MATCH (c:" + GraphModel.Labels.CONTEXT + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + "})"));
    parameters.put(GraphModel.Fields.CONTEXT_ID, scopeData.scopeId());

    if (scopeData.observationPath() != null) {
      for (var observationId : scopeData.observationPath()) {
        ret.append(("-[:" + GraphModel.Relationship.HAS_CHILD.name() + "]->(" + GraphModel.Labels.OBSERVATION + " {" + GraphModel.Fields.ID + ": ")).append(observationId).append("})");
      }
    }
    if (scopeData.observerId() != Observation.UNASSIGNED_ID) {
      // TODO needs a locator for the obs to POSTPONE to the query with reversed direction
      // .....(n..)<-[:HAS_OBSERVER]-(observer:Observation {id: ...})
    }

    return ret.toString();
  }

  @Override
  public Agent requireAgent(String agentName) {
    if ("k.LAB".equals(agentName)) {
      return klab;
    } else if (user != null && user.getName().equals(agentName)) {
      return user;
    } else if (agentName != null) {
      // TODO check if make sense
      return getOrCreateAgent(agentName, "USER");
    }
    return user;
  }

  @Override
  public List<ContextInfo> getContextInfo(Scope scope) {

    var sessionIds = new LinkedHashMap<String, SessionInfo>();

    EagerResult contexts =
        switch (scope) {
          case ContextScope contextScope ->
              query(
                  ("match(c:" + GraphModel.Labels.CONTEXT + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + "}) return c"),
                  Map.of(GraphModel.Fields.CONTEXT_ID, contextScope.getId()),
                  scope);
          case SessionScope sessionScope ->
              query(
                  ("match (c:" + GraphModel.Labels.CONTEXT + ") WHERE c." + GraphModel.Fields.ID + " STARTS WITH $" + GraphModel.Fields.SESSION_ID + " return c"),
                  Map.of(GraphModel.Fields.SESSION_ID, sessionScope.getId() + "."),
                  scope);
          case UserScope userScope -> {
            String federation = Klab.INSTANCE.getFederationData(userScope.getUser()).getId();
            Map<String, Object> params = new HashMap<>();
            params.put(GraphModel.Fields.USER, userScope.getUser().getUsername());
            if (federation != null) params.put(GraphModel.Fields.FEDERATION, federation);
            yield query(
                ("MATCH (c:" + GraphModel.Labels.CONTEXT + ") WHERE c." + GraphModel.Fields.USER + " = $" + GraphModel.Fields.USER + " OR c." + GraphModel.Fields.FEDERATION + " = $" + GraphModel.Fields.FEDERATION + " RETURN c"),
                params,
                scope);
          }
          case ServiceScope serviceScope -> query(("match(c:" + GraphModel.Labels.CONTEXT + ") return c"), Map.of(), scope);

          default -> throw new KlabIllegalStateException("Unexpected value: " + scope);
        };

    List<ContextInfo> contextInfos = new ArrayList<>();
    for (var context : adapt(contexts, Map.class, scope)) {

      ContextInfo contextInfo = new ContextInfo();
      //      contextInfo.setId(context.get(GraphModel.Fields.ID).toString());
      contextInfo.setCreationTime((Long) context.get(GraphModel.Fields.CREATED));
      contextInfo.setIdleTimeMs(System.currentTimeMillis() - (Long) context.get(GraphModel.Fields.LAST_UPDATE));

      //      contextInfo.setName(context.get(GraphModel.Fields.NAME).toString());
      //      contextInfo.setUser(context.get(GraphModel.Fields.USER).toString());
      //      contextInfo.setDescription(context.get(GraphModel.Fields.DESCRIPTION).toString());
      //      contextInfo.setServiceId(serviceId);

      contextInfo.setConfiguration(
          DigitalTwin.Configuration.builder()
              .url(
                  Utils.URLs.newURL(
                      scope.getService(RuntimeService.class).getUrl()
                          + ServicesAPI.RUNTIME.DIGITAL_TWIN.replace(
                              "{id}", context.get(GraphModel.Fields.ID).toString())))
              .id(context.get(GraphModel.Fields.ID).toString())
              .name(context.get(GraphModel.Fields.NAME).toString())
              .serviceId(serviceId)
              .owner(context.get(GraphModel.Fields.USER).toString())
              .description(context.get(GraphModel.Fields.DESCRIPTION).toString())
              .serverUrl(scope.getService(RuntimeService.class).getUrl())
              .persistence(Persistence.valueOf(context.get(GraphModel.Fields.EXPIRATION).toString()))
              .timeout(
                  scope
                      .getService(RuntimeService.class)
                      .settings()
                      .get(Setting.DIGITAL_TWIN_TIMEOUT_MINUTES, Integer.class),
                  TimeUnit.MINUTES)
              .build()
              .validate(scope));

      contextInfos.add(contextInfo);
    }

    contextInfos.sort(
        new Comparator<ContextInfo>() {
          @Override
          public int compare(ContextInfo o1, ContextInfo o2) {
            return Long.compare(o1.getCreationTime(), o2.getCreationTime());
          }
        });

    //    // collect sessions
    //    for (var context : contextInfos) {
    //      var sessionId = Utils.Paths.getFirst(context.getConfiguration().getId(), ".");
    //      var sessionInfo =
    //          sessionIds.computeIfAbsent(
    //              sessionId,
    //              (s) -> {
    //                var ss = new SessionInfo();
    //                ss.setId(s);
    //                ss.setUsername(context.getConfiguration().getOwner());
    //                return ss;
    //              });
    //      sessionInfo.getContexts().add(context);
    //    }

    return contextInfos;
  }

  @Override
  public <T extends RuntimeAsset> Query<T> query(Class<T> resultClass, Scope scope) {
    return new KnowledgeGraphQuery<>(KnowledgeGraphQuery.AssetType.classify(resultClass)) {
      @Override
      public List<T> run(Scope scope) {
        return query(this, resultClass, scope);
      }
    };
  }

  @Override
  public <T extends RuntimeAsset> List<T> query(
      Query<T> graphQuery, Class<T> resultClass, Scope scope) {
    if (!(graphQuery instanceof KnowledgeGraphQuery<?> query)) {
      throw new QueryException(QueryException.Code.UNSUPPORTED_QUERY, "Unsupported query representation");
    }
    if (!(scope instanceof ContextScope context) || !Objects.equals(rootContextId, context.getId())) {
      throw new QueryException(QueryException.Code.INVALID_QUERY, "Query requires the owning context scope");
    }
    var statement = Neo4jQueryCompiler.compile(query, rootContextId);
    if (!isOnline()) {
      throw new QueryException(QueryException.Code.BACKEND_UNAVAILABLE, "Knowledge graph is unavailable");
    }
    try (var session = driver.session();
        var transaction = session.beginTransaction(
            TransactionConfig.builder().withTimeout(Duration.ofSeconds(30)).build())) {
      var result = transaction.run(statement.cypher(), statement.parameters());
      var keys = result.keys();
      var records = result.list();
      var summary = result.consume();
      transaction.commit();
      EagerResult eager = new EagerResult() {
        public List<String> keys() { return keys; }
        public List<org.neo4j.driver.Record> records() { return records; }
        public org.neo4j.driver.summary.ResultSummary summary() { return summary; }
      };
      if (Link.class.isAssignableFrom(resultClass)) {
        var links = new ArrayList<T>();
        for (var record : records) {
          var relationship = record.get(0).asRelationship();
          var link = new LinkImpl();
          link.setRelationship(GraphModel.Relationship.valueOf(relationship.type()));
          link.setProperties(Parameters.create(relationship.asMap()));
          link.setSource(queryEndpoint(record.get(1), context));
          link.setTarget(queryEndpoint(record.get(2), context));
          link.setSequence(link.properties().get(GraphModel.Fields.SEQUENCE,
              link.properties().get(GraphModel.Fields.RANK, 0)));
          links.add((T) link);
        }
        return links;
      }
      return adapt(eager, resultClass, scope);
    } catch (QueryException e) {
      throw e;
    } catch (org.neo4j.driver.exceptions.ServiceUnavailableException e) {
      throw new QueryException(QueryException.Code.BACKEND_UNAVAILABLE, "Knowledge graph is unavailable", e);
    } catch (RuntimeException e) {
      throw new QueryException(QueryException.Code.EXECUTION_FAILED, "Knowledge graph query failed", e);
    }
  }

  private RuntimeAsset queryEndpoint(Value value, ContextScope scope) {
    var key = value.get(GraphModel.Fields.ID);
    if (key.type().name().equals("STRING")) {
      var id = key.asString();
      if (id.equals(rootContextId)) return RuntimeAsset.CONTEXT_ASSET;
      if (id.equals(rootContextId + ".DATAFLOW")) return RuntimeAsset.DATAFLOW_ASSET;
      if (id.equals(rootContextId + ".PROVENANCE")) return RuntimeAsset.PROVENANCE_ASSET;
      throw new QueryException(QueryException.Code.UNSUPPORTED_QUERY, "Unsupported graph root");
    }
    var asset = getAsset(key.asLong(), scope, RuntimeAsset.class);
    if (asset == null) throw new QueryException(QueryException.Code.EXECUTION_FAILED, "Cannot materialize query endpoint");
    return asset;
  }

  /**
   * Return a much leaner structure optimized for web transfer and used when the assets may be
   * already cached at the requesting side. Linked to the KNOWLEDGE_GRAPH_GET_LINKS API endpoint.
   *
   * @param asset
   * @param direction
   * @param scope
   * @param relationship
   * @return
   */
  public Collection<LinkInfo> getLinkInfo(
      RuntimeAsset asset,
      GraphModel.Relationship.Direction direction,
      ContextScope scope,
      GraphModel.Relationship... relationship) {

    // works only on committed observations
    if (asset.getId() == -1) {
      return List.of();
    }

    // Build the most selective match possible: use the node label and its stored id
    var sourceLabel = getLabel(asset);
    var sourceKey = getId(asset);
    if (sourceKey == null) {
      return List.of();
    }
    var sourceId = asset.getId();

    // Relationship type filter
    String relTypeFilter = null;
    if (relationship != null && relationship.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < relationship.length; i++) {
        if (i > 0) sb.append("|");
        sb.append(relationship[i].name());
      }
      relTypeFilter = sb.toString();
    }

    // Directional pattern
    String pattern;
    if (direction == GraphModel.Relationship.Direction.OUTGOING) {
      pattern = relTypeFilter == null ? "(n)-[r]->(m)" : "(n)-[r:" + relTypeFilter + "]->(m)";
    } else {
      pattern = relTypeFilter == null ? "(n)<-[r]-(m)" : "(n)<-[r:" + relTypeFilter + "]-(m)";
    }

    String query =
        (("MATCH (n:{label} {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "}) MATCH ")
                + pattern
                + (" RETURN type(r) AS " + GraphModel.Fields.RTYPE + ", properties(r) AS " + GraphModel.Fields.RPROPS + ", m." + GraphModel.Fields.ID + " AS " + GraphModel.Fields.MID))
            .replace("{label}", sourceLabel);

    var result = query(query, Map.of(GraphModel.Fields.ID, sourceKey), scope);
    if (result == null || result.records().isEmpty()) {
      return List.of();
    }

    List<LinkInfo> links = new ArrayList<>();
    for (var rec : result.records()) {
      String rtype = rec.get(GraphModel.Fields.RTYPE).asString();
      Map<String, Object> props = rec.get(GraphModel.Fields.RPROPS).asMap();

      var midValue = rec.get(GraphModel.Fields.MID);
      if (midValue.isNull()) {
        continue;
      }
      var oppositeId = transferId(midValue);
      if (oppositeId == null) {
        continue;
      }

      var link = new org.integratedmodelling.klab.api.data.impl.LinkInfoImpl();
      link.setType(GraphModel.Relationship.valueOf(rtype));
      Parameters<String> p = Parameters.create();
      if (props != null) {
        for (var e : props.entrySet()) {
          p.put(e.getKey(), e.getValue());
        }
      }
      link.setProperties(p);
      if (direction == GraphModel.Relationship.Direction.OUTGOING) {
        link.setSourceId(sourceId);
        link.setTargetId(oppositeId);
      } else {
        // The focal node is the target of an incoming relationship. Preserve the canonical
        // relationship orientation in the transfer object rather than the query's point of view.
        link.setSourceId(oppositeId);
        link.setTargetId(sourceId);
      }
      links.add(link);
    }

    return links;
  }

  /** Translate Neo4j's context-qualified IDs to the stable synthetic IDs used by clients. */
  private Long transferId(Value id) {
    if (id.type().name().equalsIgnoreCase("INTEGER")) {
      return id.asLong();
    }
    if (id.type().name().equalsIgnoreCase("STRING")) {
      var stringId = id.asString();
      if (rootContextId.equals(stringId)) {
        return RuntimeAsset.CONTEXT_ASSET_ID;
      }
      if ((rootContextId + ".PROVENANCE").equals(stringId)) {
        return RuntimeAsset.PROVENANCE_ASSET_ID;
      }
      if ((rootContextId + ".DATAFLOW").equals(stringId)) {
        return RuntimeAsset.DATAFLOW_ASSET_ID;
      }
    }
    // LinkInfo carries long IDs, so string-keyed assets such as agents cannot use this endpoint.
    return null;
  }

  @Override
  public List<Observation> getScheduledObservations(ContextScope scope) {
    if (!Objects.equals(rootContextId, scope.getId())) {
      throw new KlabIllegalArgumentException("Scheduler registry requires its owning context");
    }
    var result = query(
        ("MATCH (c:" + GraphModel.Labels.CONTEXT + " {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.CONTEXT_ID + "})-[:" + GraphModel.Relationship.HAS_CHILD.name() + "|" + GraphModel.Relationship.HAS_MEMBER.name() + "*1..]->(o:" + GraphModel.Labels.OBSERVATION + ") ")
            + ("WHERE o.`" + GraphModel.Fields.SCHEDULER_REGISTERED + "` = true RETURN DISTINCT o"),
        Map.of(GraphModel.Fields.CONTEXT_ID, rootContextId), scope);
    if (result == null) {
      throw new KlabStorageException("Cannot restore scheduler registry for " + rootContextId);
    }
    return adapt(result, Observation.class, scope);
  }

  @Override
  public Collection<Link> getLinks(
      RuntimeAsset asset,
      GraphModel.Relationship.Direction direction,
      ContextScope scope,
      GraphModel.Relationship... relationship) {

    List<Link> ret = new ArrayList<>();

    if (scope.getCurrentTransaction() != null
        && scope.getCurrentTransaction().assets().contains(asset)) {
      // this will never match an object coming from the network
      var types = EnumSet.noneOf(GraphModel.Relationship.class);
      if (relationship != null) {
        types.addAll(List.of(relationship));
      }
      ret.addAll(
          (direction == GraphModel.Relationship.Direction.OUTGOING
                  ? scope.getCurrentTransaction().outgoing(asset)
                  : scope.getCurrentTransaction().incoming(asset))
              .stream().filter(edge -> types.isEmpty() || types.contains(edge.type())).toList());
    }

    if (asset.getId() == -1) {
      return ret;
    }

    // Build the most selective match possible: use the node label and its stored id
    var sourceLabel = getLabel(asset);
    var idValue = getId(asset);

    // Relationship type filter
    String relTypeFilter = null;
    if (relationship != null && relationship.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < relationship.length; i++) {
        if (i > 0) sb.append("|");
        sb.append(relationship[i].name());
      }
      relTypeFilter = sb.toString();
    }

    // Directional pattern
    String pattern =
        direction == GraphModel.Relationship.Direction.OUTGOING
            ? (relTypeFilter == null ? "(n)-[r]->(m)" : "(n)-[r:" + relTypeFilter + "]->(m)")
            : (relTypeFilter == null ? "(n)<-[r]-(m)" : "(n)<-[r:" + relTypeFilter + "]-(m)");

    String query =
        (("MATCH (n:{label} {" + GraphModel.Fields.ID + ": $" + GraphModel.Fields.ID + "}) MATCH ")
                + pattern
                + (" RETURN type(r) AS " + GraphModel.Fields.RTYPE + ", properties(r) AS " + GraphModel.Fields.RPROPS + ", m." + GraphModel.Fields.ID + " AS " + GraphModel.Fields.MID))
            .replace("{label}", sourceLabel);

    var result = query(query, Map.of(GraphModel.Fields.ID, idValue), scope);
    if (result == null || result.records().isEmpty()) {
      return ret;
    }

    for (var rec : result.records()) {
      String rtype = rec.get(GraphModel.Fields.RTYPE).asString();
      Map<String, Object> props = rec.get(GraphModel.Fields.RPROPS).asMap();
      // Target id is expected to be numeric in most cases; skip if not
      Object targetKeyObj;
      var value = rec.get(GraphModel.Fields.MID);
      if (value.isNull()) {
        continue;
      }
      if (value.type().name().equalsIgnoreCase("INTEGER")) {
        targetKeyObj = value.asLong();
      } else if (value.type().name().equalsIgnoreCase("STRING")) {
        // If IDs are strings for some node types, we cannot use the public get(long,...) method;
        // skip such links as per the method contract.
        continue;
      } else {
        continue;
      }

      long targetId = (Long) targetKeyObj;

      var link = new LinkImpl();
      link.setRelationship(GraphModel.Relationship.valueOf(rtype));
      Parameters<String> p = Parameters.create();
      if (props != null) {
        for (var e : props.entrySet()) {
          p.put(e.getKey(), e.getValue());
        }
      }
      link.setProperties(p);
      var targetAsset = getAsset(targetId, scope, RuntimeAsset.class);
      // Selection direction does not change the orientation of the stored relationship.
      if (direction == GraphModel.Relationship.Direction.INCOMING) {
        link.setSource(targetAsset);
        link.setTarget(asset);
      } else {
        link.setSource(asset);
        link.setTarget(targetAsset);
      }
      ret.add(link);
    }

    return ret;
  }
}
