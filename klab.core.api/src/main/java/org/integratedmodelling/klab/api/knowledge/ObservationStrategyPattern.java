package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Objects of this class tagged with @ObservationStrategy will be collected,
 * matched and sorted to address observation strategy queries.
 * 
 * @author Ferd
 *
 */
public interface ObservationStrategyPattern {

	/**
	 * 
	 * @param observable
	 * @param scope
	 * @return
	 */
	boolean matches(Observable observable, ContextScope scope);

	/**
	 * An integer from 0 to 100. Only called if
	 * {@link #matches(Observable, ContextScope)} returns true.
	 * 
	 * @return
	 */
	int getCost(Observable observable, ContextScope scope);

	/**
	 * Build the actual observation strategy.
	 * 
	 * @return
	 */
	ObservationStrategy getStrategy(Observable observable, ContextScope scope);

}
