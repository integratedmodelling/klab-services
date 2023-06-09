package org.integratedmodelling.klab.api.knowledge;

import java.util.List;

import org.integratedmodelling.klab.api.lang.Contextualizable;

/**
 * Resolution strategy for a given observable. One or more of these are defined by the reasoner in
 * increasing order of cost and/or complexity. Unless the observable is abstract, the first will
 * always be the direct observation of the observable with no further computations.
 * 
 * @author mario
 *
 */
public interface ObservationStrategy {

    enum Type {
        /**
         * the strategy implies the direct observation of the observable, with or without runtime
         * recontextualization
         */
        DIRECT,
        /**
         * The strategy implies the observation of other direct observables, then the application of
         * the child strateg(ies) to each, then the computations.
         */
        DEREIFYING
    }

    /**
     * 
     * @return
     */
    Type getType();

    /**
     * 
     * @return
     */
    Observable getObservable();

    /**
     * Strategies to apply to the instantiated observable after resolution and before the
     * computations are applied.
     * 
     * @return
     */
    List<ObservationStrategy> getChildStrategies();

    /**
     * 
     * @return
     */
    List<Contextualizable> getComputations();

}
