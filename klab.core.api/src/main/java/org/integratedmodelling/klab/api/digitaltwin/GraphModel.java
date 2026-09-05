package org.integratedmodelling.klab.api.digitaltwin;

import java.util.*;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Holds the types and field constants for the digital twin graph model (*and the correspondent
 * GraphQL schema). All enums are local and they correspond to those actually used in the models so
 * that the schema is internally consistent and has no dependency.
 *
 * <p>All graphs retrieved from the runtime API follow this schema and the relationship types can be
 * used to filter graph requests.
 */
public interface GraphModel {

  /** Physical property names, including legacy spellings retained for existing databases. */
  interface Fields {
    String ID = "id";
    String NAME = "name";
    String URN = "urn";
    String TYPE = "type";
    String SEMANTIC_TYPE = "semanticType";
    String SEMANTICTYPE = "semantictype";
    String SEMANTICS = "semantics";
    String OBSERVABLE = "observable";
    String UPDATED = "updated";
    String RESOLVED = "resolved";
    String N_CHILDREN = "nChildren";
    String PARENT_ID = "parentId";
    String CHILDREN_COUNT = "childrenCount";
    String DEFINITION = "definition";
    String SIZE = "size";
    String CREDITS = "credits";
    String START = "start";
    String END = "end";
    String DESCRIPTION = "description";
    String SERVICE_TYPE = "serviceType";
    String SERVICE_NAME = "serviceName";
    String SERVICE_ID = "serviceId";
    String OBSERVATION_URN = "observationUrn";
    String OBSERVATION_ID = "observationId";
    String STRATEGY = "strategy";
    String COMPUTATION = "computation";
    String ACTUATOR_SCHEMA_VERSION = "actuatorSchemaVersion";
    String ACTUATOR_TYPE = "actuatorType";
    String COVERAGE = "coverage";
    String RESOLVED_COVERAGE = "resolvedCoverage";
    String RESOLVED_GEOMETRY = "resolvedGeometry";
    String DATA_JSON = "dataJson";
    String COMPUTATION_JSON = "computationJson";
    String ANNOTATIONS_JSON = "annotationsJson";
    String SHARDING_STRATEGY_JSON = "shardingStrategyJson";
    String ADAPTER_ID = "adapterId";
    String ADAPTER_PARAMETERS = "adapterParameters";
    String EVENT_TIMESTAMPS = "eventTimestamps";
    String HISTOGRAM = "histogram";
    String HISTOGRAMS = "histograms";
    String FILL_CURVE = "fillCurve";
    String SUGGESTED_SPLITS = "suggestedSplits";
    String MAX_BUFFER_SIZE = "maxBufferSize";
    String MIN_SPLIT_SIZE = "minSplitSize";
    String DATA_TYPE = "dataType";
    String NATIVE_TYPE = "nativeType";
    String SHARD_COUNT = "shardCount";
    String SHARD_INDEX = "shardIndex";
    String TIMESTAMP = "timestamp";
    String PERSISTENCE = "persistence";
    String OFFLOADED = "offloaded";
    String OFFSET = "offset";
    String HISTOGRAM_JSON = "histogramJson";
    String VALUE_TYPE = "valueType";
    String CREATED = "created";
    String RIGHTS = "rights";
    String FEDERATION = "federation";
    String LAST_UPDATE = "lastUpdate";
    String EXPIRATION = "expiration";
    String EXPIRATION_TYPE = "expirationType";
    String USER = "user";
    String USERNAME = "username";
    String NEXT_ID = "nextId";
    String LAST_ACTIVITY = "lastActivity";
    String SCHEDULER_TIME = "schedulerTime";
    String STACK_TRACE = "stackTrace";
    String TRIGGERING_ACTIVITY_URN = "triggeringActivityUrn";
    String SUBSTANTIAL = "substantial";
    String GEOMETRY = "geometry";
    String SHAPE = "shape";
    String LATITUDE = "latitude";
    String LONGITUDE = "longitude";
    String LOWER_LEFT = "lowerLeft";
    String UPPER_RIGHT = "upperRight";
    String BEHAVIOR_URN = "behaviorUrn";
    String METADATA = "_metadata";
    String SEQUENCE = "sequence";
    String RANK = "rank";
    String CONTEXT_ID = "contextId";
    String ACTIVITY_ID = "activityId";
    String COHORT_ID = "cohortId";
    String ASSET_ID = "assetId";
    String SOURCE_ID = "sourceId";
    String TARGET_ID = "targetId";
    String ROOT_ACTIVITY_ID = "rootActivityId";
    String PROVENANCE_ID = "provenanceId";
    String SESSION_ID = "sessionId";
    String GEOMETRY_KEY = "geometryKey";
    String LAYER_NAME = "layerName";
    String AGENT_NAME = "agentName";
    String START_TIME = "startTime";
    String PROPERTIES = "properties";
    String KEY = "key";
    String MID = "mid";
    String RTYPE = "rtype";
    String RPROPS = "rprops";
    String EXISTS = "exists";
    String DATAFLOW = "dataflow";
    String OBSERVATION_COUNT = "observationCount";
    String SCHEDULER_REGISTERED =
        org.integratedmodelling.klab.api.digitaltwin.Scheduler.REGISTRATION_METADATA_KEY;
    String EXECUTION_REQUIRED =
        org.integratedmodelling.klab.api.digitaltwin.Scheduler.EXECUTION_METADATA_KEY;
    Set<String> ALL =
        Set.of(
            ID,
            NAME,
            URN,
            TYPE,
            SEMANTIC_TYPE,
            SEMANTICTYPE,
            SEMANTICS,
            OBSERVABLE,
            UPDATED,
            RESOLVED,
            N_CHILDREN,
            PARENT_ID,
            CHILDREN_COUNT,
            DEFINITION,
            SIZE,
            CREDITS,
            START,
            END,
            DESCRIPTION,
            SERVICE_TYPE,
            SERVICE_NAME,
            SERVICE_ID,
            OBSERVATION_URN,
            OBSERVATION_ID,
            STRATEGY,
            COMPUTATION,
            ACTUATOR_SCHEMA_VERSION,
            ACTUATOR_TYPE,
            COVERAGE,
            RESOLVED_COVERAGE,
            RESOLVED_GEOMETRY,
            DATA_JSON,
            COMPUTATION_JSON,
            ANNOTATIONS_JSON,
            SHARDING_STRATEGY_JSON,
            ADAPTER_ID,
            ADAPTER_PARAMETERS,
            EVENT_TIMESTAMPS,
            HISTOGRAM,
            HISTOGRAMS,
            FILL_CURVE,
            SUGGESTED_SPLITS,
            MAX_BUFFER_SIZE,
            MIN_SPLIT_SIZE,
            DATA_TYPE,
            NATIVE_TYPE,
            SHARD_COUNT,
            SHARD_INDEX,
            TIMESTAMP,
            PERSISTENCE,
            OFFLOADED,
            OFFSET,
            HISTOGRAM_JSON,
            VALUE_TYPE,
            CREATED,
            RIGHTS,
            FEDERATION,
            LAST_UPDATE,
            EXPIRATION,
            EXPIRATION_TYPE,
            USER,
            USERNAME,
            NEXT_ID,
            LAST_ACTIVITY,
            SCHEDULER_TIME,
            STACK_TRACE,
            TRIGGERING_ACTIVITY_URN,
            SUBSTANTIAL,
            GEOMETRY,
            SHAPE,
            LATITUDE,
            LONGITUDE,
            LOWER_LEFT,
            UPPER_RIGHT,
            BEHAVIOR_URN,
            METADATA,
            SEQUENCE,
            RANK,
            CONTEXT_ID,
            ACTIVITY_ID,
            COHORT_ID,
            ASSET_ID,
            SOURCE_ID,
            TARGET_ID,
            ROOT_ACTIVITY_ID,
            PROVENANCE_ID,
            SESSION_ID,
            GEOMETRY_KEY,
            LAYER_NAME,
            AGENT_NAME,
            START_TIME,
            PROPERTIES,
            KEY,
            MID,
            RTYPE,
            RPROPS,
            EXISTS,
            DATAFLOW,
            OBSERVATION_COUNT,
            SCHEDULER_REGISTERED,
            EXECUTION_REQUIRED);
  }

  /** Persistent labels; transport enum names must not be used as database labels. */
  interface Labels {
    String ACTIVITY = "Activity";
    String ACTUATOR = "Actuator";
    String AGENT = "Agent";
    String COHORT = "Cohort";
    String CONTEXT = "Context";
    String DATA = "Data";
    String DATAFLOW = "Dataflow";
    String GEOMETRY = "Geometry";
    String OBSERVATION = "Observation";
    String PLAN = "Plan";
    String PROVENANCE = "Provenance";
    String STATISTICS = "Statistics";
  }

  // TODO queries may belong to a Queries interface here.

  enum ServiceType {
    REASONER,
    RESOLVER,
    RUNTIME,
    RESOURCES
  }

  enum SemanticType {
    QUALITY,
    AGENT,
    SUBJECT,
    FUNCTIONAL_RELATIONSHIP,
    STRUCTURAL_RELATIONSHIP,
    BOND,
    EVENT,
    PROCESS,
    CONFIGURATION
  }

  enum LinkType {
    CHILD,
    PARENT,
    OBSERVER
  }

  enum ObservationType {
    SUBJECT,
    STATE,
    PROCESS,
    OBSERVER,
    EVENT,
    RELATIONSHIP
  }

  enum ActivityType {
    INSTANTIATION,
    CONTEXTUALIZATION,
    RESOLUTION,
    EXECUTION,
    INITIALIZATION
  }

  enum ActivityOutcome {
    SUCCESS,
    FAILURE,
    EXCEPTION
  }

  enum AgentType {
    AI,
    USER,
    MODELED
  }

  enum DataType {
    DOUBLE,
    FLOAT,
    INT,
    CATEGORY,
    LONG
  }

  enum ValueType {
    SCALAR,
    DISTRIBUTION,
    TABLE
  }

  enum Persistence {
    SERVICE_SHUTDOWN
  }

  /**
   * The type of relationships in the graph. All relationship carry further information, to be fully
   * defined.
   */
  enum Relationship {
    HAS_AGENT,
    AFFECTS,
    CONTEXTUALIZED_BY,
    CONTEXTUALIZED,
    HAS_CONTEXT, // for submission activities
    EMERGED_FROM,
    HAS_OBSERVER, // for submission activities FIXME currently is used on observations
    HAS_RELATIONSHIP_SOURCE,
    HAS_RELATIONSHIP_TARGET,
    HAS_PLAN,
    BY_AGENT,
    HAS_GEOMETRY,
    PERCEIVES_GEOMETRY,
    OVERSEES_GEOMETRY,
    AFFECTS_GEOMETRY,
    CREATED,
    HAS_DATAFLOW,
    HAS_PROVENANCE,
    HAS_ACTIVITY,
    HAS_DATA, // Quality observations to their storage items
    HAS_CHILD, // Most assets that build the primary path in the KG
    HAS_MEMBER, // Cohort to Observation
    CONTRIBUTED_TO, // provenance relationship linking collective observations to cohorts
    TRIGGERED,
    RESOLVED;

    public enum Direction {
      INCOMING,
      OUTGOING
    }

    public static final Set<Relationship> PASSIVE_RELATIONSHIPS =
        EnumSet.of(CONTEXTUALIZED_BY, EMERGED_FROM, AFFECTS, CONTRIBUTED_TO);

    public Direction direction() {
      return PASSIVE_RELATIONSHIPS.contains(this) ? Direction.INCOMING : Direction.OUTGOING;
    }
  }

  record Link(long sourceId, long targetId, LinkType type) {
    public static final String SOURCE_ID_FIELD = "sourceId";
    public static final String TARGET_ID_FIELD = "targetId";
    public static final String TYPE_FIELD = "type";
  }

  record Context(long id, long created, String name, Persistence expiration, String user) {
    public static final String ID_FIELD = "id";
    public static final String NAME_FIELD = "name";
    public static final String EXPIRATION_FIELD = "expiration";
    public static final String USER_FIELD = "user";
  }

  record Data(
      long id,
      String fillCurve,
      long size,
      DataType type,
      ValueType valueType,
      long offset,
      String histogramJson,
      Persistence persistence,
      boolean offloaded) {
    public static final String ID_FIELD = "id";
    public static final String FILL_CURVE_FIELD = "fillCurve";
    public static final String SIZE_FIELD = "size";
    public static final String TYPE_FIELD = "type";
    public static final String VALUE_TYPE_FIELD = "valueType";
    public static final String OFFSET_FIELD = "offset";
    public static final String HISTOGRAM_JSON_FIELD = "histogramJson";
    public static final String PERSISTENCE_FIELD = "persistence";
    public static final String OFFLOADED_FIELD = "offloaded";
  }

  record Geometry(long id, String definition, long size) {
    public static final String ID_FIELD = "id";
    public static final String DEFINITION_FIELD = "definition";
    public static final String SIZE_FIELD = "size";
  }

  record Cohort(long id, String observable, String behaviorUrn) {
    public static final String ID_FIELD = "id";
    public static final String OBSERVABLE_FIELD = "observable";
    public static final String BEHAVIOR_URN = "behaviorUrn";
  }

  record Agent(long id, AgentType type, String name) {
    public static final String ID_FIELD = "id";
    public static final String TYPE_FIELD = "type";
    public static final String NAME_FIELD = "name";
  }

  record Observation(
      long id,
      String name,
      String urn,
      SemanticType semanticType,
      ObservationType type,
      String semantics,
      String observable,
      long updated,
      boolean resolved,
      int nChildren) {
    public static final String ID_FIELD = "id";
    public static final String NAME_FIELD = "name";
    public static final String URN_FIELD = "urn";
    public static final String OBSERVATION_TYPE_FIELD = "type";
    public static final String SEMANTIC_TYPE_FIELD = "semanticType";
    public static final String OBSERVABLE_FIELD = "observable";
    public static final String SEMANTICS_FIELD = "semantics";
    public static final String UPDATE_TIMESTAMP_FIELD = "updated";
    public static final String RESOLVED_FIELD = "resolved";
    public static final String N_CHILDREN_FIELD = "nChildren";
  }

  record Dataflow(long id) {
    public static final String ID_FIELD = "id";
  }

  record Actuator(
      long id, long observationId, String semantics, String strategy, List<String> computation) {
    public static final String ID_FIELD = "id";
    public static final String OBSERVATION_ID_FIELD = "observationId";
    public static final String SEMANTICS_FIELD = "semantics";
    public static final String STRATEGY_FIELD = "strategy";
    public static final String COMPUTATION_FIELD = "computation";
  }

  record ProvenanceNode(String id) {
    public static final String ID_FIELD = "id";
  }

  record Activity(
      long id,
      String urn,
      long size,
      long credits,
      long start,
      long end,
      String description,
      ServiceType serviceType,
      String serviceName,
      ActivityType type,
      ActivityOutcome outcome,
      String observationUrn) {
    public static final String ID_FIELD = "id";
    public static final String URN_FIELD = "urn";
    public static final String SIZE_FIELD = "size";
    public static final String CREDITS_FIELD = "credits";
    public static final String START_FIELD = "start";
    public static final String END_FIELD = "end";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String SERVICE_TYPE_FIELD = "serviceType";
    public static final String SERVICE_NAME_FIELD = "serviceName";
    public static final String TYPE_FIELD = "type";
    public static final String OUTCOME_FIELD = "outcome";
    public static final String OBSERVATION_URN_FIELD = "observationUrn";
  }

  /**
   * The serializable version of a KnowledgeGraph entire or incremental portion, whose JSON
   * translation is compatible with existing representational "standards" for graphs. Not a record
   * due to JSON serialization having hard times with them.
   */
  class KnowledgeGraph {
    private Map<String, String> properties;
    private Map<String, Node> nodes;
    private List<Edge> edges;
    private Detail detail;

    public KnowledgeGraph() {
      this.properties = new LinkedHashMap<>();
      this.nodes = new LinkedHashMap<>();
      this.edges = new ArrayList<>();
    }

    public Map<String, String> getProperties() {
      return properties;
    }

    public void setProperties(Map<String, String> properties) {
      this.properties = properties;
    }

    public Map<String, Node> getNodes() {
      return nodes;
    }

    public void setNodes(Map<String, Node> nodes) {
      this.nodes = nodes;
    }

    public List<Edge> getEdges() {
      return edges;
    }

    public void setEdges(List<Edge> edges) {
      this.edges = edges;
    }

    public enum Detail {
      /** The tree only contains the ID and a type label in each node and relationship. */
      RAW,
      /**
       * The tree contains the ID, label, and essential properties in each node and relationship.
       */
      MINIMAL,
      /** The tree contains the ID, label and full properties in each node. */
      FULL,
      /**
       * The tree contains the ID, label and the actual asset in each node. Properties may be added
       * if the asset does not contain all the info in the knowledge graph.
       */
      ASSETS
    }

    public static class Node {
      private String type;
      private String id;
      private Map<String, String> properties;
      private RuntimeAsset asset;

      public Node() {
        this.properties = new LinkedHashMap<>();
      }

      public Node(RuntimeAsset asset) {
        this.type = (asset.getId() == -1 ? asset.getTransientId() : asset.getId()) + "";
        this.properties = new LinkedHashMap<>();
        this.asset = asset;
      }

      public String getType() {
        return type;
      }

      public void setType(String type) {
        this.type = type;
      }

      public Map<String, String> getProperties() {
        return properties;
      }

      /**
       * The ID is a string for full generality, even when RuntimeAsset normally uses a long ID.
       *
       * @return
       */
      public String getId() {
        return id;
      }

      public void setId(String id) {
        this.id = id;
      }

      public void setProperties(Map<String, String> properties) {
        this.properties = properties;
      }

      public RuntimeAsset getAsset() {
        return asset;
      }

      public void setAsset(RuntimeAsset asset) {
        this.asset = asset;
      }
    }

    public static class Edge {
      private String source;
      private String target;
      private String type;
      private boolean directed;
      private Map<String, String> properties;

      public Edge() {
        this.properties = new LinkedHashMap<>();
      }

      public Edge(
          String source,
          String target,
          String relationship,
          boolean directed,
          Map<String, String> metadata) {
        this.source = source;
        this.target = target;
        this.type = relationship;
        this.directed = directed;
        this.properties = metadata != null ? metadata : new LinkedHashMap<>();
      }

      public String getSource() {
        return source;
      }

      public void setSource(String source) {
        this.source = source;
      }

      public String getTarget() {
        return target;
      }

      public void setTarget(String target) {
        this.target = target;
      }

      public String getType() {
        return type;
      }

      public void setType(String type) {
        this.type = type;
      }

      public boolean isDirected() {
        return directed;
      }

      public void setDirected(boolean directed) {
        this.directed = directed;
      }

      public Map<String, String> getProperties() {
        return properties;
      }

      public void setProperties(Map<String, String> properties) {
        this.properties = properties;
      }
    }
  }

  /**
   * Digital twin descriptor for JSON communication. This is returned by the {@link
   * org.integratedmodelling.klab.api.ServicesAPI.RUNTIME#DIGITAL_TWIN} call, with depth of info
   * depending on call parameters.
   */
  class DigitalTwin {

    private org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration configuration;
    private KnowledgeGraph knowledgeGraph;
    private Schedule schedule;
    private List<Notification> notifications = new ArrayList<>();

    public org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration
        getConfiguration() {
      return configuration;
    }

    public void setConfiguration(
        org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration configuration) {
      this.configuration = configuration;
    }

    public KnowledgeGraph getKnowledgeGraph() {
      return knowledgeGraph;
    }

    public void setKnowledgeGraph(KnowledgeGraph knowledgeGraph) {
      this.knowledgeGraph = knowledgeGraph;
    }

    public Schedule getSchedule() {
      return schedule;
    }

    public void setSchedule(Schedule schedule) {
      this.schedule = schedule;
    }

    public List<Notification> getNotifications() {
      return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
      this.notifications = notifications;
    }
  }
}
