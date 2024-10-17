package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataflowCompiler {

    public Dataflow<Observation> compile(ResolutionGraph resolutionGraph, ContextScope scope) {

        if (resolutionGraph.isEmpty()) {
            return Dataflow.empty(Observation.class);
        }

        System.out.println(Utils.Graphs.dump(resolutionGraph.graph()));

        Map<Observable, String> catalog = new HashMap<>();
        var ret = new DataflowImpl();
        for (var node : resolutionGraph.rootNodes()) {
            ret.getComputation().addAll(compileActuator(node, resolutionGraph, null, null, null, catalog));
        /*
        Compute total coverage (should be done during compilation) as intersection over the cache
        Set it into dataflow (if null, set, else intersect)
         */

        }

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

    Actuator compileObservation(Observation observation, ObservationStrategy strategy) {

        //    a. scan children:
        //    i. coverage is in the edge linking to the strategy
        //    ii. resolvable is strategy (must be for observation)
        //    iii. when strategy exists and isn't empty, make actuator with obs ID and Observable
        //    iv. pass it to compileStrategy(actuator, obs, scale, strategy)


        return null;
    }

    Actuator compileStrategy(Actuator observationActuator, Observation observation, Scale scale,
                             ObservationStrategy observationStrategy) {

        //  children: model (the plan for the resolution) or Strategy (deferred)
        //            if model
        //    determine model coverage, intersect if needed
        //    pass to compileModel(actuator, obs, scale, strategy, model)
        //		if strategy
        //    compile into actuator for deferring at point of resolution (in computations)
        //

        return null;
    }

    Actuator compileModel(Actuator observationActuator, Observation observation, Scale scale,
                          ObservationStrategy observationStrategy, Model model) {

        //    compileModel(actuator, obs, scale, strategy, model)
        //    finds Observation: call compileObservation(obs, strategy),
        //    add all computations and any deferrals

        return null;
    }

    //    (OBS) (O) earth:Region [#-1]
    //            (S) urn='substantial.direct', operations=[OBSERVE (O) earth:Region]
    //            (M) staging.vxii.basic.region-resolver
    //            (OBS) (O) geography:Elevation [#61920]
    //            (S) urn='dependent.direct', operations=[OBSERVE (O) geography:Elevation]
    //            (M) staging.vxii.basic.elevation-resolver
    //            (OBS) (O) geography:Slope [#61920]
    //            (S) urn='dependent.direct', operations=[OBSERVE (O) geography:Slope]
    //            (M) staging.vxii.basic.slope-resolver
    //
    //
    //	1. call compileObservation(obs, strategy = null)
    //
    //    compileStrategy(actuator, obs, scale, strategy)
    //
    //
    //    each Compile returns ONE actuator and adds those from the children

    private List<Actuator> compileActuator(Resolvable resolvable, ResolutionGraph resolutionGraph,
                                           Coverage geometry, Observation observation, String strategyUrn,
                                           Map<Observable, String> catalog) {
        List<Actuator> ret = new ArrayList<>();
        if (resolvable instanceof ObservationStrategy observationStrategy) {

            if (geometry.isEmpty()) {
                // TODO defer
            }

            for (var edge : resolutionGraph.graph().incomingEdgesOf(resolvable)) {
                ret.addAll(compileActuator(resolutionGraph.graph().getEdgeSource(edge), resolutionGraph,
                        geometry, observation, observationStrategy.getUrn(), catalog));
            }
        } else if (resolvable instanceof Observation obs) {
            for (var edge : resolutionGraph.graph().incomingEdgesOf(resolvable)) {
                ret.addAll(compileActuator(resolutionGraph.graph().getEdgeSource(edge), resolutionGraph,
                        Coverage.create(Scale.create(obs.getGeometry()), 1.0), obs, strategyUrn, catalog));
            }
        } else if (resolvable instanceof Model model) {
            /*
            at this point the resolvable can only be a model
             */
            ActuatorImpl actuator = new ActuatorImpl();

            actuator.setStrategyUrn(strategyUrn);
        }

        return ret;

    }


    //    public Dataflow<Observation> compile(Resolvable knowledge, Resolution resolution, ContextScope
    //    scope) {
    //
    //        DataflowImpl ret = new DataflowImpl();
    //        Actuator rootActuator = null;
    //        Map<String, Actuator> compiled = new HashMap<>();
    //
    //        for (Pair<Resolvable, Coverage> root : resolution.getResolution()) {
    //
    //            if (root.getFirst() instanceof Model) {
    //                var actuator = compileActuator((Model) root.getFirst(), resolution, root.getSecond(),
    //                        resolution.getResolvable(),
    //                        null,
    //                        ret, compiled, scope);
    //                if (rootActuator == null) {
    //                    ret.getComputation().add(actuator);
    //                } else {
    //                    rootActuator.getChildren().add(actuator);
    //                }
    //            }
    //        }
    //
    //        ret.computeCoverage();
    //
    //        return ret;
    //    }
    //
    //    private ActuatorImpl compileActuator(Model model, Resolution resolution, Coverage coverage,
    //                                         Resolvable resolvable,
    //                                         Actuator parentActuator, Dataflow dataflow,
    //                                         Map<String, Actuator> compiled, ContextScope scope) {
    //
    //        if (resolvable instanceof Observable observable) {
    //
    //            ActuatorImpl ret = null;
    //            var id = createId(observable, scope);
    //
    //            if (compiled.containsKey(observable.getReferenceName())) {
    //                ret = compileReference(compiled.get(observable.getReferenceName()));
    //            } else {
    //
    //                ret = new ActuatorImpl();
    //
    //                // keep the coverage in the dataflow aux data if it's not full, so that the runtime
    //                doesn't
    //                // have to
    //                // recalculate it.
    //                if (coverage.getCoverage() < 1) {
    //                    dataflow.getResources().put(id + "_coverage", coverage);
    //                }
    //
    //                // dependencies first
    //                for (ResolutionType type : new ResolutionType[]{ResolutionType.DIRECT,
    //                                                                ResolutionType.DEFER_INHERENCY,
    //                                                                ResolutionType.DEFER_SEMANTICS}) {
    //                    for (Triple<Resolvable, Resolvable, Coverage> resolved : resolution.getResolving(
    //                            model,
    //                            type)) {
    //                        // alias is the dependency getName()
    //                        if (resolved.getFirst() instanceof Model m) {
    //                            var dependency = compileActuator(m, resolution, resolved.getThird(),
    //                                    resolved.getSecond(), ret, dataflow,
    //                                    compiled, scope);
    //                            ret.getChildren().add(dependency);
    //                            ret.setCoverage(((Model) resolved.getFirst()).getCoverage().as(Geometry
    //                            .class));
    //                        } else if (resolved.getFirst() instanceof Observable o) {
    //                            //  ret.getDeferrals().add(Pair.of(type, o));
    //                        }
    //                    }
    //                }
    //            }
    //
    //            // self
    //            for (Contextualizable computation : model.getComputation()) {
    //
    //                /*
    //                 * TODO establish which components are needed to satisfy the call, and make the
    //                 * dataflow empty if the resource services in the scope do not locate a component.
    //                 * (shouldn't happen as the models shouldn't be resolved in that case). Versions
    //                 * should also be collected.
    //                 */
    //
    //                ret.getComputation().add(getServiceCall(computation));
    //            }
    //
    //            // GAAAH
    //            //            ret.setId(id);
    //            ret.setName(observable.getName());
    //            ret.setAlias(observable.getStatedName());
    //
    //            // add to hash
    //            compiled.put(observable.getReferenceName(), ret);
    //
    //            // filters apply to references as well
    //            for (Triple<Resolvable, Resolvable, Coverage> resolved : resolution.getResolving(
    //                    model,
    //                    ResolutionType.FILTER)) {
    //                // TODO
    //            }
    //
    //            ret.setObservable(observable);
    //            ret.setAlias(observable.getName());
    //
    //            return ret;
    //        }
    //
    //        // TODO
    //        return null;
    //    }
    //
    //    private ServiceCall getServiceCall(Contextualizable computation) {
    //        if (computation.getServiceCall() != null) {
    //            return computation.getServiceCall();
    //        }
    //        return null;
    //    }

    /*
     * The actuator ID is also the observation ID when the actuator runs. It reflects the context
     * observation and all the key fields of the observable (description type, semantics and
     * observer) and becomes the ID of the observation created (instantiators will add a sequential
     * number to the instance container's ID). A simple UUID would also work here, but this actually
     * describes it accurately and is useful for debugging. It does come out veeeery long though, so
     * we may reconsider later.
     *
     * IDs are not saved with the k.DL-serialized dataflow, so the implementation should never rely
     * on the ID no matter what generated it.
     */
    private String createId(Observable observable, ContextScope scope) {
        return (scope.getContextObservation() == null ? "" : (scope.getContextObservation().getId() + "."))
                + observable.getDescriptionType().name().toLowerCase() + "." + observable.getReferenceName() + "."
                + (observable.getObserver() == null ? scope.getIdentity().getId() :
                   ("_as_" + observable.getObserver().codeName()));
    }

    /**
     * Create a reference to the passed actuator
     *
     * @param actuator
     * @return
     */
    private ActuatorImpl compileReference(Actuator actuator) {
        ActuatorImpl ret = new ActuatorImpl();
        ret.setId(actuator.getId());
        ret.setReference(true);
        return ret;
    }
}
