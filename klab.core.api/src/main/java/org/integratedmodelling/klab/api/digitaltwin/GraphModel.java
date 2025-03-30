package org.integratedmodelling.klab.api.digitaltwin;


import java.util.List;

/**
 * Holds the types and field constants for the digital twin graph model and the correspondent
 * GraphQL schema. All enums are local and they correspond to those actually used in the models so
 * that the schema is internally consistent and has no dependency.
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
      EMERGED_FROM,
      HAS_OBSERVER,
      HAS_SIBLING,
      HAS_RELATIONSHIP_TARGET,
      HAS_PLAN,
      BY_AGENT,
      CREATED,
      HAS_DATAFLOW,
      HAS_PROVENANCE,
      HAS_ACTIVITY,
      HAS_DATA,
      HAS_CHILD,
      TRIGGERED,
      RESOLVED;
    }

    record Link(long sourceId, long targetId, LinkType type) {}

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
      Persistence persistence) {}

  record Geometry(long id, String definition, long size) {}

  record Agent(long id, AgentType type, String name) {}

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

  record Dataflow(long id) {}

  record Actuator(
      long id, long observationId, String semantics, String strategy, List<String> computation) {}

  record ProvenanceNode(String id) {}

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
      String observationUrn) {}
}
