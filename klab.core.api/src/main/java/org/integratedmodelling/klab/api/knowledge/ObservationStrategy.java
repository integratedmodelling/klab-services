package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Objects of this class are read from specifications in the worldview,
 * collected, matched and sorted to address observation strategy queries.
 * <p>
 * TODO experimental, unused
 *
 * @author Ferd
 */
public interface ObservationStrategy extends Knowledge {

    /**
     * @param observable
     * @param scope
     * @return
     */
    boolean matches(Observable observable, ContextScope scope);

    /**
     * An integer from 0 to 100. Only called if {@link #matches(Observable, ContextScope)} returns true.
     *
     * @return
     */
    int getCost(Observable observable, ContextScope scope);

}
