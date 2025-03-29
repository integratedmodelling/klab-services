package org.integratedmodelling.klab.services.runtime.neo4j;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Plan;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.provenance.impl.PlanImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.runtime.storage.BufferImpl;
import org.neo4j.cypherdsl.core.*;
import org.neo4j.driver.*;

import javax.annotation.Nullable;

/**
 * TODO check spatial queries:
 * https://www.lyonwj.com/blog/neo4j-spatial-procedures-congressional-boundaries and
 * https://neo4j-contrib.github.io/spatial/0.24-neo4j-3.1/index.html
 *
 * <p>TODO must figure out where the heck the neo4j-spatial-5.20.0.jar is (no, it's not in
 * https://github.com/neo4j-contrib/m2 nor in osgeo)
 */
public abstract class KnowledgeGraphNeo4j extends AbstractKnowledgeGraph {

  protected Driver driver;
  protected Agent user;
  protected Agent klab;
  protected String rootContextId;
  private RuntimeAsset contextNode;
  private RuntimeAsset dataflowNode;
  private RuntimeAsset provenanceNode;

  private LoadingCache<Long, Observation> observationCache =
      CacheBuilder.newBuilder()
          .maximumSize(200)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<Long, Observation>() {
                public Observation load(Long key) {
                  var ret = retrieve(key, Observation.class, scope);
                  return ret == null ? Observation.empty() : ret;
                }
              });

  // all predefined Cypher queries
  interface Queries {

    String REMOVE_CONTEXT = "match (n:Context {id: $contextId})-[*]->(c) detach delete n, c";
    String FIND_CONTEXT = "MATCH (ctx:Context {id: $contextId}) RETURN ctx";
    String CREATE_WITH_PROPERTIES = "CREATE (n:{type}) SET n = $properties RETURN n";
    String UPDATE_PROPERTIES = "MATCH (n:{type} {id: $id}) SET n += $properties RETURN n";
    String[] INITIALIZATION_QUERIES =
        new String[] {
          "MERGE (user:Agent {name: $username, type: 'USER'})",
          "MERGE (klab:Agent {name: 'k.LAB', type: 'AI'})",
          "MATCH (klab:Agent {name: 'k.LAB'}), (user:Agent {name: $username}) CREATE // main context "
              + "node\n"
              + "\t(ctx:Context {id: $contextId, name: $name, user: $username, created: "
              + "$timestamp, "
              + "expiration: $expirationType}),\n"
              + "\t// main provenance and dataflow nodes\n"
              + "\t(prov:Provenance {name: 'Provenance', id: $contextId + '.PROVENANCE'}), "
              + "(df:Dataflow "
              + "{name: 'Dataflow', id: $contextId + '.DATAFLOW'}),\n"
              + "\t(ctx)-[:HAS_PROVENANCE]->(prov),\n"
              + "\t(ctx)-[:HAS_DATAFLOW]->(df),\n"
              + "\t(prov)-[:HAS_AGENT]->(user),\n"
              + "\t(prov)-[:HAS_AGENT]->(klab),\n"
              + "\t// ACTIVITY that created the whole thing\n"
              + "\t(creation:Activity {start: $timestamp, end: $timestamp, name: "
              + "'INITIALIZATION', id: $activityId}),\n"
              + "\t// created by user\n"
              + "\t(creation)-[:BY_AGENT]->(user),\n"
              + "\t(ctx)<-[:CREATED]-(creation),\n"
              + "(prov)-[:HAS_CHILD]->(creation)"
        };
    String GET_AGENT_BY_NAME =
        "match (ctx:Context {id: $contextId})-->(prov:Provenance)-[:HAS_AGENT]->"
            + "(a:Agent {name: $agentName}) RETURN a";
  }

  class LinkImpl implements Link {

    private long id = 0;
    private Parameters<String> properties = Parameters.create();
    private GraphModel.Relationship type;

    @Override
    public GraphModel.Relationship type() {
      return type;
    }

    @Override
    public Parameters<String> properties() {
      return properties;
    }

    @Override
    public long getId() {
      return id;
    }

    @Override
    public Type classify() {
      return Type.LINK;
    }

    public void setId(long id) {
      this.id = id;
    }

    public Parameters<String> getProperties() {
      return properties;
    }

    public void setProperties(Parameters<String> properties) {
      this.properties = properties;
    }

    public GraphModel.Relationship getType() {
      return type;
    }

    public void setType(GraphModel.Relationship type) {
      this.type = type;
    }
  }

  class TransactionImpl implements Transaction {

    private final org.neo4j.driver.Transaction transaction;

    TransactionImpl() {
      this.transaction =
          driver
              .session()
              .beginTransaction(TransactionConfig.builder().withTimeout(Duration.ZERO).build());
    }

    @Override
    public void store(RuntimeAsset asset, Object... additionalProperties) {
      KnowledgeGraphNeo4j.this.store(transaction, asset, scope, additionalProperties);
    }

    @Override
    public void update(RuntimeAsset asset, Object... properties) {
      KnowledgeGraphNeo4j.this.update(transaction, asset, scope, properties);
    }

    @Override
    public void link(
        RuntimeAsset source,
        RuntimeAsset destination,
        GraphModel.Relationship relationship,
        Object... additionalProperties) {
      KnowledgeGraphNeo4j.this.link(
          transaction, source, destination, relationship, scope, additionalProperties);
    }

    @Override
    public void close() throws IOException {
      transaction.commit();
    }
  }

  @Override
  public Transaction createTransaction() {
    return new TransactionImpl();
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
  protected void initializeContext() {

    this.rootContextId = scope.getId();

    var result = query(Queries.FIND_CONTEXT, Map.of("contextId", scope.getId()), scope);

    if (result.records().isEmpty()) {
      long timestamp = System.currentTimeMillis();
      var activityId = nextKey();
      for (var query : Queries.INITIALIZATION_QUERIES) {
        query(
            query,
            Map.of(
                "contextId", scope.getId(),
                "name", scope.getName(),
                "timestamp", timestamp,
                "username", scope.getUser().getUsername(),
                "expirationType", scope.getPersistence().name(),
                "activityId", activityId),
            scope);
      }
    }

    final var dataflowNodeId = nextKey();
    final var provenanceNodeId = nextKey();
    final var contextNodeId = nextKey();

    this.dataflowNode =
        new RuntimeAsset() {
          @Override
          public long getId() {
            return dataflowNodeId;
          }

          @Override
          public Type classify() {
            // check - scope isn't a runtime asset
            return Type.DATAFLOW;
          }
        };

    this.provenanceNode =
        new RuntimeAsset() {
          @Override
          public long getId() {
            return provenanceNodeId;
          }

          @Override
          public Type classify() {
            // check - scope isn't a runtime asset
            return Type.PROVENANCE;
          }
        };

    this.contextNode =
        new RuntimeAsset() {
          @Override
          public long getId() {
            return contextNodeId;
          }

          @Override
          public Type classify() {
            // as a marker - scope isn't a runtime asset
            return Type.ARTIFACT;
          }
        };

    this.user =
        adapt(
                query(
                    Queries.GET_AGENT_BY_NAME,
                    Map.of("contextId", scope.getId(), "agentName", scope.getUser().getUsername()),
                    scope),
                Agent.class,
                scope)
            .getFirst();
    this.klab =
        adapt(
                query(
                    Queries.GET_AGENT_BY_NAME,
                    Map.of("contextId", scope.getId(), "agentName", "k.LAB"),
                    scope),
                Agent.class,
                scope)
            .getFirst();
  }

  @Override
  public void deleteContext() {
    query(Queries.REMOVE_CONTEXT, Map.of("contextId", scope.getId()), scope);
  }

  /**
   * @param query
   * @param cls
   * @param <T>
   * @return
   */
  protected <T> List<T> adapt(EagerResult query, Class<T> cls, Scope scope) {

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

      if (Map.class.isAssignableFrom(cls)) {

        ret.add((T) node.asMap(Map.of()));

      } else if (Link.class.isAssignableFrom(cls)) {

        var link = new LinkImpl();
        link.getProperties().putAll(node.asMap());
        ret.add((T) link);

      } else if (Agent.class.isAssignableFrom(cls)) {

        var instance = new AgentImpl();
        instance.setName(node.get("name").asString());
        instance.setEmpty(false);

        ret.add((T) instance);

      } else if (Observation.class.isAssignableFrom(cls)) {

        var instance = new ObservationImpl();
        var reasoner = scope.getService(Reasoner.class);

        instance.setUrn(node.get("urn").asString());
        instance.setName(node.get("name").asString());
        instance.setObservable(reasoner.resolveObservable(node.get("observable").asString()));
        instance.setResolved(node.get("resolved").asBoolean());
        instance.setId(node.get("id").asLong());

        var gResult =
            query(
                "MATCH (o:Observation)-[:HAS_GEOMETRY]->(g:Geometry) WHERE o.id"
                    + " = $id RETURN g",
                Map.of("id", node.get("id").asLong()),
                scope);

        if (gResult == null || !gResult.records().isEmpty()) {
          instance.setGeometry(adapt(gResult, Geometry.class, scope).getFirst());
        }

        ret.add((T) instance);

      } else if (Activity.class.isAssignableFrom(cls)) {
        var instance = new ActivityImpl();
        // TODO
        instance.setStart(node.get("start").asLong());
        instance.setEnd(node.get("end").asLong());
        instance.setObservationUrn(node.get("observationUrn").asString());
        instance.setName(node.get("name").asString());
        instance.setServiceName(
            node.get("serviceName").isNull() ? null : node.get("serviceName").asString());
        instance.setServiceId(
            node.get("serviceId").isNull() ? null : node.get("serviceId").asString());
        instance.setServiceType(
            node.get("serviceType").isNull()
                ? null
                : KlabService.Type.valueOf(node.get("serviceType").asString()));
        instance.setUrn(node.get("urn").isNull() ? null : node.get("urn").asString());
        instance.setDataflow(
            node.get("dataflow").isNull() ? null : node.get("dataflow").asString());
        instance.setType(Activity.Type.valueOf(instance.getName()));
        instance.setDescription(
            node.get("description").isNull()
                ? "No description"
                : node.get("description").asString());
        instance.setId(node.get("id").asLong());
        ret.add((T) instance);
      } else if (Actuator.class.isAssignableFrom(cls)) {
        var instance = new ActuatorImpl();
        // TODO
        ret.add((T) instance);
      } else if (Plan.class.isAssignableFrom(cls)) {
        var instance = new PlanImpl();
        // TODO
        ret.add((T) instance);
      } else if (Geometry.class.isAssignableFrom(cls)) {
        // TODO use a cache storing scales
        ret.add(
            (T) GeometryRepository.INSTANCE.get(node.get("definition").asString(), Geometry.class));
      }
    }
    return ret;
  }

  @Override
  public Agent user() {
    return user;
  }

  @Override
  public Agent klab() {
    return klab;
  }

  @Override
  public List<ContextInfo> getExistingContexts(UserScope scope) {

    var ret = new ArrayList<ContextInfo>();
    var result =
        scope == null
            ? query(
                "match (c:Context)<-[:CREATED]-(a:Activity) return c.id as contextId, a.start as "
                    + "startTime",
                Map.of(),
                scope)
            : query(
                "match (c:Context {user: $username})<-[:CREATED]-(a:Activity) return c"
                    + ".name as"
                    + " contextName, c.id as contextId, a.start as startTime",
                Map.of("username", scope.getUser().getUsername()),
                scope);

    for (var record : result.records()) {
      ContextInfo info = new ContextInfo();
      info.setId(record.get("contextId").asString());
      info.setName(record.get("contextName").asString());
      info.setCreationTime(record.get("startTime").asLong());
      // TODO the rest
      ret.add(info);
    }
    return ret;
  }

  @Override
  public void clear() {
    if (scope == null) {
      driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
    } else {
      query(Queries.REMOVE_CONTEXT, Map.of("contextId", scope.getId()), scope);
    }
  }

  @Override
  protected <T extends RuntimeAsset> T retrieve(Object key, Class<T> assetClass, Scope scope) {
    var result =
        assetClass == RuntimeAsset.class
            ? query("MATCH (n {id: $id}) return n", Map.of("id", key), null)
            : query(
                "MATCH (n:{assetLabel} {id: $id}) return n"
                    .replace("{assetLabel}", getLabel(assetClass)),
                Map.of("id", key),
                null);
    var adapted = adapt(result, assetClass, scope);
    return adapted.isEmpty() ? null : adapted.getFirst();
  }

  @Override
  protected long store(RuntimeAsset asset, Scope scope, Object... additionalProperties) {

    var type = getLabel(asset);
    var props = asParameters(asset, additionalProperties);
    var ret = nextKey();
    props.put("id", ret);
    if (asset instanceof Observation) {
      props.put("urn", this.scope.getId() + "." + ret);
    }
    var result =
        query(
            Queries.CREATE_WITH_PROPERTIES.replace("{type}", type),
            Map.of("properties", props),
            scope);
    if (result != null && result.records().size() == 1) {
      setId(asset, ret);
      var geometry =
          switch (asset) {
            case Observation observation -> observation.getGeometry();
            case Actuator actuator -> actuator.getCoverage();
            default -> null;
          };

      if (geometry != null) {
        storeGeometry(geometry, asset, null);
      }
    }

    if (asset instanceof Observation observation) {
      observationCache.put(observation.getId(), observation);
    }

    return ret;
  }

  protected long store(
      org.neo4j.driver.Transaction transaction,
      RuntimeAsset asset,
      Scope scope,
      Object... additionalProperties) {

    var type = getLabel(asset);
    var props = asParameters(asset, additionalProperties);
    var ret = nextKey();
    props.put("id", ret);
    if (asset instanceof Observation) {
      props.put("urn", this.scope.getId() + "." + ret);
    }
    var result =
        query(
            transaction,
            Queries.CREATE_WITH_PROPERTIES.replace("{type}", type),
            Map.of("properties", props),
            scope);
    if (result != null && result.hasNext()) {

      setId(asset, ret);
      var geometry =
          switch (asset) {
            case Observation observation -> observation.getGeometry();
            case Actuator actuator -> actuator.getCoverage();
            default -> null;
          };

      if (geometry != null) {
        storeGeometry(geometry, asset, transaction);
      }

      if (asset instanceof Observation observation) {
        observationCache.put(observation.getId(), observation);
      }
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
    var sourceQuery = matchAsset(source, "n", "sourceId");
    var targetQuery = matchAsset(destination, "c", "targetId");
    var props = asParameters(null, additionalProperties);
    var query =
        ("MATCH (n:{fromLabel}), (c:{toLabel}) WHERE {sourceQuery} AND {targetQuery} CREATE (n)"
                + "-[r:{relationshipLabel}]->(c) SET r = $properties RETURN r")
            .replace("{sourceQuery}", sourceQuery)
            .replace("{targetQuery}", targetQuery)
            .replace("{relationshipLabel}", relationship.name())
            .replace("{fromLabel}", getLabel(source))
            .replace("{toLabel}", getLabel(destination));

    query(
        transaction,
        query,
        Map.of("sourceId", getId(source), "targetId", getId(destination), "properties", props),
        scope);
  }

  private void storeGeometry(
      Geometry geometry, RuntimeAsset asset, @Nullable org.neo4j.driver.Transaction transaction) {

    var encoded = geometry.encode();

    // Must be called after update() and this may happen more than once, so we must check to avoid
    // multiple relationships.
    var exists =
        transaction == null
            ? query(
                "MATCH (n:{assetLabel} {id: $assetId})-[:HAS_GEOMETRY]->(g:Geometry) RETURN g"
                    .replace("{assetLabel}", getLabel(asset)),
                Map.of("assetId", getId(asset)),
                scope)
            : query(
                transaction,
                "MATCH (n:{assetLabel} {id: $assetId})-[:HAS_GEOMETRY]->(g:Geometry) RETURN g"
                    .replace("{assetLabel}", getLabel(asset)),
                Map.of("assetId", getId(asset)),
                scope);

    if (checkExists(exists)) {
      return;
    }

    // only record fully specified scales, not syntactic specifications
    geometry = GeometryRepository.INSTANCE.scale(geometry);

    double coverage = geometry instanceof Coverage cov ? cov.getCoverage() : 1.0;

    // the idea is that looking up the size before the monster string can be faster.
    var query = "MATCH (g:Geometry) WHERE g.definition = $definition RETURN g";
    var result =
        transaction == null
            ? query(query, Map.of("definition", encoded), scope)
            : query(transaction, query, Map.of("definition", encoded), scope);

    if (!checkExists(result)) {
      // TODO more geometry data (bounding box, time boundaries etc.)
      if (transaction == null) {
        query(
            "CREATE (g:Geometry {size: $size, definition: $definition, key: $key}) RETURN g",
            Map.of("size", geometry.size(), "definition", encoded, "key", geometry.key()),
            scope);
      } else {
        query(
            transaction,
            "CREATE (g:Geometry {size: $size, definition: $definition, key: $key}) RETURN g",
            Map.of("size", geometry.size(), "definition", encoded, "key", geometry.key()),
            scope);
      }
    }

    // TODO more properties pertaining to the link (e.g. separate space/time coverages etc)
    var properties = Map.of("coverage", coverage);

    // link it with the associated coverage
    var rel =
        transaction == null
            ? query(
                ("MATCH (n:{assetLabel}), (g:Geometry) WHERE n.id = $assetId AND g.definition = $geometryKey"
                        + " CREATE (n)"
                        + "-[r:HAS_GEOMETRY]->(g) SET r = $properties RETURN r")
                    .replace("{assetLabel}", getLabel(asset)),
                Map.of("assetId", getId(asset), "geometryKey", encoded, "properties", properties),
                scope)
            : query(
                transaction,
                ("MATCH (n:{assetLabel}), (g:Geometry) WHERE n.id = $assetId AND g.definition = $geometryKey"
                        + " CREATE (n)"
                        + "-[r:HAS_GEOMETRY]->(g) SET r = $properties RETURN r")
                    .replace("{assetLabel}", getLabel(asset)),
                Map.of("assetId", getId(asset), "geometryKey", encoded, "properties", properties),
                scope);
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
    var sourceQuery = matchAsset(source, "n", "sourceId");
    var targetQuery = matchAsset(destination, "c", "targetId");
    var props = asParameters(null, additionalProperties);
    var query =
        ("match (n:{fromLabel}), (c:{toLabel}) WHERE {sourceQuery} AND {targetQuery} CREATE (n)"
                + "-[r:{relationshipLabel}]->(c) SET r = $properties RETURN r")
            .replace("{sourceQuery}", sourceQuery)
            .replace("{targetQuery}", targetQuery)
            .replace("{relationshipLabel}", relationship.name())
            .replace("{fromLabel}", getLabel(source))
            .replace("{toLabel}", getLabel(destination));

    query(
        query,
        Map.of("sourceId", getId(source), "targetId", getId(destination), "properties", props),
        scope);
  }

  private String matchAsset(RuntimeAsset asset, String name, String queryVariable) {

    var ret =
        switch (asset) {
          case Activity activity -> name + ".id = $" + queryVariable;
          case Observation observation -> name + ".id = $" + queryVariable;
          case Actuator actuator -> name + ".id = $" + queryVariable;
          case Storage.Buffer actuator -> name + ".id = $" + queryVariable;
          case Agent agent -> name + ".name = $" + queryVariable;
          default -> null;
        };

    if (ret == null) {
      ret =
          switch (asset.classify()) {
            case ARTIFACT, DATAFLOW, PROVENANCE, DATA -> name + ".id = $" + queryVariable;
            default -> throw new KlabIllegalStateException("Unexpected value: " + asset.classify());
          };
    }

    return ret == null ? (ret = name + ".id = $" + queryVariable) : ret;
  }

  private Object getId(RuntimeAsset asset) {

    Object ret =
        switch (asset) {
          case ActuatorImpl actuator -> actuator.getInternalId();
          case ActivityImpl activity -> activity.getId();
          case ObservationImpl observation -> observation.getId();
          case Agent agent -> agent.getName();
          case BufferImpl buffer -> buffer.getInternalId();
          default -> null;
        };

    if (ret == null) {
      // it's one of the preset ones
      ret =
          switch (asset.classify()) {
            case ARTIFACT -> scope.getId();
            case DATAFLOW -> scope.getId() + ".DATAFLOW";
            case PROVENANCE -> scope.getId() + ".PROVENANCE";
            default -> throw new KlabIllegalStateException("Unexpected value: " + asset.classify());
          };
    }
    return ret;
  }

  private void setId(RuntimeAsset asset, long id) {
    switch (asset) {
      case ObservationImpl observation -> {
        observation.setId(id);
        observation.setUrn(scope.getId() + "." + id);
      }
      case ActuatorImpl actuator -> actuator.setInternalId(id);
      case BufferImpl buffer -> buffer.setInternalId(id);
      case ActivityImpl activity -> activity.setId(id);
      case AgentImpl agent -> agent.setId(id);
      default -> {}
    }
  }

  @Override
  public RuntimeAsset dataflow() {
    if (scope == null) {
      throw new KlabIllegalStateException(
          "Access to context node in a non-contexual knowledge graph");
    }
    return dataflowNode;
  }

  @Override
  public RuntimeAsset provenance() {
    if (scope == null) {
      throw new KlabIllegalStateException(
          "Access to context node in a non-contexual knowledge graph");
    }
    return provenanceNode;
  }

  @Override
  public RuntimeAsset scope() {
    if (scope == null) {
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

    if (target instanceof KnowledgeGraphQuery.AssetType assetType) {
      return switch (assetType) {
        case SCOPE -> "Context";
        case DATAFLOW -> "Dataflow";
        case PROVENANCE -> "Provenance";
        case ACTUATOR -> "Actuator";
        case ACTIVITY -> "Activity";
        case OBSERVATION -> "Observation";
        case DATA -> "Data";
        default ->
            throw new KlabInternalErrorException("Cannot find a KG node label for " + assetType);
      };
    }

    if (target instanceof GraphModel.Relationship relationship) {
      return relationship.name();
    }

    if (target instanceof Class<?> cls) {
      if (Observation.class.isAssignableFrom(cls)) {
        return "Observation";
      } else if (Activity.class.isAssignableFrom(cls)) {
        return "Activity";
      } else if (Actuator.class.isAssignableFrom(cls)) {
        return "Actuator";
      } else if (Agent.class.isAssignableFrom(cls)) {
        return "Agent";
      } else if (Plan.class.isAssignableFrom(cls)) {
        return "Plan";
      } else if (Storage.Buffer.class.isAssignableFrom(cls)) {
        return "Data";
      }
    }

    var ret =
        switch (target) {
          case Observation x -> "Observation";
          case Activity x -> "Activity";
          case Actuator x -> "Actuator";
          case Agent x -> "Agent";
          case Storage.Buffer x -> "Data";
          case Plan x -> "Plan";
          default -> null;
        };

    if (ret == null && target instanceof RuntimeAsset runtimeAsset) {
      ret =
          switch (runtimeAsset.classify()) {
            case ARTIFACT -> "Context";
            case DATAFLOW -> "Dataflow";
            case PROVENANCE -> "Provenance";
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
      ContextScope scope,
      Object... parameters) {
    var props = asParameters(runtimeAsset, parameters);
    props.remove("id");
    var result =
        query(
            transaction,
            Queries.UPDATE_PROPERTIES.replace("{type}", getLabel(runtimeAsset)),
            Map.of(
                "id",
                (runtimeAsset instanceof ActuatorImpl actuator
                    ? actuator.getInternalId()
                    : runtimeAsset.getId()),
                "properties",
                props),
            scope);
  }

  @Override
  public void update(RuntimeAsset runtimeAsset, ContextScope scope, Object... parameters) {
    var props = asParameters(runtimeAsset, parameters);
    props.remove("id");
    var result =
        query(
            Queries.UPDATE_PROPERTIES.replace("{type}", getLabel(runtimeAsset)),
            Map.of(
                "id",
                (runtimeAsset instanceof ActuatorImpl actuator
                    ? actuator.getInternalId()
                    : runtimeAsset.getId()),
                "properties",
                props),
            scope);
  }

  @Override
  protected synchronized long nextKey() {
    var ret = -1L;
    var lastActivity = System.currentTimeMillis();
    var result = query("MATCH (n:Statistics) return n.nextId", Map.of(), scope);
    if (result != null) {
      if (result.records().isEmpty()) {
        ret = 1; 
        query("CREATE (n:Statistics {nextId: 1})", Map.of(), scope);
      } else {
        var id = result.records().getFirst().get(result.keys().getFirst()).asLong();
        ret = id + 1;
        query(
            "MATCH (n:Statistics) WHERE n.nextId = $id SET n.nextId = $nextId, n.lastActivity = "
                + "$lastActivity",
            Map.of("id", id, "nextId", ret, "lastActivity", lastActivity),
            scope);
      }
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
          queryParameters.put("id", id);
        } else if (parameter instanceof Observation observation) {
          queryParameters.put("observationUrn", observation.getUrn());
        } else if (parameter instanceof Activity.Type activityType) {
          queryParameters.put("type", activityType.name());
        }
      }
    }

    var query = assetQuery("a", "Activity", queryParameters.keySet());
    if (rootActivity != null) {
      query.append("<-[*]-(r:Activity {id: $rootActivityId})");
      queryParameters.put("rootActivityId", rootActivity.getId());
    } else {
      query.append("<-[*]-(p:Provenance {id: $provenanceId})");
      queryParameters.put("provenanceId", scope.getId() + ".PROVENANCE");
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
            getScopeQuery(scope, queryParameters) + "-[:HAS_PROVENANCE]->" + "(p:Provenance)");

    if (queriables != null) {
      for (var parameter : queriables) {
        if (parameter instanceof Observable observable) {
          //
        } else if (parameter instanceof Activity rootActivity) {
        } else if (parameter instanceof Long id) {
          queryParameters.put("id", id);
          query = new StringBuilder("MATCH (a:Agent {id: $id}");
        } else if (parameter instanceof Observation observation) {
          // define start node as the one with the observation URN
        } else if (parameter instanceof String name) {
          queryParameters.put("name", name);
          query = new StringBuilder("MATCH (a:Agent {name: $name}");
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
          queryParameters.put("semantics", observable.getSemantics().getUrn());
          query.append("MATCH (o:Observation {semantics: $semantics}");
        } else if (parameter instanceof Activity rootActivity) {
        } else if (parameter instanceof Long id) {
          queryParameters.put("id", id);
          query = new StringBuilder("MATCH (o:Observation {id: $id}");
        } else if (parameter instanceof Observation observation) {
          // define start node as the one with the observation URN
        } else if (parameter instanceof String urn) {
          queryParameters.put("urn", urn);
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
          queryParameters.put("id", id);
          query = new StringBuilder("MATCH (n:Actuator {id: $id})");
        } else if (parameter instanceof Observation observation) {
          // define start node as the one with the observation URN
        } else if (parameter instanceof String name) {
          queryParameters.put("name", name);
          query.append("MATCH (n:Actuator {name: $name})");
        }
      }
    }

    var result = query(query.append(" return n").toString(), queryParameters, scope);
    return adapt(result, Actuator.class, scope);
  }

  private String getScopeQuery(ContextScope scope, Map<String, Object> parameters) {

    var scopeData = ContextScope.parseScopeId(ContextScope.getScopeId(scope));
    var ret = new StringBuilder("MATCH (c:Context {id: $contextId})");
    parameters.put("contextId", scopeData.scopeId());

    if (scopeData.observationPath() != null) {
      for (var observationId : scopeData.observationPath()) {
        ret.append("-[:HAS_CHILD]->(Observation {id: ").append(observationId).append("})");
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
    } else if (scope.getUser().getUsername().equals(agentName)) {
      return user;
    } else if (agentName != null) {
      // TODO create agent
    }
    return user;
  }

  @Override
  public List<SessionInfo> getSessionInfo(Scope scope) {

    var sessionIds = new LinkedHashMap<String, SessionInfo>();
    EagerResult contexts =
        switch (scope) {
          case ContextScope contextScope ->
              query(
                  "match(c:Context {id: $contextId}) return c",
                  Map.of("contextId", contextScope.getId()),
                  scope);
          case SessionScope sessionScope ->
              query(
                  "match (c:Context) WHERE c.id STARTS WITH $sessionId return c",
                  Map.of("sessionId", sessionScope.getId() + "."),
                  scope);
          case UserScope userScope ->
              query(
                  "match(c:Context {user: $user}) return (c)",
                  Map.of("user", userScope.getUser().getUsername()),
                  scope);
          default -> throw new KlabIllegalStateException("Unexpected value: " + scope);
        };

    List<ContextInfo> contextInfos = new ArrayList<>();
    for (var context : adapt(contexts, Map.class, scope)) {
      ContextInfo contextInfo = new ContextInfo();
      contextInfo.setId(context.get("id").toString());
      contextInfo.setCreationTime((Long) context.get("created"));
      contextInfo.setName(context.get("name").toString());
      contextInfo.setUser(context.get("user").toString());
      contextInfos.add(contextInfo);
    }

    contextInfos.sort(
        new Comparator<ContextInfo>() {
          @Override
          public int compare(ContextInfo o1, ContextInfo o2) {
            return Long.compare(o1.getCreationTime(), o2.getCreationTime());
          }
        });

    // collect sessions
    for (var context : contextInfos) {
      var sessionId = Utils.Paths.getFirst(context.getId(), ".");
      var sessionInfo =
          sessionIds.computeIfAbsent(
              sessionId,
              (s) -> {
                var ss = new SessionInfo();
                ss.setId(s);
                ss.setUsername(context.getUser());
                return ss;
              });
      sessionInfo.getContexts().add(context);
    }

    return new ArrayList<>(sessionIds.values());
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
    if (graphQuery instanceof KnowledgeGraphQuery<T> knowledgeGraphQuery) {
      try {
        String queryCode = null;
        // special case of query to retrieve a single relationship with its properties as result
        if (Link.class.isAssignableFrom(resultClass)
            && knowledgeGraphQuery.getResultType() == KnowledgeGraphQuery.AssetType.LINK
            && knowledgeGraphQuery.getRelationshipSource() != null
            && knowledgeGraphQuery.getRelationshipTarget() != null) {
          StringBuilder q = new StringBuilder("MATCH ");
          q.append("(x:")
              .append(getLabel(knowledgeGraphQuery.getRelationshipSource().getType()))
              .append(" {urn: \"")
              .append(knowledgeGraphQuery.getRelationshipSource().getUrn())
              .append("\"})-[r:")
              .append(knowledgeGraphQuery.getRelationship().name())
              .append("]->(y:")
              .append(getLabel(knowledgeGraphQuery.getRelationshipTarget().getType()))
              .append(" {urn: \"")
              .append(knowledgeGraphQuery.getRelationshipTarget().getUrn())
              .append("\"}) RETURN r");
          queryCode = q.toString();
        } else {
          var statement = compileQuery(knowledgeGraphQuery, resultClass, scope);
          if (statement == null) {
            return List.of();
          }
          queryCode = statement.build().getCypher();
        }
//        System.out.println("QUERY THIS: " + queryCode);
        return adapt(query(queryCode, null, scope), resultClass, scope);
      } catch (Throwable t) {
        scope.error(t);
        return List.of();
      }
    }
    throw new KlabUnimplementedException("Not ready to compile arbitrary query implementations");
  }

  private <T extends RuntimeAsset>
      StatementBuilder.BuildableStatement<ResultStatement> compileQuery(
          KnowledgeGraphQuery<T> query, Class<T> resultClass, Scope scope) {

    /*
     * Must have either a source or a target, which determines the direction of the relationship
     * Depth determines the relationship arity If relationship type is null, use any relationship
     * Match parameters in either source or target Match any parameters in the relationship Add
     * limit, order and offset as specified
     */
    StatementBuilder.BuildableStatement<ResultStatement> ret = null;

    switch (query.getType()) {
      case QUERY -> {
        var asset = query.getSource() == null ? query.getTarget() : query.getSource();
        if (asset == null) {
          scope.error(new KlabInternalErrorException("Cannot compile KnowledgeGraph query", query));
          return null;
        }
        var known = getQueryNode(asset);
        var unknown = Cypher.node(getLabel(KnowledgeGraphQuery.AssetType.classify(resultClass)));
        List<PatternElement> restrictions = new ArrayList<>();
        for (var restriction : query.getAssetQueryCriteria()) {
          // TODO add query criteria for the unknown node (where() in search)
          var property = unknown.property(restriction.getFirst());
          switch (Query.Operator.valueOf(restriction.getSecond())) {
            case EQUALS -> {
              restrictions.add(
                  unknown.where(
                      property.eq(fieldLiteral(restriction.getFirst(), restriction.getThird()))));
            }
            case LT -> {}
            case GT -> {}
            case LE -> {}
            case GE -> {}
            case LIKE -> {}
            case INTERSECT -> {}
            case COVERS -> {}
            case NEAREST -> {}
            case BEFORE -> {}
            case AFTER -> {}
          }
        }

        var source = query.getSource() == null ? unknown : known;
        var target = query.getSource() == null ? known : unknown;
        // TODO properties for the relationship
        var qret = Cypher.match(source.relationshipTo(target, getLabel(query.getRelationship())));

        for (int i = 0; i < restrictions.size(); i++) {
          qret = qret.match(restrictions.get(i));
        }

        return qret.returning(query.getSource() == null ? source : target);
      }
      case AND -> {
        // bring this upstream, returns a UnionQuery
        var queries =
            query.getChildren().stream()
                .map(q -> compileQuery(q, resultClass, scope).build())
                .toList();
        //        return Cypher.union(queries);
      }
      case OR -> {
        var queries =
            query.getChildren().stream().map(q -> compileQuery(q, resultClass, scope)).toList();
      }
      case NOT -> {
        // naaah
      }
    }

    return ret;
  }

  private Expression fieldLiteral(String field, String value) {

    Object val = value;
    if ("id".equals(field) && Utils.Numbers.encodesInteger(value)) {
      // TODO check
      val = Long.parseLong(value);
    }
    return Cypher.literalOf(val);
  }

  private Node getQueryNode(KnowledgeGraphQuery.Asset asset) {

    var searchField =
        switch (asset.getType()) {
          case SCOPE -> "id";
          case DATAFLOW -> "id";
          case PROVENANCE -> null;
          case LINK -> null;
          case ACTUATOR -> "id";
          case ACTIVITY -> "urn";
          case OBSERVATION -> "urn";
          case SEMANTICS -> "urn";
          case OBSERVABLE -> "urn";
          case DATA -> "id";
        };
    var searchValue = asset.getUrn();

    return Cypher.node(getLabel(asset.getType()))
        // TODO any conditions
        .withProperties(Map.of(searchField, searchValue));
  }
}
