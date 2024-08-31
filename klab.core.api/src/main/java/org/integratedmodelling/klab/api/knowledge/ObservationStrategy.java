package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Objects of this class are read from specifications in the worldview, collected, matched and sorted to
 * address observation strategy queries from resolvers.
 *
 * @author Ferd
 */
public interface ObservationStrategy extends Knowledge {


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


    int rank();

    /**
     * @param observable
     * @param scope
     * @return
     */
    boolean matches(Observable observable, ContextScope scope);


    /**
     * An integer from 0 to 100, used to rank strategies <em>in context</em> among groups of strategies with
     * the same {@link #rank()}. Only called if {@link #matches(Observable, ContextScope)} returns true.
     *
     * @return
     */
    int getCost(Observable observable, ContextScope scope);

}
