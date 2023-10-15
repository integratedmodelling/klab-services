package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.impl.ObservationStrategyImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.io.Serializable;

/**
 * Each observation strategy provides directives for the resolver to resolve a specified observable. The
 * reasoner defines the possible strategies for a specified observable in a given context
 * ({@link org.integratedmodelling.klab.api.services.Reasoner#inferStrategies(Observable, ContextScope)} and
 * ranks them in order of cost. The resolver tries to resolve them in increasing cost order, using the
 * resulting coverage to decide when to stop.
 * <p>
 * The strategy specifies the main observable it handles and iterates to a set of operations, which can be of
 * a few types. RESOLVE strategies instruct the resolver to find one or more models for the observable
 * associated with the operation. APPLY strategies request the compilation of a particular service call, which
 * must be known to the resolver through a core library implementation recognized by the runtime.  The first
 * strategy for a concrete observable X should always be its direct resolution, i.e. RESOLVE X, with cost ==
 * 0.
 * <p>
 * Operations of type DEFER are associated with another ObservationStrategy, whose
 * {@link ObservationStrategy#getOriginalObservable()} specifies a different observable that the resolver must
 * defer to. These are recorded in the dataflow so that the runtime can ask the resolver again after the
 * "deferring" observation has been made, to provide the context observation for the {@link ContextScope} in
 * which to resolve the deferred strategy. The body of the deferred strategy specifies the operations needed
 * to produce the intended original observation. Because the resolution of the deferred strategy is contextual
 * to the result of the first observation, there is no guarantee that it will succeed. For this reason
 * observation strategies that imply deferral should never have cost == 0 or their observable should be
 * optional. If the , the resulting dataflow will be appended to the actuator, substituting the deferral and
 * completing the dataflow. Dataflows that still contain deferred strategies are not self-consistent and
 * cannot be saved as resources.
 * <p>
 * The deferral mechanism allows inferred observations to be made incrementally. For example a classified
 * object with multiple classification traits can be first observed by deferring to an instantiator and a
 * classifier for the first trait, leaving the second trait in the observable for the first deferral, which
 * will in turn trigger deferral of the second if a direct observation is not possible.
 * <p>
 * The {@link ObservationStrategyPattern} class is a placeholder for plug-in observation strategy patterns
 * that can be matched to observables and produce strategies to observe them. These would be supplied
 * externally instead of hard-coded and loaded on startup or upon plug-in initialization. These aren't used at
 * this time, but they are in the code because it would be very useful to contribute strategies along with
 * plug-ins and specialized service calls. In case these are implemented, the built-in strategies should be
 * collected in declarative form and supplied with the core implementation as resources.
 */
public interface ObservationStrategy extends Serializable, Knowledge,
        Iterable<Pair<ObservationStrategy.Operation,
        ObservationStrategy.Arguments>> {

    interface Builder {

        Builder withOperation(Operation operation, Observable target);

        Builder withOperation(Operation operation, ServiceCall target);

        Builder withStrategy(Operation operation, ObservationStrategy strategy);

        Builder withCost(int cost);

        ObservationStrategy build();

    }

    // Only one of these at a time.
    public record Arguments(Observable observable, ServiceCall serviceCall,
                            ObservationStrategy contextualStrategy) implements Serializable {
    }

    enum Operation {

        /**
         * The operation requires the direct resolution of the associated observable. i.e. looking up a model
         * or a previous observation, without going through the resolver for a strategy again. The 0-rank,
         * default strategy for any concrete observable X is always RESOLVE X.
         */
        RESOLVE,

        /**
         * the operation implies deferral, i.e. further resolution of a different observable, finding the
         * associated ObservationStrategies for it. The data associated to the operation is another
         * ObservationStrategy for a different observable. The deferred strategy is inserted in the dataflow
         * using a {@link org.integratedmodelling.klab.api.services.runtime.Actuator#DEFERRED_STRATEGY_CALL}
         * call. After the resolution of the deferring observable, the operations in the deferred strategy
         * build the original observations.
         */
        DEFER,

        /**
         * The operation consists of the application of a contextualizer to the result of the previous
         * operation. The applied contextualizer is specified by the associated {@link ServiceCall} from the
         * core library. Scalar functors will be joined into a chain compiled by the runtime into a
         * Java/Groovy class and executed in parallel as configured, to avoid having to keep intermediate
         * states.
         */
        APPLY,

        /**
         * This is used as the operation in a deferral associated with an observable for the semantic
         * characterizer that will build the concrete qualities associated with an original generic quality.
         * Those are then deferred to independently, with different sub-contexts (where...) as implied by the
         * resulting category values. The associated data is the characterization observable and the operation
         * is the concretization of the original one.
         */
        CONCRETIZE,

    }

    /**
     * The original observable is either the one being resolved when the strategy is created by the reasoner,
     * or the one to defer resolution to when the strategy is the argument of a DEFER operation.
     *
     * @return
     */
    Observable getOriginalObservable();

    /**
     * Ranks start from 0 (best) and move on to indicate more and more complex and/or valuable strategies, so
     * they can be executed in sequence.
     * {@link org.integratedmodelling.klab.api.services.Reasoner#inferStrategies(Observable, ContextScope)}
     * will return the strategies in rank order, but those with the same rank are equivalent and can be
     * resolved in parallel if needed.
     *
     * @return
     */
    int getCost();

    static ObservationStrategy.Builder builder(Observable observable) {
        var ret = new ObservationStrategyImpl.Builder();
        ret.setOriginalObservable(observable);
        return ret;
    }

}
