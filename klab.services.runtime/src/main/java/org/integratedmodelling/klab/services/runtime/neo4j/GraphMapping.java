package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

import java.util.List;
import java.util.Set;

/**
 * Java POJOs for the database mapping.
 */
public class GraphMapping {

    /**
     * We keep IDs for observables so we can build a fast compatibility cache
     */
    @NodeEntity
    public static class Observable {
        @Id
        String referenceName;
        String urn;
        Set<SemanticType> types;
    }

    @NodeEntity
    public static class ContextMapping {
        @Id
        String contextId;
        List<ObservationMapping> rootObservationsMappings;
        DataflowMapping dataflowMapping;
        ProvenanceMapping provenanceMapping;
    }

    @NodeEntity
    public static class ObservationMapping {
        @Id @GeneratedValue
        Long id;

    }

    @NodeEntity
    public static class DataflowMapping {
        @Id @GeneratedValue
        Long id;
        List<ActuatorMapping> rootActuators;
    }

    @NodeEntity
    public static class ProvenanceMapping {
        @Id @GeneratedValue
        Long id;
        List<ProvenanceNodeMapping> rootProvenanceNodes;
    }

    @NodeEntity
    public static class ActuatorMapping {
        @Id @GeneratedValue
        Long id;
        Long observationId;
        String name;
        String semantics;
    }

    @NodeEntity
    public static class ProvenanceNodeMapping {
        @Id @GeneratedValue
        Long id;
        Long observationId;
    }
}
