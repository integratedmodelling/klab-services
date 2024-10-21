package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Contextualizers should be static, public classes annotated with {@link KlabFunction} embedded in static
 * superclasses tagged with {@link @Library} that provide the namespace for the associated function. These are
 * implemented and deployed as components which are served by the
 * {@link org.integratedmodelling.klab.api.services.ResourcesService} to the
 * {@link org.integratedmodelling.klab.api.services.RuntimeService} that needs it.
 * <p>
 * If a contextualizer has a constructor that takes a {@link ServiceCall} and a {@link ContextScope}, that
 * constructor will be called at initialization with the declaring function call so that state can be kept
 * across calls. Otherwise the reentrant attribute in the tagging function will be used to decide if the
 * contextualizer should be kept around across calls or not.
 *
 * @author Ferd
 */
public interface Contextualizer {

}
