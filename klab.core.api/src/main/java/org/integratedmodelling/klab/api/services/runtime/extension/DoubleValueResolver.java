package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Use this whenever the values are known to be floating point. Using it doesn't guarantee the
 * absence of boxing in the contexualization chain: all other contextualizers and the storage also
 * must be non-boxing.
 * 
 * @author mario
 *
 */
public interface DoubleValueResolver extends Contextualizer {

    /**
     *
     * @param data
     * @param locator
     * @param call
     * @param scope
     * @return
     */
    double resolve(Parameters<String> data, Locator locator, ServiceCall call, ContextScope scope);
}
