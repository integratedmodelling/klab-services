package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.lang.ServiceCall;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public interface KimObservationStrategy extends KimStatement {

    /**
     * Any filter provided to condition the applicability of the strategy. Normally
     * only one of the fields is provided.
     */
    interface Filter extends Serializable {

        boolean isNegated();

        KimObservable getMatch();

        ServiceCall getFunction();

        Literal getLiteral();
    }

    /**
     * The fields in the operations are redundant as some are only used in specific
     * types.
     */
    interface Operation extends Serializable {

        enum Type {
            RESOLVE, OBSERVE, APPLY
        }

        /**
         * Always not null.
         *
         * @return
         */
        Type getType();

        /**
         * For OBSERVE and RESOLVE. The optional character of the observable determines
         * what to do in case of insufficient coverage.
         *
         * @return
         */
        KimObservable getObservable();

        /**
         * For APPLY. Each function applies to the "current" observation, which is
         * mandatorily its first implicit argument, and returns its next state.
         *
         * @return
         */
        List<ServiceCall> getFunctions();

        /**
         * Deferred strategies will be resolved again in the context returned by a
         * previous successful resolution.
         *
         * Multiple deferred strategies should be tried in order of presentation until
         * full coverage is achieved.
         *
         * @return
         */
        List<KimObservationStrategy> getDeferredStrategies();
    }

    /**
     * Name of this strategy
     *
     * @return
     */
    String getName();

    /**
     * Name of this strategy
     *
     * @return
     */
    String getDescription();

    /**
     * Priority rank of this strategy.
     *
     * @return
     */
    int getRank();

    /**
     * Filters that determine the applicability of the strategy
     *
     * @return
     */
    List<Filter> getFilters();

    /**
     * Using filters also to produce the values for the <code>let</code> expression,
     * which could be literals, functions or concepts to match. The keys could be
     * symbols or lists thereof, being admitted for a function to return tuples.
     *
     * @return
     */
    Map<Literal, Filter> getMacroVariables();

    /**
     * No strategy makes sense unless it has 1+ operations associated.
     *
     * @return
     */
    List<Operation> getOperations();
}
