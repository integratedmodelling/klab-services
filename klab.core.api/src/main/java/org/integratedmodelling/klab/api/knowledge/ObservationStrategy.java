package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.util.List;

/**
 * The operational side of the observation strategy, coming from the reasoner after the matching to observable
 * and context has been done. Compared to the syntactic form
 * {@link org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy}, this only needs to expose the
 * operations and the identifiers/documentation for provenance; cost and other informations are only used for
 * reporting, as the observation reasoner returns the matching strategies in order of rank and cost.
 *
 * @author Ferd
 */
public interface ObservationStrategy extends Knowledge {

    /**
     * The definition of one or more operations that compose the strategy. Different operations will leave
     * different "states" on the contextualization stack for the next operation to use; the last one produces
     * the final observation.
     */
    interface Operation {

        /**
         * The type of operation to perform - either observation of the observable (looking for a model),
         * recursive resolution of a different observable, or application of a contextualizable.
         *
         * @return
         */
        KimObservationStrategy.Operation.Type getType();

        /**
         * The observable for OBSERVE and RESOLVE types, computed from the patterns in the syntactic form
         * applied to observable and scope.
         *
         * @return
         */
        Observable getObservable();

        /**
         * The contextualizable(s) for APPLY types, of which the resolver will determine accessibility.
         *
         * @return
         */
        List<Contextualizable> getContextualizables();

        /**
         * Strategies to trigger on the contextualized result of the operation, if any. For example, resolve
         * the individual observations created in a collective observation.
         * <p>
         * Each of the returned strategies is a list of contextualized operations; they represent alternatives
         * of increasing cost, i.e. resolution is complete after the first one succeeds.
         *
         * @return
         */
        List<List<Operation>> getContextualStrategies();
    }

    /**
     * The operations involved in the actual execution of the strategy. The resolver establishes if these are
     * applicable to the context..
     *
     * @return
     */
    List<Operation> getOperations();

    /**
     * The compiled Markdown/AsciiDoc documentation relative to the observable being resolved by this
     * strategy, ready for inclusion in documentation and provenance. This is obtained in each returned
     * strategy by computing the template provided as documentation for the strategy in its source form.
     *
     * @return
     */
    String getDocumentation();

    /**
     * Exposed for documentation and use by the reasoner.
     *
     * @return
     */
    String getNamespace();

    /**
     * Rank is only used for informational purposes.
     *
     * @return
     */
    int getRank();

}
