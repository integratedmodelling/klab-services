package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

public interface Resolver<T extends Observation> extends Contextualizer {

	/**
	 * 
	 * @param observation
	 * @param scope
	 * @return
	 */
	T resolve(T observation, ContextScope scope);
}
