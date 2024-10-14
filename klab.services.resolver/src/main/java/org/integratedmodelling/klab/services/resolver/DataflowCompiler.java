package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.util.HashMap;
import java.util.Map;

public class DataflowCompiler {

    public Dataflow<Observation> compile(ResolutionGraph resolutionGraph, ContextScope scope) {

        if (resolutionGraph.isEmpty()) {
            return Dataflow.empty(Observation.class);
        }

        System.out.println(Utils.Graphs.dump(resolutionGraph.graph()));

        var ret = new DataflowImpl();
        for (var node : resolutionGraph.rootNodes()) {
            ret.getComputation().add(compileActuator(node, resolutionGraph));

        /*
        Compute total coverage (should be done during compilation) as intersection over the cache
        Set it into dataflow (if null, set, else intersect)
         */

        }

        return ret;
    }

    private Actuator compileActuator(Resolvable resolvable, ResolutionGraph resolutionGraph) {


        return null;

    }


    //    public Dataflow<Observation> compile(Resolvable knowledge, Resolution resolution, ContextScope scope) {
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
//                // keep the coverage in the dataflow aux data if it's not full, so that the runtime doesn't
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
//                            ret.setCoverage(((Model) resolved.getFirst()).getCoverage().as(Geometry.class));
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
