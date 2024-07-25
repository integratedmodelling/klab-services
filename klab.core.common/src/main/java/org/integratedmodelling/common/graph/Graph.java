package org.integratedmodelling.common.graph;

/**
 * Holds the record types corresponding to the GraphQL schema in resources.
 */
public class Graph {

    enum Status {WAITING, STARTED, FINISHED, ABORTED}

    enum Level {DEBUG, INFO, WARNING, ERROR}

    enum SemanticType {QUALITY, AGENT, SUBJECT, FUNCTIONAL_RELATIONSHIP, STRUCTURAL_RELATIONSHIP, BOND,
        EVENT, PROCESS, CONFIGURATION}

    enum LinkType {CHILD, PARENT, OBSERVER}

    enum ObservationType {SUBJECT, STATE, PROCESS, OBSERVER, EVENT, RELATIONSHIP}

    public record Link() {
    }

    public record Notification(Level level, String message, String mclass) {
    }

    public record ResolutionTask() {
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
                              Observable semantics, Status resolution, Geometry observerGeometry) {
    }

    public record ObservationInput() {
    }

    public record Dataflow() {
    }

    public record Actuator() {
    }

    public record ProvenanceNode() {
    }
}
