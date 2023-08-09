package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;

public interface Resolver<T extends Observation> extends Contextualizer {

	/**
	 * 
	 * @param observation
	 * @param scope
	 * @return
	 */
	T resolve(T observation, ServiceCall call, ContextScope scope);
}
