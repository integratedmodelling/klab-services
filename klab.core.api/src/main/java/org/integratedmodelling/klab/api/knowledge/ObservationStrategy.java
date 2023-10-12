package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.impl.ObservationStrategyImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.io.Serializable;

/**
 * Each observation strategy iterates to directives for the resolver, each consisting of a type and an
 * argument. The resolver resolves the whole strategy returning the final coverage of the resolution.
 * <p>
 * The directives are applied in turn and each is executed in the context of the observation resulting from
 * the previous one. REIFY and CLASSIFY directives cause a deferred resolution, which resolves an intermediate
 * observable and then encodes an observable or observable pattern that must be resolved within the context of
 * the result of the previous operation, causing the resolver to send the dataflow for execution with the
 * deferred observable in it, followed by a trip back to the resolver from the runtime. In these cases the
 * resolution graph will link to the observable or observable pattern in lieu of a model, and the link to the
 * dependent observable will be of
 * {@link org.integratedmodelling.klab.api.services.resolver.Resolution.ResolutionType#DEFER_INHERENCY} or
 * {@link org.integratedmodelling.klab.api.services.resolver.Resolution.ResolutionType#DEFER_SEMANTICS} type.
 * The dataflow is populated with the actuators of the secondary resolution when that is done, and coverage
 * isn't known until the process is completed.
 */
public interface ObservationStrategy extends Serializable, Iterable<Pair<ObservationStrategy.Operation,
        ObservationStrategy.Arguments>> {

    interface Builder {

        Builder withOperation(Operation operation, Observable target);

        Builder withOperation(Operation operation, ServiceCall target);

        Builder withStrategy(Operation operation, ObservationStrategy strategy);

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
         * ObservationStrategy for a different observable. After the resolution of the deferred observable,
         * the operations in the deferred strategy build the original observations.
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
    int getRank();

    static ObservationStrategy.Builder builder(Observable observable) {
        var ret = new ObservationStrategyImpl.Builder();
        ret.setOriginalObservable(observable);
        return ret;
    }

}
