package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

public interface BoxingValueResolver<T> extends Contextualizer {

    /**
     * 
     * @param data
     * @param locator
     * @param call
     * @param scope
     * @return
     */
    T resolve(Parameters<String> data, Locator locator, ServiceCall call, ContextScope scope);
}
