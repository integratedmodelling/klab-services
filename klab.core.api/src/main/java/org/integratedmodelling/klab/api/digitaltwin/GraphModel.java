package org.integratedmodelling.klab.api.digitaltwin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Holds the types and field constants for the digital twin graph model (*and the correspondent
 * GraphQL schema). All enums are local and they correspond to those actually used in the models so
 * that the schema is internally consistent and has no dependency.
 *
 * <p>All graphs retrieved from the runtime API follow this schema and the relationship types can be
 * used to filter graph requests.
 *
 * <p>TODO use this in the KnowledgeGraph implementation
 */
public interface GraphModel {

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
    HAS_PARENT,
    AFFECTS,
    CONTEXTUALIZED_BY,
    CONTEXTUALIZED,
    EMERGED_FROM,
    HAS_OBSERVER,
    HAS_SIBLING,
    HAS_RELATIONSHIP_TARGET,
    HAS_PLAN,
    BY_AGENT,
    HAS_GEOMETRY,
    HAS_COVERAGE,
    CREATED,
    HAS_DATAFLOW,
    HAS_PROVENANCE,
    HAS_ACTIVITY,
    HAS_DATA,
    HAS_CHILD,
    TRIGGERED,
    RESOLVED;

    enum Direction {
      INCOMING,
      OUTGOING
    }

    public Direction direction() {
      return this == HAS_PARENT || this == HAS_SIBLING ? Direction.INCOMING : Direction.OUTGOING;
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
  public static class KnowledgeGraph {
    private Map<String, String> metadata;
    private Map<String, Node> nodes;
    private List<Edge> edges;

    public KnowledgeGraph() {
      this.metadata = new LinkedHashMap<>();
      this.nodes = new LinkedHashMap<>();
      this.edges = new ArrayList<>();
    }

    public Map<String, String> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
      this.metadata = metadata;
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

    public static class Node {
      private String label;
      private Map<String, String> metadata;
      private RuntimeAsset asset;

      public Node() {
        this.metadata = new LinkedHashMap<>();
      }

      public Node(RuntimeAsset asset) {
        this.label = asset.getId() + "";
        this.metadata = new LinkedHashMap<>();
        this.asset = asset;
      }

      public String getLabel() {
        return label;
      }

      public void setLabel(String label) {
        this.label = label;
      }

      public Map<String, String> getMetadata() {
        return metadata;
      }

      public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
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
      private String relationship;
      private boolean directed;
      private String label;
      private Map<String, String> metadata;

      public Edge() {
        this.metadata = new LinkedHashMap<>();
      }

      public Edge(
          String source,
          String target,
          String relationship,
          boolean directed,
          String label,
          Map<String, String> metadata) {
        this.source = source;
        this.target = target;
        this.relationship = relationship;
        this.directed = directed;
        this.label = label;
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
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

      public String getRelationship() {
        return relationship;
      }

      public void setRelationship(String relationship) {
        this.relationship = relationship;
      }

      public boolean isDirected() {
        return directed;
      }

      public void setDirected(boolean directed) {
        this.directed = directed;
      }

      public String getLabel() {
        return label;
      }

      public void setLabel(String label) {
        this.label = label;
      }

      public Map<String, String> getMetadata() {
        return metadata;
      }

      public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
      }
    }
  }

  /**
   * Digital twin descriptor for JSON communication. This is returned by the {@link
   * org.integratedmodelling.klab.api.ServicesAPI.RUNTIME#DIGITAL_TWIN} call, with depth of
   * info depending on call parameters.
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
