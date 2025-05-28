package org.integratedmodelling.klab.api.digitaltwin;

import java.util.List;

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


}
