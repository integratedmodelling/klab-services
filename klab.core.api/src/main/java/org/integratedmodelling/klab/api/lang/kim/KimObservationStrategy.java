package org.integratedmodelling.klab.api.lang.kim;

//import org.integratedmodelling.klab.api.collections.Literal;

import org.integratedmodelling.klab.api.lang.ServiceCall;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Observation strategy, syntactic form parsed from the language beans. These are hosted in the resources
 * service and are ingested by the reasoner along with the worldview.
 * <p>
 * Each strategy is independent and the containing document is a simple container without scoping rules or *
 * anything, so we don't preserve the container and we just add the source code to each strategy for *
 * reference and documentation.
 */
public interface KimObservationStrategy extends KlabStatement {

    /**
     * Descriptors for all methods that must be implemented as strategy functors. The dot-separated lowercase
     * call IDs in the language will be turned into camelcase and matched to this enum.
     * <p>
     * TODO each type must specify number/type of arguments, number/type of outputs, default parameter
     *  (this:This) and description
     */
    enum Functor {
        Concrete,
        AritySingle,
        ArityMulti,
        SplitOperator,
        ObjectsMerge,
        ObjectsFilter,
        TypeUnion,
        TypeIntersection,
        SplitPredicate,
        SplitRole,
        SplitIdentity,
        SplitAttribute,
        BaseObservable,
        // TODO extractors for all operands and semantic roles
        RelationshipSource,
        RelationshipTarget,
        // Operators taking another functor and an argument, translated from the infix notation in the
        // language
        EqualityOperator,
        GTOperator,
        GEOperator,
        LTOperator,
        LEOperator,
        ISOperator
    }


    /**
     * Any filter provided to condition the applicability of the strategy. Normally only one of the fields is
     * provided.
     */
    interface Filter extends Serializable {

        boolean isNegated();

        KimObservable getMatch();

        ServiceCall getFunction();

        Object getLiteral();
    }

    /**
     * The fields in the operations are redundant as some are only used in specific types.
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
         * For OBSERVE and RESOLVE. The optional character of the observable determines what to do in case of
         * insufficient coverage. Like in filters, the stated observable is a pattern that will get incarnated
         * into the specific resolution observable by the reasoner.
         *
         * @return
         */
        KimObservable getObservable();

        /**
         * For APPLY. Each function applies to the "current" observation, which is mandatorily its first
         * implicit argument, and returns its next state.
         *
         * @return
         */
        List<ServiceCall> getFunctions();

        /**
         * Deferred strategies will be resolved again in the context returned by a previous successful
         * resolution.
         * <p>
         * Multiple deferred strategies should be tried in order of presentation until full coverage is
         * achieved.
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
     * Using filters also to produce the values for the <code>let</code> expression, which could be literals,
     * functions or concepts to match. The keys could be symbols or lists thereof, being admitted for a
     * function to return tuples.
     *
     * @return
     */
    Map<KimLiteral, Filter> getMacroVariables();

    /**
     * No strategy makes sense unless it has 1+ operations associated.
     *
     * @return
     */
    List<Operation> getOperations();
}
