package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A Compiler is instantiated in context. TODO should also take a parent Dataflow and fill the
 * catalog in from it.
 */
public class DataflowCompiler {

    private final ResolutionGraph resolutionGraph;
    private final ContextScope scope;
    private Set<Observation> catalog = new HashSet<>();

    /**
     * TODO add the context dataflow.
     *
     * @param resolutionGraph
     * @param scope
     */
    public DataflowCompiler(ResolutionGraph resolutionGraph, ContextScope scope) {
        this.resolutionGraph = resolutionGraph;
        this.scope = scope;
    }

    /**
     * Main entry point. When we resolve an ObservationStrategy from the runtime we should use the
     * correspondent worker below, after locating the context actuator.
     *
     * @return
     */
    public Dataflow<Observation> compile() {

        if (resolutionGraph.isEmpty()) {
            return Dataflow.empty(Observation.class);
        }

        System.out.println(Utils.Graphs.dump(resolutionGraph.graph()));

        Map<Observable, String> catalog = new HashMap<>();
        var ret = new DataflowImpl();
        for (var node : resolutionGraph.rootNodes()) {
            /*
            These MUST be observations. We check for now but it shouldn't happen.
             */
            if (!(node instanceof Observation observation)) {
                throw new KlabIllegalStateException("Resolution root is not an observation");
            }
            ret.getComputation().add(compileObservation(observation, Scale.create(observation.getGeometry()), null));
        }

        var out = new StringWriter();
        var diocan = new PrintWriter(out);
        new DataflowEncoder(ret, scope).encode(diocan);

        System.out.println(out.toString());

        return ret;
    }

    /**
     * The entry point is calling this with a null strategy for all root observation nodes. Otherwise locate
     * and contextualize the entry point and call one of the others on the correspondent actuator.
     *
     * @param observation
     * @param strategy
     * @return
     */

    Actuator compileObservation(Observation observation, Geometry coverage, ObservationStrategy strategy) {

        var ret = new ActuatorImpl();
        ret.setObservable(observation.getObservable());
        ret.setId(observation.getId());
        ret.setActuatorType(strategy == null ? Actuator.Type.RESOLVE : Actuator.Type.OBSERVE);
        ret.setCoverage(coverage.as(Geometry.class));

        if (strategy != null) {
            ret.setStrategyUrn(strategy.getUrn());
        }

        if (catalog.contains(observation)) {
            ret.setActuatorType(Actuator.Type.REFERENCE);
            return ret;
        }

        catalog.add(observation);

        for (var edge : resolutionGraph.graph().outgoingEdgesOf(observation)) {

            var child = resolutionGraph.graph().getEdgeTarget(edge);
            var childCoverage = edge.coverage;

            if (child instanceof ObservationStrategy observationStrategy) {
                compileStrategy(ret, observation, childCoverage, observationStrategy);
            }
        }

        return ret;
    }

    /**
     * The strategy produces model actuators within the observation's
     *
     * @param observationActuator
     * @param observation
     * @param scale
     * @param observationStrategy
     * @return
     */
    void compileStrategy(Actuator observationActuator, Observation observation, Geometry scale,
                         ObservationStrategy observationStrategy) {

        for (var edge : resolutionGraph.graph().outgoingEdgesOf(observationStrategy)) {

            var child = resolutionGraph.graph().getEdgeTarget(edge);
            var coverage = edge.coverage;

            if (child instanceof Model model) {
                compileModel(observationActuator, observation, coverage, observationStrategy, model);
            }
        }

        //  children: model (the plan for the resolution), Strategy (deferred)
        //            if model
        //    determine model coverage, intersect if needed
        //    pass to compileModel(actuator, obs, scale, strategy, model)
        //		if strategy
        //    compile into actuator for deferring at point of resolution (in computations)
        //
    }

    /**
     * Compile a model's actuators within the observation's under a strategy
     *
     * @param observationActuator
     * @param observation
     * @param scale
     * @param observationStrategy
     * @param model
     */
    void compileModel(Actuator observationActuator, Observation observation, Geometry scale,
                      ObservationStrategy observationStrategy, Model model) {

        for (var edge : resolutionGraph.graph().outgoingEdgesOf(model)) {

            var child = resolutionGraph.graph().getEdgeTarget(edge);
            var coverage = edge.coverage;

            if (child instanceof Observation dependentObservation) {
                observationActuator.getChildren().add(compileObservation(dependentObservation, coverage, observationStrategy));
            }
        }

        for (var contextualizer : model.getComputation()) {
            observationActuator.getComputation().add(adaptContextualizer(contextualizer));
        }

        //    compileModel(actuator, obs, scale, strategy, model)
        //    finds Observation: call compileObservation(obs, strategy),
        //    add all computations and any deferrals
    }

    /**
     * Turn each contextualizer into a runtime-supported call and return the call.
     *
     * @param contextualizer
     * @return
     */
    private ServiceCall adaptContextualizer(Contextualizable contextualizer) {
        if (contextualizer.getServiceCall() != null) {
            return contextualizer.getServiceCall();
        }
        // TODO the rest
        return null;
    }

}
