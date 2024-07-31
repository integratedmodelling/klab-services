package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.knowledge.SemanticType;
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
        Long id;
        String referenceName;
        String urn;
        Set<SemanticType> types;
    }

    @NodeEntity
    public static class ContextMapping {
        Long id;
        String contextId;
        List<ObservationMapping> rootObservationsMappings;
        DataflowMapping dataflowMapping;
        ProvenanceMapping provenanceMapping;
    }

    @NodeEntity
    public static class ObservationMapping {
        Long id;

    }

    @NodeEntity
    public static class DataflowMapping {
        Long id;
        List<ActuatorMapping> rootActuators;
    }

    @NodeEntity
    public static class ProvenanceMapping {
        Long id;
        List<ProvenanceNodeMapping> rootProvenanceNodes;
    }

    @NodeEntity
    public static class ActuatorMapping {
        Long id;
        String name;
        String semantics;
    }

    @NodeEntity
    public static class ProvenanceNodeMapping {
        Long id;
    }
}
