package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.impl.ObservationStrategyImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;

/**
 * Each observation strategy iterates to directives for the resolver, each consisting of a type and an argument. The
 * resolver resolves the whole strategy returning the final coverage of the resolution.
 * <p>
 * The directives are applied in turn and each is executed in the context of the observation resulting from the previous
 * one. REIFY and CLASSIFY directives cause a deferred resolution, which resolves an intermediate observable and then
 * encodes an observable or observable pattern that must be resolved within the context of the result of the previous
 * operation, causing the resolver to send the dataflow for execution with the deferred observable in it, followed by a
 * trip back to the resolver from the runtime. In these cases the resolution graph will link to the observable or
 * observable pattern in lieu of a model, and the link to the dependent observable will be of
 * {@link org.integratedmodelling.klab.api.services.resolver.Resolution.ResolutionType#DEFER_INHERENCY} or
 * {@link org.integratedmodelling.klab.api.services.resolver.Resolution.ResolutionType#DEFER_SEMANTICS} type. The
 * dataflow is populated with the actuators of the secondary resolution when that is done, and coverage isn't known
 * until the process is completed.
 */
public interface ObservationStrategy extends Iterable<Pair<ObservationStrategy.Operation,
        ObservationStrategy.Arguments>> {

    interface Builder {
        ObservationStrategy build();
    }

    public record Arguments(Observable observable, ServiceCall serviceCall) {
    }

    enum Operation {

        /**
         * the strategy implies further resolution of the associated observable
         */
        RESOLVE,

        /**
         * The strategy requires the observation of the associated observable. i.e. looking up a model or a previous
         * observation, without further resolving it.
         */
        OBSERVE,

        /**
         * The strategy implies the instantiation of other direct observables, then the application of the child
         * strateg(ies) to each of the results.
         */
        REIFY,

        APPLY,

        /**
         * Extract all traits from
         */
        CHARACTERIZE,

        CLASSIFY,

        FILTER

    }

    /**
     * The original observable that this strategy applies to.
     *
     * @return
     */
    Observable getObservable();

    static ObservationStrategy.Builder builder(Observable observable) {
        return new ObservationStrategyImpl.Builder(observable);
    }

}
