package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.impl.ObservationStrategyImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;

/**
 * Each observation strategy iterates to directives for the resolver. The resolver resolves the whole strategy returning
 * the coverage of the resolution. The directives are applied in turn and each is executed in the context of the
 * observation resulting from the previous one; REIFY and CLASSIFY directives cause a deferred resolution, encoding an
 * observable or observable pattern that must be resolved within the context of the actual result of the previous
 * operation, causing the resolver to send the dataflow for execution with the deferred observable in it, and a trip
 * back to the resolver from the runtime. In these cases the dataflow node will be the observable or observable pattern,
 * and the link to the dependent knowledge will be of
 * {@link org.integratedmodelling.klab.api.services.resolver.Resolution.ResolutionType#DEFER_INHERENCY} or
 * {@link org.integratedmodelling.klab.api.services.resolver.Resolution.ResolutionType#DEFER_SEMANTICS} type.
 */
public interface ObservationStrategy extends Iterable<Pair<ObservationStrategy.Operation, ObservationStrategy.Arguments>> {

    interface Builder {
        ObservationStrategy build();
    }

    public record Arguments(Observable observable, ServiceCall serviceCall) {}

    enum Operation {

        /**
         * the strategy implies the direct resolution of an observable
         */
        RESOLVE,

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
