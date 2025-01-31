package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.lang.SemanticClause;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.List;

/**
 * Holds the types and field constants for the digital twin graph model and the correspondent
 * GraphQL schema. All enums are local and they correspond to those actually used in the models so
 * that the schema is internally consistent and has no dependency.
 */
public class GraphModel {

  // TODO queries may belong to a Queries interface here.

  public enum ServiceType {
    REASONER,
    RESOLVER,
    RUNTIME,
    RESOURCES
  }

  public enum SemanticType {
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

  public enum LinkType {
    CHILD,
    PARENT,
    OBSERVER
  }

  public enum ObservationType {
    SUBJECT,
    STATE,
    PROCESS,
    OBSERVER,
    EVENT,
    RELATIONSHIP
  }

  public enum ActivityType {
    INSTANTIATION,
    CONTEXTUALIZATION,
    RESOLUTION,
    EXECUTION,
    INITIALIZATION
  }

  public enum ActivityOutcome {
    SUCCESS,
    FAILURE,
    EXCEPTION
  }

  public enum AgentType {
    AI,
    USER,
    MODELED
  }

  public enum DataType {
    DOUBLE,
    FLOAT,
    INT,
    CATEGORY,
    LONG
  }

  public enum ValueType {
    SCALAR,
    DISTRIBUTION,
    TABLE
  }

  public enum Persistence {
    SERVICE_SHUTDOWN
  }

  public record Link(long sourceId, long targetId, LinkType type) {}

  public record Context(long id, long created, String name, Persistence expiration, String user) {
    public static final String ID_FIELD = "id";
    public static final String NAME_FIELD = "name";
    public static final String EXPIRATION_FIELD = "expiration";
    public static final String USER_FIELD = "user";
  }

  public record Data(
      long id,
      String fillCurve,
      long size,
      DataType type,
      ValueType valueType,
      long offset,
      String histogramJson,
      Persistence persistence) {}

  public record Geometry(long id, String definition, long size) {}

  public record Agent(long id, AgentType type, String name) {}

  public record Observation(
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

  public record Dataflow(long id) {}

  public record Actuator(
      long id, long observationId, String semantics, String strategy, List<String> computation) {}

  public record ProvenanceNode(String id) {}

  public record Activity(
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
