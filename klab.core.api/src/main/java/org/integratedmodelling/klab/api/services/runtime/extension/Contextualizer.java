package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Contextualizers should be static, public classes annotated with {@link KlabFunction} and embedded
 * in static superclasses tagged with {@link @Library} that provide their namespace. The
 * contextualizer prototypes and versions will be reported in the runtime's capabilities.
 * <p>
 * If a contextualizer has a constructor that takes a {@link ServiceCall} and a
 * {@link ContextScope}, it will be called with the declaring function call and the current scope at
 * initialization (and the any reentrant=true declaration will be ignored).
 * 
 * @author Ferd
 *
 */
public interface Contextualizer {

}
