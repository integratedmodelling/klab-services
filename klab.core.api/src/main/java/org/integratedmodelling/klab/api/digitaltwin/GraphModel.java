package org.integratedmodelling.klab.api.digitaltwin;

import java.util.List;

/**
 * Holds the types for the digital twin graph model and the GraphQL schema. Any changes in these must be
 * coordinated with the GraphQL schema adopted by the runtime.
 */
public class GraphModel {

    /**
     * All textual queries needed to interrogate and modify the graph model.
     */
    public interface Queries {

        interface GraphQL {

            String OBSERVE = """
                    mutation Observe {
                        observe(observation: $observation)
                    }
                    """;

        }
    }


    enum Status {WAITING, STARTED, FINISHED, ABORTED}

    enum Level {DEBUG, INFO, WARNING, ERROR}

    enum SemanticType {
        QUALITY, AGENT, SUBJECT, FUNCTIONAL_RELATIONSHIP, STRUCTURAL_RELATIONSHIP, BOND,
        EVENT, PROCESS, CONFIGURATION
    }

    enum LinkType {CHILD, PARENT, OBSERVER}

    enum ObservationType {SUBJECT, STATE, PROCESS, OBSERVER, EVENT, RELATIONSHIP}


    public record Link(String sourceId, String targetId, LinkType type) {
    }

    public record Notification(Level level, String message, String mClass, String taskId) {
    }

    public record ResolutionTask(String id, Double start, Double end, Status status,
                                 List<Notification> notifications, List<ResolutionTask> children) {
    }

    public record Grid(int xCells, int yCells, double x1, double x2, double y1, double y2) {
    }

    public record Time(double start, double end) {
    }

    public record Geometry(int multiplicity, String shape, Grid grid, String projection, Time time) {
    }

    public record Observable(String semantics, boolean collective, String referenceName,
                             SemanticType baseType) {
    }

    public record Observation(String id, String name, ObservationType type, Geometry geometry,
                              Observable semantics, Status resolution, Geometry observerGeometry,
                              int nChildren) {
    }

    public record ObservationInput(String name, String observable, String geometry, String defaultValue,
                                   String observerGeometry) {
    }

    public record Dataflow(String id, List<Actuator> actuators) {
    }

    public record Actuator(String id, Observable observable, List<Actuator> children) {
    }

    public record ProvenanceNode(String id) {
    }
}
