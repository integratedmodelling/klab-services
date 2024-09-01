package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Objects of this class are read from specifications in the worldview, collected, matched and sorted to
 * address observation strategy queries from resolvers. The observation strategy is just a container for a
 * computational strategy and does not contain the filters or variables used to assess the matching. It is
 * serializable and is built on demand based on the
 * {@link org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy} ingested by the reasoner, then
 * sent to the resolver after with the observables relevant to the actual resolution in lieu of the patterns
 * specified in the language.
 *
 * @author Ferd
 */
public interface ObservationStrategy extends Knowledge {



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
