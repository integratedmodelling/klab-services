package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.neo4j.ogm.annotation.*;

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
    public static class ObservableMapping {
        @Id @GeneratedValue
        String referenceName;
        String urn;
        Set<SemanticType> types;
    }

    @NodeEntity
    public static class ContextMapping {
        @Id
        String contextId;
        DataflowMapping dataflowMapping = new DataflowMapping();
        ProvenanceMapping provenanceMapping = new ProvenanceMapping();
    }

    @NodeEntity
    public static class ObservationMapping {
        @Id
        Long id;
        ObservableMapping observable;
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

    public static abstract class Link {
        @Id @GeneratedValue Long id;
        long timestamp;
        DigitalTwin.Relationship type;
        // TODO metadata
    }

    @RelationshipEntity
    public static class RootObservationLink extends Link {
        @StartNode
        ContextMapping context;
        @EndNode
        ObservationMapping observation;
    }

    @RelationshipEntity
    public static class ObservationLink extends Link {
        @StartNode
        ObservationMapping context;
        @EndNode
        ObservationMapping observation;
    }

    public static ObservationMapping adapt(Observation observation) {
        var ret = new ObservationMapping();
        ret.observable = adapt(observation.getObservable());
        return ret;
    }

    private static ObservableMapping adapt(Observable observable) {
        var ret = new ObservableMapping();
        ret.referenceName = observable.getReferenceName();
        ret.types = observable.getSemantics().getType();
        ret.urn = observable.getUrn();
        return ret;
    }

    public static ContextMapping adapt(ContextScope contextScope) {
        // produce and store the context root with the provenance and dataflow.
        var ret = new GraphMapping.ContextMapping();
        ret.contextId = contextScope.getId();
        ret.dataflowMapping = new DataflowMapping();
        ret.provenanceMapping = new ProvenanceMapping();
        return ret;
    }
}
