package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.utils.Utils;

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


    public enum Status {WAITING, STARTED, FINISHED, ABORTED}

    public enum Level {DEBUG, INFO, WARNING, ERROR}

    public enum SemanticType {
        QUALITY, AGENT, SUBJECT, FUNCTIONAL_RELATIONSHIP, STRUCTURAL_RELATIONSHIP, BOND,
        EVENT, PROCESS, CONFIGURATION
    }

    public enum LinkType {CHILD, PARENT, OBSERVER}

    public enum ObservationType {SUBJECT, STATE, PROCESS, OBSERVER, EVENT, RELATIONSHIP}


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
                                   String observerGeometry,
                                   List<ResolutionConstraint> resolutionConstraints) {
    }

    public record Dataflow(String id, List<Actuator> actuators) {
    }

    public record Actuator(String id, Observable observable, List<Actuator> children) {
    }

    public record ProvenanceNode(String id) {
    }

    public static org.integratedmodelling.klab.api.knowledge.observation.Observation adapt(ObservationInput observationInput, Scope scope) {
        // TODO metadata
        var observable = scope.getService(Reasoner.class).resolveObservable(observationInput.observable());
        var geometry = org.integratedmodelling.klab.api.geometry.Geometry.create(observationInput.geometry());
        var pod = observationInput.defaultValue() == null ? null :
                  Utils.Data.asPOD(observationInput.defaultValue());
        var observerGeometry = observationInput.observerGeometry() == null ? null :
                               org.integratedmodelling.klab.api.geometry.Geometry.create(observationInput.observerGeometry());

        return DigitalTwin.createObservation(scope, observable, geometry, pod, observerGeometry);
    }

    public static ObservationInput adapt(org.integratedmodelling.klab.api.knowledge.observation.Observation observation, ContextScope scope) {
        // TODO needs model/resource URN and metadata
        return new ObservationInput(observation.getName(), observation.getObservable().getUrn(),
                observation.getGeometry().encode(), Utils.Data.asString(observation.getValue()),
                observation.getObserverGeometry() == null ? null :
                observation.getObserverGeometry().encode(), scope.getResolutionConstraints());
    }

}
